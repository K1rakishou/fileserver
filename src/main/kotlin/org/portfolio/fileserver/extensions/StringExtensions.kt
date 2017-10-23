package org.portfolio.fileserver.extensions

fun String.extractExtension(): String {
    val sb = StringBuilder()
    val strLen = this.length - 1

    if (!this.contains(".")) {
        return ""
    }

    for (index in (strLen downTo 0)) {
        if (this[index] == '.') {
            break
        }

        sb.insert(0, this[index])
    }

    return sb.toString()
}