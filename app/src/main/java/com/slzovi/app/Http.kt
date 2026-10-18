package com.slzovi.app

import android.webkit.CookieManager
import java.net.HttpURLConnection
import java.net.URL

/** Jednostavni POST ka serveru koristeći isti kolačić sesije kao i web stranica u aplikaciji. */
object Http {
    fun post(path: String, json: String): Boolean {
        return try {
            val cookie = CookieManager.getInstance().getCookie(BuildConfig.SERVER_URL) ?: return false
            val c = URL(BuildConfig.SERVER_URL + path).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Cookie", cookie)
            c.outputStream.use { it.write(json.toByteArray()) }
            val ok = c.responseCode in 200..299
            c.disconnect()
            ok
        } catch (_: Exception) { false }
    }
}
