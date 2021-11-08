package com.grouptuity.grouptuity.ui.billsplit.payments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.grouptuity.grouptuity.databinding.VenmoWebviewBinding


class VenmoWebViewActivity: AppCompatActivity() {
    private lateinit var binding: VenmoWebviewBinding
    private var mVenmoWebView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = VenmoWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.extras!!.getString("url")!!

        mVenmoWebView = lastNonConfigurationInstance as WebView?
        if (mVenmoWebView != null) {
            //If screen was rotated, do not reload the whole WebView
            val webView = binding.webView
            val parent = webView.parent as CoordinatorLayout
            parent.removeView(webView)

            (mVenmoWebView!!.parent as CoordinatorLayout).removeView(mVenmoWebView)
            parent.addView(mVenmoWebView)
        } else {
            // Load the WebView
            mVenmoWebView = binding.webView.also {
                it.webViewClient = object: WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        return true
                    }
                }

                it.addJavascriptInterface(
                    VenmoJavaScriptInterface(
                        this
                    ), "VenmoAndroidSDK")
                it.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                it.settings.javaScriptEnabled = true
                it.settings.userAgentString = "venmo-android-2.0"
                it.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                it.loadUrl(url)
            }
        }
    }

    override fun onRetainCustomNonConfigurationInstance(): Any? {
        return mVenmoWebView
    }

    class VenmoJavaScriptInterface(val activity: Activity) {
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