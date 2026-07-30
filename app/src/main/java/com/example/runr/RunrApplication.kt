package com.example.runr

import android.app.Application
import org.osmdroid.config.Configuration

class RunrApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Configuration.getInstance().load(
            this,
            getSharedPreferences(OSMDROID_PREFERENCES_NAME, MODE_PRIVATE),
        )
        Configuration.getInstance().userAgentValue = OSM_TILE_USER_AGENT
    }

    companion object {
        const val OSM_TILE_USER_AGENT = "Runr/1.0 (com.example.runr; development)"
        private const val OSMDROID_PREFERENCES_NAME = "osmdroid"
    }
}
