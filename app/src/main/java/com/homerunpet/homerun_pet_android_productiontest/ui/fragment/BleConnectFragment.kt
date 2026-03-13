package com.homerunpet.homerun_pet_android_productiontest.ui.fragment

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.drake.net.Post
import com.drake.net.utils.scopeNetLife
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.base.net.SpManager
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionEvent
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionManager
import com.homerunpet.homerun_pet_android_productiontest.common.bean.HMBaseResponse
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionStaffConnectBleBinding
import com.homerunpet.homerun_pet_android_productiontest.vm.ConnectViewModel
import io.reactivex.rxjava3.disposables.Disposable

class BleConnectFragment: HMBaseFragment<ConnectViewModel, ProductionStaffConnectBleBinding>() {
    private lateinit var provision : ProvisionManager
    private lateinit var product : Product
    private var wifiDisposable: Disposable? = null
    private var onlineStatusDisposable: Disposable? = null
    private lateinit var deviceSecret :String

    private val TAG="ConnectFragment"

    override fun initView(savedInstanceState: Bundle?) {
        provision =ProvisionManager.getInstance(requireContext())
        provision.getProduct()?.let {
            product = it
        }
        
    }

    override fun onBindViewClick() {
        super.onBindViewClick()
        mBind.btnSendWiFi.setOnClickListener {
            val ssid = mBind.etSSID.text.toString().trim().takeIf { it.isNotEmpty() }
                ?: return@setOnClickListener
            val password = mBind.etPassword.text.toString().trim().takeIf { it.isNotEmpty() }
                ?: return@setOnClickListener


            Log.d(TAG, "看看设备连接的wifi号$ssid")
            Log.d(TAG, "看看设备连接的WiFi密码$password")


            wifiDisposable = provision.configureWifi(ssid, password)
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe({ event ->

                    when (event) {
                        is ProvisionEvent.Success -> {
                            Toast.makeText(requireContext(), "WiFi 配置成功", Toast.LENGTH_SHORT).show()
                            mBind.btnSendWiFi.isEnabled = true


                            if (product.deviceSerial != null) {
                                val sn = product.deviceSerial!!
                                Log.d(TAG, "拿到设备序列号，准备检查设备在线状态: $sn")
                                //配网成功，调用检查设备状态方法
                                checkDeviceStatus(sn)
                                //
                                // 3.5配成功直接跳转到这个开始测试页面啊

                                Toast.makeText(requireContext(), "配置成功网络了", Toast.LENGTH_SHORT).show()

                            } else {
                                Log.w(TAG, "当前配网设备没有序列号")
                            }
                        }

                        is ProvisionEvent.Failure -> {
                            Toast.makeText(requireContext(), "WiFi 配置失败:", Toast.LENGTH_SHORT).show()
                            mBind.btnSendWiFi.isEnabled = true
                        }

                        is ProvisionEvent.Progress -> {
                            Log.d("WiFi", "配网进度: ${event.progress}%")
                        }
                    }

                }, { throwable ->
                    Toast.makeText(requireContext(), "异常: ${throwable.message}", Toast.LENGTH_SHORT).show()
                    mBind.btnSendWiFi.isEnabled = true
                })

        }
        mBind.startBind.setOnClickListener {
            //这个方法里面的sn从spmanager中取出来的
            product.deviceSerial?.let {
                bindDeviceSn(deviceSecret,it)
            }

        }
    }

    private fun checkDeviceStatus(deviceSn: String) {
        onlineStatusDisposable = provision.checkOnlineStatus(deviceSn)
            .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
            .subscribe({ provisionEvent ->
                Log.d(TAG, "设备在线状态: $provisionEvent")
                if (provisionEvent is ProvisionEvent.Success) {
                    deviceSecret = provisionEvent.data.toString()
                    // 测试看一下检查设备上线状态的api，之后删掉
                    mBind.lookDeviceStatus.text = deviceSecret
                } else if (provisionEvent is ProvisionEvent.Failure) {
                    mBind.lookDeviceStatus.text = "设备不在线/异常"
                }
                // 你可以根据其他类型的事件，做自己的 UI 反馈
            }, { throwable ->
                Log.e(TAG, "检查设备在线状态失败: ${throwable.message}")
                mBind.lookDeviceStatus.text = "检查失败: ${throwable.message}"
            })
    }
    fun randomSn():String{
        val sn=SpManager.deviceSn
        val fixedPart = "1234567890"
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"  //随机字符池
        val randomPart = (1..15)
            .map { chars.random() }
            .joinToString("")//字符间隔，这里是无间隔
        if (sn.isNullOrEmpty()){
            return fixedPart + randomPart
        }else{
            return sn
        }
    }

    // 绑定设备sn和设备device_secret
    // 后端返回的响应是 { "error_code": "0", ... } 代表成功, 其它或非 0 代表失败
    private fun bindDeviceSn(deviceSecret: String, deviceName: String) {
        scopeNetLife {
            // 发起绑定设备的请求
            Log.d("Bind", "开始请求")
            try {
                Post<HMBaseResponse<Any>>(HmApi.postAddDevice(deviceName)) {
                    val snValue =randomSn()  //后面就不需要了
                    json(mapOf(
                        "device_secret" to deviceSecret,
                        "sn" to snValue)
                    )
                }.await().let { response ->
                    //绑定结束返回看看数据，这个绑定不能判请求是否成功，因为不知道返回响应码，只能查看
                    Log.d("bindDevice","返回: $response")
                        Toast.makeText(requireContext(), "绑定设备成功", Toast.LENGTH_SHORT).show()
                    Log.d("bindDevice", "打开弹窗")
                    dialog()

                }
                Log.d("bindDevice", "请求完成")

            }catch (e:Exception){
                Log.e("bindDevice", "请求异常", e)
                Toast.makeText(requireContext(), "网络请求失败: ${e.message}", Toast.LENGTH_SHORT).show()

            }


        }
    }
    private fun dialog(){
        AlertDialog.Builder(requireContext())
            .setTitle("SN绑定成功")
            .setMessage("是否进入测试页面")
            .setPositiveButton("确定"){dialog,_->
                dialog.dismiss()

                //跳转页面
                parentFragmentManager.beginTransaction()
                    .replace(R.id.ContainView_product,StartTestFragment())
                    .addToBackStack(null)
                    .commit()

            }
            .setNegativeButton("取消"){dialog,_->
                dialog.dismiss() }
            .show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        wifiDisposable?.dispose()
        onlineStatusDisposable?.dispose()
    }


}