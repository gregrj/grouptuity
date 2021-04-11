package com.grouptuity.grouptuity.ui.billsplit.diners

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.grouptuity.grouptuity.R

class DinersFragment : Fragment() {

    companion object {
        fun newInstance() = DinersFragment()
    }

    private lateinit var viewModel: DinersViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_diners, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(DinersViewModel::class.java)
        // TODO: Use the ViewModel
    }

}