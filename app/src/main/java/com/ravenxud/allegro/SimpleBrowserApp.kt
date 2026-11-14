package com.ravenxud.allegro

import android.app.Application
import com.ravenxud.allegro.di.ServiceLocator

class SimpleBrowserApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }

}
