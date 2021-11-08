package com.grouptuity.grouptuity.ui.billsplit.qrcodescanner


abstract class QRCodeParser {
    enum class Status {
        NO_QR_CODE,
        INVALID_URL,
        NO_INTERNET,
        REQUESTED,
        NO_RESPONSE,
        BAD_RESPONSE,
        NOT_VALID_ADDRESS,
        VALID_ADDRESS
    }

    abstract fun parse(url: String?, callback: (Status, String?) -> Unit): Pair<Status, String?>
}