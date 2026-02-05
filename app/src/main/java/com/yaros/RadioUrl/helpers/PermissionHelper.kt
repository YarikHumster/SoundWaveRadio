package com.yaros.RadioUrl.helpers

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.yaros.RadioUrl.R
import timber.log.Timber
import java.lang.ref.WeakReference
import androidx.core.content.edit
import androidx.core.net.toUri

object PermissionHelper {

    private const val PREF_NAME = "PermissionPrefs"
    private const val PREF_UNIFIED_DIALOG_SHOWN = "unified_dialog_shown"
    private const val MAX_DIALOG_SHOW_ATTEMPTS = 2

    fun checkEssentialPermissions(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (shouldShowDialog(activity, prefs)) {
            showPermissionExplanationDialog(activity, prefs)
        }
    }

    private fun shouldShowDialog(context: Context, prefs: SharedPreferences): Boolean {
        return !prefs.getBoolean(PREF_UNIFIED_DIALOG_SHOWN, false) &&
                (needsNotificationPermission(context) || needsBatteryOptimization(context))
    }

    private fun needsNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun needsBatteryOptimization(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) == false)
    }

    private fun showPermissionExplanationDialog(activity: Activity, prefs: SharedPreferences) {
        val dialogShownCount = prefs.getInt("${PREF_UNIFIED_DIALOG_SHOWN}_count", 0)
        if (dialogShownCount >= MAX_DIALOG_SHOW_ATTEMPTS) return

        val weakActivity = WeakReference(activity)
        AlertDialog.Builder(activity).apply {
            setTitle(R.string.permission_required_title)
            setMessage(buildExplanationMessage(activity))
            setPositiveButton(R.string.grant_permission) { dialog, _ ->
                logEvent("permission_dialog_accepted", mapOf(
                    "attempt" to dialogShownCount,
                    "permissions" to getRequiredPermissions(activity)
                ))
                weakActivity.get()?.let { handlePermissionGrant(it, prefs, dialogShownCount) }
                dialog.dismiss()
            }
            setNegativeButton(R.string.remind_later) { dialog, _ ->
                logEvent("permission_dialog_deferred", mapOf(
                    "attempt" to dialogShownCount,
                    "permissions" to getRequiredPermissions(activity)
                ))
                prefs.edit { putInt("${PREF_UNIFIED_DIALOG_SHOWN}_count", dialogShownCount + 1) }
                dialog.dismiss()
            }
            setOnDismissListener {
                logEvent("permission_dialog_dismissed")
            }
            setCancelable(false)
            show()

            logEvent("permission_dialog_shown", mapOf(
                "attempt" to dialogShownCount,
                "permissions" to getRequiredPermissions(activity))
            )
        }
    }

    private fun buildExplanationMessage(context: Context): String {
        val sb = StringBuilder()
        if (needsNotificationPermission(context)) {
            sb.append("\u2022 ${context.getString(R.string.notification_permission_explanation)}\n")
        }
        if (needsBatteryOptimization(context)) {
            sb.append("\u2022 ${context.getString(R.string.battery_optimization_explanation)}\n")
        }
        return sb.toString()
    }

    private fun getRequiredPermissions(context: Context): String {
        return listOfNotNull(
            if (needsNotificationPermission(context)) "notifications" else null,
            if (needsBatteryOptimization(context)) "battery" else null
        ).joinToString()
    }

    @SuppressLint("BatteryLife")
    private fun handlePermissionGrant(activity: Activity, prefs: SharedPreferences, attempt: Int) {
        val permissions = mutableListOf<String>().apply {
            if (needsNotificationPermission(activity)) add("notifications")
            if (needsBatteryOptimization(activity)) add("battery")
        }

        if (permissions.isNotEmpty()) {
            requestPermissionsSequentially(activity, permissions, 0)
        }

        // Помечаем как показанный только если все разрешения получены
        if (!needsNotificationPermission(activity) && !needsBatteryOptimization(activity)) {
            prefs.edit { putBoolean(PREF_UNIFIED_DIALOG_SHOWN, true) }
        }
    }

    private fun requestPermissionsSequentially(activity: Activity, permissions: List<String>, index: Int) {
        if (index >= permissions.size) {
            logFinalResults(activity)
            return
        }

        when (permissions[index]) {
//            "notifications" -> requestNotificationPermission(activity) {
//                logPermissionResult("notifications", isNotificationGranted(activity))
//                requestPermissionsSequentially(activity, permissions, index + 1)
//            }
            "battery" -> requestBatteryOptimization(activity) {
                logPermissionResult("battery", isBatteryOptimizationDisabled(activity))
                requestPermissionsSequentially(activity, permissions, index + 1)
            }
        }
    }

    private fun logFinalResults(context: Context) {
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()

        if (isNotificationGranted(context)) granted.add("notifications") else denied.add("notifications")
        if (isBatteryOptimizationDisabled(context)) granted.add("battery") else denied.add("battery")

        logEvent("permission_final_results", mapOf(
            "granted" to granted.joinToString(),
            "denied" to denied.joinToString())
        )
    }

//    private fun requestNotificationPermission(activity: Activity, onComplete: () -> Unit) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            val launcher = activity.registerForActivityResult(
//                ActivityResultContracts.RequestPermission()
//            ) { granted: Boolean ->
//                if (!granted) showCustomNotificationSettings(activity)
//                onComplete()
//            }
//            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
//        } else {
//            onComplete()
//        }
//    }

    private fun showCustomNotificationSettings(activity: Activity) {
        try {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                activity.startActivity(this)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open notification settings")
            openAppSettings(activity)
        }
    }

    private fun requestBatteryOptimization(activity: Activity, onComplete: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${activity.packageName}".toUri()
                    activity.startActivity(this)
                }
            } catch (e: Exception) {
                openBatteryOptimizationSettings(activity)
            }
        }
        onComplete()
    }

    private fun openBatteryOptimizationSettings(context: Context) {
        try {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                context.startActivity(this)
            }
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    private fun openAppSettings(context: Context) {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            context.startActivity(this)
        }
    }

    private fun logEvent(event: String, params: Map<String, Any?> = emptyMap()) {
        // Логирование событий через Timber (Firebase опционален)
        Timber.d("Permission event: $event, params: $params")
    }

    private fun logPermissionResult(permission: String, granted: Boolean) {
        logEvent("permission_result", mapOf(
            "permission" to permission,
            "granted" to granted)
        )
    }

    private fun isNotificationGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else true
    }

    private fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else true
    }
}