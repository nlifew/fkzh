package com.toybox.util

import io.netty.util.concurrent.FastThreadLocal

//
//// 定义一个静态 Key，用于在 Channel 的 AttributeMap 中存储 Scope
//private val CHANNEL_SCOPE_KEY = AttributeKey.valueOf<CoroutineScope>("CHANNEL_SCOPE")
//
//val Channel.scope: CoroutineScope
//    get() {
//        // 尝试从 Attribute 中获取现有的 Scope
//        val attr = this.attr(CHANNEL_SCOPE_KEY)
//        attr.get()?.let { return it }
//
//        // 使用 SupervisorJob，这样一个协程崩溃不会导致 Scope 销毁
//        // 使用 eventLoop().asCoroutineDispatcher() 绑定 IO 线程
//        val dispatcher = this.eventLoop().asCoroutineDispatcher()
//        val newScope = CoroutineScope(SupervisorJob() + dispatcher)
//
//        // 原子性地设置. 如果返回 null，说明我们设置成功了；如果返回非 null，说明其他线程抢先设置了
//        attr.setIfAbsent(newScope)?.let {
//            newScope.cancel()
//            return it
//        }
//        this.closeFuture().addListener { newScope.cancel("channel closed") }
//        return newScope
//    }

inline fun <T> threadLocal(crossinline block: () -> T): FastThreadLocal<T> {
    return object: FastThreadLocal<T>() {
        override fun initialValue(): T? {
            return block()
        }
    }
}
