package com.grouptuity.grouptuity.ui.billsplit.payments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import com.grouptuity.grouptuity.data.Payment
import java.text.NumberFormat


private fun createPaybackLaterEmailIntent(payment: Payment) = extractPeerToPeerData(payment)?.let {
    val toAddresses: Array<String>
    val subject: String

    val amountString = NumberFormat.getCurrencyInstance().format(payment.amount)

    when (it.appUserRole) {
        PeerToPeerAppUserRole.SENDING -> {
            // User owes another diner
            toAddresses = arrayOf(it.receiverAddress)
            subject = it.sender.name + " will pay you back " + amountString
        }
        PeerToPeerAppUserRole.RECEIVING -> {
            // Another diner owes the user directly
            toAddresses = arrayOf(it.senderAddress)
            subject = "Please pay back " + it.receiver.name + " " + amountString
        }
        PeerToPeerAppUserRole.MEDIATING -> {
            // Mediating between two other diners
            toAddresses = arrayOf(it.senderAddress, it.receiverAddress)
            subject = it.sender.name + " owes " + it.receiver.name + " " + amountString
        }
    }

    val body = "example body"

    Intent(Intent.ACTION_SENDTO).apply {
        type = "*/*"
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, toAddresses)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
}


//fun generateRecieptEmailBody(): String {
//    var underline = ""
//    for (n in 0..24) underline += "\u00AF"
//    var body =
//        "SPLIT BILL RECEIPT\nSent from Grouptuity for Android\nhttps://play.google.com/store/apps/details?id=com.grouptuity\n\n"
//    var venmoPayments = ""
//    val iouPayments = ""
//    var billString = ""
//    for (d in Grouptuity.getBill().diners) {
//        for (p in d.paymentsOut) {
//            when (p.type) {
//                VENMO -> venmoPayments += """• ${p.payer.name} owes ${
//                    Grouptuity.formatNumber(
//                        Grouptuity.NumberFormat.CURRENCY,
//                        p.amount
//                    )
//                } to ${p.payee.name} on Venmo. Pay here: ${
//                    getVenmoPaymentLink(
//                        p.amount,
//                        p.payee.contact.venmoUsername
//                    )
//                }
//"""
//                IOU_EMAIL -> venmoPayments += """• ${p.payer.name} owes ${
//                    Grouptuity.formatNumber(
//                        Grouptuity.NumberFormat.CURRENCY,
//                        p.amount
//                    )
//                } to ${p.payee.name}
//"""
//                else -> {}
//            }
//        }
//        billString += d.name + "\n" + d.getReceiptNotes() + "\n"
//    }
//    body += "OUTSTANDING PAYMENTS:\n$underline\n$venmoPayments\n$iouPayments\nFULL BILL:\n$underline\n$billString"
//    return body
//}


fun sendPaybackLaterEmail(activity: Activity, launcher: ActivityResultLauncher<Intent>, payment: Payment) {
    val paybackIntent = createPaybackLaterEmailIntent(payment)
    when {
        paybackIntent == null -> {
            // TODO
        }
        paybackIntent.resolveActivity(activity.packageManager) == null -> {
            // TODO
        }
        else -> {
            launcher.launch(paybackIntent)
        }
    }
}