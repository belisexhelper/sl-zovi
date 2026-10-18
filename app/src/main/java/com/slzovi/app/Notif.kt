package com.slzovi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

object Notif {
    const val CH_CALLS = "calls_v1"
    const val CH_MSGS = "msgs_v1"
    const val CH_ONGOING = "ongoing_v1"
    const val CALL_ID = 1001
    const val TAG_CALL = "call"
    const val ACTION_DECLINE = "com.slzovi.app.DECLINE"

    private fun nm(c: Context) = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels(c: Context) {
        val m = nm(c)
        val ring = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val calls = NotificationChannel(CH_CALLS, c.getString(R.string.ch_calls), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), ring)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 900, 700, 900, 700)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableLights(true)
        }
        val msgs = NotificationChannel(CH_MSGS, c.getString(R.string.ch_msgs), NotificationManager.IMPORTANCE_HIGH).apply {
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        val ongoing = NotificationChannel(CH_ONGOING, c.getString(R.string.ch_ongoing), NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
        }
        m.createNotificationChannels(listOf(calls, msgs, ongoing))
    }

    private fun activity(c: Context, req: Int, extras: Intent.() -> Unit): PendingIntent {
        val i = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extras()
        }
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Dolazni poziv: zvoni (insistent), puni ekran preko zaključanog telefona, Prihvati / Odbij. */
    fun incoming(c: Context, callId: String, title: String, text: String, group: Boolean) {
        Store.setRingId(c, callId)
        val open = activity(c, 10) { putExtra("call", true); putExtra("callId", callId) }
        val accept = activity(c, 11) { putExtra("call", true); putExtra("callId", callId); putExtra("action", "accept") }
        val decline = PendingIntent.getBroadcast(
            c, 12,
            Intent(c, ActionReceiver::class.java).setAction(ACTION_DECLINE).putExtra("callId", callId).putExtra("group", group),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(c, CH_CALLS)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF2F6FEA.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(45_000)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, c.getString(R.string.decline), decline)
            .addAction(0, c.getString(if (group) R.string.join else R.string.accept), accept)
        val n = b.build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        try { nm(c).notify(TAG_CALL, CALL_ID, n) } catch (_: SecurityException) { }
    }

    fun cancelCall(c: Context, callId: String? = null) {
        if (!callId.isNullOrEmpty()) {
            val cur = Store.ringId(c)
            if (cur.isNotEmpty() && cur != callId) return
        }
        nm(c).cancel(TAG_CALL, CALL_ID)
    }

    fun message(c: Context, title: String, body: String, key: String) {
        val open = activity(c, 20) { }
        val n = NotificationCompat.Builder(c, CH_MSGS)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF2F6FEA.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try { nm(c).notify("msg:$key", 2000, n) } catch (_: SecurityException) { }
    }

    fun ongoing(c: Context): Notification {
        val open = activity(c, 30) { }
        return NotificationCompat.Builder(c, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF2F6FEA.toInt())
            .setContentTitle(c.getString(R.string.app_name))
            .setContentText(c.getString(R.string.call_active))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }
}
