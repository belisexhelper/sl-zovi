package com.slzovi.app

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Drži aplikaciju živom (mikrofon/kamera rade i kad je ekran zaključan ili aplikacija u pozadini) dok traje poziv. */
class CallService : Service() {
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var type = 0
        if (granted(Manifest.permission.RECORD_AUDIO)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (granted(Manifest.permission.CAMERA)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (type == 0) { stopSelf(); return START_NOT_STICKY }
        try {
            ServiceCompat.startForeground(this, 2, Notif.ongoing(this), type)
        } catch (e: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        if (wl == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "slzovi:call").apply { acquire(4 * 60 * 60 * 1000L) }
        }
        return START_NOT_STICKY
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        try { wl?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
        wl = null
        super.onDestroy()
    }

    companion object {
        fun start(c: Context) {
            try { ContextCompat.startForegroundService(c, Intent(c, CallService::class.java)) } catch (_: Exception) { }
        }
        fun stop(c: Context) {
            try { c.stopService(Intent(c, CallService::class.java)) } catch (_: Exception) { }
        }
    }
}
