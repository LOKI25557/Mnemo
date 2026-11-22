package com.mnemo.app

import android.app.Application
import com.mnemo.app.di.AppContainer

/**
 * Application entry point for Mnemo.
 * Initializes the dependency injection container and verifies on-device runtime configuration.
 */
class MnemoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
