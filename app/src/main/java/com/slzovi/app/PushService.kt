package com.slzovi.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Store.setToken(this, token)
        MainActivity.deliverToken(token)
        Push.registerNative(token)
    }

    override fun onMessageReceived(m: RemoteMessage) {
        val d = m.data
        val visible = MainActivity.visible
        Store.setLast(this, (d["type"] ?: "?") + (if (visible) " (app otvorena) " else " ") + "u " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
        when (d["type"]) {
            "call" -> {
                if (visible) return // aplikacija je otvorena, zvoni sama
                val video = d["video"] == "1"
                val name = d["name"] ?: ""
                val from = d["caller"] ?: d["from"] ?: ""
                Notif.incoming(
                    this, d["callId"] ?: "",
                    getString(if (video) R.string.incoming_video else R.string.incoming_call),
                    if (name.isNotEmpty()) "$name ($from)" else from, false
                )
            }
            "gcall" -> {
                if (visible) return
                val video = d["video"] == "1"
                Notif.incoming(
                    this, d["callId"] ?: "",
                    getString(if (video) R.string.group_video else R.string.group_call) + " · " + (d["gname"] ?: ""),
                    d["caller"] ?: d["from"] ?: "", true
                )
            }
            "call_end" -> Notif.cancelCall(this, d["callId"])
            "msg" -> Notif.message(this, d["title"] ?: "SL Zovi", d["body"] ?: "", d["peer"] ?: "")
            "gmsg" -> Notif.message(this, d["title"] ?: "SL Zovi", d["body"] ?: "", "g:" + (d["group"] ?: ""))
        }
    }
}
