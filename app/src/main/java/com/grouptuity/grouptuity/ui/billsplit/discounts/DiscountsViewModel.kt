package com.grouptuity.grouptuity.ui.billsplit.discounts

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.GrouptuityApplication
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.BaseUIViewModel
import com.grouptuity.grouptuity.data.entities.Discount
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.text.NumberFormat


class DiscountsViewModel(app: GrouptuityApplication): BaseUIViewModel(app) {
    val discounts: StateFlow<List<Discount>> = repository.discounts
    val diners = repository.diners.asLiveData()
    val items = repository.items.asLiveData()

    // TODO combine should include discount currency values etc. (no lookups in the body)
    val discountData: LiveData<List<Pair<Discount, Triple<String, String, String>>>> = combine(
        discounts,
        repository.diners,
        repository.items) { discounts, diners, items ->

        val currencyFormatter = NumberFormat.getCurrencyInstance()
        val percentFormatter = NumberFormat.getPercentInstance().also {
            it.maximumFractionDigits = resources.getInteger(R.integer.percent_max_decimals)
        }

        val numberOfDiners = diners.size
        val numberOfItems = items.size

        discounts.map { discount ->
            Pair(discount, let {
                val valueString = currencyFormatter.format(discount.currencyValue.value)

                val descriptionString = if(discount.onItemsInput) {
                    val discountItems = discount.items.value

                    if(discount.asPercentInput) {
                        val percentValue = percentFormatter.format(discount.currencyValue.value.movePointLeft(2))
                        when(discountItems.size) {
                            1 -> {
                                discountItems.toList()[0].name.let {
                                    getApplication<Application>().resources.getString(R.string.discounts_onitems_percent_single, percentValue, it)
                                }
                            }
                            numberOfItems -> {
                                getApplication<Application>().resources.getString(R.string.discounts_onitems_percent_all, percentValue)
                            }
                            else -> {
                                getApplication<Application>().resources.getQuantityString(
                                    R.plurals.discounts_onitems_percent_multiple,
                                    discountItems.size,
                                    discountItems.size,
                                    percentValue)
                            }
                        }
                    } else {
                        when(discountItems.size) {
                            1 -> {
                                discountItems.toList()[0].name.let {
                                    getApplication<Application>().resources.getString(R.string.discounts_onitems_currency_single, it)
                                }
                            }
                            numberOfItems -> {
                                getApplication<Application>().resources.getString(R.string.discounts_onitems_currency_all)
                            }
                            else -> {
                                getApplication<Application>().resources.getQuantityString(
                                    R.plurals.discounts_onitems_currency_multiple,
                                    discountItems.size,
                                    discountItems.size)
                            }
                        }
                    }
                } else {
                    val discountRecipients = discount.recipients.value
                    if(discount.asPercentInput) {
                        val percentValue =
                            percentFormatter.format(discount.amount.value.movePointLeft(2))
                        when(discountRecipients.size) {
                            1 -> {
                                discountRecipients.toList()[0].name.let {
                                    getApplication<Application>().resources.getString(R.string.discounts_fordiners_percent_single, percentValue, it)
                                }
                            }
                            numberOfDiners -> {
                                getApplication<Application>().resources.getString(R.string.discounts_fordiners_percent_all, percentValue)
                            }
                            else -> {
                                getApplication<Application>().resources.getQuantityString(
                                    R.plurals.discounts_fordiners_percent_multiple,
                                    discountRecipients.size,
                                    discountRecipients.size,
                                    percentValue)
                            }
                        }
                    } else {
                        when(discountRecipients.size) {
                            1 -> {
                                discountRecipients.toList()[0].name.let {
                                    getApplication<Application>().resources.getString(R.string.discounts_fordiners_currency_single, it)
                                }
                            }
                            numberOfDiners -> {
                                getApplication<Application>().resources.getString(R.string.discounts_fordiners_currency_all)
                            }
                            else -> {
                                getApplication<Application>().resources.getQuantityString(
                                    R.plurals.discounts_fordiners_currency_multiple,
                                    discountRecipients.size,
                                    discountRecipients.size)
                            }
                        }
                    }
                }

                val reimbursementString = if(discount.cost.value.compareTo(BigDecimal.ZERO) == 0) {
                    ""
                } else {
                    val costString = currencyFormatter.format(discount.cost)
                    when(discount.purchasers.value.size) {
                        1 -> {
                            discount.purchasers.value.toList()[0].name.let {
                                getApplication<Application>().resources.getString(R.string.discounts_reimbursement_single, costString, it)
                            }
                        }
                        numberOfDiners -> { getApplication<Application>().resources.getString(R.string.discounts_reimbursement_all, costString) }
                        else -> {
                            getApplication<Application>().resources.getQuantityString(
                                R.plurals.discounts_reimbursement_multiple,
                                discount.purchasers.value.size,
                                discount.purchasers.value.size,
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
