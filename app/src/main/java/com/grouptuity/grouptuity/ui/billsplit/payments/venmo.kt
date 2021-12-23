package com.grouptuity.grouptuity.ui.billsplit.payments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import com.android.volley.Request
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.JsonParser
import com.grouptuity.grouptuity.BuildConfig
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Payment
import com.grouptuity.grouptuity.databinding.VenmoWebviewBinding
import com.grouptuity.grouptuity.ui.billsplit.qrcodescanner.QRCodeParser
import java.io.UnsupportedEncodingException
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


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


class VenmoWebViewActivity: AppCompatActivity() {
    private lateinit var binding: VenmoWebviewBinding
    private var retainedInstance: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = VenmoWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.extras!!.getString("url")!!
        Log.e("url", url)

        val oldWebView = lastNonConfigurationInstance as WebView?
        if (oldWebView == null) {
            // Load the WebView
            Log.e("venmo","loading new WebView")
            binding.webView.also { webView ->

                webView.settings.also {
                    it.javaScriptEnabled = true
                    it.cacheMode = WebSettings.LOAD_NO_CACHE
                }

                webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

                webView.webViewClient = object: WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        return false
                    }
                }

                webView.addJavascriptInterface(
                    VenmoJavaScriptInterface(
                        this
                    ), "VenmoAndroidSDK")



                webView.loadUrl("https://venmo.com/signup/")

                retainedInstance = webView
            }
        } else {
            Log.e("venmo","reusing loading new WebView")

            // If prior WebView instance is available (e.g., if screen was rotated), reuse the view
            val parent = (binding.webView.parent as CoordinatorLayout).also {
                it.removeView(binding.webView)
            }

            (oldWebView.parent as CoordinatorLayout).removeView(oldWebView)
            parent.addView(oldWebView)

            retainedInstance = oldWebView
        }
    }

    override fun onRetainCustomNonConfigurationInstance(): Any? {
        // Store the WebView so it can be recovered during activity re-start after screen rotation
        return retainedInstance
    }

    inner class VenmoJavaScriptInterface(val activity: Activity) {
        @JavascriptInterface
        fun paymentSuccessful(signed_request: String?) {
            activity.setResult(RESULT_OK, Intent().also { it.putExtra("signedrequest", signed_request) })
            activity.finish()
        }

        @JavascriptInterface
        fun error(error_message: String?) {
            activity.setResult(RESULT_OK, Intent().also { it.putExtra("error_message", error_message) })
            activity.finish()
        }

        @JavascriptInterface
        fun cancel() {
            activity.setResult(RESULT_CANCELED)
            activity.finish()
        }
    }
}


private fun createVenmoAppIntent(sending: Boolean, counterpartyAddress: String, amount: String, note: String, appName: String): Intent {
    var venmoURI = "venmosdk://paycharge?txn=" + if (sending) "pay" else "charge"

    if (counterpartyAddress.isNotBlank()) {
        try { venmoURI += "&recipients=" + URLEncoder.encode(counterpartyAddress, "UTF-8") }
        catch (e: UnsupportedEncodingException) {
            Log.e("venmo", "cannot encode recipient $counterpartyAddress")
        }
    }

    try {
        venmoURI += "&amount=" + URLEncoder.encode(amount, "UTF-8")
    }
    catch (e: UnsupportedEncodingException) {
        Log.e("venmo", "cannot encode amount $amount")
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

fun sendVenmoRequest(frag: Fragment, appLauncher: ActivityResultLauncher<Intent>, installLauncher: ActivityResultLauncher<Intent>?, payment: Payment) {
    val peerToPeerData = extractPeerToPeerData(payment) ?: return

    val numberFormat = NumberFormat.getNumberInstance()
    numberFormat.minimumFractionDigits = 2
    numberFormat.maximumFractionDigits = 2
    numberFormat.isGroupingUsed = false
    val amount: String = numberFormat.format(payment.amount)

    when (peerToPeerData.appUserRole) {
        PeerToPeerAppUserRole.SENDING -> {
            try {
                appLauncher.launch(
                    createVenmoAppIntent(
                        true,
                        peerToPeerData.receiverAddress,
                        amount,
                        frag.resources.getString(R.string.venmo_note),
                        frag.resources.getString(R.string.app_name)
                    )
                )
            } catch(e: ActivityNotFoundException) {
                if (installLauncher != null) {
                    try {
                        installLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.venmo")))
                    } catch (e: ActivityNotFoundException) {
                        installLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.venmo")))
                    }
                }
            }
        }
        PeerToPeerAppUserRole.RECEIVING -> {
            try {
                appLauncher.launch(
                    createVenmoAppIntent(
                        false,
                        peerToPeerData.senderAddress,
                        amount,
                        frag.resources.getString(R.string.venmo_note),
                        frag.resources.getString(R.string.app_name)
                    )
                )
            } catch(e: ActivityNotFoundException) {
                if (installLauncher != null) {
                    try {
                        installLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.venmo")))
                    } catch (e: ActivityNotFoundException) {
                        installLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.venmo")))
                    }
                }
            }
        }
        PeerToPeerAppUserRole.MEDIATING -> {
            // Cannot create a transaction between third parties using the app
            // TODO
        }
    }
}

fun parseVenmoResponse(context: Context, result: ActivityResult, referencePayment: Payment): Pair<Boolean, String> {
    if (result.resultCode == Activity.RESULT_OK) {
        result.data?.getStringExtra("signedrequest")?.apply {
            try {
                val encodedStringArray = this.split(".")
                val encodedSignature = encodedStringArray[0]
                val encodedPayload = encodedStringArray[1]

                val decodedSignature: String = base64UrlDecode(encodedSignature)
                val expectedSignature = hashHmac(encodedPayload)
                if (decodedSignature != expectedSignature) {
                    return Pair(true, context.getString(R.string.venmo_unparseable_response))
                }

                val obj = JsonParser.parseString(base64UrlDecode(encodedPayload)).asJsonArray[0].asJsonObject

                val success = obj["success"].asString
                if (success == "0") {
                    // Only fail to confirm transaction if response is explicitly not
                    // successful in case the response format changes in the future.
                    return Pair(true, context.getString(R.string.venmo_unsuccessful))
                }

                val amount = obj["amount"].asString
                // TODO where to locate this?
                val peerToPeerData = extractPeerToPeerData(referencePayment)!!
                val numberFormat = NumberFormat.getNumberInstance()
                numberFormat.minimumFractionDigits = 2
                numberFormat.maximumFractionDigits = 2
                numberFormat.isGroupingUsed = false
                val targetAmount = numberFormat.format(referencePayment.amount)
                if (amount != targetAmount) {
                    val currencyAmount = NumberFormat.getCurrencyInstance().format(amount)
                    val currencyTargetAmount = NumberFormat.getCurrencyInstance().format(targetAmount)
                    return Pair(true, context.getString(R.string.venmo_wrong_amount, currencyAmount, currencyTargetAmount))
                }

                when (obj["action"].asString) {
                    "pay" -> {
                        val receiverAddress = obj["target"].asJsonObject["user"].asJsonObject["username"].asString
                        if (receiverAddress != peerToPeerData.receiverAddress) {
                            return Pair(true, context.getString(R.string.venmo_wrong_receiver, receiverAddress, peerToPeerData.receiverAddress))
                        }
                        val senderAddress = obj["actor"].asJsonObject["username"].asString
                        if (senderAddress != peerToPeerData.senderAddress) {
                            return Pair(true, context.getString(R.string.venmo_wrong_sender, senderAddress, peerToPeerData.senderAddress))
                        }
                    }
                    "charge" -> {
                        val senderAddress = obj["target"].asJsonObject["user"].asJsonObject["username"].asString
                        if (senderAddress != peerToPeerData.senderAddress) {
                            return Pair(true, context.getString(R.string.venmo_wrong_sender, senderAddress, peerToPeerData.senderAddress))
                        }
                        val receiverAddress = obj["actor"].asJsonObject["username"].asString
                        if (receiverAddress != peerToPeerData.receiverAddress) {
                            return Pair(true, context.getString(R.string.venmo_wrong_receiver, receiverAddress, peerToPeerData.receiverAddress))
                        }
                    }
                    else -> {
                        return Pair(true, context.getString(R.string.venmo_unparseable_response))
                    }
                }

                // All validations passed
                return Pair(true, context.getString(R.string.venmo_successful))
            } catch(e: Exception){
                e.printStackTrace()
                return Pair(true, context.getString(R.string.venmo_unparseable_response))
            }
        }
    }

    return Pair(false, context.getString(R.string.venmo_canceled))
}

private fun hashHmac(payload: String): String? {
    return try {
        val mac: Mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(BuildConfig.VENMO_SECRET.toByteArray(), "HmacSHA256"))
        String(mac.doFinal(payload.toByteArray()))
    } catch (e: Exception) {
        Log.d("VenmoSDK Error Message Caught", e.message!!)
        ""
    }
}

private fun base64UrlDecode(payload: String) =
    String(
        Base64.decode(
            payload.replace('-', '+').replace('_', '/').trim {
                it <= ' '
            },
            Base64.DEFAULT
        )
    )