package com.toybox.util

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets


fun ByteBuf.inputStream() = ByteBufInputStream(this)
fun ByteBuf.losslessInput(): InputStream = object: FilterInputStream(
    inputStream()
) {
    private val readerIndex = readerIndex()
    override fun close() {
        super.close()
        readerIndex(readerIndex)
    }
}

fun ByteBuf.outputStream() = ByteBufOutputStream(this)


// TODO 可以再优化下
fun ByteBuf.writeUtf8(text: CharSequence, from: Int = 0, to: Int = text.length) {
    val encoder = Charsets.UTF_8.newEncoder()
    writeBytes(encoder.encode(CharBuffer.wrap(text, from, to)))
}

fun ByteBuf.readUtf8() = readUtf8In(StringBuilder())

// TODO 可以再优化下
fun ByteBuf.readUtf8In(out: StringBuilder): StringBuilder {
//    val decoder = Charsets.UTF_8.newDecoder()
//    for (byteBuffer in nioBuffers()) {
//        out.append(decoder.decode(byteBuffer))
//    }
    val buff = CharArray(64 * 1024)
    inputStream().reader(StandardCharsets.UTF_8).use {
        while (true) {
            val count = it.read(buff)
            if (count < 0) break
            out.append(buff, 0, count)
        }
    }
    return out
}