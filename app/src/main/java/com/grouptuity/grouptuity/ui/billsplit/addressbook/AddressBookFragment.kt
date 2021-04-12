package com.grouptuity.grouptuity.ui.billsplit.addressbook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import androidx.transition.Transition
import com.grouptuity.grouptuity.AppViewModel
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.databinding.FragAddressBookBinding

import com.grouptuity.grouptuity.ui.custom.setNullOnDestroy
import com.grouptuity.grouptuity.ui.custom.transitions.CircularRevealTransition
import com.grouptuity.grouptuity.ui.custom.transitions.Revealable
import com.grouptuity.grouptuity.ui.custom.transitions.RevealableImpl

class AddressBookFragment: Fragment(), Revealable by RevealableImpl() {
    private val args: AddressBookFragmentArgs by navArgs()
    private lateinit var appViewModel: AppViewModel
    private lateinit var addressBookViewModel: AddressBookViewModel
    private var binding by setNullOnDestroy<FragAddressBookBinding>()
    private lateinit var backPressedCallback: OnBackPressedCallback

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        appViewModel = ViewModelProvider(requireActivity()).get(AppViewModel::class.java)
        addressBookViewModel = ViewModelProvider(requireActivity()).get(AddressBookViewModel::class.java)

        binding = FragAddressBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.coveredFragment.setImageBitmap(coveredFragmentBitmap)

        enterTransition = CircularRevealTransition(
                binding.fadeView,
                binding.revealedLayout,
                args.originParams,
                resources.getInteger(R.integer.frag_transition_duration).toLong(),
                true).addListener(object: Transition.TransitionListener {
            override fun onTransitionStart(transition: Transition) { }

            override fun onTransitionEnd(transition: Transition) {
//             TODO   if(addressBookViewModel.hasNotAttemptedDeviceContactRead) {
//                    requestContactsRefresh()
//                }
            }

            override fun onTransitionCancel(transition: Transition) { }
            override fun onTransitionPause(transition: Transition) { }
            override fun onTransitionResume(transition: Transition) { }

        })

        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        backPressedCallback = object: OnBackPressedCallback(true) { override fun handleOnBackPressed() { onBackPressed() } }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    override fun onResume() {
        super.onResume()
        binding.fadeView.visibility = View.GONE
    }

    private fun onBackPressed() {
        closeFragment(false)
    }

    private fun closeFragment(addSelectionsToBill: Boolean) {
        // Prevent callback from intercepting back pressed events
        backPressedCallback.isEnabled = false

        // Freeze UI in place as the fragment closes
        addressBookViewModel.freezeOutput()

        // Add selected contacts to bill before closing fragment
        if(addSelectionsToBill) {
            // TODO addressBookViewModel.addSelectedContactsToBill()
        }

        // Closing animation shrinking fragment into the FAB of the previous fragment.
        // Transition is defined here to incorporate dynamic changes to window insets.
        returnTransition = CircularRevealTransition(
                binding.fadeView,
                binding.revealedLayout,
                args.originParams.withInsetsOn(binding.fab),
                resources.getInteger(R.integer.frag_transition_duration).toLong(),
                false)

        requireActivity().onBackPressed()
    }
}