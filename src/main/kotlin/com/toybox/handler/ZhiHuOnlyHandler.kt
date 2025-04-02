package com.toybox.handler

import com.toybox.util.Log
import com.toybox.util.base64
import com.toybox.util.httpHeaders
import com.toybox.util.peerAddress
import com.toybox.util.random
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.EmptyHttpHeaders
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion

private const val TAG = "ZhiHuOnlyHandler"

class ZhiHuOnlyHandler: SimpleChannelInboundHandler<FullHttpRequest>() {

    private var peerAddress: String = ""

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpRequest) {
        Log.i(TAG, "channelRead0: new tcp request " +
                "from '$peerAddress', " +
                "decodeResult '${msg.decoderResult().isSuccess}'")

        if (msg.decoderResult().isFailure) {
            Log.i(TAG, "channelRead0: close '$peerAddress' cause invalid http request " +
                    "${msg.decoderResult().cause()}")
            ctx.errorAndClose(HttpResponseStatus.BAD_REQUEST)
            return
        }
        // 更新原 ip
        msg.headers()["x-real-ip"]?.takeIf { it.isNotEmpty() }?.also {
            Log.i(TAG, "channelRead0: map ip from '$peerAddress' to '$it'")
            peerAddress = it
        }

        val host: String? = msg.headers()[HttpHeaderNames.HOST]
        if (host?.endsWith("zhihu.com") != true) {
            Log.i(TAG, "channelRead0: invalid 'Host: $host' in '$peerAddress', close it")
            ctx.errorAndClose(HttpResponseStatus.BAD_REQUEST)
            return
        }
        Log.i(TAG, "channelRead0: zhihu.com confirmed from '$peerAddress', fire it")
        ctx.fireChannelRead(msg.retain())
    }

    private fun ChannelHandlerContext.errorAndClose(code: HttpResponseStatus) {
        val content = """
            {
              "errno": ${code.code()},
              "errmsg": "${code.reasonPhrase()}",
              "random_id": "${ByteArray(64).random().base64()}"
            }
        """.trimIndent().toByteArray()

        val headers = httpHeaders {
            HttpHeaderNames.CONTENT_TYPE to "application/json"
        }
        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, code, Unpooled.wrappedBuffer(content),
            headers, EmptyHttpHeaders.INSTANCE,
        )
        writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        Log.i(TAG, "channelActive: new tcp connected '$peerAddress'")
        peerAddress = ctx.peerAddress().toString()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        Log.i(TAG, "channelInactive: tcp disconnected '$peerAddress'")
    }
}