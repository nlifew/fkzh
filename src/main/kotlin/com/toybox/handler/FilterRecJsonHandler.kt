package com.toybox.handler

import com.google.gson.JsonObject
import com.toybox.config
import com.toybox.interceptor.ContentType
import com.toybox.interceptor.HttpInterceptor
import com.toybox.interceptor.Path
import com.toybox.util.Log
import com.toybox.util.getAsString
import com.toybox.util.removeIf
import com.toybox.util.useAsJson
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpResponse

private const val TAG = "FilterRecJsonHandler"

@Path("/api/v3/feed/topstory/recommend")
@ContentType("application/json")
class FilterRecJsonHandler: HttpInterceptor() {

    override fun onResponseHit(msg: FullHttpResponse): HttpResponse {
        Log.d(TAG, "channelRead0: accepted !")
        return msg.useAsJson<JsonObject> { handleBody(it, false) }
    }

    fun handleBody(json: JsonObject, test: Boolean) {
        json.getAsJsonArray("data").removeIf { item ->
            val type = item.asJsonObject.getAsString("type")
            // 移除广告类型
            if (type != "feed") {
                return@removeIf true
            }
            val target = item.asJsonObject.getAsJsonObject("target")
            when (target.getAsString("type")) {
                "answer" -> isBoringAnswer(item.asJsonObject)
                "article" -> isBoringArticle(item.asJsonObject)
                "zvideo" -> isBoringVideo(item.asJsonObject)
                else -> false // 姑且放过吧
            }
        }
    }

    private fun isBoringAnswer(item: JsonObject): Boolean {
        val question = item.getAsJsonObject("target")
            .getAsJsonObject("question")
        val title = question.getAsString("title")
        val author = question.getAsJsonObject("author").getAsString("name")

        val isTitleBlack = config.blackList.isTitleBlack(title)
        val isAuthorBlack = config.blackList.isAuthorBlack(author)

        Log.i(TAG, "isBoringAnswer: '$title' -> '$isTitleBlack'")
        Log.i(TAG, "isBoringAnswer: '$author' -> '$isAuthorBlack'")

        if (isTitleBlack || isAuthorBlack) {
            return true
        }

        // 替换 title
        question.addProperty("title", "[回答]${title}")
        return false
    }

    private fun isBoringArticle(item: JsonObject): Boolean {
        val target = item.getAsJsonObject("target")
        val title = target.getAsString("title")
        val author = target.getAsJsonObject("author").getAsString("name")

        val isTitleBlack = config.blackList.isTitleBlack(title)
        val isAuthorBlack = config.blackList.isAuthorBlack(author)

        Log.i(TAG, "isBoringArticle: '$title' -> '$isTitleBlack'")
        Log.i(TAG, "isBoringArticle: '$author' -> '$isAuthorBlack'")

        if (isTitleBlack || isAuthorBlack) {
            return true
        }
        // 替换 title
        target.addProperty("title", "[专栏]${title}")
        return false
    }

    private fun isBoringVideo(item: JsonObject): Boolean {
        // always filter it
        return true
    }
}

