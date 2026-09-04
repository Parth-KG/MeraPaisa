package com.kg.merapaisa.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live rates from frankfurter.app. The only network in the app, kept away from the ViewModel
 * and the database so each has one job.
 */
class ExchangeRateApi {

    /**
     * Converts minor units between two ISO 4217 codes. Returns null when no rate could be
     * fetched — callers must surface that rather than carrying on with the original amount.
     */
    suspend fun convert(amountMinor: Long, from: String, to: String): Long? {
        if (from == to) return amountMinor
        val major = amountMinor / 100.0
        val converted = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { fetch(major, from, to) }
        }
        return converted?.let { Math.round(it * 100) }
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
