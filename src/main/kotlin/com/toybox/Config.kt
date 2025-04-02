package com.toybox

import com.google.gson.annotations.SerializedName
import com.toybox.util.println

object ConfigHolder {
    @JvmStatic
    lateinit var instance: Config
}

inline var config: Config
    get() = ConfigHolder.instance
    set(value) {
        ConfigHolder.instance = value
    }

data class Config(
    @SerializedName("thread_num")
    val threadNum: Int,
    @SerializedName("http")
    val http: HttpConfig,
    @SerializedName("dns")
    val dns: DnsConfig,
)

data class HttpConfig(
    @SerializedName("local_port")
    val localPort: Int,
    @SerializedName("local_addr")
    val localAddress: String,
    @SerializedName("max_http_content_size")
    val maxHttpContentSize: Int,
    @SerializedName("blacklist")
    val blackList: BlackList,
    @SerializedName("key_path")
    val keyPath: String?,
    @SerializedName("cert_path")
    val certPath: String?,
)

data class BlackList(
    @SerializedName("question_title")
    val questionTitleList: List<String>,
    @SerializedName("question_author")
    val questionAuthorList: List<String>,
) {

    fun isQuestionTitleBlack(title: String): Boolean {
        return questionTitleList.any { title.contains(it) }
    }

    fun isQuestionAuthorBlack(author: String): Boolean {
        return questionAuthorList.any { author.contains(it) }
    }
}

data class DnsConfig(
    @SerializedName("local_port")
    val localPort: Int,
    @SerializedName("local_addr")
    val localAddress: String,
    @SerializedName("upstream")
    val upStreams: List<String>,
    @SerializedName("host")
    val host: String,
)

