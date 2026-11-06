package com.byteall.kharismart

import android.app.Application
import com.byteall.kharismart.di.AppContainer

class KharismartApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
