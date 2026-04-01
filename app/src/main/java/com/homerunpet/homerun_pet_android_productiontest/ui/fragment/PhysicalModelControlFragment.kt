package com.homerunpet.homerun_pet_android_productiontest.ui.fragment

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.drake.net.utils.scopeNetLife
import com.drake.net.Get
import com.drake.net.Post
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.base.net.SpManager
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
    private lateinit var deviceSericl:String

    override fun initView(savedInstanceState: Bundle?) {
        provision =ProvisionManager.getInstance(requireContext())
        product = provision.getProduct() ?: return

        val stringList= listOf("属性","服务","事件")
        val fragmentList = listOf<Fragment>(PropertyFragment(),ServiceFragment(),EventFragment())

        mBind.viewPager2.bindFragment2(this,fragmentList,mBind.tabLayout,stringList)
        val pk=product.product_key?:return

        deviceSericl= product.deviceSerial.toString()
        Log.d(TAG, "initView:此时配好网的设备的${pk} ")

        //获取物模型信息
        scopeNetLife {
            val res = Get<HMBaseResponse<TotalData>>(HmApi.getProductsThingModel(pk)).await()
            Log.d(TAG, "initView: 获取物模型信息${res.data}")

            Log.d(TAG, "准备打印viewModel")
            Log.d(TAG, "Fragment中的viewModel实例: ${mViewModel.hashCode()}")
            Log.d(TAG, "准备调用setTotalData")
            mViewModel.setTotalData(res.data)
            Log.d(TAG, "setTotalData调用完毕")

            Log.d(TAG, "initView: 设备序列号$deviceSericl")
            val endTime = System.currentTimeMillis() / 1000  // 当前时间（秒）
            val startTime = endTime - 24 * 60 * 60  // 往前推24小时

            val evebtData=Get<HMBaseResponse<Any>>(HmApi.getDeviceEvent(deviceSericl)){
                setQuery("start_time",startTime)
                setQuery("end_time",endTime)
                setQuery("event_type","all")
            }.await()
            Log.d(TAG, "initView: 查看一下设备事件的数据${evebtData.data}")

        }.catch {
            Log.e(TAG, "异常: ${it.javaClass.simpleName} - ${it.message}", it)
        }


    }


    override fun onBindViewClick() {
        mBind.btnFinish.setOnClickListener {
            dialog()


        }
    }
    private fun switchFragment(fragment:Fragment){
        parentFragmentManager.beginTransaction()
            .replace(R.id.ContainView_product,fragment)
            .commit()
    }

    private fun dialog(){
        AlertDialog.Builder(requireContext())
            .setTitle("上传产测报告")
            .setMessage("测试结果是")
            .setPositiveButton("成功"){ dialog, _ ->
                postTestReport(1)

            }
            .setNegativeButton("失败"){_,_->
                postTestReport(0)

            }
            .show()
    }

    private fun postTestReport(status:Int){
        scopeNetLife {
            val res = Post<HMBaseResponse<Any>>(HmApi.postTestReport(deviceSericl)) {
                json(
                    mapOf(
                        "sn" to SpManager.deviceSn,
                        "status" to status  //这里是填写的数据，但是是上个页面测试完成后调用返回测试结果的数据填进去，这里默认是0
                    )
                )
            }.await()

            Log.d(TAG, "postTestReport: 查看返回的产测报告数据${res.data}")
            switchFragment(PostTestResultFragment())
        }.catch{
            Log.e(TAG, "postTestReport异常: ${it.javaClass.simpleName} - ${it.message}", it)
        }
    }
}