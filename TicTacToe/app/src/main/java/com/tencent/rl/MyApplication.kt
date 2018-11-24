package com.tencent.rl

import android.app.Application
import android.content.Context

class MyApplication: Application() {

    companion object {
        lateinit var GlobalContext: Context
    }

    override fun onCreate() {
        super.onCreate()
        GlobalContext = applicationContext
    }

}