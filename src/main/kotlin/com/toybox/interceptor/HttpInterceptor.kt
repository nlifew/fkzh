package com.toybox.interceptor

import com.toybox.config
import com.toybox.handler.BlockHandler
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
        BlockHandler(),
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
    private val sparseResponseList = arrayListOf<Pair<HttpObject, ChannelPromise?>>()

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
            ReferenceCountUtil.release(msg)
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
        var finalMsg: Any? = msg as HttpObject
        var finalPromise = promise

        if (msg is HttpResponse) {
            check(isResponseHitTest == null)
            isResponseHitTest = isRequestHitTest.removeFirst() && hitTest(msg)
        }
        if (isResponseHitTest!!) {
            val pair = aggregate(ctx, msg, promise)
            finalMsg = pair?.first?.let { onResponseHit(it) }
            finalPromise = pair?.second
        }
        if (msg is LastHttpContent) {
            isResponseHitTest = null
        }
        if (finalMsg != null) {
            ctx.write(finalMsg, finalPromise)
        }
    }

    private fun HttpRequest.path(): String {
        return uri().substringBefore('?')
    }

    private fun HttpResponse.contentType(): String? {
        return get(HttpHeaderNames.CONTENT_TYPE)
    }

    private fun aggregate(
        ctx: ChannelHandlerContext,
        msg: HttpObject,
        promise: ChannelPromise?
    ): Pair<FullHttpResponse, ChannelPromise?>? {
        if (sparseResponseList.isEmpty() && msg is FullHttpResponse) {
            return msg to promise
        }
        sparseResponseList.add(msg to promise)
        if (msg !is LastHttpContent) {
            return null
        }

        val httpObjects = sparseResponseList.map { it.first }
        val promises = sparseResponseList.map { it.second }
        sparseResponseList.clear()

        val channel = embeddedChannelRef.get()
        check(channel.inboundMessages().isEmpty())
        channel.writeInbound(*httpObjects.toTypedArray())
        check(channel.inboundMessages().size == 1)

        return channel.readInbound<FullHttpResponse>() to ctx.newPromise().addListener { f ->
            if (f.isSuccess) {
                promises.forEach { it?.setSuccess() }
            } else {
                promises.forEach { it?.setFailure(f.cause()) }
            }
        }
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
        super.handlerRemoved(ctx)
        sparseResponseList.forEach {
            ReferenceCountUtil.release(it.first)
        }
        sparseResponseList.forEach {
            ReferenceCountUtil.release(it.second)
        }
        sparseResponseList.clear()
    }
}
