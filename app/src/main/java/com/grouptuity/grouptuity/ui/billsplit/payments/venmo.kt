package com.grouptuity.grouptuity.ui.billsplit.payments

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.grouptuity.grouptuity.BuildConfig
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.ui.billsplit.qrcodescanner.QRCodeParser
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder


class VenmoAddressParser(context: Context): QRCodeParser() {
    companion object {
        const val DOMAIN = "https://venmo.com"
        const val PREFIX = "/u/"
    }

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val requestQueue = Volley.newRequestQueue(context, object: HurlStack() {
        override fun createConnection(url: URL?): HttpURLConnection {
            return super.createConnection(url).also { it.instanceFollowRedirects = false }
        }
    })

    override fun parse(url: String?, callback: (Status, String?) -> Unit): Pair<Status, String?> {
        return when {
            (url == null || !url.startsWith(DOMAIN)) -> {
                Pair(Status.INVALID_URL, null)
            }
            url.startsWith(DOMAIN + PREFIX) -> {
                // Venmo QR code with address in the string rather than a number
                Pair(Status.VALID_ADDRESS, url.substringAfter(PREFIX).substringBefore("?"))
            }
            else -> {
                if (connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
                    Pair(Status.NO_INTERNET, null)
                } else {
                    val request = StringRequest(
                        Request.Method.GET,
                        url,
                        {
                            // Current venmo website redirects so this should not run. Implementation
                            // could be required if the website changes.
                            Pair(Status.BAD_RESPONSE, null)
                        },
                        { error ->
                            if (error.networkResponse?.statusCode != 302) {
                                Pair(Status.BAD_RESPONSE, null)
                            } else {
                                val locationRedirectUrl: String? = error.networkResponse?.headers?.get("Location")
                                if (locationRedirectUrl?.startsWith("/u/") == true) {
                                    callback(Status.VALID_ADDRESS,
                                        locationRedirectUrl.substringAfter(PREFIX).substringBefore("?"))
                                } else {
                                    // Redirecting to an unexpected web address
                                    Pair(Status.BAD_RESPONSE, null)
                                }
                            }
                        })

                    // Delay request until UI showing load in progress has had time to render
                    Handler(Looper.getMainLooper()).postDelayed({
                        requestQueue.add(request)
                    }, 100)

                    Pair(Status.REQUESTED, null)
                }
            }
        }
    }
}


private fun createIntent(uriBase: String, recipient: String, amount: String, note: String, appName: String): Intent {
    var venmoURI = uriBase

    if (recipient.isNotBlank()) {
        try { venmoURI += "&recipients=" + URLEncoder.encode(recipient, "UTF-8") }
        catch (e: UnsupportedEncodingException) {
            Log.e("venmo", "cannot encode recipient $recipient")
        }
    }

    if (amount.isNotBlank()) {
        try { venmoURI += "&amount=" + URLEncoder.encode(amount, "UTF-8") }
        catch (e: UnsupportedEncodingException) {
            Log.e("venmo", "cannot encode amount $amount")
        }
    }

    try {
        venmoURI += "&note=" + URLEncoder.encode(note, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
            Log.e("venmo", "cannot encode note")
    }

    try {
        venmoURI += "&app_id=" + URLEncoder.encode(BuildConfig.VENMO_APP_ID.toString(), "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        Log.e("venmo", "cannot encode app ID")
    }

    try {
        venmoURI += "&app_name=" + URLEncoder.encode(appName, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        Log.e("venmo", "cannot encode app name Grouptuity")
    }

    try {
        venmoURI += "&app_local_id=" + URLEncoder.encode("abcd", "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        Log.e("venmo", "cannot encode app local id")
    }

    venmoURI += "&using_new_sdk=true"
    venmoURI = venmoURI.replace("\\+".toRegex(), "%20") // use %20 encoding instead of +

    return Intent(Intent.ACTION_VIEW, Uri.parse(venmoURI))
}

fun getVenmoAppIntent(context: Context, recipient: String, amount: String, pay: Boolean) = createIntent(
    "venmosdk://paycharge?txn=" + if(pay) "pay" else "charge",
    recipient,
    amount,
    context.getString(R.string.venmo_note),
    context.getString(R.string.app_name)
)

fun getVenmoWebViewIntent(context: Context, counterparty: String, amount: String, pay: Boolean) =
    Intent(context, VenmoWebViewActivity::class.java).also {
        var venmoURI = "https://venmo.com/u/${counterparty}?txn=" + if(pay) "pay" else "charge"

        if (amount.isNotBlank()) {
            try { venmoURI += "&amount=" + URLEncoder.encode(amount, "UTF-8") }
            catch (e: UnsupportedEncodingException) {
                Log.e("venmo", "cannot encode amount $amount")
            }
        }

        try {
            venmoURI += "&note=" + URLEncoder.encode(context.getString(R.string.venmo_note), "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            Log.e("venmo", "cannot encode note")
        }

        try {
            venmoURI += "&app_id=" + URLEncoder.encode(BuildConfig.VENMO_APP_ID.toString(), "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            Log.e("venmo", "cannot encode app ID")
        }

        try {
            venmoURI += "&app_name=" + URLEncoder.encode(context.getString(R.string.app_name), "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            Log.e("venmo", "cannot encode app name Grouptuity")
        }

        try {
            venmoURI += "&app_local_id=" + URLEncoder.encode("abcd", "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            Log.e("venmo", "cannot encode app local id")
        }

        try {
            venmoURI += "&client=" + URLEncoder.encode("android", "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            Log.e("venmo", "cannot encode client=android")
        }

        it.putExtra("url", venmoURI.replace("\\+".toRegex(), "%20"))
    }

private fun createOldWebViewUri(uriBase: String, recipient: String, amount: String, note: String, appName: String): String {
    var venmoURI = uriBase

    if (recipient.isNotBlank()) {
        try { venmoURI += "&recipients=" + URLEncoder.encode(recipient, "UTF-8") }
        catch (e: UnsupportedEncodingException) {
            Log.e("venmo", "cannot encode recipient $recipient")
        }
    }

    if (amount.isNotBlank()) {
        try { venmoURI += "&amount=" + URLEncoder.encode(amount, "UTF-8") }
        catch (e: UnsupportedEncodingException) {
            Log.e("venmo", "cannot encode amount $amount")
        }
    }

    try {
        venmoURI += "&note=" + URLEncoder.encode(note, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        Log.e("venmo", "cannot encode note")
    }

    try {
        venmoURI += "&app_id=" + URLEncoder.encode(BuildConfig.VENMO_APP_ID.toString(), "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        Log.e("venmo", "cannot encode app ID")
    }

    try {
        venmoURI += "&app_name=" + URLEncoder.encode(appName, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        Log.e("venmo", "cannot encode app name Grouptuity")
    }

    try {
        venmoURI += "&app_local_id=" + URLEncoder.encode("abcd", "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        Log.e("venmo", "cannot encode app local id")
    }

    try {
        venmoURI += "&client=" + URLEncoder.encode("android", "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        Log.e("venmo", "cannot encode client=android")
    }

//    return venmoURI.replace("\\+".toRegex(), "%20") // use %20 encoding instead of +

    return "https://venmo.com"
}

fun getVenmoWebViewChargeIntent(context: Context, counterparty: String, amount: String, pay: Boolean) =
    Intent(context, VenmoWebViewActivity::class.java).also {
        it.putExtra("url", createOldWebViewUri(
            "https://venmo.com/touch/signup_to_pay?txn=charge",
            counterparty,
            amount,
            context.getString(R.string.venmo_note),
            context.getString(R.string.app_name)
        )
        )
    }
