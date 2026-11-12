package com.popquest.bubbleshooter

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import org.json.JSONTokener

class MainActivity : AppCompatActivity() {

    lateinit var web: WebView
    lateinit var ads: AdsManager
    lateinit var billing: BillingManager
    lateinit var games: GamesManager
    lateinit var analytics: Analytics

    private var pageReady = false
    private val pendingJs = mutableListOf<String>()

    /** Opens Play Games screens (leaderboard, achievements). */
    val playGamesUi = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { hideSystemBars() }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#9ADBFF")) }
        web = WebView(this)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        // Keep the game clear of camera cut-outs; status and navigation bars are hidden while playing.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(cut.left, cut.top, cut.right, cut.bottom)
            WindowInsetsCompat.CONSUMED
        }
        hideSystemBars()

        analytics = Analytics(this)
        ads = AdsManager(this)
        billing = BillingManager(this)
        games = GamesManager(this)

        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true               // keeps progress in localStorage
            mediaPlaybackRequiresUserGesture = false // lets the music start at launch
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100
            setSupportZoom(false)
        }
        web.isVerticalScrollBarEnabled = false
        web.overScrollMode = WebView.OVER_SCROLL_NEVER
        web.setBackgroundColor(Color.parseColor("#9ADBFF"))
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assets.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == "appassets.androidplatform.net") return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }
        }
        web.addJavascriptInterface(NativeBridge(this), "PQNative")
        web.loadUrl("https://appassets.androidplatform.net/assets/game/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.PQ ? String(PQ.back()) : 'false'") { result ->
                    if (result?.trim('"') != "true") finish()
                }
            }
        })

        ads.init()
        billing.connect()
    }

    /** Call a function on window.PQ in the game. Calls made before the game has loaded are queued. */
    fun js(fn: String, vararg args: Any?) {
        val list = args.joinToString(",") {
            when (it) {
                null -> "null"
                is Boolean, is Number -> it.toString()
                else -> JSONObject.quote(it.toString())
            }
        }
        val code = "window.PQ && PQ.$fn($list)"
        runOnUiThread {
            if (pageReady) web.evaluateJavascript(code, null) else pendingJs.add(code)
        }
    }

    fun onPageReady() = runOnUiThread {
        if (pageReady) return@runOnUiThread
        pageReady = true
        pendingJs.forEach { web.evaluateJavascript(it, null) }
        pendingJs.clear()
        games.checkSignedIn()
    }

    fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun vibrate(pattern: String) {
        val parts = pattern.split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it >= 0 }
        if (parts.isEmpty()) return
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (parts.size == 1) {
            vibrator.vibrate(VibrationEffect.createOneShot(parts[0].coerceAtLeast(1), VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            // Web pattern is [on, off, on, ...]; Android waveform starts with an "off" time.
            val timings = LongArray(parts.size + 1)
            parts.forEachIndexed { i, ms -> timings[i + 1] = ms }
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        }
    }

    override fun onStart() {
        super.onStart()
        Reminders.cancelAll(this)   // no reminders while the game is open
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        js("pause", false)
        billing.refreshPurchases()
    }

    override fun onPause() {
        js("pause", true)
        web.onPause()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Ask the game which reminders to send (full lives, daily gift, event ending) and schedule them.
        if (pageReady) {
            web.evaluateJavascript("window.PQ ? PQ.reminders() : '[]'") { result ->
                val json = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull() ?: "[]"
                Reminders.schedule(applicationContext, json)
            }
        }
    }

    override fun onDestroy() {
        billing.close()
        web.destroy()
        super.onDestroy()
    }
}
