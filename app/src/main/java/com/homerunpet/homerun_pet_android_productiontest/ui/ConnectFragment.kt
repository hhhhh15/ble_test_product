package com.homerunpet.homerun_pet_android_productiontest.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionEvent
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionManager
import com.homerunpet.homerun_pet_android_productiontest.databinding.ConnectBinding
import com.homerunpet.homerun_pet_android_productiontest.vm.ConnectViewModel
import io.reactivex.rxjava3.disposables.Disposable

class ConnectFragment: HMBaseFragment<ConnectViewModel, ConnectBinding>() {
    private lateinit var provision : ProvisionManager
    private var wifiDisposable: Disposable? = null
    private lateinit var deviceSn :String
    private val TAG="gggg"

    override fun initView(savedInstanceState: Bundle?) {
        provision =ProvisionManager.getInstance(requireContext())

        deviceSn=arguments?.getString("device_sn") ?: error("device_sn是空的啊")
    }

    override fun onBindViewClick() {
        super.onBindViewClick()
        mBind.btnSendWiFi.setOnClickListener{
            val ssid=mBind.etSSID.text.toString().trim().takeIf { it.isNotEmpty() }?: return@setOnClickListener
            val password=mBind.etPassword.text.toString().trim().takeIf { it.isNotEmpty() }?: return@setOnClickListener

            Log.d(TAG, "看看设备连接的序列号$deviceSn")
            Log.d(TAG, "看看设备连接的wifi号$ssid")
            Log.d(TAG, "看看设备连接的WiFi密码$password")

            wifiDisposable =provision.configureWifi(ssid,password)
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe({ event ->
                    when (event) {
                        is ProvisionEvent.Success -> {
                            Toast.makeText(requireContext(), "WiFi 配置成功", Toast.LENGTH_SHORT).show()
                            // 成功后可以选择保持按钮禁用，或者重新启用
                            mBind.btnSendWiFi.isEnabled = true
                        }
                        is ProvisionEvent.Failure -> {
                            Toast.makeText(requireContext(), "WiFi 配置失败:", Toast.LENGTH_SHORT).show()
                            mBind.btnSendWiFi.isEnabled = true // 失败后可重试
                        }
                        is ProvisionEvent.Progress -> {
                            Log.d("WiFi", "配网进度: ${event.progress}%")
                            // 可以更新进度条
                        }
                    }
                }, { throwable ->
                    Toast.makeText(requireContext(), "异常: ${throwable.message}", Toast.LENGTH_SHORT).show()
                    mBind.btnSendWiFi.isEnabled = true
                })
        }
        provision.checkBindStatus(deviceSn)
        //假设设备配网成功，则设备端和云端连接，然后客户端就能从云端调取信息返回查看一下结果
        provision.checkOnlineStatus(deviceSn)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        wifiDisposable?.dispose()
    }


}