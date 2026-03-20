package com.toybox.handler

import com.toybox.interceptor.HttpInterceptor
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.EmptyHttpHeaders
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import java.io.File

class RemoveQuestionAdHandler: HttpInterceptor() {
    override fun hitTest(req: HttpRequest): Boolean {
        return req.uri.startsWith("/question/")
    }

    override fun onRequestHit(req: HttpRequest): HttpResponse? {
        val body = File("/Users/wangaihu/Desktop/fkzh/src/test/resources/zhihu_question.html")
            .readBytes()

        val headers = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML)
        headers.set(HttpHeaderNames.CONTENT_LENGTH, body.size)

        return DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(body), headers, EmptyHttpHeaders.INSTANCE,
        )
    }
}