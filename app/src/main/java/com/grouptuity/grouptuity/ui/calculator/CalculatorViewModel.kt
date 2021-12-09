package com.grouptuity.grouptuity.ui.calculator

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.Event
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.flow.*


class CalculatorViewModel(app: Application): UIViewModel(app) {

    private val inputLock = MutableStateFlow(false).also { addInputLock(it) }
    val calcData = CalculatorData(CalculationType.TIP_PERCENT, inputLock, isOutputFlowing, autoHideNumberPad = false)

    val toolBarInTertiaryState: LiveData<Boolean> = calcData.acceptButtonEnabled
    val acceptEvents: LiveData<Event<Pair<Double, String>>> = calcData.acceptEvents.asFlow().transformLatest {
        it.consume()?.apply {
            emit(Event(Pair(
                this.toDouble(),
                CalculatorData.formatRawValue(
                    false,
                    this,
                    calcData.isInPercent.value == true))))
        }
    }.asLiveData()

    fun initialize(type: CalculationType) {
        unFreezeOutput()
        calcData.reset(type,null, showNumberPad = true)
    }
}