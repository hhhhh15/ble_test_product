package com.homerunpet.homerun_pet_android_productiontest.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.homerunpet.homerun_pet_android_productiontest.data.Event

class EventAdapter:RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val eventData:MutableList<Event> =mutableListOf()


    fun submitList(list: List<Event>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = eventData.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                eventData[oldPos].id == list[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                eventData[oldPos] == list[newPos]
        })
        eventData.clear()
        eventData.addAll(list)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        TODO("Not yet implemented")
    }

    override fun getItemCount(): Int {
        TODO("Not yet implemented")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        TODO("Not yet implemented")
    }
}