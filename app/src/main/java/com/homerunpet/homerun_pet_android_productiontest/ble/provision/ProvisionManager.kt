package com.homerunpet.homerun_pet_android_productiontest.ble.provision

import android.annotation.SuppressLint
import android.content.Context
import com.drake.net.Get
import com.drake.net.utils.scopeNet
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.base.net.SpManager
import com.homerunpet.homerun_pet_android_productiontest.ble.model.DeviceInfoDetailBean
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.ble.model.ProvisionProtocol
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.impl.HomerunCustomProvisionProtocol
import com.homerunpet.homerun_pet_android_productiontest.common.ext.saveDistributionNetworkLog
//import com.homerunpet.homerun_pet_android_productiontest.distribution_network.bean.Product
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * 配网管理器 - 统一配网入口
 *
 * 功能：
 * - 自动根据设备类型选择合适的配网协议
 * - 提供统一的配网接口
 * - 管理配网生命周期
 *
 * @date 2026-01-08
 */
class ProvisionManager private constructor(
    private val context: Context
) {

    private var currentProtocol: IProvisionProtocol? = null
    // 全局唯一的产品数据源
    private var globalProduct: Product? = null

    // 配网随机验证码
    private var provisionRandomCode: String = ""

    //当前用户ID
    private val userid:String= SpManager.userId.toString()

    // 连接开始时间
    var connectionStartTime: Long = 0L

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ProvisionManager? = null

        fun getInstance(context: Context): ProvisionManager {
            return instance ?: synchronized(this) {
                instance ?: ProvisionManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * 设置/更新设备信息（初始化协议）
     * 自动识别协议类型并初始化对应的实现类
     * 注意：此方法不执行连接操作，连接请调用 connect()
     */
    fun setProduct(product: Product) {
        this.globalProduct = product
        
        // 根据 product 中的 hmFastBleDevice 判断协议类型
        val protocol = product.hmFastBleDevice?.protocol ?: run {
            // 如果没有 hmFastBleDevice，判断是否是 AP 配网
            ProvisionProtocol.AP
        }

        // 如果协议类型改变，或者当前没有协议实例，则重新创建
        if (currentProtocol == null || getCurrentProtocol() != protocol) {
            currentProtocol = when (protocol) {
//                ProvisionProtocol.EZIOT -> EzIotProvisionProtocol(context)
//                ProvisionProtocol.BLUFI -> BluFiProvisionProtocol(context)
                ProvisionProtocol.HOMERUN_CUSTOM -> HomerunCustomProvisionProtocol(context)
//                ProvisionProtocol.AP -> ApProvisionProtocol(context)
                else -> null
            }
        }

        // 更新 Protocol 内部的 Product
        // 注意：这里我们将 globalProduct 传给协议，保证引用一致
        currentProtocol?.setProduct(product)
    }

    /**
     * 获取当前操作的设备
     */
    fun getProduct(): Product? {
        return currentProtocol?.getProduct() ?: globalProduct
    }
    
    /**
     * 设置期望 MTU (由 UI 控制)
     */
    fun setTargetMtu(mtu: Int) {
        currentProtocol?.setTargetMtu(mtu)
    }
    
    /**
     * 连接设备
     * 需先调用 setProduct
     */
    fun connect(): Observable<ProvisionEvent> {
        return currentProtocol?.connect()
            ?: Observable.just(ProvisionEvent.Failure(-1, "协议未初始化，请先调用 setProduct"))
    }

    /**
     * 第二步：获取设备信息
     */
    fun getDeviceInfo(): Observable<ProvisionEvent> {
        return currentProtocol?.getDeviceInfo()
            ?: Observable.just(ProvisionEvent.Failure(-1, "未连接设备"))
    }

    /**
     * 第三步：配置WiFi
     */
    fun configureWifi(
        ssid: String,
        password: String,
        extraParams: Map<String, Any>? = null
    ): Observable<ProvisionEvent> {
        connectionStartTime = System.currentTimeMillis() / 1000
        return currentProtocol?.configureWifi(ssid, password, extraParams)
            ?: Observable.just(ProvisionEvent.Failure(-1, "未连接设备"))
    }

//    /**
//     * 第四步：检查设备绑定状态
//     */
//    fun checkBindStatus(deviceSerial: String): Observable<ProvisionEvent> {
//        return currentProtocol?.checkBindStatus(deviceSerial)
//            ?: Observable.just(ProvisionEvent.Failure(-1, "协议未初始化"))
//    }

    /**
     * 第五步：检查设备在线状态
     */
    fun checkOnlineStatus(deviceSerial: String): Observable<ProvisionEvent> {
        val protocol = currentProtocol ?: return Observable.just(ProvisionEvent.Failure(-1, "协议未初始化"))

        // 自研协议参数不同 (provision_code)，走自己的实现
        if (protocol is HomerunCustomProvisionProtocol) {
            return protocol.checkOnlineStatus(deviceSerial)
        }

        // 其他协议统一走这里的逻辑 (使用 provision_timestamp)
        return fetchProvisionStatusCommon(deviceSerial)
            .flatMap { detail ->
                saveDistributionNetworkLog(protocol.getProduct(), "查询设备在线状态", customData = "设备已上线")
                Observable.just(ProvisionEvent.Success(deviceSerial, "设备已上线", detail) as ProvisionEvent)
            }
            .onErrorReturn { e ->
                saveDistributionNetworkLog(
                    product = protocol.getProduct(),
                    stepName = "查询设备在线状态",
                    errorCode = "DEVICE_NOT_ONLINE",
                    customData = e.message.toString()
                )
                ProvisionEvent.Failure(-1, e.message ?: "设备不在线")
            }
            .subscribeOn(Schedulers.io())
    }

    private fun fetchProvisionStatusCommon(deviceSerial: String): Observable<DeviceInfoDetailBean> {
        return Observable.create { emitter ->
            val scope = scopeNet {
                try {
                    provisionRandomCode = (100000..999999).random().toString()
                    val detail = Get<DeviceInfoDetailBean?>(HmApi.getHrDeviceProvisionStatus(userid) ){
                        setQuery("provision_code", provisionRandomCode)
                        setQuery("device_name", deviceSerial)
                        setQuery("provision_timestamp", ProvisionManager.getInstance(context).connectionStartTime)
                    }.await()
                    if (!emitter.isDisposed) {
                        if (detail != null) {
                            emitter.onNext(detail)
                            emitter.onComplete()
                        } else {
                            emitter.tryOnError(Throwable("设备不在线"))
                        }
                    }
                } catch (e: Exception) {
                    if (!emitter.isDisposed) {
                        emitter.tryOnError(Throwable("设备不在线"))
                    }
                }
            }
            emitter.setCancellable {
                scope.cancel()
            }
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        currentProtocol?.cleanup()
        currentProtocol = null
        globalProduct = null
    }

    /**
     * 断开指定设备
     */
    fun disconnectDevice() {
        currentProtocol?.disconnect()
    }

    /**
     * 获取当前使用的协议类型
     */
    fun getCurrentProtocol(): ProvisionProtocol? {
        return when (currentProtocol) {
//            is EzIotProvisionProtocol -> ProvisionProtocol.EZIOT
//            is BluFiProvisionProtocol -> ProvisionProtocol.BLUFI
            is HomerunCustomProvisionProtocol -> ProvisionProtocol.HOMERUN_CUSTOM
//            is ApProvisionProtocol -> ProvisionProtocol.AP
            else -> null
        }
    }
}
