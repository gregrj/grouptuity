package com.grouptuity.grouptuity.ui.billsplit.diners

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.data.UIViewModel
import kotlinx.coroutines.flow.combine
import java.text.NumberFormat

class DinersViewModel(application: Application): UIViewModel(application) {
    private val formatter = NumberFormat.getCurrencyInstance()

    // Diners paired with their individual subtotals as currency strings
    val dinerData: LiveData<List<Pair<Diner, String>>> = combine(repository.diners, repository.individualSubtotals) { diners, subtotals ->
        diners.map { diner ->
            diner to formatter.format(subtotals.getOrDefault(diner, 0.0))
        }
    }.asLiveData()

    fun removeDiner(diner: Diner) { repository.removeDiner(diner) }

    fun addSelfToBill() { repository.addSelfAsDiner() }
}