package com.toybox.interceptor

import java.net.URL

private const val EXACT = 0
private const val TEXT = 1

const val NO_MATCH = -1

class UriMatcher private constructor(
    private var mCode: Int,
    private val mWhich: Int,
    private val mText: String?,
) {
    private val mChildren = ArrayList<UriMatcher>()

    constructor(code: Int): this(
        mCode = code,
        mWhich = NO_MATCH,
        mText = null,
    )

    fun addPath(path: String, code: Int) {
        require(code >= 0) { "code $code is invalid: it must be positive" }

        val tokens = path.split('/').filter { it.isNotEmpty() }

        var node = this
        for (token in tokens) {
            val child = node.mChildren.firstOrNull { it.mText == token }
                ?: createChild(token).also { node.mChildren.add(it) }
            node = child
        }
        node.mCode = code
    }

    private fun createChild(token: String): UriMatcher {
        return when (token) {
            "*" -> UriMatcher(NO_MATCH, TEXT, "*")
            else -> UriMatcher(NO_MATCH, EXACT, token)
        }
    }

    fun matchPath(path: String): Int {
        return match(path.split('/').filter { it.isNotEmpty() })
    }

    private fun match(pathSegments: List<String>): Int {
        var node: UriMatcher = this
        for (path in pathSegments) {
            val found = node.mChildren.firstOrNull {
                it.matchSingle(path)
            } ?: return NO_MATCH

            node = found
        }
        return node.mCode
    }

    private fun matchSingle(path: String): Boolean {
        return when (mWhich) {
            EXACT -> mText == path
            TEXT -> true
            else -> TODO()
        }
    }
}