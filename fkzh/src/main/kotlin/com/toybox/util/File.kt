package com.toybox.util

import com.toybox.Config
import java.io.File
import java.io.InputStream

fun openFile(path: String): InputStream {
    val innerPath = path.substringAfter("file://")
    if (innerPath == path) {
        return File(path).inputStream()
    }
    return Config::class.java.classLoader.getResourceAsStream(innerPath)
}
