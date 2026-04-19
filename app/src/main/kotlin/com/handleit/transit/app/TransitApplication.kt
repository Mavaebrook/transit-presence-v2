package com.handleit.transit.app

import android.app.Application
import com.handleit.transit.common.ConfigValidator
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class TransitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        ConfigValidator.validate(isDebug = true)
    }
}
