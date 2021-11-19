package com.grouptuity.grouptuity.ui.billsplit.dinerdetails

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import com.grouptuity.grouptuity.ui.billsplit.payments.algorandAddressToString
import com.grouptuity.grouptuity.ui.billsplit.payments.cashAppAddressToCashtag
import com.grouptuity.grouptuity.ui.billsplit.payments.venmoAddressToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.text.NumberFormat
import kotlin.math.abs

class DinerDetailsViewModel(app: Application): UIViewModel(app) {
    private val currencyFormatter = NumberFormat.getCurrencyInstance()
    private val percentFormatter = NumberFormat.getPercentInstance().also {
        it.maximumFractionDigits = getApplication<Application>().resources.getInteger(R.integer.percent_max_decimals)
    }
    private val totalOwedToOthersString = app.resources.getString(R.string.dinerdetails_debts_total_owed_to_others)
    private val totalOwedByOthersString = app.resources.getString(R.string.dinerdetails_debts_total_owed_by_others)

    val loadedDiner = MutableStateFlow<Diner?>(null)
    val dinerEmailAddresses = loadedDiner.map { it?.emailAddresses ?: emptyList() }.asLiveData()
    private val numDiners = repository.diners.mapLatest { it.size }
    private val numItems = repository.items.mapLatest { it.size }
    private val unusedDiscountAmount: Flow<Double?> =
        combine(loadedDiner, repository.individualExcessDiscountsReleased) { diner, discountsReleased ->
            discountsReleased[diner]
        }
    private val borrowedDiscountAmount: Flow<Double?> =
        combine(loadedDiner, repository.individualExcessDiscountsAcquired) { diner, discountsAcquired ->
            discountsAcquired[diner]
        }

    val toolbarTitle = loadedDiner.mapNotNull { it?.name }.asLiveData()

    private val editingBiographicsMutable = MutableStateFlow(false)
    val editingBiographics: LiveData<Boolean> = editingBiographicsMutable.asLiveData()

    private val emailAddressNotSet = context.getString(R.string.dinerdetails_biographics_address_not_set, context.getString(PaymentMethod.IOU_EMAIL.addressNameStringId))
    private val venmoAddressNotSet = context.getString(R.string.dinerdetails_biographics_address_not_set, context.getString(PaymentMethod.VENMO.addressNameStringId))
    private val cashtagNotSet = context.getString(R.string.dinerdetails_biographics_address_not_set, context.getString(PaymentMethod.CASH_APP.addressNameStringId))
    private val algoAddressNotSet = context.getString(R.string.dinerdetails_biographics_address_not_set, context.getString(PaymentMethod.ALGO.addressNameStringId))

    val email: LiveData<Triple<Boolean, String, Boolean>> = combine(loadedDiner, editingBiographicsMutable) { diner, editing ->
        val address = diner?.paymentAddressDefaults?.get(PaymentMethod.IOU_EMAIL)
        if(address == null){
            Triple(false, emailAddressNotSet, editing)
        } else {
            Triple(true, address, editing)
        }
    }.asLiveData()
    val venmoAddress: LiveData<Triple<Boolean, String, Boolean>> = combine(loadedDiner, editingBiographicsMutable) { diner, editing ->
        val address = diner?.paymentAddressDefaults?.get(PaymentMethod.VENMO)
        if(address == null){
            Triple(false, venmoAddressNotSet, editing)
        } else {
            Triple(true, address, editing)
        }
    }.asLiveData()
    val cashtag: LiveData<Triple<Boolean, String, Boolean>> = combine(loadedDiner, editingBiographicsMutable) { diner, editing ->
        val address = diner?.paymentAddressDefaults?.get(PaymentMethod.CASH_APP)
        if(address == null){
            Triple(false, cashtagNotSet, editing)
        } else {
            Triple(true, address, editing)
        }
    }.asLiveData()
    val algorandAddress: LiveData<Triple<Boolean, String, Boolean>> = combine(loadedDiner, editingBiographicsMutable) { diner, editing ->
        val address = diner?.paymentAddressDefaults?.get(PaymentMethod.ALGO)
        if(address == null){
            Triple(false, algoAddressNotSet, editing)
        } else {
            Triple(true, address, editing)
        }
    }.asLiveData()

    val items: LiveData<List<Pair<Item,Triple<String, String, String?>>>> = loadedDiner.mapLatest {
        it?.items?.map { item ->
            Pair(
                item,
                Triple(
                    item.name,
                    currencyFormatter.format(item.price / item.diners.size),
                    if (item.diners.size > 1) {
                        getApplication<Application>().resources.getQuantityString(
                            R.plurals.dinerdetails_item_split,
                            item.diners.size,
                            item.diners.size,
                            currencyFormatter.format(item.price)
                        )
                    }
                    else{
                        null
                    }
                )
            )
        } ?: emptyList()
    }.asLiveData()
    val enableItemExpansion: LiveData<Boolean> = loadedDiner.mapLatest { it?.items?.isNotEmpty() ?: false }.asLiveData()
    val subtotalString: LiveData<String> = combine(loadedDiner, repository.individualSubtotals) { loadedDiner, subtotals ->
        currencyFormatter.format(subtotals[loadedDiner] ?: 0.0)
    }.asLiveData()

    val discounts: LiveData<List<Pair<Discount,Triple<String, String, String?>>>> =
        combine(loadedDiner, numDiners, numItems, repository.individualDiscountShares, repository.discountValues) { diner, numDiners, numItems, discountShares, discountValues ->
            if (diner == null) {
                emptyList()
            } else {
                discountShares[diner]?.keys?.mapNotNull { discount ->

                    val discountShare = discountShares[diner]?.get(discount)?.let { currencyFormatter.format(it) }

                    val descriptionString = getDiscountDescription(
                        discount,
                        discountValues.getOrDefault(discount, 0.0),
                        numItems,
                        numDiners)

                    val sharingString = if (discount.onItems) {
                        val recipients = mutableSetOf<String>().also { recipientSet ->
                            discount.items.forEach { recipientSet.addAll(it.dinerIds) }
                        }

                        when(recipients.size) {
                            1 -> {
                                null
                            }
                            2 -> {
                                val loadedDinerId = loadedDiner.value?.id
                                val otherRecipient = recipients.filter {
                                    it != loadedDinerId
                                }.map { recipientId ->
                                    repository.diners.value.find { it.id == recipientId }
                                }

                                if (otherRecipient.size == 1) {
                                    getApplication<Application>().resources.getString(
                                        R.string.dinerdetails_discount_item_sharing_two,
                                        otherRecipient[0]?.name ?: "1")
                                } else {
                                    getApplication<Application>().resources.getQuantityString(
                                        R.plurals.dinerdetails_discount_item_sharing_many,
                                        1,
                                        1)
                                }
                            }
                            numDiners -> {
                                getApplication<Application>().resources.getString(R.string.dinerdetails_discount_item_sharing_all)
                            }
                            else -> {
                                getApplication<Application>().resources.getQuantityString(
                                    R.plurals.dinerdetails_discount_item_sharing_many,
                                    recipients.size-1,
                                    recipients.size-1)
                            }
                        }
                    } else {
                        null
                    }

                    if (discountShare !== null) {
                        Pair(discount, Triple(descriptionString, discountShare, sharingString))
                    } else {
                        null
                    }
                } ?: emptyList()
            }
        }.asLiveData()
    val enableDiscountExpansion: LiveData<Boolean> = combine(
        loadedDiner,
        repository.individualDiscountShares,
        repository.individualExcessDiscountsAcquired) { diner, discountShares, discountsBorrowed ->
        diner != null && (!discountShares[diner].isNullOrEmpty() || discountsBorrowed.getOrDefault(diner, 0.0) > PRECISION)
    }.asLiveData()
    val unusedDiscountString: LiveData<String?> = unusedDiscountAmount.mapLatest { it?.let {
        currencyFormatter.format(-it) } }.asLiveData()
    val borrowedDiscountString: LiveData<String?> = borrowedDiscountAmount.mapLatest { it?.let { currencyFormatter.format(it) } }.asLiveData()
    val discountsTotalString: LiveData<String> =
        combine(loadedDiner, unusedDiscountAmount, borrowedDiscountAmount, repository.individualDiscountTotals) { loadedDiner, unused, borrowed, rawDiscounts ->
            val rawDiscount = rawDiscounts[loadedDiner] ?: 0.0
            val unusedDiscount = unused ?: 0.0
            val borrowedDiscount = borrowed ?: 0.0
            currencyFormatter.format(rawDiscount - unusedDiscount + borrowedDiscount)
        }.asLiveData()

    val afterDiscountsTotalString: LiveData<String> = combine(loadedDiner, repository.individualSubtotalsWithDiscounts) { loadedDiner, amounts ->
        currencyFormatter.format(loadedDiner?.let { amounts.getOrDefault(it, 0.0) } ?: 0.0)
    }.asLiveData()
    val taxPercentString: LiveData<String> = repository.taxPercent.mapLatest { percentFormatter.format(0.01*it) }.asLiveData()
    val taxAmountString: LiveData<String> = combine(loadedDiner, repository.individualTax) { loadedDiner, amounts ->
        currencyFormatter.format(loadedDiner?.let { amounts.getOrDefault(it, 0.0) } ?: 0.0)
    }.asLiveData()
    val afterTaxTotalString: LiveData<String> = combine(loadedDiner, repository.individualSubtotalWithDiscountsAndTax) { loadedDiner, amounts ->
        currencyFormatter.format(loadedDiner?.let { amounts.getOrDefault(it, 0.0) } ?: 0.0)
    }.asLiveData()
    val tipPercentString: LiveData<String> = repository.tipPercent.mapLatest { percentFormatter.format(0.01*it) }.asLiveData()
    val tipAmountString: LiveData<String> = combine(loadedDiner, repository.individualTip) { loadedDiner, amounts ->
        currencyFormatter.format(loadedDiner?.let { amounts.getOrDefault(it, 0.0) } ?: 0.0)
    }.asLiveData()
    val totalString: LiveData<String> = combine(loadedDiner, repository.individualTotal) { loadedDiner, amounts ->
        currencyFormatter.format(loadedDiner?.let { amounts.getOrDefault(it, 0.0) } ?: 0.0)
    }.asLiveData()

    val reimbursements: LiveData<List<Triple<String, String, String>>> = combine(
        loadedDiner,
        repository.individualReimbursementDebts,
        repository.individualReimbursementCredits,
        numItems,
        numDiners,
        repository.discountValues) { input ->

        val reimbursements = mutableListOf<Triple<String, String, String>>()

        val diner = input[0]
        if (diner != null) {
            val debts = input[1] as Map<Diner, Map<Discount, Double>>
            val credits = input[2] as Map<Diner, Map<Discount, Double>>
            val numItems = input[3] as Int
            val numDiners = input[4] as Int
            val discountValues = input[5] as Map<Discount, Double>

            debts[diner]?.forEach { (discount, amount) ->
                reimbursements.add(
                    Triple(
                        getApplication<Application>().resources.getString(R.string.dinerdetails_reimbursement_pay),
                        currencyFormatter.format(amount),
                        "(" + getDiscountDescription(
                            discount,
                            discountValues[discount] ?: 0.0,
                            numItems,
                            numDiners) + ")"
                    )
                )
            }

            credits[diner]?.forEach { (discount, amount) ->
                reimbursements.add(
                    Triple(
                        getApplication<Application>().resources.getString(R.string.dinerdetails_reimbursement_receive),
                        currencyFormatter.format(-amount),
                        "(" + getDiscountDescription(
                            discount,
                            discountValues[discount] ?: 0.0,
                            numItems,
                            numDiners) + ")"
                    )
                )
            }
        }

        reimbursements
    }.asLiveData()
    val enableReimbursementExpansion: LiveData<Boolean> = combine(
        loadedDiner,
        repository.individualReimbursementDebts,
        repository.individualReimbursementCredits) { diner, debts, credits ->
        diner != null && (!debts[diner].isNullOrEmpty() || !credits[diner].isNullOrEmpty())
    }.asLiveData()
    private val reimbursementsTotal: Flow<Double> =
        combine(loadedDiner, repository.individualReimbursementDebts, repository.individualReimbursementCredits) { loadedDiner, debts, credits ->
            (debts[loadedDiner]?.values?.sum() ?: 0.0) - (credits[loadedDiner]?.values?.sum() ?: 0.0)
        }
    val reimbursementsTotalString: LiveData<String> = reimbursementsTotal.mapLatest { currencyFormatter.format(it) }.asLiveData()

    val debts: LiveData<List<Pair<Debt,Triple<String, String, String>>>> = combine(
        loadedDiner,
        repository.debts,
        repository.individualDebtOwed,
        repository.individualDebtHeld) { diner, debts, debtsOwed, debtsHeld ->

        val debtItems = mutableListOf<Pair<Debt, Triple<String, String, String>>>()

        if (diner != null) {
            val debtsOwedByDiner = debtsOwed[diner]
            val debtsHeldByDiner = debtsHeld[diner]

            debts.forEach {
                if (diner.debtsOwed.contains(it)) {

                    val debtDetails = when {
                        it.creditors.size == 1 && it.debtors.size == 1 -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.dinerdetails_debt_paid,
                                1,
                                currencyFormatter.format(it.amount),
                                it.creditors[0].name)
                        }
                        it.creditors.size == 1 && it.debtors.size > 1 -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.dinerdetails_shared_debt_paid,
                                1,
                                currencyFormatter.format(it.amount),
                                it.creditors[0].name)
                        }
                        it.creditors.size > 1 && it.debtors.size == 1 -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.dinerdetails_debt_paid,
                                it.creditors.size,
                                currencyFormatter.format(it.amount),
                                it.creditors.size)
                        }
                        else -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.dinerdetails_shared_debt_paid,
                                it.creditors.size,
                                currencyFormatter.format(it.amount),
                                it.creditors.size)
                        }
                    }

                    debtItems.add(
                        Pair(
                            it,
                            Triple(
                                it.name,
                                currencyFormatter.format(debtsOwedByDiner?.get(it) ?: 0.0),
                                debtDetails
                            )
                        )
                    )
                } else if (diner.debtsHeld.contains(it)) {
                    val debtDetails = when {
                        it.creditors.size == 1 && it.debtors.size == 1 -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.dinerdetails_debt_received,
                                1,
                                currencyFormatter.format(it.amount),
                                it.debtors[0].name)
                        }
                        it.creditors.size > 1 && it.debtors.size == 1 -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.dinerdetails_shared_debt_received,
                                1,
                                currencyFormatter.format(it.amount),
                                it.debtors[0].name)
                        }
                        it.creditors.size == 1 && it.debtors.size > 1 -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.dinerdetails_debt_received,
                                it.debtors.size,
                                currencyFormatter.format(it.amount),
                                it.debtors.size)
                        }
                        else -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.dinerdetails_shared_debt_received,
                                it.debtors.size,
                                currencyFormatter.format(it.amount),
                                it.debtors.size)
                        }
                    }

                    debtItems.add(
                        Pair(
                            it,
                            Triple(
                                it.name,
                                currencyFormatter.format(
                                    debtsHeldByDiner?.get(it)?.let { -it } ?: 0.0
                                ),
                                debtDetails
                            )
                        )
                    )
                }
            }
        }

        debtItems
    }.asLiveData()
    val debtTotal: Flow<Double> = combine(
        loadedDiner,
        reimbursementsTotal,
        repository.individualDebtOwed,
        repository.individualDebtHeld) { diner, reimbursementsTotal, debtsOwed, debtsHeld ->
        reimbursementsTotal + (debtsOwed[diner]?.values?.sum() ?: 0.0) - (debtsHeld[diner]?.values?.sum() ?: 0.0)
    }
    val debtTotalTitleString: LiveData<String> = debtTotal.mapLatest { if (it >= 0.0) totalOwedToOthersString else totalOwedByOthersString }.asLiveData()
    val debtTotalString: LiveData<String> = debtTotal.mapLatest { currencyFormatter.format(abs(it)) }.asLiveData()

    private fun getDiscountDescription(discount: Discount, discountValue: Double, numItems: Int, numDiners: Int): String {
        return if (discount.onItems) {
            if (discount.asPercent) {
                val percentValue = percentFormatter.format(0.01 * discount.value)
                when (discount.items.size) {
                    1 -> {
                        discount.items[0].name.let {
                            getApplication<Application>().resources.getString(
                                R.string.discounts_onitems_percent_single,
                                percentValue,
                                it
                            )
                        }
                    }
                    numItems -> {
                        getApplication<Application>().resources.getString(
                            R.string.discounts_onitems_percent_all,
                            percentValue
                        )
                    }
                    else -> {
                        getApplication<Application>().resources.getQuantityString(
                            R.plurals.discounts_onitems_percent_multiple,
                            discount.itemIds.size,
                            discount.itemIds.size,
                            percentValue
                        )
                    }
                }
            } else {
                discountValue.let { currencyFormatter.format(it) }.let { discountValueString ->
                    when (discount.itemIds.size) {
                        1 -> {
                            discount.items[0].name.let {
                                getApplication<Application>().resources.getString(
                                    R.string.dinerdetails_discount_onitems_currency_single,
                                    discountValueString,
                                    it
                                )
                            }
                        }
                        numItems -> {
                            getApplication<Application>().resources.getString(
                                R.string.dinerdetails_discount_onitems_currency_all,
                                discountValueString
                            )
                        }
                        else -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.dinerdetails_discount_onitems_currency_multiple,
                                discount.itemIds.size,
                                discountValueString,
                                discount.itemIds.size
                            )
                        }
                    }
                }
            }
        } else {
            if (discount.asPercent) {
                val percentValue = percentFormatter.format(0.01 * discount.value)
                when (discount.recipientIds.size) {
                    1 -> {
                        discount.recipients[0].name.let {
                            getApplication<Application>().resources.getString(
                                R.string.discounts_fordiners_percent_single,
                                percentValue,
                                it
                            )
                        }
                    }
                    numItems -> {
                        getApplication<Application>().resources.getString(
                            R.string.discounts_fordiners_percent_all,
                            percentValue
                        )
                    }
                    else -> {
                        getApplication<Application>().resources.getQuantityString(
                            R.plurals.discounts_fordiners_percent_multiple,
                            discount.recipientIds.size,
                            discount.recipientIds.size,
                            percentValue
                        )
                    }
                }
            } else {
                discountValue.let { currencyFormatter.format(it) }.let { discountValueString ->
                    when (discount.recipientIds.size) {
                        1 -> {
                            discount.recipients[0].name.let {
                                getApplication<Application>().resources.getString(
                                    R.string.dinerdetails_discount_fordiners_currency_single,
                                    discountValueString,
                                    it
                                )
                            }
                        }
                        numDiners -> {
                            getApplication<Application>().resources.getString(R.string.dinerdetails_discount_fordiners_currency_all)
                        }
                        else -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.dinerdetails_discount_fordiners_currency_multiple,
                                discount.recipientIds.size,
                                discountValueString,
                                discount.recipientIds.size
                            )
                        }
                    }
                }
            }
        }
    }

    fun initializeForDiner(diner: Diner) {
        unFreezeOutput()

        editingBiographicsMutable.value = false

        loadedDiner.value = diner
    }

    fun editBiographics() {
        editingBiographicsMutable.value = true
    }

    fun handleOnBackPressed(): Boolean {
        return if (editingBiographicsMutable.value) {
            editingBiographicsMutable.value = false
            false
        } else {
            true
        }
    }

    fun removeDebt(debt: Debt) { repository.removeDebt(debt) }
}