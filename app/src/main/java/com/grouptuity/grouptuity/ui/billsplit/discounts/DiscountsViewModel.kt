package com.grouptuity.grouptuity.ui.billsplit.discounts

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Discount
import com.grouptuity.grouptuity.data.UIViewModel
import com.grouptuity.grouptuity.data.getDiscountCurrencyValue
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.text.NumberFormat


class DiscountsViewModel(app: Application): UIViewModel(app) {
    val discounts: StateFlow<List<Discount>> = repository.discounts
    val diners = repository.diners.asLiveData()
    val items = repository.items.asLiveData()

    val discountData: LiveData<List<Pair<Discount, Triple<String, String, String>>>> = combine(
        discounts,
        repository.diners,
        repository.items,
        repository.individualSubtotals) { discounts, diners, items, subtotals ->

        val currencyFormatter = NumberFormat.getCurrencyInstance()
        val percentFormatter = NumberFormat.getPercentInstance().also {
            it.maximumFractionDigits = getApplication<Application>().resources.getInteger(R.integer.percent_max_decimals)
        }

        val numberOfDiners = diners.size
        val numberOfItems = items.size

        discounts.map { discount ->
            Pair(discount, let {
                val valueString = currencyFormatter.format(getDiscountCurrencyValue(discount, subtotals))

                val descriptionString = if(discount.onItems) {
                    if(discount.asPercent) {
                        val percentValue = percentFormatter.format(0.01 * discount.value)
                        when(discount.items.size) {
                            1 -> {
                                discount.items[0].name.let {
                                    getApplication<Application>().resources.getString(R.string.discounts_onitems_percent_single, percentValue, it)
                                }
                            }
                            numberOfItems -> {
                                getApplication<Application>().resources.getString(R.string.discounts_onitems_percent_all, percentValue)
                            }
                            else -> {
                                getApplication<Application>().resources.getQuantityString(
                                    R.plurals.discounts_onitems_percent_multiple,
                                    discount.itemIds.size,
                                    discount.itemIds.size,
                                    percentValue)
                            }
                        }
                    } else {
                        when(discount.itemIds.size) {
                            1 -> {
                                discount.items[0].name.let {
                                    getApplication<Application>().resources.getString(R.string.discounts_onitems_currency_single, it)
                                }
                            }
                            numberOfItems -> {
                                getApplication<Application>().resources.getString(R.string.discounts_onitems_currency_all)
                            }
                            else -> {
                                getApplication<Application>().resources.getQuantityString(
                                    R.plurals.discounts_onitems_currency_multiple,
                                    discount.itemIds.size,
                                    discount.itemIds.size)
                            }
                        }
                    }
                } else {
                    if(discount.asPercent) {
                        val percentValue = percentFormatter.format(0.01 * discount.value)
                        when(discount.recipientIds.size) {
                            1 -> {
                                discount.recipients[0].name.let {
                                    getApplication<Application>().resources.getString(R.string.discounts_fordiners_percent_single, percentValue, it)
                                }
                            }
                            numberOfDiners -> {
                                getApplication<Application>().resources.getString(R.string.discounts_fordiners_percent_all, percentValue)
                            }
                            else -> {
                                getApplication<Application>().resources.getQuantityString(
                                    R.plurals.discounts_fordiners_percent_multiple,
                                    discount.recipientIds.size,
                                    discount.recipientIds.size,
                                    percentValue)
                            }
                        }
                    } else {
                        when(discount.recipientIds.size) {
                            1 -> {
                                discount.recipients[0].name.let {
                                    getApplication<Application>().resources.getString(R.string.discounts_fordiners_currency_single, it)
                                }
                            }
                            numberOfDiners -> {
                                getApplication<Application>().resources.getString(R.string.discounts_fordiners_currency_all)
                            }
                            else -> {
                                getApplication<Application>().resources.getQuantityString(
                                    R.plurals.discounts_fordiners_currency_multiple,
                                    discount.recipientIds.size,
                                    discount.recipientIds.size)
                            }
                        }
                    }
                }

                val reimbursementString = if(discount.cost == null) {
                    ""
                } else {
                    val costString = currencyFormatter.format(discount.cost)
                    when(discount.purchaserIds.size) {
                        1 -> {
                            discount.purchasers[0].name.let {
                                getApplication<Application>().resources.getString(R.string.discounts_reimbursement_single, costString, it)
                            }
                        }
                        numberOfDiners -> { getApplication<Application>().resources.getString(R.string.discounts_reimbursement_all, costString) }
                        else -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.discounts_reimbursement_multiple,
                                discount.purchaserIds.size,
                                discount.purchaserIds.size,
                                costString)
                        }
                    }
                }

                Triple(valueString, descriptionString, reimbursementString)
            })
        }
    }.asLiveData()

    fun removeDiscount(discount: Discount) = repository.removeDiscount(discount)
}