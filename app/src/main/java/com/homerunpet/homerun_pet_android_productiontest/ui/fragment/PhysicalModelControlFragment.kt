package com.homerunpet.homerun_pet_android_productiontest.ui.fragment

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import com.drake.net.utils.scopeNetLife
import com.drake.net.Get
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionManager
import com.homerunpet.homerun_pet_android_productiontest.common.bean.HMBaseResponse
import com.homerunpet.homerun_pet_android_productiontest.common.ext.bindFragment2
import com.homerunpet.homerun_pet_android_productiontest.data.TotalData
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionStaffPhysicalModelMainBinding
import com.homerunpet.homerun_pet_android_productiontest.vm.ModelDataViewModel

class PhysicalModelControlFragment: HMBaseFragment<ModelDataViewModel, ProductionStaffPhysicalModelMainBinding>() {
    private lateinit var provision : ProvisionManager
    private lateinit var product : Product
    private val TAG="PhysicalModelControlFragment"

    override fun initView(savedInstanceState: Bundle?) {
        provision =ProvisionManager.getInstance(requireContext())
        product = provision.getProduct() ?: return

        val stringList= listOf("属性","服务","事件")
        val fragmentList = listOf<Fragment>(PropertyFragment(),ServiceFragment(),EventFragment())

        mBind.viewPager2.bindFragment2(this,fragmentList,mBind.tabLayout,stringList)
        val pk=product.product_key?:return
        Log.d(TAG, "initView:此时配好网的设备的${pk} ")

        //获取物模型信息
        scopeNetLife {
            val res=Get<HMBaseResponse<TotalData>>(HmApi.getProductsThingModel(pk)).await()
//            mBind.tvData.text=res.data.toString()
            Log.d(TAG, "initView: 获取物模型信息${res.data}")
            mViewModel.setTotalData(res.data)
        }
    }
}