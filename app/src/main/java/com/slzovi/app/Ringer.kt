package com.slzovi.app

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/** Zvonjenje dok je aplikacija otvorena: sistemski ton zvona (jačina zvona, ne medija) + vibracija. */
object Ringer {
    private val h = Handler(Looper.getMainLooper())
    private var ring: Ringtone? = null
    private var vib: Vibrator? = null

    private val keep = object : Runnable {
        override fun run() {
            val r = ring ?: return
            try { if (!r.isPlaying) r.play() } catch (_: Exception) { }
            h.postDelayed(this, 700)
        }
    }
    private val limit = Runnable { stop() }

    fun start(c: Context) {
        h.post {
            if (ring != null) return@post
            val app = c.applicationContext
            try {
                val r = RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                if (r != null) {
                    r.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    r.play()
                    ring = r
                }
            } catch (_: Exception) { }
            try {
                @Suppress("DEPRECATION")
                val v = app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 700), 0))
                vib = v
            } catch (_: Exception) { }
            h.postDelayed(keep, 700)
            h.postDelayed(limit, 50_000)
        }
    }

    fun stop() {
        h.post {
            h.removeCallbacks(keep)
            h.removeCallbacks(limit)
            try { ring?.stop() } catch (_: Exception) { }
            ring = null
            try { vib?.cancel() } catch (_: Exception) { }
            vib = null
        }
    }
}
