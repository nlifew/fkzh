package com.toybox.handler

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.toybox.config
import com.toybox.util.Log
import com.toybox.util.get
import com.toybox.util.getAsString
import com.toybox.util.gson
import com.toybox.util.losslessInput
import com.toybox.util.outputStream
import com.toybox.util.set
import com.toybox.util.startsWithAscii
import com.toybox.util.toJsonArray
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus

private const val TAG = "FilterRecJsonHandler"

class FilterRecJsonHandler: SimpleChannelInboundHandler<RequestResponsePair>() {

    override fun acceptInboundMessage(msg: Any?): Boolean {
        val msg = (msg as? RequestResponsePair) ?: return false
        return msg.req.uri().startsWith("/api/v3/feed/topstory/recommend?")
                && msg.resp.status() == HttpResponseStatus.OK
                && msg.resp[HttpHeaderNames.CONTENT_TYPE]?.startsWith("application/json") == true
                && msg.resp.content().startsWithAscii()
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: RequestResponsePair) {
        Log.d(TAG, "channelRead0: accepted !")
        val body = msg.resp.content()
        handleBody(body, false)
        msg.resp[HttpHeaderNames.CONTENT_LENGTH] = body.readableBytes()
        ctx.writeAndFlush(msg.resp.retain())
    }

    fun handleBody(body: ByteBuf, test: Boolean) {
        val json = body.losslessInput().reader().use {
            gson.fromJson(it, JsonObject::class.java)
        }
        // 移除无聊的东西
        val newData = JsonArray()
        json.getAsJsonArray("data").forEach { item ->
            val target = item.asJsonObject.getAsJsonObject("target")
            when (target.getAsString("type")) {
                "answer" -> handleAnswer(item.asJsonObject, newData)
                "article" -> handleArticle(item.asJsonObject, newData)
                "zvideo" -> handleZVideo(item.asJsonObject, newData)
                else -> newData.add(item) // 姑且放过吧
            }
        }
        json.add("data", newData)

        // Gson 不会调 writer.flush()。如果我们也不调，会导致拿到的 json 不完整，囧
        body.clear().outputStream().writer().use {
            gson.toJson(json, it)
            it.flush()
        }
    }

    private fun handleAnswer(item: JsonObject, out: JsonArray) {
        val question = item.getAsJsonObject("target").getAsJsonObject("question") ?: return
        val title = question.getAsString("title")
        val author = question.getAsJsonObject("author").getAsString("name")

        val isTitleBlack = config.http.blackList.isQuestionTitleBlack(title)
        val isAuthorBlack = config.http.blackList.isQuestionAuthorBlack(author)

        Log.i(TAG, "isBoringAnswer: '$title' -> '$isTitleBlack'")
        Log.i(TAG, "isBoringAnswer: '$author' -> '$isAuthorBlack'")

        if (isTitleBlack || isAuthorBlack) {
            return
        }
        out.add(item)

        // 替换 title
        question.addProperty("title", "[回答]${title}")
    }

    private fun handleArticle(item: JsonObject, out: JsonArray) {
        val target = item.getAsJsonObject("target")
        val title = target.getAsString("title")
        val author = target.getAsJsonObject("author").getAsString("name")

        val isTitleBlack = config.http.blackList.isQuestionTitleBlack(title)
        val isAuthorBlack = config.http.blackList.isQuestionAuthorBlack(author)

        Log.i(TAG, "isBoringArticle: '$title' -> '$isTitleBlack'")
        Log.i(TAG, "isBoringArticle: '$author' -> '$isAuthorBlack'")

        if (isTitleBlack || isAuthorBlack) {
            return
        }
        out.add(item)
        // 替换 title
        target.addProperty("title", "[专栏]${title}")
    }

    private fun handleZVideo(item: JsonObject, out: JsonArray) {
        // always filter it
    }
}

