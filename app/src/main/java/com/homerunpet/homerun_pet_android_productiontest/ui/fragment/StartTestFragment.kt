package com.homerunpet.homerun_pet_android_productiontest.ui.fragment

import android.os.Bundle
import com.drake.net.Get
import com.drake.net.utils.scopeNetLife
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionManager
import com.homerunpet.homerun_pet_android_productiontest.common.bean.HMBaseResponse
import com.homerunpet.homerun_pet_android_productiontest.data.DeviceMessage
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionStaffTestBinding


class StartTestFragment: HMBaseFragment<BaseViewModel, ProductionStaffTestBinding>() {
    private lateinit var provision : ProvisionManager
    private lateinit var product : Product
    private lateinit var deviceSeracl : String
    private lateinit var deviceMessage: DeviceMessage

    override fun initView(savedInstanceState: Bundle?) {
        provision =ProvisionManager.getInstance(requireContext())
        provision.getProduct()?.let {
            product = it
            deviceSeracl=product.deviceSerial?:return
        }
        scopeNetLife {
            val deviceData=Get<HMBaseResponse<DeviceMessage>>(HmApi.getDevice(deviceSeracl)).await()
            deviceData.data?.let {
                deviceMessage=it
                mBind.tvName.text = "产品型号：${deviceMessage.device_name}"
                mBind.tvPn.text = "PN：${deviceMessage.product_key}"
                mBind.tvSn.text = "SN：${deviceMessage.sn}"
                mBind.tvFirmwareVersion.text = "固件版本：${deviceMessage.firmware_data.firstOrNull()?.version ?: ""}"
            }
        }


    }

    override fun onBindViewClick() {
        mBind.btnStartTest.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.ContainView_product,PhysicalModelControlFragment())
                .addToBackStack(null)
                .commit()
        }

    }

}