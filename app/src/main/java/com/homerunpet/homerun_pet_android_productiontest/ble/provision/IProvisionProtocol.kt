package com.homerunpet.homerun_pet_android_productiontest.ble.provision

import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import io.reactivex.rxjava3.core.Observable

/**
 * 配网协议抽象接口
 * 所有配网协议必须实现此接口
 * 
 * 实现类：
 * - EzIotProvisionProtocol（萤石）
 * - BluFiProvisionProtocol（ESP）
 * - HomerunCustomProvisionProtocol（霍曼自研）
 * - ApProvisionProtocol（AP配网）
 * 
 * @date 2026-01-08
 */
interface IProvisionProtocol {
    
    /**
     * 设置/更新产品信息
     * 仅保存数据，不执行连接
     */
    fun setProduct(product: Product)

    /**
     * 获取当前产品信息
     */
    fun getProduct(): Product?
    
    /**
     * 设置期望的 MTU 大小 (可选)
     * 默认不执行任何操作，由各协议自行实现
     */
    fun setTargetMtu(mtu: Int) {}
    
    /**
     * 第一步：连接设备
     * 需先调用 setProduct
     */
    fun connect(): Observable<ProvisionEvent>

    /**
     * 第二步：获取设备信息
     *
     * @return Observable<ProvisionEvent> 包含设备信息的事件流
     */
    fun getDeviceInfo(): Observable<ProvisionEvent> {
        return Observable.error(UnsupportedOperationException("Not implemented"))
    }
    
    /**
     * 第三步：配置WiFi
     * 
     * @param ssid WiFi名称
     * @param password WiFi密码
     * @param extraParams 额外参数（如verifyCode、deviceSerial等）
     * @return Observable<ProvisionEvent> 配网过程事件流
     */
    fun configureWifi(
        ssid: String,
        password: String,
        extraParams: Map<String, Any>? = emptyMap()
    ): Observable<ProvisionEvent>

    /**
     * 第四步：检查设备绑定状态 (轮询用)
     *
     * @param deviceSerial 设备序列号
     * @return Observable<ProvisionEvent>
     *         - Success: 绑定/上线成功
     *         - Failure: 发生错误
     *         - Complete(Empty): 暂未成功，需继续轮询
     */
//    fun checkBindStatus(deviceSerial: String): Observable<ProvisionEvent> {
//        return Observable.error(UnsupportedOperationException("Not implemented"))
//    }

    /**
     * 第五步：检查设备在线状态
     * @param deviceSerial 设备序列号
     */
    fun checkOnlineStatus(deviceSerial: String): Observable<ProvisionEvent> {
        return Observable.error(UnsupportedOperationException("Not implemented"))
    }

    /**
     * 断开连接
     */
    fun disconnect()
    
    /**
     * 清理资源
     */
    fun cleanup()
}

/**
 * 配网事件
 * 用于表示配网过程中的各种状态
 */
sealed class ProvisionEvent {
    /**
     * 进度事件
     * 
     * @param step 当前步骤标识（如"connecting", "configuring"）
     * @param progress 进度值（0-100）
     * @param message 用户可读的进度消息
     */
    data class Progress(
        val step: String,
        val progress: Int,
        val message: String
    ) : ProvisionEvent()
    
    /**
     * 成功事件
     * 
     * @param deviceId 设备ID（通常是MAC地址）
     * @param message 成功消息
     * @param data 成功返回的数据（可选）
     */
    data class Success(
        val deviceId: String,
        val message: String = "配网成功",
        val data: Any? = null
    ) : ProvisionEvent()
    
    /**
     * 失败事件
     * 
     * @param errorCode 错误码
     * @param message 错误消息
     */
    data class Failure(
        val errorCode: Int,
        val message: String
    ) : ProvisionEvent()
}
