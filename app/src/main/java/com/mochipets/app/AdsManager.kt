package com.mochipets.app

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Consent (Google UMP) and rewarded ads (AdMob).
 *
 * Ads are only ever shown inside this app, and only when the user taps "Watch ad".
 * Debug builds use Google's test ad unit; see app/build.gradle.kts for release ids.
 */
class AdsManager(private val activity: Activity) {

    private val consentInfo: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
    private val initStarted = AtomicBoolean(false)
    private var rewardedAd: RewardedAd? = null
    private var loading = false

    /** Called (on the main thread) whenever ad readiness or consent state changes. */
    var onStateChanged: (() -> Unit)? = null

    val isReady: Boolean
        get() = rewardedAd != null

    val privacyOptionsRequired: Boolean
        get() = consentInfo.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Gather consent first (shows a form only where required), then start the Ads SDK. */
    fun start() {
        val params = ConsentRequestParameters.Builder().build()
        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    if (consentInfo.canRequestAds()) initAds()
                    onStateChanged?.invoke()
                }
            },
            { _ ->
                if (consentInfo.canRequestAds()) initAds()
            }
        )
        // Consent from a previous session may already allow ads.
        if (consentInfo.canRequestAds()) initAds()
    }

    fun showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ -> onStateChanged?.invoke() }
    }

    private fun initAds() {
        if (!initStarted.compareAndSet(false, true)) return
        val testIds = BuildConfig.TEST_DEVICE_IDS.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val config = RequestConfiguration.Builder()
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
            .setTestDeviceIds(testIds)
            .build()
        val appContext = activity.applicationContext
        Thread {
            MobileAds.setRequestConfiguration(config)
            MobileAds.initialize(appContext) { }
            activity.runOnUiThread {
                if (!activity.isFinishing) loadRewarded()
            }
        }.start()
    }

    fun loadRewarded() {
        if (loading || rewardedAd != null || !initStarted.get()) return
        loading = true
        RewardedAd.load(
            activity,
            BuildConfig.REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    loading = false
                    onStateChanged?.invoke()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    loading = false
                    onStateChanged?.invoke()
                }
            }
        )
    }

    /**
     * Shows the rewarded ad. [onReward] runs after the ad is closed, only if the user earned the reward.
     * [onFailed] runs if no ad is ready or the ad could not be shown.
     */
    fun showRewarded(onReward: () -> Unit, onFailed: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            loadRewarded()
            onFailed()
            return
        }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewarded()
                if (earned) onReward()
                onStateChanged?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                loadRewarded()
                onFailed()
            }
        }
        ad.show(activity, OnUserEarnedRewardListener { earned = true })
    }
}
