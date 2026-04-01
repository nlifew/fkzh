package com.toybox.util


fun findAndReplace(
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
        beginIndex = document.lastIndexOf(beginToken, preferredOffset + beginToken.length)
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
    Log.d("Html", "findAndReplace: " +
            "indexOf() cost '${(t1-t0)/1000_000}' ms, " +
            "transfer() cost '${(t2-t1)/1000_0000}' ms, " +
            "replace() cost '${(t3-t2)/1000_000}' ms."
    )
}