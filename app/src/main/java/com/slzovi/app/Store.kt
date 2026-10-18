package com.slzovi.app

import android.content.Context
import org.json.JSONObject

/** Mala lokalna memorija: Firebase podešavanja, token i akcija iz obaveštenja koju web stranica preuzima. */
object Store {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("zovi", Context.MODE_PRIVATE)

    fun cfg(c: Context): JSONObject? =
        try { p(c).getString("cfg", null)?.let { JSONObject(it) } } catch (_: Exception) { null }

    fun setCfg(c: Context, json: String) { p(c).edit().putString("cfg", json).apply() }

    fun token(c: Context): String = p(c).getString("token", "") ?: ""
    fun setToken(c: Context, t: String) { p(c).edit().putString("token", t).apply() }

    /** Akcija ("accept") + id poziva; važi 60 sekundi. */
    fun setAction(c: Context, action: String, id: String?) {
        val o = JSONObject().put("a", action).put("id", id ?: "").put("ts", System.currentTimeMillis())
        p(c).edit().putString("action", o.toString()).apply()
    }

    fun takeAction(c: Context): String {
        val s = p(c).getString("action", null) ?: return "null"
        p(c).edit().remove("action").apply()
        return try {
            val o = JSONObject(s)
            if (System.currentTimeMillis() - o.getLong("ts") > 60_000) "null"
            else JSONObject().put("a", o.getString("a")).put("id", o.getString("id")).toString()
        } catch (_: Exception) { "null" }
    }

    fun err(c: Context): String = p(c).getString("err", "") ?: ""
    fun setErr(c: Context, e: String) { p(c).edit().putString("err", e.take(300)).apply() }

    fun ringId(c: Context): String = p(c).getString("ringId", "") ?: ""
    fun setRingId(c: Context, id: String) { p(c).edit().putString("ringId", id).apply() }
}
