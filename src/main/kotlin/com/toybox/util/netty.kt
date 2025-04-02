package com.toybox.util

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.dns.DatagramDnsQuery
import io.netty.handler.codec.dns.DatagramDnsResponse
import io.netty.handler.codec.dns.DefaultDnsQuery
import io.netty.handler.codec.dns.DefaultDnsResponse
import io.netty.handler.codec.dns.DnsQuery
import io.netty.handler.codec.dns.DnsResponse
import io.netty.handler.codec.dns.DnsResponseCode
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpMessage
import io.netty.util.AsciiString
import java.io.EOFException
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.logging.Filter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

fun ChannelHandlerContext.peerAddress(): SocketAddress? = channel().remoteAddress()
fun ChannelHandlerContext.localAddress(): SocketAddress = channel().localAddress()

@JvmInline
value class HttpHeaderScope(
    val headers: DefaultHttpHeaders
) {
    infix fun AsciiString.to(value: String) {
        headers.add(this, value)
    }
}

inline fun httpHeaders(block: HttpHeaderScope.() -> Unit): DefaultHttpHeaders {
    return DefaultHttpHeaders().also { block(HttpHeaderScope(it)) }
}

operator fun HttpMessage.get(name: AsciiString): String? {
    return headers()[name]
}
operator fun HttpMessage.set(name: AsciiString, value: Any) {
    headers()[name] = value
}


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

fun <R> ByteBuf.use(block: (ByteBuf) -> R): R {
    val idx = readerIndex()
    return try {
        block(this)
    } finally {
        readerIndex(idx)
    }
}

fun ByteBuf.startsWithAscii(): Boolean {
    if (readableBytes() <= 8) {
        return false
    }
    for (i in 0 until 8) {
        val ch = getByte(i).toInt() and 0xFF
        if (ch !in 0x20..0x7E) {
            return false
        }
    }
    return true
}

fun ChannelHandlerContext.writeCompact(msg: Any, promise: ChannelPromise) {
    if (promise.channel() == channel()) {
        write(msg, promise)
        return
    }
    val compact = newPromise()
    compact.addListener {
        when {
            it.isSuccess -> promise.setSuccess()
            else -> promise.setFailure(it.cause())
        }
    }
    write(msg, compact)
}

fun DnsQuery.newResponse(): DnsResponse {
    return when (this) {
        is DatagramDnsQuery -> DatagramDnsResponse(
            recipient(), sender(), id(), opCode(), DnsResponseCode.NOERROR
        )
        is DefaultDnsQuery -> DefaultDnsResponse(
            id(), opCode(), DnsResponseCode.NOERROR
        )
        else -> throw UnsupportedOperationException("unknown DnsQuery '$this'")
    }
}
