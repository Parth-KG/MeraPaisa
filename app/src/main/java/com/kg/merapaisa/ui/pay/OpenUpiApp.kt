package com.kg.merapaisa.ui.pay

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands a `upi://pay` link to the phone's UPI apps: Android's picker, or the one set as default
 * (Parth, 14 Nov 2026). False when no app on the phone opens such links, which the manifest's
 * `<queries>` entry lets the app see. The UPI app then shows the payee and the amount and waits for
 * the payer's PIN; nothing here pays.
 */
fun openUpiApp(context: Context, link: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
    if (context.packageManager.queryIntentActivities(intent, 0).isEmpty()) return false
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
