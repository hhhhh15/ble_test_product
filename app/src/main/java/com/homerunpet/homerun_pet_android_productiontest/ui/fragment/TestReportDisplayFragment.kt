package com.homerunpet.homerun_pet_android_productiontest.ui.fragment

import android.os.Bundle
import android.widget.Toast
import com.drake.net.Get
import com.drake.net.utils.scopeNetLife
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.common.bean.HMBaseResponse
import com.homerunpet.homerun_pet_android_productiontest.data.DeviceMessage
import com.homerunpet.homerun_pet_android_productiontest.data.TestReportDetailBean
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionAdminReportBinding

class TestReportDisplayFragment: HMBaseFragment<BaseViewModel, ProductionAdminReportBinding>()  {

    private var testReport:TestReportDetailBean?=null
    private var device:DeviceMessage?=null
    private var dn: String? = null
    private lateinit var realDn:String

    override fun initView(savedInstanceState: Bundle?) {
        //打开这个页面就直接将网络请求获取到的数据直接展示
        dn=arguments?.getString("dn")
        if (dn.isNullOrBlank()) {
            Toast.makeText(requireContext(), "缺少DN，无法查询", Toast.LENGTH_SHORT).show()
            activity?.onBackPressedDispatcher?.onBackPressed()
            return
        }
        realDn=dn!!

        mBind.tvDeviceInfo.text=getDevcieData().toString()
    }

    fun getDevcieData(): DeviceMessage? {
        scopeNetLife {
            val deviceData=Get<HMBaseResponse<DeviceMessage>>(HmApi.getDevice(realDn)).await()
            device=deviceData.data
        }
        return device
    }


}