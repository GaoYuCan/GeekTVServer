package top.sinkdev

import java.util.Base64

fun String.encodeURLBase64(): String {
    return Base64.getUrlEncoder().encodeToString(this.toByteArray())
}

fun String.decodeURLBase64(): String {
    return Base64.getUrlDecoder().decode(this).decodeToString()
}
