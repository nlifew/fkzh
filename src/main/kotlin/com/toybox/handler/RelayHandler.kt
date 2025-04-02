package com.toybox.handler

import com.toybox.config
import com.toybox.util.Log
import com.toybox.util.peerAddress
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
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.ssl.SslContextBuilder

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
        waitingRequests.add(msg.retain()) // 防止被回收

        val clientCtx = this.clientCtx
        if (clientCtx == null) {
            // 还没有建立通往 zhihu.com 的真链接，开始发起
            if (waitingRequests.size == 1) {
                connectToZhiHu(ctx.channel().eventLoop())
            }
        } else {
            clientCtx.writeAndFlush(msg.retain())
        }
    }

    private inner class ClientHandler: ChannelDuplexHandler() {

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            if (msg is FullHttpResponse) {
                // 组装成 Pair 然后发射出去
                val request = waitingRequests.removeFirst()
                ctx.fireChannelRead(
                    RequestResponsePair(request, msg)
                )
            } else {
                ctx.fireChannelRead(msg)
            }
        }

        override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
            if (msg is FullHttpResponse) {
                thisCtx?.writeCompact(msg, promise)?: msg.release()
            } else {
                ctx.write(msg, promise)
            }
        }

        override fun flush(ctx: ChannelHandlerContext) {
            thisCtx?.flush()
            ctx.flush()
        }

        override fun channelActive(ctx: ChannelHandlerContext) {
            super.channelActive(ctx)
            clientCtx = ctx
            waitingRequests.forEach { ctx.write(it.retain()) }
            ctx.flush()
        }

        override fun channelInactive(ctx: ChannelHandlerContext?) {
            super.channelInactive(ctx)
            clientCtx = ctx
            thisCtx?.close()
            thisCtx = null
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            Log.i(TAG, "exceptionCaught: close zhihu connection !", cause)
            ctx.close()
        }
    }

    private class EchoHandler: SimpleChannelInboundHandler<RequestResponsePair>() {
        override fun channelRead0(ctx: ChannelHandlerContext, msg: RequestResponsePair) {
//            msg.resp.content().toString(StandardCharsets.UTF_8).println()
            ctx.writeAndFlush(msg.resp.retain())
        }
    }

    private fun connectToZhiHu(loop: EventLoop) {
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
                        HttpContentDecompressor(),
                        HttpObjectAggregator(config.http.maxHttpContentSize * 1024),
                        ClientHandler(),
                        FilterRecHtmlHandler(),
                        FilterRecJsonHandler(),
                        EchoHandler(),
                    )
                }
            })
            .connect("www.zhihu.com", 443)
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