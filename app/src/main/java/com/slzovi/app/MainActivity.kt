package com.slzovi.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var pendingReq: PermissionRequest? = null
    private var callActive = false
    private var lockUntil = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val startPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val reqPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val r = pendingReq ?: return@registerForActivityResult
        pendingReq = null
        val ok = r.resources.filter { res -> androidPerm(res)?.let { granted(it) } ?: false }
        if (ok.isEmpty()) r.deny() else r.grant(ok.toTypedArray())
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun androidPerm(res: String): String? = when (res) {
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
        else -> null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = WeakReference(this)
        web = WebView(this)
        setContentView(web)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString + " SLZoviApp/" + BuildConfig.VERSION_NAME
            setSupportMultipleWindows(false)
        }
        web.addJavascriptInterface(Bridge(), "ZoviApp")

        val host = Uri.parse(BuildConfig.SERVER_URL).host
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                if (u.host == host || u.scheme == "file" || u.scheme == "about" || u.scheme == "blob") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) { }
                return true
            }

            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) v.loadUrl("file:///android_asset/offline.html")
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (request.origin.host != host) { request.deny(); return@runOnUiThread }
                    val need = request.resources.mapNotNull { androidPerm(it) }.filter { !granted(it) }.distinct()
                    if (need.isEmpty()) request.grant(request.resources)
                    else { pendingReq?.deny(); pendingReq = request; reqPerms.launch(need.toTypedArray()) }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else moveTaskToBack(true)
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(BuildConfig.SERVER_URL)

        handleIntent(intent, true)
        askPermissionsOnce()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, false)
    }

    private fun handleIntent(i: Intent?, first: Boolean) {
        i ?: return
        val fromCall = i.getBooleanExtra("call", false)
        lockScreenMode(fromCall)
        if (fromCall) {
            // daj stranici vremena da primi poziv; ne skrivaj aplikaciju iza zaključanog ekrana prerano
            lockUntil = System.currentTimeMillis() + 60_000
            handler.postDelayed({ if (!callActive) lockScreenMode(false) }, 61_000)
        }
        val act = i.getStringExtra("action")
        if (act != null) {
            Store.setAction(this, act, i.getStringExtra("callId"))
            Notif.cancelCall(this)
            i.removeExtra("action")
            if (!first) js("window.Z&&Z.onAppAction&&Z.onAppAction()")
        }
    }

    private fun askPermissionsOnce() {
        val want = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) want.add(Manifest.permission.POST_NOTIFICATIONS)
        if (!granted(Manifest.permission.RECORD_AUDIO)) want.add(Manifest.permission.RECORD_AUDIO)
        if (!granted(Manifest.permission.CAMERA)) want.add(Manifest.permission.CAMERA)
        val p = getSharedPreferences("zovi", Context.MODE_PRIVATE)
        if (want.isNotEmpty() && !p.getBoolean("asked", false)) {
            p.edit().putBoolean("asked", true).apply()
            startPerms.launch(want.toTypedArray())
        }
    }

    /** Dok traje dolazni poziv prikaži aplikaciju preko zaključanog ekrana; inače nikad. */
    @Suppress("DEPRECATION")
    private fun lockScreenMode(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(on)
            setTurnScreenOn(on)
        } else {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    private fun js(code: String) { runOnUiThread { try { web.evaluateJavascript(code, null) } catch (_: Exception) { } } }

    override fun onResume() {
        super.onResume()
        visible = true
        // ako je poziv zvonio preko obaveštenja, preuzmi zvonjenje (jačina zvona) i sakrij obaveštenje
        if (Notif.isRinging(this)) { Notif.hide(this); Ringer.start(this) }
        // namerno bez web.onPause(): WebSocket i tajmeri ostaju aktivni dok je proces živ
    }

    override fun onPause() {
        visible = false
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        if (instance?.get() === this) instance = null
        super.onDestroy()
    }

    /** Most ka web stranici: window.ZoviApp */
    inner class Bridge {
        @JavascriptInterface
        fun initPush(json: String) {
            val app = applicationContext
            if (json.isBlank()) return
            try {
                val o = JSONObject(json)
                if (!o.has("appId") || !o.has("apiKey") || !o.has("projectId") || !o.has("senderId")) return
            } catch (_: Exception) { return }
            Store.setCfg(app, json)
            Push.fetchToken(app) { t -> deliverToken(t) }
        }

        @JavascriptInterface
        fun callState(active: Boolean) {
            runOnUiThread {
                if (active) {
                    callActive = true
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (visible) { Notif.hide(this@MainActivity); CallService.start(this@MainActivity) }
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    CallService.stop(this@MainActivity)
                    AudioRoute.reset(this@MainActivity)
                    if (callActive || System.currentTimeMillis() > lockUntil) { callActive = false; lockScreenMode(false) }
                }
            }
        }

        @JavascriptInterface
        fun setSpeaker(on: Boolean) { AudioRoute.set(applicationContext, on) }

        @JavascriptInterface
        fun ringStart() { if (visible) Ringer.start(applicationContext) }

        @JavascriptInterface
        fun ringStop() { Ringer.stop() }

        @JavascriptInterface
        fun takeAction(): String = Store.takeAction(applicationContext)

        @JavascriptInterface
        fun status(): String {
            val app = applicationContext
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val full = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
            return JSONObject()
                .put("notif", NotificationManagerCompat.from(app).areNotificationsEnabled())
                .put("fullScreen", full)
                .put("push", Store.cfg(app) != null && Store.token(app).isNotEmpty())
                .put("cfg", Store.cfg(app) != null)
                .put("token", Store.token(app).isNotEmpty())
                .put("err", Store.err(app))
                .put("last", Store.last(app))
                .put("battery", (app.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(app.packageName))
                .put("version", BuildConfig.VERSION_NAME)
                .toString()
        }

        @JavascriptInterface
        fun openSettings(kind: String) {
            val pkg = Uri.parse("package:" + packageName)
            val i = when (kind) {
                "notif" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                "fullscreen" ->
                    if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                else ->
                    if ((getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(packageName))
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    else Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { startActivity(i) } catch (_: Exception) {
                try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
            }
        }

        @JavascriptInterface
        fun testLocal() {
            Notif.message(applicationContext, "SL Zovi", "Probno obaveštenje na ovom telefonu — ako ovo vidite i čujete, obaveštenja su dozvoljena.", "test")
        }

        @JavascriptInterface
        fun reload() { runOnUiThread { web.loadUrl(BuildConfig.SERVER_URL) } }
    }

    companion object {
        @Volatile var visible = false
        private var instance: WeakReference<MainActivity>? = null

        fun deliverToken(token: String) {
            val a = instance?.get() ?: return
            a.js("window.Z&&Z.onPushToken&&Z.onPushToken(" + JSONObject.quote(token) + ")")
        }
    }
}
