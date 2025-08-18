package com.toybox

import com.aayushatharva.brotli4j.Brotli4jLoader
import com.toybox.handler.RelayHandler
import com.toybox.util.gson
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpContentCompressor
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import java.io.File

lateinit var nio: NioEventLoopGroup

fun envSetup() {
    config = File("config.json").reader().use {
        gson.fromJson(it, Config::class.java)
    }

    Brotli4jLoader.ensureAvailability()
//    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED)
    nio = NioEventLoopGroup(config.threadNum)
}

fun main() {
    envSetup()
    startProxyServer()
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
                    HttpContentCompressor(),
                    RelayHandler(),
                )
            }
        })
        .bind(config.http.localAddress, config.http.localPort)
        .await()
}
