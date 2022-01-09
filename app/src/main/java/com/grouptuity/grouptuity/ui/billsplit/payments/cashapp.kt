package com.grouptuity.grouptuity.ui.billsplit.payments

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.entities.Payment
import com.grouptuity.grouptuity.ui.billsplit.qrcodescanner.QRCodeParser
import java.text.NumberFormat

fun cashAppAddressToCashtag(address: String) = address


class CashtagParser: QRCodeParser() {
    companion object {
        const val DOMAIN = "https://cash.app/"
    }

    override fun parse(url: String?, callback: (Status, String?) -> Unit): Pair<Status, String?> {
        return if (url != null && url.startsWith(DOMAIN)) {
            Pair(Status.VALID_ADDRESS, url.substringAfter(DOMAIN).substringBefore("?"))
        } else {
            Pair(Status.INVALID_URL, null)
        }
    }
}


fun sendCashAppRequest(frag: Fragment, appLauncher: ActivityResultLauncher<Intent>, installLauncher: ActivityResultLauncher<Intent>?, payment: Payment) {

    val peerToPeerData = extractPeerToPeerData(payment) ?: return

    when (peerToPeerData.appUserRole) {
        PeerToPeerAppUserRole.SENDING -> {
            try {
                appLauncher.launch(Intent().also {
                    it.component = ComponentName("com.squareup.cash", "com.squareup.cash.ui.MainActivity")
                })

                Toast.makeText(
                    frag.requireActivity(),
                    frag.resources.getString(
                        R.string.payment_method_cash_app_toast_pay,
                        NumberFormat.getCurrencyInstance().format(payment.amount),
                        peerToPeerData.receiverAddress
                    ),
                    Toast.LENGTH_LONG).show()
            } catch(e: ActivityNotFoundException) {
                if (installLauncher != null) {
                    try {
                        installLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.squareup.cash")))
                    } catch (e: ActivityNotFoundException) {
                        installLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.squareup.cash")))
                    }
                }
            }
        }
        PeerToPeerAppUserRole.RECEIVING -> {
            try {
                appLauncher.launch(Intent().also {
                    it.component = ComponentName("com.squareup.cash", "com.squareup.cash.ui.MainActivity")
                })

                Toast.makeText(
                    frag.requireActivity(),
                    frag.resources.getString(
                        R.string.payment_method_cash_app_toast_request,
                        NumberFormat.getCurrencyInstance().format(payment.amount),
                        peerToPeerData.senderAddress
                    ),
                    Toast.LENGTH_LONG).show()
            } catch(e: ActivityNotFoundException) {
                if (installLauncher != null) {
                    try {
                        installLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.squareup.cash")))
                    } catch (e: ActivityNotFoundException) {
                        installLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.squareup.cash")))
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