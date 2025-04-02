package com.toybox

import com.aayushatharva.brotli4j.Brotli4jLoader
import com.toybox.handler.DnsRequestHandler
import com.toybox.handler.RelayHandler
import com.toybox.handler.ZhiHuOnlyHandler
import com.toybox.util.gson
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder
import io.netty.handler.codec.dns.DatagramDnsQueryEncoder
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder
import io.netty.handler.codec.dns.TcpDnsQueryDecoder
import io.netty.handler.codec.dns.TcpDnsResponseEncoder
import io.netty.handler.codec.http.HttpContentCompressor
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpServerKeepAliveHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.File

lateinit var nio: NioEventLoopGroup

fun envSetup() {
    File("config.json").reader().use {
        config = gson.fromJson(it, Config::class.java)
    }

    Brotli4jLoader.ensureAvailability()
//    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED)
    nio = NioEventLoopGroup(config.threadNum)
}

fun main() {
    envSetup()
    startProxyServer()
    startDnsServer()
}

private fun startProxyServer() {
    val certPath = config.http.certPath
    val keyPath = config.http.keyPath
    val sslCtx = if (!certPath.isNullOrEmpty() && !keyPath.isNullOrEmpty())
        SslContextBuilder.forServer(File(certPath), File(keyPath)).build()
    else
        null

    ServerBootstrap()
        .channel(NioServerSocketChannel::class.java)
        .group(nio, nio)
        .childHandler(object: ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
                sslCtx?.newHandler(ch.alloc())?.let {
                    ch.pipeline().addLast(it)
                }
                ch.pipeline().addLast(
                    HttpServerCodec(),
                    HttpServerKeepAliveHandler(),
                    HttpObjectAggregator(config.http.maxHttpContentSize * 1024, true),
                    HttpContentCompressor(),
                    ZhiHuOnlyHandler(),
                    RelayHandler(),
                )
            }
        })
        .bind(config.http.localAddress, config.http.localPort)
        .await()
}

private fun startDnsServer() {
    Bootstrap()
        .group(nio)
        .channel(NioDatagramChannel::class.java)
        .handler(object: ChannelInitializer<DatagramChannel>() {
            override fun initChannel(ch: DatagramChannel) {
                ch.pipeline().addLast(
                    DatagramDnsQueryDecoder(),
                    DatagramDnsResponseEncoder(),
                    DnsRequestHandler(),
                )
            }
        })
        .bind(config.dns.localAddress, config.dns.localPort)
        .await()
}
