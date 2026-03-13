package com.homerunpet.homerun_pet_android_productiontest.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.homerunpet.homerun_pet_android_productiontest.data.Service

class ServiceAdapter(
    private val listener: ServiceItemActionListener
) :RecyclerView.Adapter<RecyclerView.ViewHolder>(){
    private val serviceList: MutableList<Service> = mutableListOf()

    fun submitData(list:List<Service>){
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {

            override fun getOldListSize(): Int = serviceList.size

            override fun getNewListSize(): Int = list.size

            //判断是否是同一个item，其实是查看id判断的，需要重写ModelData属性id
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return serviceList[oldItemPosition] == list[newItemPosition]
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return serviceList[oldItemPosition] == list[newItemPosition]
            }
        })

        serviceList.clear()
        serviceList.addAll(list)  //serviceList有数据了

        diffResult.dispatchUpdatesTo(this)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        TODO("Not yet implemented")
    }

    override fun getItemCount(): Int =serviceList.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        TODO("Not yet implemented")
    }
}