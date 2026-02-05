package com.yaros.RadioUrl

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.navigateUp
import com.yaros.RadioUrl.helpers.AdManager
import com.yaros.RadioUrl.helpers.GoogleServicesHelper
import com.yaros.RadioUrl.helpers.AppThemeHelper
import com.yaros.RadioUrl.helpers.FileHelper
import com.yaros.RadioUrl.helpers.ImportHelper
import com.yaros.RadioUrl.helpers.PermissionHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper
import com.yaros.RadioUrl.ui.ReviewManager
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private var analytics: Any? = null  // Nullable для опциональной поддержки Firebase
    private lateinit var adManager: AdManager
    private lateinit var reviewManager: ReviewManager

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
//        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (PreferencesHelper.isHouseKeepingNecessary()) {
            ImportHelper.removeDefaultStationImageUris(this)
            if (PreferencesHelper.loadCollectionSize() != -1) {
                PreferencesHelper.saveEditStationsEnabled(true)
            }
            PreferencesHelper.saveHouseKeepingNecessaryState()
        }

        // Инициализация Firebase Analytics (если доступен)
        initializeFirebaseAnalytics()

        setContentView(R.layout.activity_main)

        reviewManager = ReviewManager(this)
        reviewManager.initialize()

        PermissionHelper.checkEssentialPermissions(this)

        FileHelper.createNomediaFile(getExternalFilesDir(null))
        setSupportActionBar(findViewById(R.id.main_toolbar))
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val navView: BottomNavigationView = findViewById(R.id.bottomNavigationView)

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_categories, R.id.navigation_menu)
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        PreferencesHelper.registerPreferenceChangeListener(sharedPreferenceChangeListener)
        startPlayerService()

        // Инициализация Firebase Messaging (если доступен)
        initializeFirebaseMessaging()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pscid",
                "Новости",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        adManager = (application as URLRadio).adManager
        adManager.setActivity(this)
        adManager.initBannerAd(this)
        adManager.loadBannerAd()

    }

    /**
     * Безопасная инициализация Firebase Analytics
     */
    private fun initializeFirebaseAnalytics() {
        if (GoogleServicesHelper.isFirebaseAvailable()) {
            analytics = GoogleServicesHelper.getFirebaseAnalytics(this)
            if (analytics != null) {
                Timber.tag("MainActivity").d("Firebase Analytics initialized")
            } else {
                Timber.tag("MainActivity").w("Firebase Analytics initialization failed")
            }
        } else {
            Timber.tag("MainActivity").i("Firebase Analytics not available - running without analytics")
        }
    }

    /**
     * Безопасная инициализация Firebase Messaging
     */
    private fun initializeFirebaseMessaging() {
        if (!GoogleServicesHelper.isFirebaseAvailable()) {
            Timber.tag("MainActivity").i("Firebase Messaging not available - push notifications disabled")
            return
        }

        GoogleServicesHelper.getFirebaseMessagingToken(
            onSuccess = { token ->
                Timber.tag("MainActivity").d("FCM Token: $token")
                // Здесь можно отправить токен на сервер
            },
            onFailure = { exception ->
                Timber.tag("MainActivity").w(exception, "Failed to get FCM token")
            }
        )
    }

    @UnstableApi
    private fun startPlayerService() {
        if (!isServiceRunning(PlayerService::class.java)) {
            val serviceIntent = Intent(this, PlayerService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { it.service.className == serviceClass.name }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_graph)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()
        Timber.tag("ONRESUME").d("OnResume")
        adManager.loadBannerAd()
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferencesHelper.unregisterPreferenceChangeListener(sharedPreferenceChangeListener)
        adManager.destroyBannerAd()
    }

    private val sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                Keys.PREF_THEME_SELECTION -> {
                    AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
                }
            }
        }

}
