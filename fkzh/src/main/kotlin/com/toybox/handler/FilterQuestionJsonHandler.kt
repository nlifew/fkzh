package com.toybox.handler

import com.google.gson.JsonObject
import com.toybox.interceptor.HttpInterceptor
import com.toybox.interceptor.Path
import com.toybox.util.useAsJson
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpResponse

@Path("/api/v4/questions/#/feeds")
class FilterQuestionJsonHandler: HttpInterceptor() {

    override fun onResponseHit(resp: FullHttpResponse): HttpResponse {
        return resp.useAsJson<JsonObject>(::handleBody)
    }

    private fun handleBody(json: JsonObject) {
        // 移除划线内容
        json.getAsJsonArray("data")
            .forEach {
                it.asJsonObject.getAsJsonObject("target").remove("segment_infos")
            }

        // 移除广告信息
        json.remove("ad_info")
    }
}