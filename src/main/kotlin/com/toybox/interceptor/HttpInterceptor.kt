package com.toybox.interceptor

import com.toybox.config
import com.toybox.handler.FilterRecHtmlHandler
import com.toybox.handler.FilterRecJsonHandler
import com.toybox.handler.RemoveMomentsAdHandler
import com.toybox.util.get
import com.toybox.util.threadLocal
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.*
import io.netty.util.ReferenceCountUtil

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Path(val value: String)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ContentType(val value: String)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Code(val value: Int)


val interceptorFactory: (SocketChannel) -> Array<ChannelHandler> = {
    arrayOf(
        FilterRecHtmlHandler(),
        FilterRecJsonHandler(),
        RemoveMomentsAdHandler(),
    )
}

private val embeddedChannelRef = threadLocal {
    EmbeddedChannel(
        HttpContentDecompressor(),
        HttpObjectAggregator(config.http.maxHttpContentSize * 1024),
    )
}

abstract class HttpInterceptor: ChannelDuplexHandler() {

    private val path = ann<Path>()?.value
    private val code = ann<Code>()?.value ?: 200
    private val contentType = ann<ContentType>()?.value

    private inline fun <reified T: Annotation> ann(): T? {
        return javaClass.getDeclaredAnnotation(T::class.java)
    }

    private val isRequestHitTest = ArrayDeque<Boolean>()
    private var isResponseHitTest: Boolean? = null
    private val sparseResponseList = arrayListOf<HttpObject>()

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
        var resp: HttpResponse? = null
        var hitTest = false

        if (msg is HttpRequest && hitTest(msg)) {
            hitTest = true
            resp = onRequestHit(msg)
        }
        if (resp == null) {
            isRequestHitTest.add(hitTest)
            ctx.fireChannelRead(msg)
        } else {
            ctx.writeAndFlush(resp)
        }
    }

    protected open fun hitTest(req: HttpRequest): Boolean {
        if (path != null && req.path() != path) {
            return false
        }
        return true
    }

    protected open fun hitTest(resp: HttpResponse): Boolean {
        if (contentType != null && resp.contentType()?.startsWith(contentType) != true) {
            return false
        }
        if (resp.status().code() != code) {
            return false
        }
        return true
    }

    protected open fun onRequestHit(req: HttpRequest): HttpResponse? {
        return null
    }

    protected open fun onResponseHit(resp: FullHttpResponse): HttpResponse {
        return resp
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any?, promise: ChannelPromise?) {
        var result: Any? = msg as HttpObject

        if (msg is HttpResponse) {
            check(isResponseHitTest == null)
            isResponseHitTest = isRequestHitTest.removeFirst() && hitTest(msg)
        }
        if (isResponseHitTest!!) {
            result = aggregate(msg)?.let { onResponseHit(it) }
        }
        if (msg is LastHttpContent) {
            isResponseHitTest = null
        }
        if (result != null) {
            ctx.write(result, promise)
        }
    }

    private fun HttpRequest.path(): String {
        return uri().substringBefore('?')
    }

    private fun HttpResponse.contentType(): String? {
        return get(HttpHeaderNames.CONTENT_TYPE)
    }

    private fun aggregate(msg: HttpObject): FullHttpResponse? {
        if (sparseResponseList.isEmpty() && msg is FullHttpResponse) {
            return msg
        }
        sparseResponseList.add(msg)
        if (msg !is LastHttpContent) {
            return null
        }
        val channel = embeddedChannelRef.get()
        check(channel.inboundMessages().isEmpty())
        channel.writeInbound(*sparseResponseList.toTypedArray())
        sparseResponseList.clear()
        check(channel.inboundMessages().size == 1)
        return channel.readInbound<FullHttpResponse>()
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
        super.handlerRemoved(ctx)
        sparseResponseList.forEach { ReferenceCountUtil.release(it) }
        sparseResponseList.clear()
    }
}
