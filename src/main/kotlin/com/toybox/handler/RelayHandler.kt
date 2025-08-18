package com.toybox.handler

import com.toybox.config
import com.toybox.util.Log
import com.toybox.util.peerAddress
import com.toybox.util.set
import com.toybox.util.writeCompact
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPromise
import io.netty.channel.EventLoop
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContentDecompressor
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.ssl.SslContextBuilder
import io.netty.util.ReferenceCountUtil

private const val TAG = "RelayHandler"

/**
 * 我要当中间人，爷爷奶奶可高兴了，给我爱吃的大嘴巴子
 */
class RelayHandler: SimpleChannelInboundHandler<FullHttpRequest>() {

    private var thisCtx: ChannelHandlerContext? = null
    private var clientCtx: ChannelHandlerContext? = null
    private val waitingRequests = ArrayDeque<FullHttpRequest>()

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpRequest) {
        Log.i(TAG, "channelRead0: '${ctx.peerAddress()}' -> 'zhihu.com': '${msg.uri()}'")

        val host = "www.zhihu.com"
        msg[HttpHeaderNames.HOST] = host
        waitingRequests.add(msg.retain(2)) // [1]
        // [1]. 第一次是保存到列表里，抵消 SimpleChannelInboundHandler 的 release，
        // 第二次是传递给下一个 handler 需要增加一次

        val clientCtx = this.clientCtx
        if (clientCtx == null) {
            // 还没有建立通往 zhihu.com 的真链接，开始发起
            if (waitingRequests.size == 1) {
                connectToHost(ctx.channel().eventLoop(), host)
            }
        } else {
            clientCtx.writeAndFlush(msg)
        }
    }

    private inner class ClientHandler: ChannelDuplexHandler() {

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            if (msg !is FullHttpResponse) {
                ReferenceCountUtil.release(msg)
                return
            }
            val req = waitingRequests.removeFirst()
            try {
                channelRead0(ctx, req, msg)
            } finally {
                req.release()
            }
        }

        private fun channelRead0(
            ctx: ChannelHandlerContext, req: HttpRequest, resp: HttpResponse,
        ) {
            if (!resp.decoderResult().isSuccess) {
                thisCtx?.writeAndFlush(resp)
                return
            }
            // 歪，有人需要吗？
            val hitTest = InterceptHitTest(req, resp)
            ctx.fireUserEventTriggered(hitTest)
            if (!hitTest.result) {
                Log.i(TAG, "channelRead0: ignore: '${req.uri()}'")
                thisCtx?.writeAndFlush(resp)
                return
            }
            ctx.fireChannelRead(resp)
        }

        override fun channelActive(ctx: ChannelHandlerContext) {
            super.channelActive(ctx)
            clientCtx = ctx
            waitingRequests.forEach { ctx.write(it.retain()) }
            ctx.flush()
        }

        override fun channelInactive(ctx: ChannelHandlerContext?) {
            super.channelInactive(ctx)
            clientCtx = null
            thisCtx?.close()
        }

        override fun write(ctx: ChannelHandlerContext?, msg: Any?, promise: ChannelPromise?) {
            if (msg is HttpResponse) {
                thisCtx?.writeAndFlush(msg)
                return
            }
            super.write(ctx, msg, promise)
        }

        override fun flush(ctx: ChannelHandlerContext?) {
            thisCtx?.flush()
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            Log.i(TAG, "exceptionCaught: close real connection !", cause)
            ctx.close()
        }
    }

    private fun connectToHost(loop: EventLoop, host: String) {
        val sslCtx = SslContextBuilder.forClient()
            .build()

        Bootstrap()
            .group(loop)
            .channel(NioSocketChannel::class.java)
            .handler(object: ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(
                        sslCtx.newHandler(ch.alloc()),
                        HttpClientCodec(),
                        HttpObjectAggregator(config.http.maxHttpContentSize * 1024),
                        ClientHandler(),
                        HttpContentDecompressor(),
                        HttpObjectAggregator(config.http.maxHttpContentSize * 1024),
                        *interceptorFactory(ch),
                    )
                }
            })
            .connect(host, 443)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        thisCtx = ctx
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        super.channelInactive(ctx)
        thisCtx = null

        // 释放掉队列中的临时资源
        waitingRequests.forEach { it.release() }
        waitingRequests.clear()

        // 关掉 client
        clientCtx?.close()
        clientCtx = null
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        Log.i(TAG, "exceptionCaught: close '${ctx.peerAddress()}' !", cause)
        ctx.close()
    }
}