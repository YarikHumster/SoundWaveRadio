package com.yaros.RadioUrl.helpers

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ProcessLifecycleOwner
import com.yandex.mobile.ads.appopenad.AppOpenAd
import com.yandex.mobile.ads.appopenad.AppOpenAdEventListener
import com.yandex.mobile.ads.appopenad.AppOpenAdLoadListener
import com.yandex.mobile.ads.appopenad.AppOpenAdLoader
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestConfiguration
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.MobileAds
import com.yandex.mobile.ads.instream.MobileInstreamAds
import com.yaros.RadioUrl.R
import timber.log.Timber
import kotlin.math.roundToInt

class AdManager(application: Application) {
    private var activity: AppCompatActivity? = null
    private var appOpenAd: AppOpenAd? = null
    private var adRequest: AdRequest
    private var bannerAd: BannerAdView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val bannerRefreshInterval: Long = 10000

    init {
        MobileAds.initialize(application) {
            MobileInstreamAds.setAdGroupPreloading(true)
            MobileAds.enableLogging(true)

            val processLifecycleObserver = DefaultProcessLifecycleObserver(
                onProcessCaseForeground = ::showAppOpenAd
            )
            ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        }

        adRequest = AdRequest.Builder().build()
    }

    private inner class AdEventListener : AppOpenAdEventListener {
        override fun onAdShown() {
            loadAppOpenAd()
            Timber.tag("AppAdOpen").d("Ad has been shown.")
        }

        override fun onAdFailedToShow(adError: AdError) {
            clearAppOpenAd()
            loadAppOpenAd()
            Timber.tag("AppAdOpen").e("Failed to show ad: ${adError.description}")
        }

        override fun onAdDismissed() {
            clearAppOpenAd()
            Timber.tag("AppAdOpen").d("Ad dismissed.")
        }

        override fun onAdClicked() {
            Timber.tag("AppAdOpen").d("Ad clicked.")
        }

        override fun onAdImpression(impressionData: ImpressionData?) {
            Timber.tag("AppAdOpen").d("Ad impression: $impressionData")
        }
    }

    fun initBannerAd(activity: AppCompatActivity) {
        val bannerContainer = activity.findViewById<FrameLayout>(R.id.bannerAdView)
        bannerAd = BannerAdView(activity).apply {
            setAdUnitId("R-M-4490409-1")
        }
        bannerContainer.addView(bannerAd)
        startBannerRefresh()
    }

    private fun startBannerRefresh() {
        handler.post(object : Runnable {
            override fun run() {
                loadBannerAd()
                handler.postDelayed(this, bannerRefreshInterval)
            }
        })
    }

    fun loadBannerAd() {
        activity?.let { activity ->
            bannerAd?.apply {
                val displayMetrics = activity.resources.displayMetrics
                val density = displayMetrics.density

                // Защита от нулевой плотности
                val safeDensity = if (density == 0f) 1f else density

                // Рассчитываем ширину баннера
                val screenWidthPixels = displayMetrics.widthPixels
                val adWidth = (screenWidthPixels / safeDensity).roundToInt()

                // Рассчитываем максимальную высоту с защитой от деления на ноль
                val screenHeight = (displayMetrics.heightPixels / safeDensity).toInt()
                val maxAdHeight = if (screenHeight == 0) 50 else (screenHeight / 12).coerceAtLeast(50)

                try {
                    // Пытаемся установить рассчитанный размер
                    setAdSize(BannerAdSize.inlineSize(activity, adWidth, maxAdHeight))
                    Timber.tag("BannerAd").d("Custom size: ${adWidth}dp x ${maxAdHeight}dp")
                } catch (e: Exception) {
                    // При ошибке используем стандартный размер 350x50
                    setAdSize(BannerAdSize.fixedSize(activity, 350, 50))
                    Timber.tag("BannerAd").e(e, "Using default banner size")
                }

                loadAd(adRequest)
            }
        }
    }

    private fun loadAppOpenAd() {
        activity?.let { activity ->
            val appOpenAdLoader = AppOpenAdLoader(activity)
            val adUnitId = "R-M-4490409-4"
            val adRequestConfig = AdRequestConfiguration.Builder(adUnitId).build()

            appOpenAdLoader.run {
                setAdLoadListener(object : AppOpenAdLoadListener {
                    override fun onAdLoaded(appOpenAd: AppOpenAd) {
                        this@AdManager.appOpenAd = appOpenAd
                        Timber.tag("AppOpenAd").d("App open ad loaded.")
                    }

                    override fun onAdFailedToLoad(error: AdRequestError) {
                        Timber.tag("AppOpenAd").e("Failed to load app open ad: ${error.description}")
                    }
                })
                loadAd(adRequestConfig)
            }
        }
    }

    private fun showAppOpenAd() {
        activity?.let { activity ->
            appOpenAd?.apply {
                setAdEventListener(AdEventListener())
                show(activity)
                Timber.tag("AppOpenAd").d("Attempting to show app open ad.")
            }
        }
    }

    private fun clearAppOpenAd() {
        appOpenAd?.setAdEventListener(null)
        appOpenAd = null
        Timber.tag("AppOpenAd").d("App open ad cleared.")
    }

    fun destroyBannerAd() {
        handler.removeCallbacksAndMessages(null)
        bannerAd?.destroy()
        Timber.tag("BannerAd").d("Banner ad destroyed.")
    }

    fun setActivity(activity: AppCompatActivity) {
        this.activity = activity
        loadAppOpenAd()
    }
}
