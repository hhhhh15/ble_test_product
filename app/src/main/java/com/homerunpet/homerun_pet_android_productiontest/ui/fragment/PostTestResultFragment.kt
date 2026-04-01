package com.homerunpet.homerun_pet_android_productiontest.ui.fragment

import android.os.Bundle
import android.util.Log
import com.drake.net.Get
import com.drake.net.utils.scopeNetLife
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionManager
import com.homerunpet.homerun_pet_android_productiontest.common.bean.HMBaseResponse
import com.homerunpet.homerun_pet_android_productiontest.data.TestReportDetailBean
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionStaffPostTestReportBinding

class PostTestResultFragment:HMBaseFragment<BaseViewModel,ProductionStaffPostTestReportBinding>() {
    private var testReport: TestReportDetailBean?=null
    private lateinit var provision : ProvisionManager
    private lateinit var product : Product
    private lateinit var deviceSerial:String
    private val TAG="PostTestResultFragment"

    override fun initView(savedInstanceState: Bundle?) {
        provision =ProvisionManager.getInstance(requireContext())
        provision.getProduct()?.let {
            product = it
        }
        deviceSerial=product.deviceSerial?:return

    }

    override fun onBindViewClick() {
        mBind.btnCompleteTest.setOnClickListener {
            //回到产测主页面，是扫描获取sn，没有绑定之前，不行，还是弄一个配完网在线状态的东西出来
            getTestReportData()
            }
        }



    private fun getTestReportData():TestReportDetailBean?{
        scopeNetLife {
            val reportData=Get<HMBaseResponse<TestReportDetailBean>>(HmApi.getTestReport()){
                setQuery("device_name", deviceSerial)
            }.await()
            Log.d(TAG, "getTestReportData: 获取产测报告$reportData.data")
            testReport=reportData.data
            // 直接在这里更新UI
            mBind.tvTestResult.text = testReport.toString()
        }.catch{
            Log.e(TAG, "getTestReportData异常: ${it.message}", it)
        }
        return testReport
    }

}