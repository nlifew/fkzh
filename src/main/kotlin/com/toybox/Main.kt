package com.toybox

import com.aayushatharva.brotli4j.Brotli4jLoader
import com.toybox.handler.RelayHandler
import com.toybox.handler.ZhiHuOnlyHandler
import com.toybox.interceptor.interceptorFactory
import com.toybox.util.gson
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.EmptyHttpHeaders
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpServerKeepAliveHandler
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.ResourceLeakDetector
import java.io.File

lateinit var nio: NioEventLoopGroup

fun envSetup() {
    config = File("config.json").reader().use {
        gson.fromJson(it, Config::class.java)
    }

    Brotli4jLoader.ensureAvailability()
    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED)
    nio = NioEventLoopGroup(config.threadNum)
}

fun main() {
    envSetup()
    startProxyServer()
    println("ok")
}

private class EchoHandler: SimpleChannelInboundHandler<FullHttpRequest>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpRequest) {
        val body = buildString {
            append(msg.method()).append(" ").append(msg.uri()).append(" ").append(msg.protocolVersion()).append("\r\n")
            msg.headers().forEach {
                append(it.key).append(": ").append(it.value).append("\r\n")
            }
            append("\r\n")
        }.toByteArray()

        val headers = DefaultHttpHeaders()
        headers.add("Content-Length", body.size)
        headers.add("Content-Type", "text/plain; charset=UTF-8")

        val resp = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(body),
            headers, EmptyHttpHeaders.INSTANCE,
        )
        ctx.writeAndFlush(resp)
    }

}

private fun startProxyServer() {
    ServerBootstrap()
        .channel(NioServerSocketChannel::class.java)
        .group(nio, nio)
        .childHandler(object: ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
                ch.pipeline().addLast(
                    HttpServerCodec(),
//                    HttpServerKeepAliveHandler(),
                    HttpObjectAggregator(config.http.maxHttpContentSize * 1024, true),
//                    EchoHandler(),
                    ZhiHuOnlyHandler(),
                    *interceptorFactory(ch),
                    RelayHandler(),
                )
            }
        })
        .bind(config.http.localAddress, config.http.localPort)
        .await()
}
