package com.toybox.handler

import com.google.gson.JsonObject
import com.toybox.interceptor.ContentType
import com.toybox.interceptor.HttpInterceptor
import com.toybox.interceptor.Path
import com.toybox.util.findAndReplace
import com.toybox.util.gson
import com.toybox.util.readUtf8
import com.toybox.util.reader
import com.toybox.util.set
import com.toybox.util.writeUtf8
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponse

@Path("/question/#")
@ContentType("text/html")
class FilterQuestionHtmlHandler: HttpInterceptor() {

    override fun onResponseHit(resp: FullHttpResponse): HttpResponse {
        val body = resp.content()
        handleBody(body, false)
        resp[HttpHeaderNames.CONTENT_LENGTH] = body.readableBytes()
        return resp
    }

    fun handleBody(body: ByteBuf, test: Boolean) {
        val document = body.readUtf8()

        // 移除第一屏内容
        findAndReplace(
            document = document,
            beginToken = "<div role=\"listitem\"></div>",
            endToken = "<div role=\"listitem\"></div>",
            preferredOffset = 5 * 1024,
            block = ::removeFirstScreen
        )

        // 移除广告
        findAndReplace(
            document = document,
            beginToken = "<script id=\"js-initialData\" type=\"text/json\">",
            endToken = "</script>",
            preferredOffset = 6 * 1024,
            block = ::removeAds
        )

        body.clear().writeUtf8(document)
    }

    private fun removeFirstScreen(sb: StringBuilder) {
        sb.setLength(0)
        sb.append(' ')
    }

    private fun removeAds(sb: StringBuilder) {
        val json = gson.fromJson(sb.reader(), JsonObject::class.java)

        // 广告
        json.getAsJsonObject("initialState")
            ?.getAsJsonObject("question")
            ?.remove("adverts")

        // 划线
        json.getAsJsonObject("initialState")
            ?.getAsJsonObject("entities")
            ?.getAsJsonObject("answers")
            ?.entrySet()?.forEach {
                it.value.asJsonObject.remove("segmentInfos")
            }

        // 视频
        json.getAsJsonObject("initialState")
            ?.getAsJsonObject("entities")
            ?.remove("zvideos")

        gson.toJson(json, sb.clear())
    }
}