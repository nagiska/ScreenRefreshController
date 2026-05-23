package com.screenrefresh.controller

import android.app.Application
import com.screenrefresh.controller.data.AppDatabase

class ScreenRefreshApp : Application() {
    val db by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: ScreenRefreshApp
            private set
    }
}
