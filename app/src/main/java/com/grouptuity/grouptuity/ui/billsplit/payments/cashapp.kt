package com.grouptuity.grouptuity.ui.billsplit.payments

import com.grouptuity.grouptuity.ui.billsplit.qrcodescanner.QRCodeParser


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