package com.popquest.bubbleshooter

import android.webkit.JavascriptInterface

/**
 * Methods the game calls from JavaScript as window.PQNative.xxx().
 * They arrive on a background thread, so anything touching the UI hops to the main thread.
 */
class NativeBridge(private val a: MainActivity) {

    @JavascriptInterface fun ready() = a.onPageReady()

    // Ads
    @JavascriptInterface fun showRewarded(id: String) = a.runOnUiThread { a.ads.showRewarded(id) }
    @JavascriptInterface fun showInterstitial(id: String) = a.runOnUiThread { a.ads.showInterstitial(id) }

    // Purchases
    @JavascriptInterface fun buy(productId: String) = a.runOnUiThread { a.billing.buy(productId) }
    @JavascriptInterface fun restore() = a.runOnUiThread { a.billing.restore(showResult = true) }

    // Google Play Games
    @JavascriptInterface fun signIn() = a.runOnUiThread { a.games.signIn() }
    @JavascriptInterface fun submitScore(points: String) = a.runOnUiThread { a.games.submitScore(points.toLongOrNull() ?: 0L) }
    @JavascriptInterface fun showLeaderboard() = a.runOnUiThread { a.games.showLeaderboard() }
    @JavascriptInterface fun unlock(key: String) = a.runOnUiThread { a.games.unlock(key) }
    @JavascriptInterface fun showAchievements() = a.runOnUiThread { a.games.showAchievements() }
    @JavascriptInterface fun saveCloud(json: String) = a.runOnUiThread { a.games.saveCloud(json) }

    // Device
    @JavascriptInterface fun vibrate(pattern: String) = a.runOnUiThread { a.vibrate(pattern) }
    @JavascriptInterface fun askNotifications() = a.runOnUiThread { a.askNotificationPermission() }
    @JavascriptInterface fun logEvent(name: String, json: String) = a.analytics.log(name, json)
}
