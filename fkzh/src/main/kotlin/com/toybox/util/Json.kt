package com.toybox.util

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames


val gson = Gson()

fun List<JsonElement>.toJsonArray() = JsonArray(size).also { array ->
    forEach { array.add(it) }
}

fun JsonObject.getAsString(name: String): String {
    return get(name).asString
}

fun JsonArray.removeIf(block: (JsonElement) -> Boolean) {
    val iterator = this.iterator()
    while (iterator.hasNext()) {
        if (block(iterator.next())) {
            iterator.remove()
        }
    }
}

inline fun <reified T> ByteBuf.readAsJson(): T {
    return inputStream().reader(Charsets.UTF_8).use {
        gson.fromJson(it, T::class.java)
    }
}

fun ByteBuf.writeAsJson(value: Any) {
    outputStream().writer().use {
        gson.toJson(value, it)
        it.flush() // [1]
    }
    // [1]. Gson 不会调 writer.flush()。如果我们也不调，会导致拿到的 json 不完整，囧
}

inline fun <reified T: JsonElement> ByteBuf.useAsJson(block: (T) -> Unit) {
    val json = readAsJson<T>()
    block(json)
    clear().writeAsJson(json)
}

inline fun <reified T: JsonElement> FullHttpResponse.useAsJson(
    block: (T) -> Unit
): FullHttpResponse {
    val body = content()
    body.useAsJson(block)
    headers().set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes())
    return this
}
