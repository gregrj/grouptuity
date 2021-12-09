package com.grouptuity.grouptuity.ui.util.views

import android.annotation.SuppressLint
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.grouptuity.grouptuity.data.CalculatorData


fun setupCollapsibleNumberPad(viewLifecycleOwner: androidx.lifecycle.LifecycleOwner,
                              data: CalculatorData,
                              numberPad: com.grouptuity.grouptuity.databinding.NumberPadCollapsibleBinding,
                              useValuePlaceholder: Boolean=true,
                              showBasisToggleButtons: Boolean=true) {

    // If layout sets correct calculator height without needing placeholder for the value TextView,
    // then valuePlaceholder can be hidden
    numberPad.displayPlaceholder.visibility = if (useValuePlaceholder) View.INVISIBLE else View.GONE

    // If only one basis mode will be used, then the basis toggle buttons can be omitted
    if (showBasisToggleButtons) {
        numberPad.basisToggleButtons.visibility = View.INVISIBLE

        data.isInPercent.observe(viewLifecycleOwner) {
            numberPad.buttonCurrency.isEnabled = it
            numberPad.buttonPercent.isEnabled = !it
        }

        numberPad.buttonCurrency.setOnClickListener { data.switchToCurrency() }
        numberPad.buttonPercent.setOnClickListener { data.switchToPercent() }
    } else {
        numberPad.basisToggleButtons.visibility = View.GONE
    }

    // Observer for expanding and collapsing the number pad
    data.isNumberPadVisible.observe(viewLifecycleOwner, { visible ->
        BottomSheetBehavior.from(numberPad.container).state =
            if (visible)
                BottomSheetBehavior.STATE_EXPANDED
            else
                BottomSheetBehavior.STATE_COLLAPSED
    })

    // Observers for button states
    data.decimalButtonEnabled.observe(viewLifecycleOwner) { numberPad.buttonDecimal.isEnabled = it }
    data.zeroButtonEnabled.observe(viewLifecycleOwner) { numberPad.button0.isEnabled = it }
    data.nonZeroButtonsEnabled.observe(viewLifecycleOwner) {
        numberPad.button1.isEnabled = it
        numberPad.button2.isEnabled = it
        numberPad.button3.isEnabled = it
        numberPad.button4.isEnabled = it
        numberPad.button5.isEnabled = it
        numberPad.button6.isEnabled = it
        numberPad.button7.isEnabled = it
        numberPad.button8.isEnabled = it
        numberPad.button9.isEnabled = it
    }
    data.acceptButtonEnabled.observe(viewLifecycleOwner) {
        if(it) {
            numberPad.buttonAccept.show()
        } else {
            numberPad.buttonAccept.hide()
        }
    }

    // Button click listeners
    numberPad.button0.setOnClickListener { data.addDigit('0') }
    numberPad.button1.setOnClickListener { data.addDigit('1') }
    numberPad.button2.setOnClickListener { data.addDigit('2') }
    numberPad.button3.setOnClickListener { data.addDigit('3') }
    numberPad.button4.setOnClickListener { data.addDigit('4') }
    numberPad.button5.setOnClickListener { data.addDigit('5') }
    numberPad.button6.setOnClickListener { data.addDigit('6') }
    numberPad.button7.setOnClickListener { data.addDigit('7') }
    numberPad.button8.setOnClickListener { data.addDigit('8') }
    numberPad.button9.setOnClickListener { data.addDigit('9') }
    numberPad.buttonDecimal.setOnClickListener { data.addDecimal() }
    numberPad.buttonAccept.setOnClickListener { data.tryAcceptValue() }

    // Prevent number pad from being dragged and block input while in motion
    with(BottomSheetBehavior.from(numberPad.container)) {
        isDraggable = false

        addBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback(){
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                /* Disable input lock when sheet is nearly settled. State change updates have an
                   apparent delay that blocks resumption of user input for too long after the sheet
                   has settled visually. */
                if(slideOffset <= 0.05f) {
                    data.inputLock.value = false
                } else if(slideOffset >= 0.95) {
                    data.inputLock.value = false
                }
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> { data.inputLock.value = false }
                    BottomSheetBehavior.STATE_COLLAPSED -> { data.inputLock.value = false }
                    else -> { data.inputLock.value = true }
                }
            }
        })
    }
}


fun setupFixedNumberPad(viewLifecycleOwner: androidx.lifecycle.LifecycleOwner,
                        data: CalculatorData,
                        numberPad: com.grouptuity.grouptuity.databinding.NumberPadFixedBinding) {

    // Observer for the displayed value
    data.displayValue.observe(viewLifecycleOwner) {
            value: String -> numberPad.displayTextview.text = value
    }

    // Observers for button states
    data.decimalButtonEnabled.observe(viewLifecycleOwner) { numberPad.buttonDecimal.isEnabled = it }
    data.zeroButtonEnabled.observe(viewLifecycleOwner) { numberPad.button0.isEnabled = it }
    data.nonZeroButtonsEnabled.observe(viewLifecycleOwner) {
        numberPad.button1.isEnabled = it
        numberPad.button2.isEnabled = it
        numberPad.button3.isEnabled = it
        numberPad.button4.isEnabled = it
        numberPad.button5.isEnabled = it
        numberPad.button6.isEnabled = it
        numberPad.button7.isEnabled = it
        numberPad.button8.isEnabled = it
        numberPad.button9.isEnabled = it
    }
    data.acceptButtonEnabled.observe(viewLifecycleOwner) {
        if(it) {
            numberPad.buttonAccept.show()
        } else {
            numberPad.buttonAccept.hide()
        }
    }

    // Button click listeners
    numberPad.button0.setOnClickListener { data.addDigit('0') }
    numberPad.button1.setOnClickListener { data.addDigit('1') }
    numberPad.button2.setOnClickListener { data.addDigit('2') }
    numberPad.button3.setOnClickListener { data.addDigit('3') }
    numberPad.button4.setOnClickListener { data.addDigit('4') }
    numberPad.button5.setOnClickListener { data.addDigit('5') }
    numberPad.button6.setOnClickListener { data.addDigit('6') }
    numberPad.button7.setOnClickListener { data.addDigit('7') }
    numberPad.button8.setOnClickListener { data.addDigit('8') }
    numberPad.button9.setOnClickListener { data.addDigit('9') }
    numberPad.buttonDecimal.setOnClickListener { data.addDecimal() }
    numberPad.buttonAccept.setOnClickListener { data.tryAcceptValue() }

    // Observers and listeners for the backspace/clear button
    data.backspaceButtonVisible.observe(viewLifecycleOwner) { numberPad.buttonBackspace.visibility = if(it) View.VISIBLE else View.GONE }
    numberPad.buttonBackspace.setOnClickListener { data.removeDigit() }
    numberPad.buttonBackspace.setOnLongClickListener {
        data.clearValue()
        true
    }
}


@SuppressLint("ClickableViewAccessibility")
fun setupCalculatorDisplay(viewLifecycleOwner: androidx.lifecycle.LifecycleOwner,
                           data: CalculatorData,
                           valueTextView: TextView,
                           editButton: ImageView,
                           backSpaceButton: ImageButton) {

    // Observer for the displayed value
    data.displayValue.observe(viewLifecycleOwner) { value: String -> valueTextView.text = value }

    // Observer and listener for the edit button
    data.editButtonVisible.observe(viewLifecycleOwner) { editButton.visibility = if(it) View.VISIBLE else View.GONE }

    // Observers and listeners for the backspace/clear button
    data.backspaceButtonVisible.observe(viewLifecycleOwner) { backSpaceButton.visibility = if(it) View.VISIBLE else View.GONE }
    backSpaceButton.setOnClickListener { data.removeDigit() }
    backSpaceButton.setOnLongClickListener {
        data.clearValue()
        true
    }

    // Allow user to open the calculator by clicking the ValueTextView if closed
    data.isNumberPadVisible.observe(viewLifecycleOwner, {
        if(it) {
            valueTextView.setOnClickListener(null)
            valueTextView.setOnTouchListener { _, _ -> true }

        } else {
            valueTextView.setOnClickListener { data.openCalculator() }
            valueTextView.setOnTouchListener(null)
        }
    })
}