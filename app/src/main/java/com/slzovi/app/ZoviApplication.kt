package com.slzovi.app

import android.app.Application

class ZoviApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notif.createChannels(this)
        Push.init(this) // ako su podešavanja već sačuvana; neophodno kad FCM pokrene proces
    }
}
