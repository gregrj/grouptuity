package com.grouptuity.grouptuity.ui.billsplit.taxtip

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.grouptuity.grouptuity.R

class TaxTipFragment : Fragment() {

    companion object {
        fun newInstance() = TaxTipFragment()
    }

    private lateinit var viewModel: TaxTipViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.frag_tax_tip, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(TaxTipViewModel::class.java)
        // TODO: Use the ViewModel
    }

}