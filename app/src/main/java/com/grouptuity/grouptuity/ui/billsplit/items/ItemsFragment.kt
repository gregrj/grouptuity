package com.grouptuity.grouptuity.ui.billsplit.items

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.grouptuity.grouptuity.R

class ItemsFragment : Fragment() {

    companion object {
        fun newInstance() = ItemsFragment()
    }

    private lateinit var viewModel: ItemsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_items, container, false)
    }


}