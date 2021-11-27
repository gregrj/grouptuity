package com.grouptuity.grouptuity.ui.billsplit.payments

//import android.content.Intent
//import android.net.Uri
//import androidx.core.content.ContextCompat
//import java.text.SimpleDateFormat
//import java.util.*
//
//
//fun getIOUEmailIntent(payer: String?=null, payee: String?=null): Intent {
//
//    var toAddresses: String
//    var ccAddresses: String
//    val subject: String
//    val body: String
//
//    when {
//        payer == null -> {
//            // User owes another diner
//        }
//        payee == null -> {
//            // Another diner owes the user
//        }
//        else -> {
//            // Mediating between two other diners
//        }
//    }
//
//    return Intent(Intent.ACTION_SENDTO).apply {
//        type = "*/*"
//        putExtra(Intent.EXTRA_EMAIL, toAddresses)
//        putExtra(Intent.EXTRA_CC, ccAddresses)
//        putExtra(Intent.EXTRA_SUBJECT, subject)
//        putExtra(Intent.EXTRA_HTML_TEXT, body)
//    }
//}
//
//
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