package com.toybox.handler

import com.toybox.config
import com.toybox.util.Log
import com.toybox.util.localAddress
import com.toybox.util.newResponse
import com.toybox.util.peerAddress
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.dns.DatagramDnsQuery
import io.netty.handler.codec.dns.DatagramDnsResponse
import io.netty.handler.codec.dns.DefaultDnsRawRecord
import io.netty.handler.codec.dns.DefaultDnsResponse
import io.netty.handler.codec.dns.DnsQuery
import io.netty.handler.codec.dns.DnsQuestion
import io.netty.handler.codec.dns.DnsRecordType
import io.netty.handler.codec.dns.DnsResponse
import io.netty.handler.codec.dns.DnsResponseCode
import io.netty.handler.codec.dns.DnsSection
import io.netty.resolver.dns.DefaultDnsCache
import io.netty.resolver.dns.DnsNameResolver
import io.netty.resolver.dns.DnsNameResolverBuilder
import io.netty.util.concurrent.Future
import java.net.InetAddress
import java.net.InetSocketAddress

private const val TAG = "DnsRequestHandler"

private val dnsCache = DefaultDnsCache(0, 3600, 0) // 1小时的缓存

class DnsRequestHandler: SimpleChannelInboundHandler<DnsQuery>() {

    private lateinit var resolver: DnsNameResolver
    private val awaitingFutures = arrayListOf<Future<in InetAddress>>()

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        super.handlerAdded(ctx)
        resolver = DnsNameResolverBuilder(ctx.channel().eventLoop())
            .datagramChannelType(NioDatagramChannel::class.java)
            .resolveCache(dnsCache)
            .queryTimeoutMillis(5000) // 5 秒超时
//            .nameServerProvider()
            .build()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: DnsQuery) {
        val question = msg.recordAt<DnsQuestion>(DnsSection.QUESTION, 0)
        val domain = question.name()
        Log.i(TAG, "channelRead0: dns query '$domain' from '${ctx.peerAddress()}'")

        val response = msg.newResponse()
        response.addRecord(DnsSection.QUESTION, question)

        when (question.type()) {
            DnsRecordType.A -> {
                handleARecord(ctx, domain, response)
            }
            else -> {
                response.setCode(DnsResponseCode.NOTIMP)
                ctx.writeAndFlush(response)
            }
        }
    }

    private fun handleARecord(
        ctx: ChannelHandlerContext, domain: String, resp: DnsResponse
    ) {
        if (domain.endsWith("zhihu.com.") || domain.endsWith(".zhihu.com.")) {
            Log.d(TAG, "handleARecord: shortcut zhihu.com with '${config.dns.host}'")
            ctx.response(domain, InetAddress.getByName(config.dns.host), resp)
            return
        }
        resolver.resolve(domain)
            .also { awaitingFutures.add(it) }
            .addListener { future ->
                Log.i(TAG, "handleARecord: dns result: '$domain' -> '${future.get()}'")
                if (!awaitingFutures.contains(future)) {
                    return@addListener
                }
                if (future.isSuccess) {
                    ctx.response(domain, future.get() as InetAddress, resp)
                } else {
                    resp.setCode(DnsResponseCode.NXDOMAIN)
                    ctx.writeAndFlush(resp)
                }
            }
    }

    private fun ChannelHandlerContext.response(
        domain: String, address: InetAddress, resp: DnsResponse
    ) {
        val record = DefaultDnsRawRecord(
            domain,
            DnsRecordType.A,
            300,
            Unpooled.wrappedBuffer(address.address)
        )
        resp.addRecord(DnsSection.ANSWER, record)
        writeAndFlush(resp)
    }


    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        Log.i(TAG, "channelActive: new tcp connected from '${ctx.peerAddress()}'")
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        Log.i(TAG, "channelActive: tcp disconnected from '${ctx.peerAddress()}'")
        awaitingFutures.forEach { it.cancel(false) }
        awaitingFutures.clear()
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        super.exceptionCaught(ctx, cause)
        Log.e(TAG, "exceptionCaught: exception from '${ctx.peerAddress()}', close it", cause)
        ctx.close()
    }
}