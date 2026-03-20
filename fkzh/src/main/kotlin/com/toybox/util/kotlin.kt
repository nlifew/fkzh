package com.toybox.util

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.InputStream
import java.io.Reader
import java.lang.StringBuilder
import java.util.Base64
import kotlin.random.Random

inline fun <reified T> T.println() = also { println(it) }

fun ByteArray.random(off: Int = 0, len: Int = size): ByteArray {
    Random.Default.nextBytes(this, off, len)
    return this
}

fun ByteArray.base64(): String {
    return Base64.getEncoder().encodeToString(this)
}

fun <K, V> List<Map.Entry<K, V>>.toMap(): Map<K, V> {
    return if (isEmpty())
        emptyMap()
    else
        LinkedHashMap<K, V>(size, 1f).also { map ->
            forEach { map[it.key] = it.value }
        }
}

fun StringBuilder.reader() = object: Reader() {
    private var offset = 0
    private var mark = -1

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (offset >= length) return -1

        val consumed = kotlin.math.min(len, length - offset)
        this@reader.getChars(offset, consumed + offset, cbuf, off)
        offset += consumed
        return consumed
    }

    override fun markSupported(): Boolean {
        return true
    }

    override fun mark(readlimit: Int) {
        check(readlimit >= 0)
        mark = offset
    }

    override fun reset() {
        offset = mark
        mark = -1
    }

    override fun skip(n: Long): Long {
        check(n.toInt() >= 0)
        val consumed = kotlin.math.min(n.toInt(), length - offset)
        offset += consumed
        return consumed.toLong()
    }

    override fun ready(): Boolean {
        return true
    }

    override fun close() { /* no-op */ }
}

inline fun <R> benchmark(tag: String, block: () -> R): R {
    val t0 = System.nanoTime()
    return try {
        block.invoke()
    } finally {
        val t1 = System.nanoTime()
        Log.d(tag, "benchmark: cost ${(t1-t0) / 1000_000} ms.")
    }
}
