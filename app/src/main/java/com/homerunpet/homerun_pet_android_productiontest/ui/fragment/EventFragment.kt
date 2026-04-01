package com.homerunpet.homerun_pet_android_productiontest.ui.fragment

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.drake.net.utils.scopeNetLife
import com.homerunpet.homerun_pet_android_productiontest.adapter.EventAdapter
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionStaffPhsicalModelEventBinding
import com.homerunpet.homerun_pet_android_productiontest.vm.ModelDataViewModel

class EventFragment: HMBaseFragment<ModelDataViewModel, ProductionStaffPhsicalModelEventBinding>() {
    private lateinit var adapter: EventAdapter
//    private var items: MutableList<Event> = mutableListOf()

    override fun initView(savedInstanceState: Bundle?) {
        //这里我就从初始化导入数据试试,算了，列表不会更新数据,不从这个里面导入了， adapter.submitLis
        adapter = EventAdapter()


        mBind.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        mBind.recyclerView.adapter = adapter

        mViewModel.totalData.observe(viewLifecycleOwner) { total ->
            adapter.submitList(total?.events.orEmpty())

        }
        scopeNetLife {

        }
    }
}