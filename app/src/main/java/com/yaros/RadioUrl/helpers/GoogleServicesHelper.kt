package com.yaros.RadioUrl.helpers

import android.content.Context
import android.os.Bundle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import timber.log.Timber

/**
 * Централизованный помощник для runtime-детекции Google Services
 * Обеспечивает универсальную работу приложения на устройствах с/без Google Services
 */
object GoogleServicesHelper {

    private const val TAG = "GoogleServicesHelper"

    @Volatile
    private var googleServicesAvailable: Boolean? = null

    @Volatile
    private var firebaseAvailable: Boolean? = null

    /**
     * Проверяет доступность Google Play Services на устройстве
     */
    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        if (googleServicesAvailable != null) {
            return googleServicesAvailable!!
        }

        return try {
            val googleApiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
            val available = resultCode == ConnectionResult.SUCCESS

            googleServicesAvailable = available

            if (!available) {
                Timber.tag(TAG).w("Google Play Services unavailable. Code: $resultCode")
            } else {
                Timber.tag(TAG).d("Google Play Services available")
            }

            available
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error checking Google Play Services availability")
            googleServicesAvailable = false
            false
        }
    }

    /**
     * Проверяет доступность Firebase
     */
    fun isFirebaseAvailable(): Boolean {
        if (firebaseAvailable != null) {
            return firebaseAvailable!!
        }

        return try {
            Class.forName("com.google.firebase.FirebaseApp")
            firebaseAvailable = true
            Timber.tag(TAG).d("Firebase available")
            true
        } catch (e: ClassNotFoundException) {
            Timber.tag(TAG).w("Firebase not available: ${e.message}")
            firebaseAvailable = false
            false
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error checking Firebase availability")
            firebaseAvailable = false
            false
        }
    }

    /**
     * Безопасная инициализация Firebase
     */
    fun initFirebaseSafely(context: Context): Boolean {
        if (!isFirebaseAvailable()) {
            Timber.tag(TAG).w("Skipping Firebase initialization - not available")
            return false
        }

        return try {
            val firebaseApp = com.google.firebase.FirebaseApp.initializeApp(context)
            if (firebaseApp != null) {
                Timber.tag(TAG).d("Firebase initialized successfully")
                true
            } else {
                Timber.tag(TAG).w("Firebase already initialized")
                true
            }
        } catch (e: IllegalStateException) {
            // Firebase уже инициализирован
            Timber.tag(TAG).d("Firebase already initialized")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Firebase initialization failed")
            false
        }
    }

    /**
     * Получить Firebase Analytics (если доступен)
     */
    fun getFirebaseAnalytics(context: Context): Any? {
        if (!isFirebaseAvailable()) {
            return null
        }

        return try {
            com.google.firebase.analytics.FirebaseAnalytics.getInstance(context)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get Firebase Analytics")
            null
        }
    }

    /**
     * Безопасное логирование события в Firebase Analytics
     */
    fun logAnalyticsEvent(context: Context, eventName: String, params: Map<String, Any?> = emptyMap()) {
        if (!isFirebaseAvailable()) {
            Timber.tag(TAG).d("Analytics event (no Firebase): $eventName")
            return
        }

        try {
            val analytics = getFirebaseAnalytics(context) as? com.google.firebase.analytics.FirebaseAnalytics
            if (analytics != null) {
                val bundle = Bundle().apply {
                    params.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Double -> putDouble(key, value)
                            is Boolean -> putBoolean(key, value)
                            null -> putString(key, null)
                        }
                    }
                }
                analytics.logEvent(eventName, bundle)
                Timber.tag(TAG).d("Analytics event logged: $eventName")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to log analytics event: $eventName")
        }
    }

    /**
     * Получить Firebase Messaging токен (если доступен)
     */
    fun getFirebaseMessagingToken(onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        if (!isFirebaseAvailable()) {
            Timber.tag(TAG).w("Firebase Messaging not available")
            onFailure(Exception("Firebase not available"))
            return
        }

        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        onSuccess(task.result!!)
                    } else {
                        onFailure(task.exception ?: Exception("Unknown error"))
                    }
                }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get FCM token")
            onFailure(e)
        }
    }

    /**
     * Проверяет, нужно ли показывать функционал, зависящий от Google Services
     */
    fun shouldEnableGoogleFeatures(context: Context): Boolean {
        return isGooglePlayServicesAvailable(context) && isFirebaseAvailable()
    }

    /**
     * Получить информацию о доступности сервисов для отладки
     */
    fun getServicesStatus(context: Context): String {
        return buildString {
            appendLine("Google Services Status:")
            appendLine("- Google Play Services: ${if (isGooglePlayServicesAvailable(context)) "✓ Available" else "✗ Not Available"}")
            appendLine("- Firebase: ${if (isFirebaseAvailable()) "✓ Available" else "✗ Not Available"}")
        }
    }

    /**
     * Сброс кэша для повторной проверки (для тестирования)
     */
    fun resetCache() {
        googleServicesAvailable = null
        firebaseAvailable = null
    }
}
