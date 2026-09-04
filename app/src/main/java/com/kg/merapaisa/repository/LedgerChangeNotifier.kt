package com.kg.merapaisa.repository

/**
 * Told whenever the ledger changes, so surfaces outside the app can refresh. Keeps the
 * repository free of framework types; the Android implementation lives in the widget package.
 */
fun interface LedgerChangeNotifier {
    suspend fun onLedgerChanged()

    companion object {
        /** For tests and any caller with nothing to refresh. */
        val None = LedgerChangeNotifier { }
    }
}
