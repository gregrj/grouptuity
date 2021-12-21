package com.grouptuity.grouptuity.ui.util

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.BaseUIViewModel
import com.grouptuity.grouptuity.data.UIViewModel
import com.grouptuity.grouptuity.ui.util.views.LockableFrameLayout
import com.grouptuity.grouptuity.ui.util.views.setNullOnDestroy


abstract class UIFragment<VB: ViewBinding, VM: UIViewModel<I ,O>, I, O>: Fragment() {
    protected var binding by setNullOnDestroy<VB>()
        private set
    protected lateinit var viewModel: VM
        private set
    private lateinit var backPressedCallback: OnBackPressedCallback

    @MainThread
    abstract fun inflate(inflater: LayoutInflater, container: ViewGroup?): VB

    abstract fun createViewModel(): VM

    abstract fun getInitialInput(): I

    @MainThread
    open fun onFinish(output: O) {
        // Close fragment using default onBackPressed behavior
        requireActivity().onBackPressed()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = createViewModel()
        viewModel.initialize(getInitialInput())

        binding = inflate(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // If covered_fragment view exists, set the activity's stored bitmap as the image
        view.findViewById<ImageView>(R.id.covered_fragment)?.setImageBitmap(MainActivity.storedViewBitmap)

        // Intercept user interactions while while fragment transitions and animations are running
        view.findViewById<LockableFrameLayout>(R.id.root_layout)?.attachLock(viewModel.isInputLocked)

        // Intercept back pressed events to allow fragment-specific behaviors
        backPressedCallback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!viewModel.isInputLocked.value)
                    viewModel.handleOnBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        viewModel.finishFragmentEvent.observe(viewLifecycleOwner) {
            it.consume()?.apply {
                // Prevent callback from intercepting back pressed events
                backPressedCallback.isEnabled = false

                // Invoke callback method to execute fragment-specific closing tasks
                onFinish(this.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Reset UI input/output locks leftover from aborted transitions/animations
        viewModel.unlockInput()
        viewModel.unlockOutput()
    }
}


abstract class BaseUIFragment<VB: ViewBinding, VM: BaseUIViewModel>: UIFragment<VB, VM, Unit?, Unit?>() {
    override fun getInitialInput(): Unit? = null
}