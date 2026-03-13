package com.homerunpet.homerun_pet_android_productiontest.ui.fragment

import android.os.Bundle
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import com.homerunpet.homerun_pet_android_productiontest.adapter.PropertyAdapter
import com.homerunpet.homerun_pet_android_productiontest.adapter.PropertyItemActionListener
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionManager
import com.homerunpet.homerun_pet_android_productiontest.data.Property
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionStaffPhsicalModelAttrBinding
import com.homerunpet.homerun_pet_android_productiontest.vm.ModelDataViewModel

class PropertyFragment : HMBaseFragment<ModelDataViewModel, ProductionStaffPhsicalModelAttrBinding>() {
    private lateinit var adapter: PropertyAdapter
    private lateinit var provision : ProvisionManager
    private lateinit var product : Product
    private lateinit var deviceSerial:String
    private val TAG="PropertyFragment"

    override fun initView(savedInstanceState: Bundle?) {
        provision =ProvisionManager.getInstance(requireContext())
        provision.getProduct()?.let {
            product = it
        }
        deviceSerial=product.deviceSerial?:return
        Log.d(TAG, "initView: 准备网络请求的序列号$deviceSerial")

        //导入到适配器中的数据，因为异步，所以就不从初始化开始导入，直接用适配器的submitList方法导入的
        adapter = PropertyAdapter(mutableListOf(), object : PropertyItemActionListener {
            override fun onEnumItemSelected(item: Property, value: String) {
                mViewModel.issuePropertyControlRequest(item.identifier,deviceSerial,value)
            }
            override fun onNumberValueSelected(item: Property, value: Int) {
                mViewModel.issuePropertyControlRequest(item.identifier,deviceSerial,value)
            }
            override fun onBoolValueChanged(item: Property, value: Boolean) {
                mViewModel.issuePropertyControlRequest(item.identifier,deviceSerial,value)
            }
        })

        mBind.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        mBind.recyclerView.adapter = adapter

        mViewModel.totalData.observe(viewLifecycleOwner) { total ->
            Log.d(TAG, "initView: PropertyFragment")
            adapter.submitList(total?.properties.orEmpty())
        }
    }

}