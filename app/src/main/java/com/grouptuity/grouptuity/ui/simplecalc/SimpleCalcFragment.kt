package com.grouptuity.grouptuity.ui.simplecalc

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.grouptuity.grouptuity.MainActivity
import com.grouptuity.grouptuity.databinding.FragSimpleCalcBinding
import com.grouptuity.grouptuity.ui.custom.views.setNullOnDestroy

/**
 * A simple [Fragment] subclass.
 * Use the [SimpleCalcFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SimpleCalcFragment: Fragment() {
    private var binding by setNullOnDestroy<FragSimpleCalcBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragSimpleCalcBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as MainActivity
        mainActivity.attachToolbar(binding.toolbar)
    }

//    companion object {
//        /**
//         * Use this factory method to create a new instance of
//         * this fragment using the provided parameters.
//         *
//         * @param param1 Parameter 1.
//         * @param param2 Parameter 2.
//         * @return A new instance of fragment SimpleCalcFragment.
//         */
//        // TODO: Rename and change types and number of parameters
//        @JvmStatic
//        fun newInstance(param1: String, param2: String) =
//                SimpleCalcFragment().apply {
//                    arguments = Bundle().apply {
//                        putString(ARG_PARAM1, param1)
//                        putString(ARG_PARAM2, param2)
//                    }
//                }
//    }
}