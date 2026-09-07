package com.kg.merapaisa.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/**
 * The amount the service is asked about, in major units.
 *
 * Always a magnitude. The service answers a negative `amount` with HTTP 422 "invalid amount",
 * and half of this app's balances are negative — those are the people you owe.
 */
internal fun requestAmountMajor(amountMinor: Long): Double = abs(amountMinor) / 100.0

/**
 * Puts back the sign the request could not carry, so converting what you owe still reads as
 * owed rather than flipping to owing.
 */
internal fun convertedMinor(amountMinor: Long, convertedMagnitudeMajor: Double): Long {
    val magnitudeMinor = Math.round(convertedMagnitudeMajor * 100)
    return if (amountMinor < 0) -magnitudeMinor else magnitudeMinor
}

/**
 * Live rates from frankfurter.app. The only network in the app, kept away from the ViewModel
 * and the database so each has one job.
 */
class ExchangeRateApi {

    /**
     * Converts minor units between two ISO 4217 codes. Returns null when no rate could be
     * fetched — callers must surface that rather than carrying on with the original amount.
     *
     * Only the magnitude is sent. The service answers a negative `amount` with HTTP 422
     * ("invalid amount"), and a negative balance is simply one you owe rather than one you
     * are owed — so passing the raw figure made conversion fail for half the ledger, and
     * fail with a message blaming the user's connection. The sign is re-applied here.
     */
    suspend fun convert(amountMinor: Long, from: String, to: String): Long? {
        if (from == to) return amountMinor
        val converted = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { fetch(requestAmountMajor(amountMinor), from, to) }
        } ?: return null
        return convertedMinor(amountMinor, converted)
    }

    private fun fetch(amountMajor: Double, from: String, to: String): Double? {
        val url = URL("$BASE_URL?amount=$amountMajor&from=$from&to=$to")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).getJSONObject("rates").getDouble(to)
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://api.frankfurter.app/latest"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
        const val REQUEST_TIMEOUT_MS = 12_000L
    }
}
