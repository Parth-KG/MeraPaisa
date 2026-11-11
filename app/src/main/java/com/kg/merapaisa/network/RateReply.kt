package com.kg.merapaisa.network

/**
 * Reads Frankfurter's answer to `latest?base=<from>&symbols=<to>` and returns the rate from
 * [from] to [to], or null when the answer cannot be trusted.
 *
 * The reply looks like `{"amount":1.0,"base":"INR","date":"2026-11-11","rates":{"USD":0.010673}}`.
 * It is refused, and the caller says it couldn't get today's rate, when:
 *  - it is not JSON, or not an object (an HTML error page, an empty body, a 404's message);
 *  - its `base` is not [from]. The old request never checked this, so a service that ignored the
 *    asked-for currency and answered in euros would have had a euro rate recorded as the rupee
 *    one, about a hundred times too much from INR to USD, and a currency change writes that rate
 *    into a person's whole history;
 *  - there is no rate for [to], or it is not a number, or it is zero, negative or not finite.
 *
 * A pure function over the text, so it runs under JVM tests. `org.json` is an Android stub that
 * throws there, and `data/Json.kt` refuses decimals on purpose (a backup must never round an
 * amount), so this has its own small reader.
 */
internal fun parseRate(body: String, from: String, to: String): Double? {
    val root = JsonReader(body).readDocument() as? Map<*, *> ?: return null
    if (root["base"] != from) return null
    val rates = root["rates"] as? Map<*, *> ?: return null
    val rate = rates[to] as? Double ?: return null
    return rate.takeIf { it.isFinite() && it > 0.0 }
}

/** An ISO 4217 code as the app stores one: three capital letters, and nothing to escape in a URL. */
internal fun isCurrencyCode(code: String): Boolean = code.length == 3 && code.all { it in 'A'..'Z' }

/**
 * Just enough JSON for a rate reply: objects, arrays, strings, numbers, true, false and null.
 * Numbers come back as Double, objects as Map and arrays as List. Anything malformed, or nested
 * deeper than a rate reply ever is, makes the whole document null.
 */
private class JsonReader(private val text: String) {
    private var pos = 0

    fun readDocument(): Any? = try {
        val value = readValue(depth = 0)
        skipSpace()
        if (pos == text.length) value else null
    } catch (e: Malformed) {
        null
    }

    private class Malformed : Exception()

    private fun fail(): Nothing = throw Malformed()

    private fun skipSpace() {
        while (pos < text.length && text[pos] in " \t\r\n") pos++
    }

    private fun readValue(depth: Int): Any? {
        if (depth > MAX_DEPTH) fail()
        skipSpace()
        if (pos >= text.length) fail()
        return when (text[pos]) {
            '{' -> readObject(depth)
            '[' -> readArray(depth)
            '"' -> readString()
            't' -> readWord("true", true)
            'f' -> readWord("false", false)
            'n' -> readWord("null", null)
            else -> readNumber()
        }
    }

    private fun readWord(word: String, value: Any?): Any? {
        if (!text.startsWith(word, pos)) fail()
        pos += word.length
        return value
    }

    private fun readObject(depth: Int): Map<String, Any?> {
        pos++ // {
        val map = LinkedHashMap<String, Any?>()
        skipSpace()
        if (pos < text.length && text[pos] == '}') {
            pos++
            return map
        }
        while (true) {
            skipSpace()
            if (pos >= text.length || text[pos] != '"') fail()
            val key = readString()
            skipSpace()
            if (pos >= text.length || text[pos] != ':') fail()
            pos++
            map[key] = readValue(depth + 1)
            skipSpace()
            if (pos >= text.length) fail()
            when (text[pos++]) {
                ',' -> continue
                '}' -> return map
                else -> fail()
            }
        }
    }

    private fun readArray(depth: Int): List<Any?> {
        pos++ // [
        val list = ArrayList<Any?>()
        skipSpace()
        if (pos < text.length && text[pos] == ']') {
            pos++
            return list
        }
        while (true) {
            list.add(readValue(depth + 1))
            skipSpace()
            if (pos >= text.length) fail()
            when (text[pos++]) {
                ',' -> continue
                ']' -> return list
                else -> fail()
            }
        }
    }

    private fun readString(): String {
        pos++ // opening quote
        val out = StringBuilder()
        while (true) {
            if (pos >= text.length) fail()
            val c = text[pos++]
            when {
                c == '"' -> return out.toString()
                c == '\\' -> {
                    if (pos >= text.length) fail()
                    when (val e = text[pos++]) {
                        '"', '\\', '/' -> out.append(e)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) fail()
                            val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: fail()
                            out.append(code.toChar())
                            pos += 4
                        }
                        else -> fail()
                    }
                }
                c < ' ' -> fail()
                else -> out.append(c)
            }
        }
    }

    /** JSON's own number shape: an optional minus, digits without a leading zero, then the rest. */
    private fun readNumber(): Double {
        val start = pos
        if (pos < text.length && text[pos] == '-') pos++
        when {
            pos < text.length && text[pos] == '0' -> pos++
            pos < text.length && text[pos] in '1'..'9' -> skipDigits()
            else -> fail()
        }
        if (pos < text.length && text[pos] == '.') {
            pos++
            if (skipDigits() == 0) fail()
        }
        if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
            pos++
            if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
            if (skipDigits() == 0) fail()
        }
        return text.substring(start, pos).toDouble()
    }

    private fun skipDigits(): Int {
        val start = pos
        while (pos < text.length && text[pos] in '0'..'9') pos++
        return pos - start
    }

    private companion object {
        const val MAX_DEPTH = 16
    }
}
