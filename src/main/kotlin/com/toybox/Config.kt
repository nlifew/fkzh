package com.toybox

import com.google.gson.annotations.SerializedName
import com.toybox.util.println

lateinit var config: Config


data class Config(
    @SerializedName("thread_num")
    val threadNum: Int,
    @SerializedName("http")
    val http: HttpConfig,
    @SerializedName("blacklist")
    val blackList: BlackList
)

data class HttpConfig(
    @SerializedName("local_port")
    val localPort: Int,
    @SerializedName("local_addr")
    val localAddress: String,
    @SerializedName("max_http_content_size")
    val maxHttpContentSize: Int
)

data class BlackList(
    @SerializedName("title")
    val titleList: List<String>,
    @SerializedName("author")
    val authorList: List<String>,
) {

    fun isTitleBlack(title: String): Boolean {
        return titleList.any { title.contains(it) }
    }

    fun isAuthorBlack(author: String): Boolean {
        return authorList.any { author.contains(it) }
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

