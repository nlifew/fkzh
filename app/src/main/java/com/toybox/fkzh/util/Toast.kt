package com.toybox.fkzh.util

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.toybox.fkzh.app.appContext

val mainHandler = Handler(Looper.getMainLooper())

fun toast(duration: Int = Toast.LENGTH_SHORT, block: () -> String) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        mainHandler.post { toast(duration, block) }
        return
    }
    Toast.makeText(appContext, block(), duration).show()
}
