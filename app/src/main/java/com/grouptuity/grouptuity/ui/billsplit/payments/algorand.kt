package com.grouptuity.grouptuity.ui.billsplit.payments

<<<<<<< Updated upstream
=======
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.grouptuity.grouptuity.BuildConfig
import com.grouptuity.grouptuity.data.entities.Payment
import com.grouptuity.grouptuity.ui.billsplit.qrcodescanner.QRCodeParser
>>>>>>> Stashed changes
import org.walletconnect.walletconnectv2.WalletConnectClient
import org.walletconnect.walletconnectv2.client.ClientTypes

fun algorandAddressToString(address: String): String {
    return address
}

//fun algorand() {
//    val initializeParams = ClientTypes.InitialParams(useTls = true, hostName = "relay.walletconnect.com", apiKey = "sample key", isController = true)
//    WalletConnectClient.initalize(initalizeParams)
//}