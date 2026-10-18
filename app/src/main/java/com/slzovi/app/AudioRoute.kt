package com.slzovi.app

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Biranje izlaza zvuka tokom poziva: zvučnik ili slušalica na uhu (a ako su priključene slušalice, one). */
object AudioRoute {
    private fun am(c: Context) = c.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun set(c: Context, speaker: Boolean) {
        try {
            val a = am(c)
            a.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= 31) {
                val devs = a.availableCommunicationDevices
                val headset = intArrayOf(
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET
                )
                val pick = if (speaker) devs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                else devs.firstOrNull { it.type in headset } ?: devs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                if (pick != null) a.setCommunicationDevice(pick) else legacy(a, speaker)
            } else {
                legacy(a, speaker)
            }
        } catch (_: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun legacy(a: AudioManager, on: Boolean) { a.isSpeakerphoneOn = on }

    fun reset(c: Context) {
        try {
            val a = am(c)
            if (Build.VERSION.SDK_INT >= 31) a.clearCommunicationDevice()
            else legacy(a, false)
            a.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) { }
    }
}
