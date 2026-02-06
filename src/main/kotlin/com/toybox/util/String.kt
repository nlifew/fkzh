package com.toybox.util

fun StringBuilder.replaceAll(before: String, after: String): StringBuilder {
    var beginIndex = 0
    while (true) {
        val idx = indexOf(before, beginIndex)
        if (idx < 0) break
        replace(idx, idx + before.length, after)
        beginIndex += after.length
    }
    return this
}
