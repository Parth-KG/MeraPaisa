package com.kg.merapaisa.data

import java.io.ByteArrayOutputStream

/*
 * Base64URL, unpadded, for update links and for the photos in a backup.
 *
 * Hand-rolled because java.util.Base64 is API 26 against a minSdk of 24, and android.util.Base64
 * cannot run in a JVM unit test. It lived inside SharePayload.kt until v3.4.0, when backups
 * began to carry profile photos and needed the same codec.
 */


private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

internal fun base64UrlEncode(bytes: ByteArray): String = buildString((bytes.size + 2) / 3 * 4) {
    var i = 0
    while (i + 2 < bytes.size) {
        val n = (bytes[i].toInt() and 0xFF shl 16) or
            (bytes[i + 1].toInt() and 0xFF shl 8) or
            (bytes[i + 2].toInt() and 0xFF)
        append(ALPHABET[n ushr 18 and 0x3F])
        append(ALPHABET[n ushr 12 and 0x3F])
        append(ALPHABET[n ushr 6 and 0x3F])
        append(ALPHABET[n and 0x3F])
        i += 3
    }
    when (bytes.size - i) {
        1 -> {
            val n = bytes[i].toInt() and 0xFF shl 16
            append(ALPHABET[n ushr 18 and 0x3F])
            append(ALPHABET[n ushr 12 and 0x3F])
        }
        2 -> {
            val n = (bytes[i].toInt() and 0xFF shl 16) or (bytes[i + 1].toInt() and 0xFF shl 8)
            append(ALPHABET[n ushr 18 and 0x3F])
            append(ALPHABET[n ushr 12 and 0x3F])
            append(ALPHABET[n ushr 6 and 0x3F])
        }
    }
}

/**
 * Null on any character outside the alphabet, or on a length that cannot be whole bytes.
 *
 * Standard Base64 `+` and `/` are accepted too: a chat app, or a user retyping a link, may hand
 * back the non-URL variant, and refusing it would look like a broken link rather than a
 * transport quirk. Trailing `=` padding is tolerated and ignored for the same reason.
 */
internal fun base64UrlDecode(s: String): ByteArray? {
    val clean = s.trimEnd('=')
    // Four Base64 characters carry three bytes; a remainder of one cannot be any whole byte.
    if (clean.length % 4 == 1) return null

    val out = ByteArrayOutputStream(clean.length / 4 * 3 + 3)
    var buffer = 0
    var bits = 0
    for (c in clean) {
        val v = when (c) {
            '+' -> 62
            '/' -> 63
            else -> ALPHABET.indexOf(c)
        }
        if (v < 0) return null
        buffer = (buffer shl 6) or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out.write(buffer ushr bits and 0xFF)
        }
    }
    // Leftover bits must be zero padding rather than discarded data.
    if (bits > 0 && (buffer and ((1 shl bits) - 1)) != 0) return null
    return out.toByteArray()
}
