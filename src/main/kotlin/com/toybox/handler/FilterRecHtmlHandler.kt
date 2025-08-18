package com.toybox.handler

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.toybox.config
import com.toybox.util.Log
import com.toybox.util.get
import com.toybox.util.getAsString
import com.toybox.util.gson
import com.toybox.util.readUtf8
import com.toybox.util.reader
import com.toybox.util.set
import com.toybox.util.startsWithAscii
import com.toybox.util.toMap
import com.toybox.util.use
import com.toybox.util.writeUtf8
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import kotlin.text.startsWith

private const val TAG = "FilterRecHtmlHandler"

@Path("/")
@ContentType("text/html")
class FilterRecHtmlHandler: HttpInterceptor() {

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse) {
        Log.d(TAG, "channelRead0: accepted !")
        val body = msg.content()
        handleBody(body, false)
        msg[HttpHeaderNames.CONTENT_LENGTH] = body.readableBytes()
        ctx.writeAndFlush(msg.retain())
    }

    fun handleBody(body: ByteBuf, test: Boolean) {
        // 用 indexOf 和 substring 代替 document
        // val document = body.losslessInput().use { Jsoup.parse(it, "UTF-8", "zhihu.com") }
        val document = body.use { it.readUtf8() }

        // js-initialData
        findAndReplace(
            document = document,
            beginToken = "<script id=\"js-initialData\" type=\"text/json\">",
            endToken = "</script>",
            preferredOffset = 100 * 1024,
            block = { filterJson(it, test) }
        )

        // div
        findAndReplace(
            document = document,
            beginToken = "<div role=\"listitem\"></div>",
            endToken = "<div role=\"listitem\"></div>",
            preferredOffset = 5 * 1024,
            block = { filterDiv(it, test) }
        )
        // 还原回去
        body.clear().writeUtf8(document)
    }

    private fun findAndReplace(
        document: StringBuilder,
        beginToken: String,
        endToken: String,
        preferredOffset: Int,
        block: (StringBuilder) -> Unit,
    ) {
        val t0 = System.nanoTime()

        var beginIndex = document.indexOf(beginToken, preferredOffset)
        var endIndex = -1
        val text = StringBuilder()

        if (beginIndex == -1) {
            beginIndex = document.indexOf(beginToken)
        }
        if (beginIndex > 0) {
            beginIndex += beginToken.length
            endIndex = document.indexOf(endToken, beginIndex)
            text.append(document, beginIndex, endIndex)
        }
        val t1 = System.nanoTime()

        if (text.isNotEmpty()) {
//            File("input.json").writeText(jsonText)
            block.invoke(text)
        }
        val t2 = System.nanoTime()

        // 结束，还原 dom 树并写回
        if (text.isNotEmpty()) {
//        dataNodes.first().setWholeData(json.toString())
            document.replace(beginIndex, endIndex, text.toString())
        }
        val t3 = System.nanoTime()
        Log.d(TAG, "findAndReplace: indexOf() cost '${(t1-t0)/1000_000}' ms, " +
                "transfer() cost '${(t2-t1)/1000_0000}' ms, " +
                "replace() cost '${(t3-t2)/1000_000}' ms.")
    }

    private fun find(
        document: StringBuilder, startIndex: Int,
        startToken: String, endToken: String
    ): String? {
        var startIndex = document.indexOf(startToken, startIndex)
            .takeIf { it > -1 } ?: return null
        startIndex += startToken.length

        val endIndex = document.indexOf(endToken, startIndex)
            .takeIf { it > -1 } ?: return null

        return document.substring(startIndex, endIndex)
    }

    private fun filterJson(jsonText: StringBuilder, test: Boolean) {
        val json = gson.fromJson(jsonText.reader(), JsonObject::class.java)

        // 过滤回答和专栏
        val boringAnswers = removeBoringAnswers(json)
        val boringArticles = removeBoringArticles(json)
        val boringFeeds = removeBoringFeeds(json, boringAnswers + boringArticles)
        removeBoringRecommends(json, boringFeeds)

//        // recommend 里的 serverPayloadOrigin 参与第一屏的构建，删除掉
//        val serverPayloadOrigin = json
//            .getAsJsonObject("initialState")
//            ?.getAsJsonObject("topstory")
//            ?.getAsJsonObject("recommend")
//            ?.getAsJsonObject("serverPayloadOrigin")
//            ?.getAsJsonArray("data")
//        serverPayloadOrigin?.filter {
//            val id = it.asJsonObject.getAsString("id")
//            invalidItemIds.contains(id)
//        }?.forEach {
//            serverPayloadOrigin.remove(it)
//        }
        gson.toJson(json, jsonText.clear())
    }

    private fun removeBoringAnswers(json: JsonObject): Map<String, JsonElement> {
        val answers = json.getAsJsonObject("initialState")
            ?.getAsJsonObject("entities")
            ?.getAsJsonObject("answers")
            ?.asJsonObject
        // 遍历所有回答，找到要过滤的回答
        val boring = answers?.entrySet()
            ?.filterIndexed { i, it ->
                Log.d(TAG, "filterJson: check answer: '${it.key}'")
                isAnswerInBlackList(it.value.asJsonObject)
            }

        // 在 entity 中删除掉
        boring?.forEach { answers.remove(it.key) }
        return boring?.toMap() ?: emptyMap()
    }

    private fun isAnswerInBlackList(answer: JsonObject): Boolean {
        // 检查问题是否在黑名单
        val question = answer.getAsJsonObject("question") ?: return false
        val questionTitle = question.getAsString("title")
        val questionAuthor = question.getAsJsonObject("author").getAsString("name")

        val isTitleBlack = config.blackList.isTitleBlack(questionTitle)
        val isAuthorBlack = config.blackList.isAuthorBlack(questionAuthor)
        
        Log.i(TAG, "isAnswerInBlackList: '$questionTitle' -> '$isTitleBlack'")
        Log.i(TAG, "isAnswerInBlackList: '$questionAuthor' -> '$isAuthorBlack'")
        question.addProperty("title", "[回答]${questionTitle}")
        
        return isTitleBlack || isAuthorBlack
    }

    private fun removeBoringArticles(json: JsonObject): Map<String, JsonElement> {
        val articles = json.getAsJsonObject("initialState")
            ?.getAsJsonObject("entities")
            ?.getAsJsonObject("articles")
            ?.asJsonObject

        val boring = articles?.entrySet()?.filter {
            Log.d(TAG, "filterJson: check article: '${it.key}'")
            isArticleInBlackList(it.value.asJsonObject)
        }
        boring?.forEach { articles.remove(it.key) }
        return boring?.toMap() ?: emptyMap()
    }

    private fun isArticleInBlackList(article: JsonObject): Boolean {
        val title = article.getAsString("title")
        val author = article.getAsJsonObject("author")
            .getAsString("name")

        val isTitleInBlackList = config.blackList.isTitleBlack(title)
        val isAuthorInBlackList = config.blackList.isAuthorBlack(author)

        Log.i(TAG, "isArticleInBlackList: '$title' -> '$isTitleInBlackList'")
        Log.i(TAG, "isArticleInBlackList: '$author' -> '$isAuthorInBlackList'")
        article.addProperty("title", "[专栏]${title}")

        return isTitleInBlackList || isAuthorInBlackList
    }

    private fun removeBoringFeeds(
        json: JsonObject,
        entities: Map<String, JsonElement>,
    ): Map<String, JsonElement> {
        val feeds = json
            .getAsJsonObject("initialState")
            ?.getAsJsonObject("entities")
            ?.getAsJsonObject("feeds")
            ?.asJsonObject

        val boring = feeds?.entrySet()?.filter {
            val targetId = it.value.asJsonObject
                .getAsJsonObject("target")
                .getAsString("id")
            entities.containsKey(targetId)
        }
        boring?.forEach { feeds.remove(it.key) }
        return boring?.toMap() ?: emptyMap()
    }

    private fun removeBoringRecommends(
        json: JsonObject,
        entities: Map<String, JsonElement>,
    ): List<JsonElement> {
        val items = json
            .getAsJsonObject("initialState")
            ?.getAsJsonObject("topstory")
            ?.getAsJsonObject("recommend")
            ?.getAsJsonArray("items")

        val boring = items?.filter {
            val id = it.asJsonObject.getAsString("id")
            entities.containsKey(id)
        }
        boring?.forEach { items.remove(it) }
        return boring ?: emptyList()
    }

    private fun filterDiv(divText: StringBuilder, test: Boolean) {
        // 直接去掉吧…视觉效果并不好
//        val token = "div class=\"Card TopstoryItem TopstoryItem-isRecommend\""
//        var beginIndex = divText.indexOf(token).takeIf { it >= 0 } ?: return
//        while (beginIndex + token.length < divText.length) {
//            val endIndex = divText.indexOf(token, beginIndex + token.length)
//                .takeIf { it > -1 } ?: divText.length
//
//            if (isBoringDiv(divText, beginIndex, endIndex)) {
//                divText.deleteRange(beginIndex, endIndex)
//            } else {
//                beginIndex = endIndex
//            }
//        }
        divText.clear().append(' ')
    }

    private fun isBoringDiv(divText: StringBuilder, startIndex: Int, endIndex: Int): Boolean {
        val title = find(divText, startIndex, "<meta itemProp=\"name\" content=\"", "\"")
        val result = config.blackList.isTitleBlack(title ?: "")
        Log.i(TAG, "filterDiv: check div: '$title' -> '$result'")
        return result
    }
}