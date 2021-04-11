package com.grouptuity.grouptuity.ui.billsplit.diners

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.grouptuity.grouptuity.R
import com.grouptuity.grouptuity.data.Diner
import com.grouptuity.grouptuity.databinding.FragDinersListitemBinding
import com.grouptuity.grouptuity.ui.custom.RecyclerViewListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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