package com.yaros.RadioUrl

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.yaros.RadioUrl.helpers.AdManager
import com.yaros.RadioUrl.helpers.GoogleServicesHelper
import com.yaros.RadioUrl.helpers.AppThemeHelper
import com.yaros.RadioUrl.helpers.NetworkHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper.initPreferences
import dagger.hilt.android.HiltAndroidApp
import android.app.Application
import timber.log.Timber
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class URLRadio : Application() {
    var firebaseAnalytics: Any? = null  // Nullable для опциональной поддержки Firebase
    private val TAG: String = URLRadio::class.java.simpleName
    lateinit var adManager: AdManager
        private set

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).v("URLRadio application started.")

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Инициализация Firebase с runtime-детекцией
        initFirebaseWithFallback()
        initPreferences()
        AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
        adManager = AdManager(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        NetworkHelper.initialize(this)

        // Вывод статуса сервисов
        Timber.tag(TAG).d(GoogleServicesHelper.getServicesStatus(this))
    }

    private fun initFirebaseWithFallback() {
        if (!GoogleServicesHelper.isFirebaseAvailable()) {
            Timber.tag(TAG).w("Firebase services are unavailable - running in FOSS mode")
            return
        }

        try {
            // Безопасная инициализация Firebase
            if (GoogleServicesHelper.initFirebaseSafely(this)) {
                initFirebaseComponents()
            } else {
                Timber.tag(TAG).w("Firebase initialization failed")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Firebase initialization error")
        }
    }

    private fun initFirebaseComponents() {
        try {
            // Инициализация аналитики
            firebaseAnalytics = GoogleServicesHelper.getFirebaseAnalytics(this)
            if (firebaseAnalytics != null) {
                Timber.tag(TAG).d("Firebase Analytics initialized successfully")
            }
            // Инициализация FCM с обработкой ошибок
            initFCM()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Firebase component initialization failed")
        }
    }

    private fun initFCM() {
        if (!GoogleServicesHelper.isFirebaseAvailable()) return

        GoogleServicesHelper.getFirebaseMessagingToken(
            onSuccess = { token ->
                Timber.tag("FCM").d("FCM Token: $token")
            },
            onFailure = { exception ->
                Timber.tag("FCM").w(exception, "Failed to get FCM token")
            }
        )
    }

    var lifecycleObserver: LifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)
            Keys.isForeground = true
        }

        override fun onStop(owner: LifecycleOwner) {
            super.onStop(owner)
            Keys.isForeground = false
            Keys.isPausedFromClick = false
        }
    }

    /**
     * Проверка доступности Firebase (делегируется в GoogleServicesHelper)
     */
    fun isFirebaseAvailable(): Boolean {
        return GoogleServicesHelper.isFirebaseAvailable()
    }

    /**
     * Проверка доступности Google Play Services
     */
    fun isGooglePlayServicesAvailable(): Boolean {
        return GoogleServicesHelper.isGooglePlayServicesAvailable(this)
    }

    override fun onLowMemory() {
        Timber.tag(TAG).w("Low memory state detected")
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        Timber.tag(TAG).w("Trim memory level: $level")
        super.onTrimMemory(level)
    }

    override fun onTerminate() {
        super.onTerminate()
        Timber.tag(TAG).v("URLRadio application terminated.")
    }

}
