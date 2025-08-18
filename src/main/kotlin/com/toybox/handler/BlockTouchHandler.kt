package com.toybox.handler

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpResponse

@Path("/lastread/touch")
class BlockTouchHandler: HttpInterceptor() {
    override fun channelRead0(
        ctx: ChannelHandlerContext,
        msg: FullHttpResponse
    ) {
    }
}