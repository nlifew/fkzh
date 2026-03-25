package com.toybox.fkzh.app

import android.app.Application
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

lateinit var appContext: AppContext
    private set

class AppContext: Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }
}