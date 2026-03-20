package com.toybox.handler

import com.google.gson.JsonObject
import com.toybox.interceptor.HttpInterceptor
import com.toybox.interceptor.Path
import com.toybox.util.Log
import com.toybox.util.getAsString
import com.toybox.util.removeIf
import com.toybox.util.useAsJson
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpResponse

private const val TAG = "RemoveMomentsAdHandler"

@Path("/api/v3/moments")
class RemoveMomentsAdHandler: HttpInterceptor() {

    override fun onResponseHit(resp: FullHttpResponse): HttpResponse {
        Log.d(TAG, "channelRead0: accepted !")
        return resp.useAsJson<JsonObject> { handleBody(it, false) }
    }

    fun handleBody(json: JsonObject, test: Boolean) {
        json.getAsJsonArray("data").removeIf {
            it.asJsonObject.getAsString("type") == "feed_advert"
        }
    }
}