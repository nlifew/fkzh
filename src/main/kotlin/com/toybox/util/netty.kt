package com.toybox.util

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpMessage
import io.netty.util.AsciiString
import java.io.FilterInputStream
import java.io.InputStream
import java.net.SocketAddress
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

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

fun ChannelHandlerContext.writeCompact(msg: Any?, promise: ChannelPromise?) {
    if (promise == null || promise.channel() == channel()) {
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
