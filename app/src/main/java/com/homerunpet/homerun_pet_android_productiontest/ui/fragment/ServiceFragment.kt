package com.homerunpet.homerun_pet_android_productiontest.ui.fragment

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.homerunpet.homerun_pet_android_productiontest.adapter.PropertyAdapter
import com.homerunpet.homerun_pet_android_productiontest.adapter.ServiceAdapter
import com.homerunpet.homerun_pet_android_productiontest.adapter.ServiceItemActionListener
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.data.ModelData
import com.homerunpet.homerun_pet_android_productiontest.data.Service
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionStaffPhsicalModelServiceBinding
import com.homerunpet.homerun_pet_android_productiontest.vm.ModelDataViewModel

class ServiceFragment: HMBaseFragment<ModelDataViewModel, ProductionStaffPhsicalModelServiceBinding>() {
    private lateinit var adapter: ServiceAdapter

    override fun initView(savedInstanceState: Bundle?) {
        adapter = ServiceAdapter(object : ServiceItemActionListener {
            override fun onClickAction(item: Service) {
                TODO("Not yet implemented")
            }
        })

        mBind.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        mBind.recyclerView.adapter = adapter

        mViewModel.totalData.observe(viewLifecycleOwner) { total ->
            adapter.submitData(total?.services.orEmpty())
        }
    }
}