package com.grouptuity.grouptuity.ui.billsplit.payments

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.grouptuity.grouptuity.BuildConfig
import com.grouptuity.grouptuity.data.entities.Payment
import com.grouptuity.grouptuity.ui.billsplit.qrcodescanner.QRCodeParser
import org.walletconnect.walletconnectv2.WalletConnectClient
import org.walletconnect.walletconnectv2.client.ClientTypes
import org.walletconnect.walletconnectv2.client.WalletConnectClientListeners


class AlgorandAddressParser(context: Context): QRCodeParser() {
    companion object {
        const val DOMAIN = "https://cash.app/"

        fun isValidAddress(address: String) {
            true // TODO 58 characters long, letters and numbers, handle checksum?
        }
    }

    override fun parse(url: String?, callback: (Status, String?) -> Unit): Pair<Status, String?> {
        return if (true) {
            Pair(Status.VALID_ADDRESS, url)
        } else {
            Pair(Status.INVALID_URL, null)
        }
    }
}


fun startAlgorandTransaction(frag: Fragment, appLauncher: ActivityResultLauncher<Intent>, payment: Payment) {
    val initializeParams = ClientTypes.InitialParams(
        frag.requireActivity().application,
        useTls = true,
        hostName = "relay.walletconnect.com",
        apiKey = BuildConfig.WALLETCONNECT_KEY,
        isController = true)

    WalletConnectClient.initialize(initializeParams)



    val pairParams = ClientTypes.PairParams("wc:...")
    val pairListener = WalletConnectClientListeners.Pairing { sessionProposal -> /* handle session proposal */ }
    WalletConnectClient.pair(pairParams, pairListener)
}