package com.grouptuity.grouptuity.ui.billsplit.dinerdetails

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.GrouptuityApplication
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.*
import com.grouptuity.grouptuity.data.entities.*
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.text.NumberFormat
import kotlin.math.abs

class DinerDetailsViewModel(app: Application): UIViewModel<String, Diner>(app) {
    private val currencyFormatter = NumberFormat.getCurrencyInstance()
    private val percentFormatter = NumberFormat.getPercentInstance().also {
        it.maximumFractionDigits = getApplication<Application>().resources.getInteger(R.integer.percent_max_decimals)
    }
    private val totalOwedToOthersString = app.resources.getString(R.string.dinerdetails_debts_total_owed_to_others)
    private val totalOwedByOthersString = app.resources.getString(R.string.dinerdetails_debts_total_owed_by_others)

    val loadedDiner = MutableStateFlow<Diner?>(null)
    val dinerEmailAddresses: LiveData<List<String>> = loadedDiner.map {
        it?.emailAddresses ?: emptyList()
    }.asLiveData()

    val toolbarTitle = loadedDiner.map { it?.name ?: "" }.asLiveData()

    private val editingBiographicsMutable = MutableStateFlow(false)
    val editingBiographics: LiveData<Boolean> = editingBiographicsMutable.asLiveData()

    private val emailAddressNotSet = context.getString(R.string.dinerdetails_biographics_address_not_set, context.getString(
        PaymentMethod.PAYBACK_LATER.addressNameStringId))
    private val venmoAddressNotSet = context.getString(R.string.dinerdetails_biographics_address_not_set, context.getString(PaymentMethod.VENMO.addressNameStringId))
    private val cashtagNotSet = context.getString(R.string.dinerdetails_biographics_address_not_set, context.getString(PaymentMethod.CASH_APP.addressNameStringId))
    private val algoAddressNotSet = context.getString(R.string.dinerdetails_biographics_address_not_set, context.getString(PaymentMethod.ALGO.addressNameStringId))

    val email: LiveData<Triple<Boolean, String, Boolean>> = combine(loadedDiner, editingBiographicsMutable) { diner, editing ->
        val address = diner?.addresses?.get(PaymentMethod.PAYBACK_LATER) ?: emailAddressNotSet
        Triple(true, address, editing)
    }.asLiveData()
    val venmoAddress: LiveData<Triple<Boolean, String, Boolean>> = combine(loadedDiner, editingBiographicsMutable) { diner, editing ->
        val address = diner?.addresses?.get(PaymentMethod.VENMO) ?: venmoAddressNotSet
        Triple(true, address, editing)
    }.asLiveData()
    val cashtag: LiveData<Triple<Boolean, String, Boolean>> = combine(loadedDiner, editingBiographicsMutable) { diner, editing ->
        val address = diner?.addresses?.get(PaymentMethod.CASH_APP) ?: cashtagNotSet
        Triple(true, address, editing)
    }.asLiveData()
    val algorandAddress: LiveData<Triple<Boolean, String, Boolean>> = combine(loadedDiner, editingBiographicsMutable) { diner, editing ->
        val address = diner?.addresses?.get(PaymentMethod.ALGO) ?: algoAddressNotSet
        Triple(true, address, editing)
    }.asLiveData()

    val items: LiveData<List<Pair<Item, Triple<String, String, String?>>>> = loadedDiner.map {
        it?.items?.value?.map { item ->
            val numDinersOnItem = item.diners.value.size
            Pair(
                item,
                Triple(
                    item.name,
                    currencyFormatter.format(item.dinerPriceShare.value),
                    if (numDinersOnItem > 1) {
                        getApplication<Application>().resources.getQuantityString(
                            R.plurals.dinerdetails_item_split,
                            numDinersOnItem,
                            numDinersOnItem,
                            currencyFormatter.format(item.price)
                        )
                    } else{
                        null
                    }
                )
            )
        } ?: emptyList<Pair<Item, Triple<String, String, String?>>>()
    }.asLiveData()
    val enableItemExpansion: LiveData<Boolean> = loadedDiner.flatMapLatest {
        it?.items ?: flow { emptyList<Item>() }
    }.map { it.isNotEmpty() }.asLiveData()
    val subtotalString: LiveData<String> = loadedDiner.map {
        currencyFormatter.format(it?.displayedSubtotal ?: BigDecimal.ZERO)
    }.asLiveData()

    val discounts: LiveData<List<Pair<Discount,Triple<String, String, String?>>>> =
        combine(
            loadedDiner,
            repository.numberOfDiners,
            repository.numberOfItems
        ) { diner, numDiners, numItems ->
            emptyList<Pair<Discount,Triple<String, String, String?>>>()
            // TODO
//            diner.
//            discountShares[diner]?.keys?.mapNotNull { discount ->
//
//                val discountShare = discountShares[diner]?.get(discount)?.let { currencyFormatter.format(it) }
//
//                val descriptionString = getDiscountDescription(
//                    discount,
//                    discountValues.getOrDefault(discount, 0.0),
//                    numItems,
//                    numDiners)
//
//                val sharingString = if (discount.onItems) {
//                    val recipients = mutableSetOf<String>().also { recipientSet ->
//                        discount.items.forEach { recipientSet.addAll(it.dinerIds) }
//                    }
//
//                    when(recipients.size) {
//                        1 -> {
//                            null
//                        }
//                        2 -> {
//                            val loadedDinerId = loadedDiner.value?.id
//                            val otherRecipient = recipients.filter {
//                                it != loadedDinerId
//                            }.map { recipientId ->
//                                repository.diners.value.find { it.id == recipientId }
//                            }
//
//                            if (otherRecipient.size == 1) {
//                                getApplication<Application>().resources.getString(
//                                    R.string.dinerdetails_discount_item_sharing_two,
//                                    otherRecipient[0]?.name ?: "1")
//                            } else {
//                                getApplication<Application>().resources.getQuantityString(
//                                    R.plurals.dinerdetails_discount_item_sharing_many,
//                                    1,
//                                    1)
//                            }
//                        }
//                        numDiners -> {
//                            getApplication<Application>().resources.getString(R.string.dinerdetails_discount_item_sharing_all)
//                        }
//                        else -> {
//                            getApplication<Application>().resources.getQuantityString(
//                                R.plurals.dinerdetails_discount_item_sharing_many,
//                                recipients.size-1,
//                                recipients.size-1)
//                        }
//                    }
//                } else {
//                    null
//                }
//
//                if (discountShare !== null) {
//                    Pair(discount, Triple(descriptionString, discountShare, sharingString))
//                } else {
//                    null
//                }
//            } ?: emptyList()
        }.asLiveData()
    val enableDiscountExpansion: LiveData<Boolean> = MutableLiveData(false)
        //combine(
//        loadedDiner,
//
//        ) { diner, discountShares, discountsBorrowed ->
//        diner != null && (!discountShares[diner].isNullOrEmpty() || discountsBorrowed.getOrDefault(diner, 0.0) > PRECISION)
//    }.asLiveData()
    val unusedDiscountString: LiveData<String?> = MutableLiveData("tbd")
//        unusedDiscountAmount.mapLatest { it?.let {
//        currencyFormatter.format(-it) } }.asLiveData()
    val borrowedDiscountString: LiveData<String?> =  MutableLiveData("tbd")
        //borrowedDiscountAmount.mapLatest { it?.let { currencyFormatter.format(it) } }.asLiveData()
    val discountsTotalString: LiveData<String> =  MutableLiveData("tbd")
//        combine(loadedDiner, unusedDiscountAmount, borrowedDiscountAmount, repository.individualDiscountTotals) { loadedDiner, unused, borrowed, rawDiscounts ->
//            val rawDiscount = rawDiscounts[loadedDiner] ?: 0.0
//            val unusedDiscount = unused ?: 0.0
//            val borrowedDiscount = borrowed ?: 0.0
//            currencyFormatter.format(rawDiscount - unusedDiscount + borrowedDiscount)
//        }.asLiveData()

    val afterDiscountsTotalString: LiveData<String> =  MutableLiveData("tbd")
//        combine(loadedDiner, repository.individualSubtotalsWithDiscounts) { loadedDiner, amounts ->
//        currencyFormatter.format(loadedDiner?.let { amounts.getOrDefault(it, 0.0) } ?: 0.0)
//    }.asLiveData()
    val taxPercentString: LiveData<String> =  MutableLiveData("tbd")
//        repository.taxPercent.mapLatest { percentFormatter.format(0.01*it) }.asLiveData()
    val taxAmountString: LiveData<String> = MutableLiveData("tbd")
//        combine(loadedDiner, repository.individualTax) { loadedDiner, amounts ->
//        currencyFormatter.format(loadedDiner?.let { amounts.getOrDefault(it, 0.0) } ?: 0.0)
//    }.asLiveData()
    val afterTaxTotalString: LiveData<String> = MutableLiveData("tbd")
//        combine(loadedDiner, repository.individualSubtotalWithDiscountsAndTax) { loadedDiner, amounts ->
//        currencyFormatter.format(loadedDiner?.let { amounts.getOrDefault(it, 0.0) } ?: 0.0)
//    }.asLiveData()
    val tipPercentString: LiveData<String> = MutableLiveData("tbd")
//    repository.tipPercent.mapLatest { percentFormatter.format(0.01*it) }.asLiveData()
    val tipAmountString: LiveData<String> = MutableLiveData("tbd")
//        combine(loadedDiner, repository.individualTip) { loadedDiner, amounts ->
//        currencyFormatter.format(loadedDiner?.let { amounts.getOrDefault(it, 0.0) } ?: 0.0)
//    }.asLiveData()
    val totalString: LiveData<String> =  MutableLiveData("tbd")
//    combine(loadedDiner, repository.individualTotal) { loadedDiner, amounts ->
//        currencyFormatter.format(loadedDiner?.let { amounts.getOrDefault(it, 0.0) } ?: 0.0)
//    }.asLiveData()

    val reimbursements: LiveData<List<Triple<String, String, String>>> =  MutableLiveData(emptyList())
//    combine(
//        loadedDiner,
//        ) { input ->
//
//        val reimbursements = mutableListOf<Triple<String, String, String>>()
//
//        val diner = input[0]
//        if (diner != null) {
//            val debts = input[1] as Map<Diner, Map<Discount, Double>>
//            val credits = input[2] as Map<Diner, Map<Discount, Double>>
//            val numItems = input[3] as Int
//            val numDiners = input[4] as Int
//            val discountValues = input[5] as Map<Discount, Double>
//
//            debts[diner]?.forEach { (discount, amount) ->
//                reimbursements.add(
//                    Triple(
//                        getApplication<Application>().resources.getString(R.string.dinerdetails_reimbursement_pay),
//                        currencyFormatter.format(amount),
//                        "(" + getDiscountDescription(
//                            discount,
//                            discountValues[discount] ?: 0.0,
//                            numItems,
//                            numDiners) + ")"
//                    )
//                )
//            }
//
//            credits[diner]?.forEach { (discount, amount) ->
//                reimbursements.add(
//                    Triple(
//                        getApplication<Application>().resources.getString(R.string.dinerdetails_reimbursement_receive),
//                        currencyFormatter.format(-amount),
//                        "(" + getDiscountDescription(
//                            discount,
//                            discountValues[discount] ?: 0.0,
//                            numItems,
//                            numDiners) + ")"
//                    )
//                )
//            }
//        }
//
//        reimbursements
//    }.asLiveData()
    val enableReimbursementExpansion: LiveData<Boolean> = loadedDiner.flatMapLatest { diner ->
        if (diner == null) {
            MutableStateFlow(false)
        } else {
            combine(diner.discountsPurchased, diner.discountsWithDebts) { purchased, withDebts ->
                purchased.isNotEmpty() || withDebts.isNotEmpty()
            }
        }
    }.asLiveData()
    val reimbursementsTotalString: LiveData<String> = loadedDiner.flatMapLatest {
        it?.reimbursementTotal?.displayed ?: MutableStateFlow(BigDecimal.ZERO)
    }.map { currencyFormatter.format(it) }.asLiveData()

    val debts: LiveData<List<Pair<Debt,Triple<String, String, String>>>> =
        loadedDiner.map { diner ->
            emptyList<Pair<Debt,Triple<String, String, String>>>()
//            diner.debtsOwed.value union diner.debtsHeld.value.map { debt ->
//                val debtors = debt.debtors.value
//                val creditors = debt.creditors.value
//                val numDebtors = debtors.size
//                val numCreditors = creditors.size
//
//                if (diner in debtors) {
//                    val debtDetails = when {
//                        numCreditors == 1 && numDebtors == 1 -> {
//                            getApplication<Application>().resources.getQuantityString(
//                                R.plurals.dinerdetails_debt_paid,
//                                1,
//                                currencyFormatter.format(debt.amount),
//                                creditors.first().name)
//                        }
//                        numCreditors == 1 && numDebtors > 1 -> {
//                            getApplication<Application>().resources.getQuantityString(
//                                R.plurals.dinerdetails_shared_debt_paid,
//                                1,
//                                currencyFormatter.format(debt.amount),
//                                creditors.first().name)
//                        }
//                        numCreditors > 1 && numDebtors == 1 -> {
//                            getApplication<Application>().resources.getQuantityString(
//                                R.plurals.dinerdetails_debt_paid,
//                                numCreditors,
//                                currencyFormatter.format(debt.amount),
//                                numCreditors)
//                        }
//                        else -> {
//                            getApplication<Application>().resources.getQuantityString(
//                                R.plurals.dinerdetails_shared_debt_paid,
//                                numCreditors,
//                                currencyFormatter.format(debt.amount),
//                                numCreditors)
//                        }
//                    }
//
//                    Pair(
//                        it,
//                        Triple(
//                            it.name,
//                            currencyFormatter.format(debtsOwedByDiner?.get(it) ?: 0.0),
//                            debtDetails
//                        )
//                    )
//                } else if (diner.debtsHeld.value.contains(it)) {
//                    val debtDetails = when {
//                        numCreditors == 1 && numDebtors == 1 -> {
//                            getApplication<Application>().resources.getQuantityString(
//                                R.plurals.dinerdetails_debt_received,
//                                1,
//                                currencyFormatter.format(it.amount),
//                                debtors.first().name)
//                        }
//                        numCreditors > 1 && numDebtors == 1 -> {
//                            getApplication<Application>().resources.getQuantityString(
//                                R.plurals.dinerdetails_shared_debt_received,
//                                1,
//                                currencyFormatter.format(it.amount),
//                                debtors.first().name)
//                        }
//                        numCreditors == 1 && numDebtors > 1 -> {
//                            getApplication<Application>().resources.getQuantityString(
//                                R.plurals.dinerdetails_debt_received,
//                                numDebtors,
//                                currencyFormatter.format(it.amount),
//                                numDebtors)
//                        }
//                        else -> {
//                            getApplication<Application>().resources.getQuantityString(
//                                R.plurals.dinerdetails_shared_debt_received,
//                                numDebtors,
//                                currencyFormatter.format(it.amount),
//                                numDebtors)
//                        }
//                    }
//
//                    debtItems.add(
//                        Pair(
//                            it,
//                            Triple(
//                                it.name,
//                                currencyFormatter.format(
//                                    debtsHeldByDiner?.get(it)?.let { -it } ?: 0.0
//                                ),
//                                debtDetails
//                            )
//                        )
//                    )
//                }
//            }
        }.asLiveData()

    private val debtTotal: Flow<BigDecimal> = loadedDiner.flatMapLatest {
        it?.standaloneDebtsTotal?.displayed ?: MutableStateFlow(BigDecimal.ZERO)
    }
    val debtTotalTitleString: LiveData<String> = debtTotal.map {
        if (it >= BigDecimal.ZERO) totalOwedToOthersString else totalOwedByOthersString
    }.asLiveData()
    val debtTotalString: LiveData<String> = debtTotal.map { currencyFormatter.format(it.abs()) }.asLiveData()

    private fun getDiscountDescription(discount: Discount, discountValue: Double, numItems: Int, numDiners: Int): String {
        return "tbd"
        // TODO
//        return if (discount.onItemsInput) {
//            if (discount.asPercentInput) {
//                val percentValue = percentFormatter.format(0.01 * discount.amount.value.multi)
//                when (discount.items.size) {
//                    1 -> {
//                        discount.items[0].name.let {
//                            getApplication<Application>().resources.getString(
//                                R.string.discounts_onitems_percent_single,
//                                percentValue,
//                                it
//                            )
//                        }
//                    }
//                    numItems -> {
//                        getApplication<Application>().resources.getString(
//                            R.string.discounts_onitems_percent_all,
//                            percentValue
//                        )
//                    }
//                    else -> {
//                        getApplication<Application>().resources.getQuantityString(
//                            R.plurals.discounts_onitems_percent_multiple,
//                            discount.itemIds.size,
//                            discount.itemIds.size,
//                            percentValue
//                        )
//                    }
//                }
//            } else {
//                discountValue.let { currencyFormatter.format(it) }.let { discountValueString ->
//                    when (discount.itemIds.size) {
//                        1 -> {
//                            discount.items[0].name.let {
//                                getApplication<Application>().resources.getString(
//                                    R.string.dinerdetails_discount_onitems_currency_single,
//                                    discountValueString,
//                                    it
//                                )
//                            }
//                        }
//                        numItems -> {
//                            getApplication<Application>().resources.getString(
//                                R.string.dinerdetails_discount_onitems_currency_all,
//                                discountValueString
//                            )
//                        }
//                        else -> {
//                            getApplication<Application>().resources.getQuantityString(
//                                R.plurals.dinerdetails_discount_onitems_currency_multiple,
//                                discount.itemIds.size,
//                                discountValueString,
//                                discount.itemIds.size
//                            )
//                        }
//                    }
//                }
//            }
//        } else {
//            if (discount.asPercent) {
//                val percentValue = percentFormatter.format(0.01 * discount.value)
//                when (discount.recipientIds.size) {
//                    1 -> {
//                        discount.recipients[0].name.let {
//                            getApplication<Application>().resources.getString(
//                                R.string.discounts_fordiners_percent_single,
//                                percentValue,
//                                it
//                            )
//                        }
//                    }
//                    numItems -> {
//                        getApplication<Application>().resources.getString(
//                            R.string.discounts_fordiners_percent_all,
//                            percentValue
//                        )
//                    }
//                    else -> {
//                        getApplication<Application>().resources.getQuantityString(
//                            R.plurals.discounts_fordiners_percent_multiple,
//                            discount.recipientIds.size,
//                            discount.recipientIds.size,
//                            percentValue
//                        )
//                    }
//                }
//            } else {
//                discountValue.let { currencyFormatter.format(it) }.let { discountValueString ->
//                    when (discount.recipientIds.size) {
//                        1 -> {
//                            discount.recipients[0].name.let {
//                                getApplication<Application>().resources.getString(
//                                    R.string.dinerdetails_discount_fordiners_currency_single,
//                                    discountValueString,
//                                    it
//                                )
//                            }
//                        }
//                        numDiners -> {
//                            getApplication<Application>().resources.getString(R.string.dinerdetails_discount_fordiners_currency_all)
//                        }
//                        else -> {
//                            getApplication<Application>().resources.getQuantityString(
//                                R.plurals.dinerdetails_discount_fordiners_currency_multiple,
//                                discount.recipientIds.size,
//                                discountValueString,
//                                discount.recipientIds.size
//                            )
//                        }
//                    }
//                }
//            }
//        }
    }

    override fun onInitialize(input: String) {
        editingBiographicsMutable.value = false
        loadedDiner.value = repository.getDiner(input)!!
    }

    override fun handleOnBackPressed() {
        if (editingBiographicsMutable.value) {
            editingBiographicsMutable.value = false
        } else {
            loadedDiner.value?.let { finishFragment(it) }
        }
    }

    fun editBiographics() {
        editingBiographicsMutable.value = true
    }

    fun removeDebt(debt: Debt) { repository.removeDebt(debt) }
}