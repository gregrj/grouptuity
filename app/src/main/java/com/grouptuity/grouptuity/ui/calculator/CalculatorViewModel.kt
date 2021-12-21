package com.grouptuity.grouptuity.ui.calculator

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.grouptuity.grouptuity.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


class CalculatorViewModel(app: Application): UIViewModel<CalculationType, Pair<Double, String>?>(app) {

    private val calcData = CalculatorData(CalculationType.TIP_PERCENT, autoHideNumberPad = false) {
        // Freeze UI for number pad will update before exit transition starts
        notifyTransitionStarted()
    }
    val calculator = CalculatorImpl(this, calcData)

    val toolBarInTertiaryState: LiveData<Boolean> = calculator.acceptButtonEnabled

    init {
        viewModelScope.launch {
            calcData.acceptEvents.collect {
                it.consume()?.apply {
                    finishFragment(
                        Pair(
                            this.value.toDouble(),
                            CalculatorData.formatRawValue(
                                false,
                                this.value,
                                calcData.isInPercent.value
                            )
                        )
                    )
                }
            }
        }
    }


    override fun onInitialize(input: CalculationType) {
        calcData.reset(input,null, showNumberPad = true)
    }

    override fun handleOnBackPressed() { finishFragment(null) }
}