package com.toybox.handler

import com.toybox.SSLPolicy
import com.toybox.config
import com.toybox.util.Log
import com.toybox.util.peerAddress
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoop
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.ReferenceCountUtil

private const val TAG = "RelayHandler"

private val clientSSLCtx: SslContext? by lazy {
    when (config.http.remoteSSLPolicy) {
        SSLPolicy.NONE -> null
        SSLPolicy.UNSECURE -> SslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build()
        SSLPolicy.FULL -> SslContextBuilder.forClient()
            .build()
    }
}

/**
 * 我要当中间人，爷爷奶奶可高兴了，给我爱吃的大嘴巴子
 */
class RelayHandler: SimpleChannelInboundHandler<FullHttpRequest>() {

    private var thisCtx: ChannelHandlerContext? = null
    private var clientCtx: ChannelHandlerContext? = null
    private val waitingRequests = ArrayDeque<FullHttpRequest>()

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpRequest) {
        Log.i(TAG, "channelRead0: '${ctx.peerAddress()}' >>>>>>>> '${msg.uri()}'")

        // 替换 host
        msg.headers().set(HttpHeaderNames.HOST, config.http.host)

        val clientCtx = this.clientCtx
        if (clientCtx != null) {
            clientCtx.writeAndFlush(msg.retain())
        } else {
            waitingRequests.add(msg.retain()) // [1]
            // [1]. 抵消 SimpleChannelInboundHandler 的 release
            // 还没有建立通往服务器的真链接，开始发起
            if (waitingRequests.size == 1) {
                connectToHost(ctx.channel().eventLoop())
            }
        }
    }

    private inner class ClientHandler: ChannelDuplexHandler() {

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            thisCtx?.writeAndFlush(msg) ?: run { ReferenceCountUtil.release(msg) }
        }

        override fun channelActive(ctx: ChannelHandlerContext) {
            Log.i(TAG, "channelActive: ClientHandler active, flush HTTP request. " +
                    "size = '${waitingRequests.size}'")
            super.channelActive(ctx)
            clientCtx = ctx
            waitingRequests.forEach { ctx.write(it) }
            waitingRequests.clear()
            ctx.flush()
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            Log.i(TAG, "channelActive: ClientHandler inactive")
            super.channelInactive(ctx)
            clientCtx = null
            thisCtx?.close()
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            Log.e(TAG, "exceptionCaught: close real connection !", cause)
            ctx.close()
        }
    }

    private fun connectToHost(loop: EventLoop) {
        Bootstrap()
            .group(loop)
            .channel(NioSocketChannel::class.java)
            .handler(object: ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    clientSSLCtx?.newHandler(ch.alloc())?.let {
                        ch.pipeline().addLast(it)
                    }
                    ch.pipeline().addLast(
                        HttpClientCodec(),
                        ClientHandler(),
                    )
                }
            })
            .connect(config.http.remoteAddress, config.http.remotePort)
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