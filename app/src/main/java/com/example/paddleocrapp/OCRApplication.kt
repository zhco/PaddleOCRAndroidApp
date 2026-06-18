package com.example.paddleocrapp

import android.app.Application

/**
 * Application 类
 */
class OCRApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: OCRApplication
            private set
    }
}
