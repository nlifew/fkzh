package com.toybox.handler

import com.toybox.util.get
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.*
import java.lang.ref.WeakReference
import java.net.URL

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Path(val value: String)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ContentType(val value: String)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Code(val value: Int)


class InterceptHitTest(
    val req: HttpRequest,
    val resp: HttpResponse,
) {
    var result: Boolean = false
}

val interceptorFactory: (SocketChannel) -> Array<HttpInterceptor> = {
    arrayOf(
        FilterRecHtmlHandler(),
        FilterRecJsonHandler(),
        BlockTouchHandler(),
    )
}

abstract class HttpInterceptor: SimpleChannelInboundHandler<FullHttpResponse>() {

    private val code = ann<Code>()?.value ?: 200
    private val path = ann<Path>()?.value
    private val contentType = ann<ContentType>()?.value

    private inline fun <reified T: Annotation> ann(): T? {
        return javaClass.getDeclaredAnnotation(T::class.java)
    }

    private var hitTest = false

    override fun acceptInboundMessage(msg: Any?): Boolean {
        return hitTest && msg is FullHttpResponse
    }

    abstract override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse)

    private fun hitTest(evt: InterceptHitTest): Boolean {
        evt.result = onHitTest(evt.req, evt.resp)
        hitTest = evt.result
        return hitTest
    }

    private fun onHitTest(req: HttpRequest, resp: HttpResponse): Boolean {
        if (path != null && req.path() != path) {
            return false
        }
        if (contentType != null && resp.contentType()?.startsWith(contentType) != true) {
            return false
        }
        if (resp.status().code() != code) {
            return false
        }
        return true
    }

    private fun HttpRequest.path(): String {
        return uri().substringBefore('?')
    }

    private fun HttpResponse.contentType(): String? {
        return get(HttpHeaderNames.CONTENT_TYPE)
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt is InterceptHitTest && hitTest(evt)) {
            return
        }
        super.userEventTriggered(ctx, evt)
    }
}
