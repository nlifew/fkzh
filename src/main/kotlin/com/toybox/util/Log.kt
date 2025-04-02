package com.toybox.util

import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Formatter
import kotlin.math.min


object Log {
    fun i(tag: String, msg: String, e: Throwable? = null) {
        println("I", tag, msg, e)
    }

    fun d(tag: String, msg: String, e: Throwable? = null) {
        println("D", tag, msg, e)
    }

    fun e(tag: String, msg: String, e: Throwable? = null) {
        println("E", tag, msg, e)
    }

    fun println(level: String, tag: String, msg: String, e: Throwable?) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        val milliSeconds = calendar.get(Calendar.MILLISECOND)

        val sb = StringBuilder()
        Formatter(sb).format(
            "%d-%02d-%02d %02d:%02d:%02d.%03d %s/%-24s %s",
            year, month, day, hour, minutes, seconds, milliSeconds, level, tag, msg
        )
        val writer = object: Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) {
                sb.append(cbuf, off, len)
            }
            override fun flush() { /* no-op */ }
            override fun close() { /* no-op */ }
        }
        e?.printStackTrace(PrintWriter(writer))

        System.err.println(sb)
    }
}