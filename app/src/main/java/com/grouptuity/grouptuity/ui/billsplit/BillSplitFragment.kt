package com.grouptuity.grouptuity.ui.billsplit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.databinding.FragBillSplitBinding
import com.grouptuity.grouptuity.ui.custom.setNullOnDestroy

class BillSplitFragment: Fragment() {
    private var binding by setNullOnDestroy<FragBillSplitBinding>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragBillSplitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationIcon(R.drawable.ic_menu)
    }
}