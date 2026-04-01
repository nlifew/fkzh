package com.toybox.handler

import com.toybox.interceptor.HttpInterceptor
import com.toybox.interceptor.NO_MATCH
import com.toybox.interceptor.Path
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

@Path("/commercial_api/banners_v3/*")               // 各种banner
@Path("/api/v4/question/#/banner")                  // 问题详情下的 banner
@Path("/api/v4/search/hot_search")                  // 大家都在搜
@Path("/api/v4/moments/recommend_follow_people")    // 推荐关注
@Path("/api/v4/creators/extra_card")                // 创作中心中的广告
@Path("/api/v4/search/preset_words")                // 搜索框轮播词
@Path("/api/v3/entity_word")                        // 回答中的知乎直达高亮词
@Path("/ai_ingress/general/conf")                   // 知乎直达
@Path("/ai_ingress/general/me")
//@Path("/lastread/touch")                          // 触摸位置上报 [1]
// [1]. 不能block掉，否则会造成重复推荐
@Path("/api/v4/answers/#/relationship")             // XXX人赞同了该回答
@Path("/api/v4/questions/#/similar-questions")      // 相关问题推荐
@Path("/api/v4/questions/#/labels/v2")              // 圆桌收录：2026新春放映室
@Path("/v5.1/topics/question/#/relation/v2")        // 相关电影卡片
class BlockHandler: HttpInterceptor() {

    override fun onRequestHit(req: HttpRequest): HttpResponse? {
        val body = Unpooled.wrappedBuffer("""
            {}
        """.trimIndent().toByteArray())

        val headers = DefaultHttpHeaders()
        headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
        headers.set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes())

        return DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            body, headers, EmptyHttpHeaders.INSTANCE,
        )
    }
}