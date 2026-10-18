package com.slzovi.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** "Odbij" iz obaveštenja o pozivu — ne otvara aplikaciju. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Notif.ACTION_DECLINE) return
        val id = intent.getStringExtra("callId") ?: ""
        val group = intent.getBooleanExtra("group", false)
        Notif.cancelCall(context)
        if (group || id.isEmpty()) return
        val pr = goAsync()
        Thread {
            try { Http.post("/api/call/decline", JSONObject().put("callId", id).toString()) } finally { pr.finish() }
        }.start()
    }
}
