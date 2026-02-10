package com.toybox.handler

import com.toybox.interceptor.HttpInterceptor
import com.toybox.interceptor.NO_MATCH
import com.toybox.interceptor.UriMatcher
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
import io.netty.handler.codec.http.QueryStringDecoder

private val uriMatcher = UriMatcher(NO_MATCH).apply {
    addPath("/commercial_api/banners_v3/*", 200)            // 各种banner
    addPath("/api/v4/search/hot_search", 200)               // 大家都在搜
    addPath("/api/v4/moments/recommend_follow_people", 200) // 推荐关注
    addPath("/api/v4/creators/extra_card", 200)             // 创作中心中的广告
    addPath("/api/v4/search/preset_words", 200)             // 搜索框轮播词
    addPath("/api/v3/entity_word", 200)                     // 回答中的知乎直达高亮词
    addPath("/ai_ingress/general/conf", 200)                // 知乎直达
    addPath("/ai_ingress/general/me", 200)
    addPath("/lastread/touch", 201)                         // 触摸位置上报
}

class BlockHandler: HttpInterceptor() {

    private val matchList = ArrayDeque<Int>()

    override fun hitTest(req: HttpRequest): Boolean {
        val path = QueryStringDecoder(req.uri()).path()
        val match = uriMatcher.matchPath(path)
        if (match > 0) {
            matchList.add(match)
        }
        return match > 0
    }

    override fun onRequestHit(req: HttpRequest): HttpResponse? {
        val body = Unpooled.wrappedBuffer("""
            {}
        """.trimIndent().toByteArray())

        val headers = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        headers.set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes())

        val match = matchList.removeFirst()
        return DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.valueOf(match),
            body, headers, EmptyHttpHeaders.INSTANCE,
        )
    }
}