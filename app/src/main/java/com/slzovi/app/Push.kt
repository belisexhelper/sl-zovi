package com.slzovi.app

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

/** Firebase se pokreće dinamički — podešavanja stižu sa servera (Admin → Server), nema google-services.json u aplikaciji. */
object Push {
    private const val TAG = "ZoviPush"

    fun init(ctx: Context): Boolean {
        val app = ctx.applicationContext
        try {
            if (FirebaseApp.getApps(app).isNotEmpty()) return true
            val c = Store.cfg(app) ?: return false
            val o = FirebaseOptions.Builder()
                .setApplicationId(c.getString("appId"))
                .setApiKey(c.getString("apiKey"))
                .setProjectId(c.getString("projectId"))
                .setGcmSenderId(c.getString("senderId"))
                .build()
            FirebaseApp.initializeApp(app, o)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "init failed", e)
            Store.setErr(app, "init: " + e)
            return false
        }
    }

    fun fetchToken(ctx: Context, cb: (String) -> Unit) {
        if (!init(ctx)) return
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
                if (t.isSuccessful && !t.result.isNullOrEmpty()) {
                    Store.setToken(ctx, t.result)
                    Store.setErr(ctx, "")
                    cb(t.result)
                } else {
                    Log.e(TAG, "token failed", t.exception)
                    Store.setErr(ctx, "token: " + t.exception)
                }
            }
        } catch (e: Exception) { Log.e(TAG, "token error", e); Store.setErr(ctx, "token: " + e) }
    }

    /** Javlja token serveru direktno (kad se token promeni a aplikacija nije otvorena). */
    fun registerNative(token: String) {
        Thread { Http.post("/api/push/register", org.json.JSONObject().put("token", token).toString()) }.start()
    }
}
