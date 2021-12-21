package com.grouptuity.grouptuity.ui.billsplit.qrcodescanner

import android.app.Application
import android.content.Context
import android.os.Parcelable
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.google.mlkit.vision.barcode.Barcode
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.PaymentMethod
import com.grouptuity.grouptuity.ui.billsplit.payments.AlgorandAddressParser
import com.grouptuity.grouptuity.ui.billsplit.payments.CashtagParser
import com.grouptuity.grouptuity.ui.billsplit.payments.VenmoAddressParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.parcelize.Parcelize


@Parcelize
data class QRCodeDisplayResults(val title: String,
                                val message: String,
                                val result: String,
                                val status: QRCodeParser.Status): Parcelable


class QRCodeScannerViewModel(app: Application): AndroidViewModel(app) {
    protected val context: Context
        get() = getApplication<Application>().applicationContext

    companion object {
        private val venmoDisplayTitle = mapOf(
            QRCodeParser.Status.INVALID_URL to R.string.qrcodescanner_title_invalid_url,
            QRCodeParser.Status.NO_INTERNET to R.string.qrcodescanner_title_no_internet,
            QRCodeParser.Status.REQUESTED to R.string.qrcodescanner_title_requested_venmo,
            QRCodeParser.Status.NO_RESPONSE to R.string.qrcodescanner_title_no_response_venmo,
            QRCodeParser.Status.BAD_RESPONSE to R.string.qrcodescanner_title_bad_response_venmo,
            QRCodeParser.Status.NOT_VALID_ADDRESS to R.string.qrcodescanner_title_invalid_venmo,
            QRCodeParser.Status.VALID_ADDRESS to R.string.qrcodescanner_title_valid_venmo)

        private val venmoDisplayMessage = mapOf(
            QRCodeParser.Status.INVALID_URL to R.string.qrcodescanner_message_invalid_url_venmo,
            QRCodeParser.Status.NO_INTERNET to R.string.qrcodescanner_message_no_internet_venmo,
            QRCodeParser.Status.NO_RESPONSE to R.string.qrcodescanner_message_no_response_venmo,
            QRCodeParser.Status.BAD_RESPONSE to R.string.qrcodescanner_message_bad_response_venmo,
            QRCodeParser.Status.NOT_VALID_ADDRESS to R.string.qrcodescanner_message_invalid_venmo)

        private val cashAppDisplayTitle = mapOf(
            QRCodeParser.Status.INVALID_URL to R.string.qrcodescanner_title_invalid_url,
            QRCodeParser.Status.VALID_ADDRESS to R.string.qrcodescanner_title_valid_cash_app)

        private val cashAppDisplayMessage = mapOf(
            QRCodeParser.Status.INVALID_URL to R.string.qrcodescanner_message_invalid_url_cash_app)

        private val algorandDisplayTitle = mapOf(
            QRCodeParser.Status.INVALID_URL to R.string.qrcodescanner_title_invalid_url,
            QRCodeParser.Status.VALID_ADDRESS to R.string.qrcodescanner_title_valid_algorand)

        private val algorandDisplayMessage = mapOf(
            QRCodeParser.Status.INVALID_URL to R.string.qrcodescanner_message_invalid_url_algorand)
    }

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    private val algorandAddressParser = AlgorandAddressParser(context.applicationContext)
    private val cashtagParser = CashtagParser()
    private val venmoAddressParser = VenmoAddressParser(context.applicationContext)

    private var qrCodeSubject = 0

    private val hasCameraPermission = MutableStateFlow(false)
    private val paymentMethod = MutableStateFlow(PaymentMethod.CASH)
    private val dinerName = MutableStateFlow("")
    private val rawBarcode = MutableStateFlow<Barcode?>(null)
    private val loadStatus = MutableStateFlow(QRCodeParser.Status.NO_QR_CODE)
    private val verifiedAddress = MutableStateFlow<String?>(null)

    val cameraActive: LiveData<Boolean> = combine(rawBarcode, hasCameraPermission) { barcode, permitted ->
        barcode == null && permitted
    }.asLiveData()
    val chipInstructions: LiveData<String?> = combine(rawBarcode, paymentMethod, dinerName) { barcode, method, name ->
        if (barcode == null) {
            context.getString(
                when (method) {
                    PaymentMethod.VENMO -> { R.string.qrcodescanner_chip_instruction_venmo }
                    PaymentMethod.CASH_APP -> { R.string.qrcodescanner_chip_instruction_cash_app }
                    else -> { R.string.qrcodescanner_chip_instruction_general }
                },
                name)
        } else {
            null
        }
    }.asLiveData()
    val displayResults: LiveData<QRCodeDisplayResults?> = combine(rawBarcode, verifiedAddress, paymentMethod, loadStatus) { barcode, address, method, status ->
        if(barcode == null) {
            null
        } else {
            when (method) {
                PaymentMethod.VENMO -> {
                    QRCodeDisplayResults(
                        venmoDisplayTitle[status]?.let {
                            if (status == QRCodeParser.Status.VALID_ADDRESS)
                                context.getString(it, dinerName.value)
                            else
                                context.getString(it)
                        } ?: "",
                        venmoDisplayMessage[status]?.let { context.getString(it) } ?: "",
                        address?.let { "@$address" } ?: "",
                        status)
                }
                PaymentMethod.CASH_APP -> {
                    QRCodeDisplayResults(
                        cashAppDisplayTitle[status]?.let {
                            if (status == QRCodeParser.Status.VALID_ADDRESS)
                                context.getString(it, dinerName.value)
                            else
                                context.getString(it)
                        } ?: "",
                        cashAppDisplayMessage[status]?.let { context.getString(it) } ?: "",
                        address ?: "",
                        status)
                }
                PaymentMethod.ALGO -> {
                    QRCodeDisplayResults(
                        algorandDisplayTitle[status]?.let {
                            if (status == QRCodeParser.Status.VALID_ADDRESS)
                                context.getString(it, dinerName.value)
                            else
                                context.getString(it)
                        } ?: "",
                        algorandDisplayMessage[status]?.let { context.getString(it) } ?: "",
                        address ?: "",
                        status)
                }
                else -> {
                    null
                }
            }
        }
    }.asLiveData()

    val hasBarcode get() = displayResults.value != null

    fun getSubject() = qrCodeSubject
    fun getPaymentMethod() = paymentMethod.value
    fun getDinerName() = dinerName.value
    fun getVerifiedAddress() = verifiedAddress.value

    private fun requestUrl(url: String?): QRCodeParser.Status {
        Log.e("url", url ?: "")

        when(paymentMethod.value) {
            PaymentMethod.ALGO -> { algorandAddressParser }
            PaymentMethod.CASH_APP -> { cashtagParser }
            PaymentMethod.VENMO -> { venmoAddressParser }
            else -> { null }
        }?.apply {
            val (requestStatus, address) = parse(url) { status, address ->
                verifiedAddress.value = address
                loadStatus.value = status
            }

            if (requestStatus == QRCodeParser.Status.VALID_ADDRESS) {
                verifiedAddress.value = address
            }
            return requestStatus
        }

        return QRCodeParser.Status.INVALID_URL
    }

    fun initialize(subject: Int, method: PaymentMethod, name: String) {
        hasCameraPermission.value = false
        qrCodeSubject = subject
        paymentMethod.value = method
        dinerName.value = name
        rawBarcode.value = null
        verifiedAddress.value = null
        loadStatus.value = QRCodeParser.Status.NO_QR_CODE
    }

    fun clearBarcode() {
        rawBarcode.value = null
        verifiedAddress.value = null
        loadStatus.value = QRCodeParser.Status.NO_QR_CODE
    }
    fun setBarcode(barcode: Barcode) {
        if (rawBarcode.value == null) {
            verifiedAddress.value = null

            // Try to extract url first. If not possible (e.g., Algorand address), then use the
            // display value.
            loadStatus.value = requestUrl(barcode.url?.url ?: barcode.displayValue)
            rawBarcode.value = barcode

            vibrator?.apply { vibrate(25) }
        }
    }

    fun retryLoad() {
        rawBarcode.value?.apply {
            loadStatus.value = requestUrl(rawBarcode.value!!.url?.url)
        }
    }

    fun allowCameraUse() { hasCameraPermission.value = true }
    fun blockCameraUse() { hasCameraPermission.value = false }
}