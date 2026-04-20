package com.handleit.transit.app

import android.app.Application
import com.handleit.transit.common.ConfigValidator
import com.handleit.transit.common.TransitConfig
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class TransitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TransitConfig.MAPS_API_KEY = BuildConfig.GOOGLE_MAP_KEY
        Timber.plant(Timber.DebugTree())
        ConfigValidator.validate(isDebug = true)
    }
}
