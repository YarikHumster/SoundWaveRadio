package com.yaros.RadioUrl.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.yaros.RadioUrl.helpers.GoogleServicesHelper
import ru.rustore.sdk.review.RuStoreReviewManagerFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

class ReviewManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("ReviewPrefs", Context.MODE_PRIVATE)
    private var firebaseAnalytics: Any? = null  // Nullable для опциональной поддержки Firebase

    init {
        // Инициализация Firebase Analytics только если доступен
        if (GoogleServicesHelper.isFirebaseAvailable()) {
            firebaseAnalytics = GoogleServicesHelper.getFirebaseAnalytics(context)
        }
    }

    companion object {
        private const val KEY_GP_REVIEW_SHOWN = "gp_review_shown"
        private const val KEY_RS_REVIEW_SHOWN = "rs_review_shown"
        private const val KEY_INSTALL_TIME = "install_time"
        private const val KEY_GP_LAUNCH_COUNT = "gp_launch_count"
        private const val KEY_RS_LAUNCH_COUNT = "rs_launch_count"
        private const val LAUNCH_THRESHOLD = 10
        private const val RS_LAUNCH_DELAY_DAYS = 3L
    }

    fun initialize() {
        initInstallTime()

        when (getInstallSource()) {
            InstallSource.GOOGLE_PLAY -> handleGooglePlayLogic()
            InstallSource.RUSTORE -> handleRuStoreLogic()
            InstallSource.UNKNOWN -> handleUnknownSource()
        }
    }

    private fun initInstallTime() {
        if (!sharedPreferences.contains(KEY_INSTALL_TIME)) {
            sharedPreferences.edit() { putLong(KEY_INSTALL_TIME, System.currentTimeMillis()) }
        }
    }

    // Улучшенное определение источника установки
    private fun getInstallSource(): InstallSource {
        return when {
            isInstalledViaGooglePlay() -> InstallSource.GOOGLE_PLAY
            isRuStoreAvailable() -> InstallSource.RUSTORE
            else -> InstallSource.UNKNOWN
        }
    }

    private fun isInstalledViaGooglePlay(): Boolean {
        return try {
            val installer = context.packageManager.getInstallerPackageName(context.packageName)
            installer == "com.android.vending"
        } catch (e: Exception) {
            false
        }
    }

    private fun isRuStoreAvailable(): Boolean {
        return try {
            context.packageManager.getPackageInfo("ru.rustore.app", 0) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun handleGooglePlayLogic() {
        val launchCount = sharedPreferences.getInt(KEY_GP_LAUNCH_COUNT, 0) + 1
        sharedPreferences.edit() { putInt(KEY_GP_LAUNCH_COUNT, launchCount) }

        if (launchCount >= LAUNCH_THRESHOLD &&
            !sharedPreferences.getBoolean(KEY_GP_REVIEW_SHOWN, false)) {
            showGooglePlayReview()
        }
    }

    private fun handleRuStoreLogic() {
        val launchCount = sharedPreferences.getInt(KEY_RS_LAUNCH_COUNT, 0) + 1
        sharedPreferences.edit() { putInt(KEY_RS_LAUNCH_COUNT, launchCount) }

        val installTime = sharedPreferences.getLong(KEY_INSTALL_TIME, System.currentTimeMillis())
        val daysSinceInstall = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - installTime)

        if (daysSinceInstall >= RS_LAUNCH_DELAY_DAYS &&
            !sharedPreferences.getBoolean(KEY_RS_REVIEW_SHOWN, false)) {
            showRuStoreReview()
        }
    }

    private fun handleUnknownSource() {
        Timber.d("Unknown installation source, no review logic applied")
    }

    private fun showGooglePlayReview() {
        Timber.d("Starting Google Play review flow")
        val manager = ReviewManagerFactory.create(context)

        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(context as android.app.Activity, task.result)
                    .addOnCompleteListener {
                        sharedPreferences.edit() { putBoolean(KEY_GP_REVIEW_SHOWN, true) }
                        logReviewCompletedEvent("Google Play")
                    }
            }
        }.addOnFailureListener {
            logReviewErrorEvent("Google Play", it)
        }
    }

    private fun showRuStoreReview() {
        Timber.d("Starting RuStore review flow")
        val manager = RuStoreReviewManagerFactory.create(context)

        manager.requestReviewFlow().addOnSuccessListener { reviewInfo ->
            manager.launchReviewFlow(reviewInfo).addOnSuccessListener {
                sharedPreferences.edit() { putBoolean(KEY_RS_REVIEW_SHOWN, true) }
                logReviewCompletedEvent("RuStore")
            }.addOnFailureListener {
                logReviewErrorEvent("RuStore", it)
            }
        }.addOnFailureListener {
            logReviewErrorEvent("RuStore", it)
        }
    }

    private fun logReviewCompletedEvent(source: String) {
        GoogleServicesHelper.logAnalyticsEvent(
            context,
            "review_event",
            mapOf(
                "source" to source,
                "type" to "review_completed"
            )
        )
    }

    private fun logReviewErrorEvent(source: String, exception: Throwable) {
        GoogleServicesHelper.logAnalyticsEvent(
            context,
            "review_error",
            mapOf(
                "source" to source,
                "error_type" to exception.javaClass.simpleName,
                "error_message" to exception.message
            )
        )
    }

    enum class InstallSource {
        GOOGLE_PLAY, RUSTORE, UNKNOWN
    }
}