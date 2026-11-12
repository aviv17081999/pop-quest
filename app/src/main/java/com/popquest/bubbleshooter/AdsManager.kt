package com.popquest.bubbleshooter

import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/** AdMob rewarded + interstitial ads, with Google's consent form for EU/UK players. */
class AdsManager(private val a: MainActivity) {

    private val started = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var rewarded: RewardedAd? = null
    private var interstitial: InterstitialAd? = null
    private var loadingRewarded = false
    private var loadingInterstitial = false

    fun init() {
        val consent = UserMessagingPlatform.getConsentInformation(a)
        consent.requestConsentInfoUpdate(
            a, ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(a) {
                    if (consent.canRequestAds()) start()
                }
            },
            { if (consent.canRequestAds()) start() }
        )
        if (consent.canRequestAds()) start()
    }

    private fun start() {
        if (!started.compareAndSet(false, true)) return
        MobileAds.initialize(a) { a.runOnUiThread { loadRewarded(); loadInterstitial() } }
    }

    private fun loadRewarded() {
        if (!started.get() || rewarded != null || loadingRewarded) return
        loadingRewarded = true
        RewardedAd.load(a, a.getString(R.string.admob_rewarded_id), AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; loadingRewarded = false }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingRewarded = false
                    handler.postDelayed({ loadRewarded() }, 30_000)
                }
            })
    }

    private fun loadInterstitial() {
        if (!started.get() || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(a, a.getString(R.string.admob_interstitial_id), AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad; loadingInterstitial = false }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingInterstitial = false
                    handler.postDelayed({ loadInterstitial() }, 30_000)
                }
            })
    }

    /** Tells the game whether the player earned the reward. */
    fun showRewarded(callbackId: String) {
        val ad = rewarded
        if (ad == null) { a.js("onAd", callbackId, false); loadRewarded(); return }
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                a.hideSystemBars(); a.js("onAd", callbackId, earned); loadRewarded()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                a.js("onAd", callbackId, false); loadRewarded()
            }
        }
        ad.show(a) { earned = true }
    }

    /** Between-level ad. The game continues whether or not an ad was available. */
    fun showInterstitial(callbackId: String) {
        val ad = interstitial
        if (ad == null) { a.js("onAd", callbackId, true); loadInterstitial(); return }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                a.hideSystemBars(); a.js("onAd", callbackId, true); loadInterstitial()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                a.js("onAd", callbackId, true); loadInterstitial()
            }
        }
        ad.show(a)
    }
}
