package com.grouptuity.grouptuity.ui.billsplit.payments

<<<<<<< Updated upstream
=======
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.entities.Payment
>>>>>>> Stashed changes
import com.grouptuity.grouptuity.ui.billsplit.qrcodescanner.QRCodeParser

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