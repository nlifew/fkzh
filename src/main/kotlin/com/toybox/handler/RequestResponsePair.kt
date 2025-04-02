package com.toybox.handler

import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.util.ReferenceCounted

class RequestResponsePair(
    @JvmField val req: FullHttpRequest,
    @JvmField val resp: FullHttpResponse,
): ReferenceCounted {
    private var refCount = 1

    override fun refCnt(): Int = refCount

    override fun retain(): RequestResponsePair {
        return retain(1)
    }

    override fun retain(increment: Int): RequestResponsePair {
        check(refCount > 0 && req.refCnt() > 0 && resp.refCnt() > 0)
        refCount += increment
        req.retain(increment)
        resp.retain(increment)
        return this
    }

    override fun touch() = this

    override fun touch(hint: Any?) = this

    override fun release(): Boolean {
        return release(1)
    }

    override fun release(decrement: Int): Boolean {
        check(refCount > 0 && req.refCnt() > 0 && resp.refCnt() > 0)
        refCount -= decrement
        req.release(decrement)
        resp.release(decrement)
        return (refCount == 0)
    }
}