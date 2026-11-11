package com.kg.merapaisa.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/**
 * The size of an amount in major units, which today's rate is applied to.
 *
 * Always a magnitude. It was once sent to the service, which answered a negative `amount` with
 * HTTP 422 "invalid amount" while half of this app's balances are negative (the people you owe).
 * Only a rate is asked for now, but converting the magnitude and restoring the sign afterwards
 * keeps what you owe from flipping to owing.
 */
internal fun requestAmountMajor(amountMinor: Long): Double = abs(amountMinor) / 100.0

/**
 * Puts back the sign [requestAmountMajor] took off, so converting what you owe still reads as
 * owed rather than flipping to owing.
 */
internal fun convertedMinor(amountMinor: Long, convertedMagnitudeMajor: Double): Long {
    val magnitudeMinor = Math.round(convertedMagnitudeMajor * 100)
    return if (amountMinor < 0) -magnitudeMinor else magnitudeMinor
}

/**
 * Live rates from Frankfurter, kept away from the ViewModel and the database so each has one job.
 *
 * Asked at `api.frankfurter.dev/v1/latest` with `base` and `symbols`, the address and parameters
 * Frankfurter documents. The app used to ask `api.frankfurter.app/latest` with `amount`, `from`
 * and `to`, which answered through a redirect with parameters no longer documented, and never
 * checked which currency the answer was in. The reply is now checked (see [parseRate]).
 */
class ExchangeRateApi {

    /**
     * The plain rate from one currency to another, for converting many amounts at once.
     *
     * Changing someone's currency rewrites every entry in their history, and asking the service
     * per entry would be dozens of requests for one answer that does not change between them. So
     * the rate is fetched once and applied locally by `convertAll`.
     *
     * Asked for as a rate on one unit rather than derived from a converted balance. A balance of
     * zero would give no ratio at all, and a person at zero can still have a history worth
     * converting.
     */
    suspend fun rate(from: String, to: String): Double? {
        if (from == to) return 1.0
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { fetch(from, to) }
        }
    }

    private fun fetch(from: String, to: String): Double? {
        if (!isCurrencyCode(from) || !isCurrencyCode(to)) return null
        val url = URL("$BASE_URL?base=$from&symbols=$to")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            // A rate reply is under a hundred characters. Anything far longer is not one.
            val body = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(MAX_REPLY_CHARS + 1)
                var read = 0
                while (read < buffer.size) {
                    val n = reader.read(buffer, read, buffer.size - read)
                    if (n < 0) break
                    read += n
                }
                if (read > MAX_REPLY_CHARS) return null
                String(buffer, 0, read)
            }
            parseRate(body, from, to)
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://api.frankfurter.dev/v1/latest"
        const val MAX_REPLY_CHARS = 16_384
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
        const val REQUEST_TIMEOUT_MS = 12_000L
    }
}
