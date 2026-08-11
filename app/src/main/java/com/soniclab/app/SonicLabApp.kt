package com.soniclab.app

import android.app.Application
import com.soniclab.app.di.AppContainer

class SonicLabApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.playerController.bind()
    }
}
