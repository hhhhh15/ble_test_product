package com.homerunpet.homerun_pet_android_productiontest.ble.provision

import android.annotation.SuppressLint
import android.content.Context
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.ble.model.ProvisionProtocol
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.impl.HomerunCustomProvisionProtocol
import io.reactivex.rxjava3.core.Observable

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

    private var currentProtocol: IProvisionProtocol? = null  //可指向任何一个实现了接口的实现类，多实现体现
    // 全局唯一的产品数据源
    private var globalProduct: Product? = null

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

        // 如果协议类型改变，或者当前没有协议实例，则重新创建对应的实现类，目前就一个HomerunCustomProvisionProtocol实现类
        //private var currentProtocol: IProvisionProtocol? = null 就=HomerunCustomProvisionProtocol（）

        if (currentProtocol == null || getCurrentProtocol() != protocol) {
            currentProtocol = when (protocol) {
                ProvisionProtocol.HOMERUN_CUSTOM -> HomerunCustomProvisionProtocol(context)
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
        return currentProtocol?.configureWifi(ssid, password, extraParams)
            ?: Observable.just(ProvisionEvent.Failure(-1, "未连接设备"))
    }

    /**
     * 第四步：检查设备绑定状态
     */
    fun checkBindStatus(deviceSerial: String): Observable<ProvisionEvent> {
        return currentProtocol?.checkBindStatus(deviceSerial)
            ?: Observable.just(ProvisionEvent.Failure(-1, "协议未初始化"))
    }

    /**
     * 第五步：检查设备在线状态
     */
    fun checkOnlineStatus(deviceSerial: String): Observable<ProvisionEvent> {
        return currentProtocol?.checkOnlineStatus(deviceSerial)
            ?: Observable.just(ProvisionEvent.Failure(-1, "协议未初始化"))
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
        val protocol = currentProtocol?.getProduct()?.hmFastBleDevice?.protocol
        (protocol as HomerunCustomProvisionProtocol).disconnect()
    }

    /**
     * 获取当前使用的协议类型
     */
    fun getCurrentProtocol(): ProvisionProtocol? {
        return when (currentProtocol) {
            is HomerunCustomProvisionProtocol -> ProvisionProtocol.HOMERUN_CUSTOM
            else -> null
        }
    }
}
