package com.grouptuity.grouptuity.ui.billsplit.diners

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.databinding.FragDinersBinding
import com.grouptuity.grouptuity.databinding.FragDinersListitemBinding
import com.grouptuity.grouptuity.ui.custom.RecyclerViewListener
import com.grouptuity.grouptuity.ui.custom.setNullOnDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class DinersFragment : Fragment() {

    companion object {
        fun newInstance() = DinersFragment()
    }

    private var binding by setNullOnDestroy<FragDinersBinding>()
    private lateinit var viewModel: DinersViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(this).get(DinersViewModel::class.java)
        binding = FragDinersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerAdapter = DinersRecyclerViewAdapter(requireContext(), object: RecyclerViewListener {
            override fun onClick(view: View) { }
            override fun onLongClick(view: View): Boolean { return false }
        })

        viewModel.dinerData.observe(viewLifecycleOwner, { dinerData ->
            lifecycleScope.launch {
                recyclerAdapter.updateDataSet(dinerData)
            }

        })
    }
}

class DinersRecyclerViewAdapter(private val context: Context,
                                private val listener: RecyclerViewListener): RecyclerView.Adapter<DinersRecyclerViewAdapter.ViewHolder>() {

    private var mDataSet = emptyList<Pair<Diner,String>>()

    inner class ViewHolder(val viewBinding: FragDinersListitemBinding): RecyclerView.ViewHolder(viewBinding.root) {
        init {
            itemView.setOnClickListener(listener)
            itemView.setOnLongClickListener(listener)
        }
    }

    override fun getItemCount() = mDataSet.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(FragDinersListitemBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val newDiner = mDataSet[position]

        // TODO
    }

    suspend fun updateDataSet(newDataSet: List<Pair<Diner, String>>) {
        val adapter = this

        val diffResult = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
            override fun getOldListSize() = mDataSet.size

            override fun getNewListSize() = newDataSet.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = newDataSet[newPosition].first.id == mDataSet[oldPosition].first.id

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val newDiner = newDataSet[newPosition].first
                val oldDiner = mDataSet[oldPosition].first

                return newDiner.id == oldDiner.id &&
                        newDiner.name == oldDiner.name &&
                        newDiner.photoUri == oldDiner.photoUri &&
                        newDiner.items.size == oldDiner.items.size &&
                        newDataSet[newPosition].second == mDataSet[oldPosition].second
            }
        })

        withContext(Dispatchers.Main) {
            adapter.notifyItemChanged(mDataSet.size - 1) // clears BottomOffset from old last item
            mDataSet = newDataSet
            diffResult.dispatchUpdatesTo(adapter)
        }
    }
}