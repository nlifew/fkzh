package com.toybox.fkzh.util

import android.util.Log
import com.toybox.util.LogImpl

class AndroidLogImpl: LogImpl {
    override fun println(
        level: String,
        tag: String,
        msg: String,
        e: Throwable?
    ) {
        val priority = when (level) {
            "V" -> Log.VERBOSE
            "I" -> Log.INFO
            "D" -> Log.DEBUG
            "W" -> Log.WARN
            "E" -> Log.ERROR
            else -> Log.ASSERT
        }
        if (e == null) {
            Log.println(priority, tag, msg)
            return
        }
        val sb = StringBuilder(msg)
            .append('\n')
            .append(Log.getStackTraceString(e))
        Log.println(priority, tag, sb.toString())
    }

}