package com.homerunpet.homerun_pet_android_productiontest.ble.provision.impl

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.bhm.ble.BleManager
import com.bhm.ble.device.BleDevice
import com.drake.net.Get
import com.drake.net.utils.scopeNet
//import com.homerunpet.homerun_pet_android_productiontest.common.SharedPreferencesUtils
//import com.homerunpet.homerun_pet_android_productiontest.common.StaticDataUtils
import com.homerunpet.homerun_pet_android_productiontest.ble.model.DeviceInfoDetailBean
import com.homerunpet.homerun_pet_android_productiontest.ble.model.DevicesProductsDetailBean
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.IProvisionProtocol
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionEvent
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.util.HomerunBleCipher
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.util.HomerunBleGattHelper
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.util.HomerunBlePacketUtils
import com.homerunpet.homerun_pet_android_productiontest.common.ext.saveDistributionNetworkLog
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
//import com.homerunpet.homerun_pet_android_productiontest.distribution_network.constant.DeviceNetErrorManager
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.json.JSONArray
import org.json.JSONObject
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

/**
 * 霍曼自研配网协议实现类
 *
 * @param context 上下文
 * @param apiService API 服务实例
 */
class HomerunCustomProvisionProtocol(
    private val context: Context
) : IProvisionProtocol {

    // region ---------------- 常量与成员变量 ----------------

    companion object {

        // 服务 UUID
        const val SERVICE_UUID = "0000ace0-0000-1000-8000-00805f9b34fb"

        // 特征值 UUID 定义

        // Write/Indicate - 配网信息下发与状态监听
        const val CHARACTERISTIC_CONFIG_UUID = "0000fc01-0000-1000-8000-00805f9b34fb"

        // Read - 获取设备所属用户 ID （不用校验了）
        const val CHARACTERISTIC_USER_ID_UUID = "0000fc02-0000-1000-8000-00805f9b34fb"

    }

    // 当前操作的产品信息
    private var currentProduct: Product? = null

    // 当前连接的 BLE 设备
    private var connectedBleDevice: BleDevice? = null

    // 设备详情信息
    private var isDeviceDetailFetched = false
    private var deviceDetail: DevicesProductsDetailBean? = null

    // 接收包缓存: MsgID -> (Seq -> Payload)
    private val packetBuffer = ConcurrentHashMap<Int, TreeMap<Int, ByteArray>>()

    // MTU 协商大小，默认 512 (可通过外部修改)
    private var targetMtu: Int = 512

    // 配网随机验证码
    private var provisionRandomCode: String = ""

    // endregion

    // region ---------------- 接口实现 (IProvisionProtocol) ----------------

    override fun setProduct(product: Product) {
        this.currentProduct = product

        Log.d("Provision", "HomerunCustomProvisionProtocol.setProduct: product=$product")
        Log.d("Provision", "MAC=${product.hmFastBleDevice?.mac}")
        Log.d("deviceSerial", "deviceSerial=${product.deviceSerial}和设备的${product.hmFastBleDevice?.deviceSerial}")
    }

    override fun getProduct(): Product? {
        return currentProduct
    }

    override fun setTargetMtu(mtu: Int) {
        this.targetMtu = mtu
    }

    /**
     * 第一步：连接设备
     * 1. 强制断开旧连接
     * 2. 发起新连接
     * 3. 打印服务列表 (调试用)
     */
    override fun connect(): Observable<ProvisionEvent> {
        val product = currentProduct
        if (product == null) {
            saveDistributionNetworkLog(
                product = null,
                stepName = "蓝牙连接",
                errorCode = "DEVICE_BLE_CONNECT_FAIL",
                customData = "未设置产品信息"
            )
            return Observable.error(Throwable("未设置产品信息"))
        }
        val mac = product.address
        if (mac.isNullOrEmpty()) {
            saveDistributionNetworkLog(
                product = currentProduct,
                stepName = "蓝牙连接",
                errorCode = "DEVICE_BLE_CONNECT_FAIL",
                customData = "设备MAC地址为空"
            )
            return Observable.error(Throwable("设备MAC地址为空"))
        }

        // 每次连接前重置详情状态
        isDeviceDetailFetched = false
        deviceDetail = null

        // 1. 先执行蓝牙连接
        return Observable.create<Boolean> { emitter ->
            // 确保断开之前的连接
            BleManager.get().disConnect(mac)
            Thread.sleep(100)
            connectBleDevice(mac, emitter)
        }
            .flatMap { success ->
                if (success) {
                    val bleDevice = connectedBleDevice
                    if (bleDevice == null) {
                        saveDistributionNetworkLog(
                            product = currentProduct,
                            stepName = "蓝牙连接",
                            errorCode = "DEVICE_BLE_CONNECT_FAIL",
                            customData = "连接异常: 设备对象为空"
                        )
                        return@flatMap Observable.error<ProvisionEvent>(Throwable("连接异常: 设备对象为空"))
                    }

                    HomerunBleGattHelper.setMtu(bleDevice, targetMtu)
                        .onErrorResumeNext { error ->
                            saveDistributionNetworkLog(
                                product = currentProduct,
                                stepName = "获取设备信息",
                                errorCode = "NEGOTIATE_MTU_FAIL",
                                customData = "MTU协商失败: $targetMtu, ${error.message}"
                            )
                            Observable.error(error)
                        }
                        .flatMap { mtu ->
                            val serviceDump = HomerunBleGattHelper.dumpGattServices(bleDevice)
                            saveDistributionNetworkLog(
                                product = currentProduct,
                                stepName = "蓝牙连接",
                                customData = "MTU协商成功: $mtu, Services:\n$serviceDump"
                            )
                            saveDistributionNetworkLog(
                                product = currentProduct,
                                stepName = "蓝牙连接",
                                customData = "首次配网模式，完成初始化"
                            )
                            Observable.just(ProvisionEvent.Success(mac) as ProvisionEvent)
                            // 根据模式决定是否权鉴
                            // if (product.hmFastBleDevice?.provisionMode == 1) {
                            //     saveDistributionNetworkLog(
                            //         product = currentProduct,
                            //         stepName = "蓝牙连接",
                            //         customData = "修改模式，开始所有权权鉴 (Read FC01)..."
                            //     )
                            //     modifyNetwork()
                            // } else {
                            //     saveDistributionNetworkLog(
                            //         product = currentProduct,
                            //         stepName = "蓝牙连接",
                            //         customData = "首次配网模式，完成初始化"
                            //     )
                            //     Observable.just(ProvisionEvent.Success(mac) as ProvisionEvent)
                            // }
                        }
                } else {
                    Observable.error(Throwable("蓝牙连接失败"))
                }
            }
            .subscribeOn(Schedulers.io())
    }

    /**
     * 第二步：配置WiFi
     *
     * 流程:
     * 1. 密钥准备 (Secret -> AES Key).
     * 2. 开启 Indicate 监听 (FC04).
     * 3. 发送配网信息 (SSID/PWD/UID/Brokers).
     */
    override fun configureWifi(ssid: String, password: String, extraParams: Map<String, Any>?): Observable<ProvisionEvent> {
        return Observable.defer {
            val productKey = currentProduct?.product_key
            if (productKey.isNullOrEmpty()) {
                saveDistributionNetworkLog(
                    product = currentProduct,
                    stepName = "发送配网数据",
                    errorCode = "DEVICE_BLE_CONFIG_FUNC_FAIL",
                    customData = "产品Key为空"
                )
                return@defer Observable.error(Throwable("产品Key为空"))
            }

            fetchProductsByKey(productKey)
                .flatMap { success ->
                    if (success) {
                        Observable.just(Unit)
                    } else {
                        Observable.error(Throwable("查询产品信息失败"))
                    }
                }
                .flatMap {
                    val bleDevice = connectedBleDevice
                    if (bleDevice == null) {
                        saveDistributionNetworkLog(
                            product = currentProduct,
                            stepName = "发送配网数据",
                            errorCode = "DEVICE_BLE_CONNECT_FAIL",
                            customData = "设备未连接"
                        )
                        return@flatMap Observable.error(Throwable("设备未连接"))
                    }

//                    val uid = SharedPreferencesUtils.getStringValue(StaticDataUtils.curUserId)
                    val uid = "00000000" // 字符串占位

                    // 必须从详情中获取 Brokers
                    val detailBrokers = deviceDetail?.brokers
                    if (detailBrokers.isNullOrEmpty()) {
                        saveDistributionNetworkLog(
                            product = currentProduct,
                            stepName = "发送配网数据",
                            errorCode = "DEVICE_BLE_CONFIG_FUNC_FAIL",
                            customData = "Brokers 为空，无法配网"
                        )
                        return@flatMap Observable.error(Throwable("Brokers 为空，无法配网"))
                    }
                    val brokers = JSONArray(detailBrokers).toString()

                    // 生成随机 Code 用于校验
                    provisionRandomCode = (100000..999999).random().toString()
                    saveDistributionNetworkLog(
                        product = currentProduct,
                        stepName = "发送配网数据",
                        customData = "流程启动: SSID=$ssid, 随机码=$provisionRandomCode"
                    )

                    // 从详情中获取 Secret
                    val secretHex = deviceDetail?.product_secret
                    if (secretHex.isNullOrEmpty()) {
                        saveDistributionNetworkLog(
                            product = currentProduct,
                            stepName = "发送配网数据",
                            errorCode = "DEVICE_BLE_CONFIG_FUNC_FAIL",
                            customData = "设备密钥为空，无法配网"
                        )
                        return@flatMap Observable.error(Throwable("设备密钥为空，无法配网"))
                    }
                    val aesKey = HomerunBleCipher.generateKey(secretHex)

                    val sendTrigger = PublishSubject.create<ProvisionEvent>()

                    // 首次配网：直接开始监听并发送
                    val listenObservable = waitForProvisionResponse(aesKey) {
                        sendProvisionInfo(bleDevice, ssid, password, uid, brokers, provisionRandomCode, aesKey)
                            .subscribe(sendTrigger)
                    }

                    Observable.merge(listenObservable, sendTrigger)
                }
        }.subscribeOn(Schedulers.io())
    }

    private fun fetchProductsByKey(productKey: String): Observable<Boolean> {
        return Observable.create { emitter ->
            val scope = scopeNet {
                try {
                    val detail = Get<DevicesProductsDetailBean?>(HmApi.getProductsByKey(productKey)).await()
                    Log.d("dddd", "fetchProductsByKey: $detail")
                    if (!emitter.isDisposed) {
                        if (detail != null) {
                            saveDistributionNetworkLog(
                                product = currentProduct,
                                stepName = "发送配网数据",
                                customData = "获取设备详情成功: $productKey"
                            )
                            deviceDetail = detail
                            isDeviceDetailFetched = true
                            emitter.onNext(true)
                        } else {
                            saveDistributionNetworkLog(
                                product = currentProduct,
                                stepName = "获取设备信息",
                                errorCode = "QUERY_DEVICE_PRODUCT_FAIL",
                                customData = "获取设备详情返回 Null: $productKey"
                            )
                            emitter.onNext(false)
                        }
                    }
                } catch (e: Exception) {
                    saveDistributionNetworkLog(
                        product = currentProduct,
                        stepName = "获取设备信息",
                        errorCode = "QUERY_DEVICE_PRODUCT_FAIL",
                        customData = "获取设备详情异常: ${e.message}"
                    )
                    if (!emitter.isDisposed) emitter.onNext(false)
                }
                if (!emitter.isDisposed) emitter.onComplete()
            }
            emitter.setCancellable {
                saveDistributionNetworkLog(
                    product = currentProduct,
                    stepName = "获取设备信息",
                    customData = "获取设备详情被取消: $productKey"
                )
                scope.cancel()
            }
        }
    }

//    /**
//     * 查询配网信息和设备状态
//     */
//    override fun checkBindStatus(deviceSerial: String): Observable<ProvisionEvent> {
//        HmLog("[查询] 检查绑定状态(Mock): 默认成功")
//        return Observable.just(ProvisionEvent.Success(deviceSerial, "绑定成功"))
//    }

    /**
     * 查询配网信息和设备状态 (使用设备详情校验上线)
     */
    override fun checkOnlineStatus(deviceSerial: String): Observable<ProvisionEvent> {
        return fetchProvisionStatus(deviceSerial)
            .flatMap { detail ->
                saveDistributionNetworkLog(currentProduct, "查询设备在线状态", customData = "设备已上线")
                //将detail包装成ProvisionEvent对象，所以connectfragment订阅的数据是ProvisionEvent
                Observable.just(ProvisionEvent.Success(deviceSerial, "设备已上线", detail) as ProvisionEvent)
            }
            .doOnError { e ->
                saveDistributionNetworkLog(
                    product = currentProduct,
                    stepName = "查询设备在线状态",
                    errorCode = "DEVICE_NOT_ONLINE",
                    customData = e.message.toString()
                )
            }
            .subscribeOn(Schedulers.io())
    }

    private fun fetchProvisionStatus(deviceSerial: String): Observable<DeviceInfoDetailBean> {
        return Observable.create { emitter ->
            val scope = scopeNet {
                try {
                    val detail = Get<DeviceInfoDetailBean?>(HmApi.getDeviceProvisionStatus(deviceSerial)) {
                        //给当前这次 GET 请求的 URL 添加一个 query 参数
                        setQuery("provision_code", provisionRandomCode)
                    }.await()
                    if (!emitter.isDisposed) {
                        if (detail != null && detail.device_secret.isNullOrEmpty().not()) {
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

    override fun disconnect() {
        connectedBleDevice?.let { BleManager.get().disConnect(it) }
        connectedBleDevice = null
    }

    override fun cleanup() {
        disconnect()
        currentProduct = null
        connectedBleDevice = null
        packetBuffer.clear()
    }

    // endregion

    // region ---------------- 修改/重置配网 (Public Methods) ----------------

    /**
     * 修改配网 - 第一步 (所有权校验)
     *
     * 在对已绑定设备进行 WiFi 修改前，必须先校验归属权。
     * 该方法会读取硬件 UID 并与当前登录 UID 进行比对。
     *
     * @return 校验结果事件流
     */
    private fun modifyNetwork(): Observable<ProvisionEvent> {
        return Observable.defer {
            val bleDevice = connectedBleDevice ?: return@defer Observable.error<ProvisionEvent>(Throwable("设备未连接"))
            val productKey = currentProduct?.product_key ?: return@defer Observable.error<ProvisionEvent>(Throwable("产品Key为空"))

            // 1. 确保产品密钥详情已获取 (用于权鉴解密)
            fetchProductsByKey(productKey).flatMap { success ->
                if (success) Observable.just(Unit)
                else Observable.error(Throwable("获取设备密钥详情失败"))
            }.flatMap {
//                val uid = SharedPreferencesUtils.getStringValue(StaticDataUtils.curUserId)
                val  uid ="0000000"  //占位符的
                val secretHex = deviceDetail?.product_secret
                if (secretHex.isNullOrEmpty()) {
                    saveDistributionNetworkLog(
                        product = currentProduct,
                        stepName = "发送配网数据",
                        errorCode = "DEVICE_BLE_CONFIG_FUNC_FAIL",
                        customData = "设备密钥为空，无法配网"
                    )
                    return@flatMap Observable.error<ProvisionEvent>(Throwable("设备密钥为空，无法配网"))
                }
                val aesKey = HomerunBleCipher.generateKey(secretHex)

                // 2. 核心: 校验设备归属权 (Read FC01)
                verifyDeviceOwner(bleDevice, aesKey, uid)
                    .flatMap {
                        // 校验通过: 说明当前用户是设备所有者，允许后续修改网络
                        // 校验通过: 说明当前用户是设备所有者，允许后续修改网络
                        saveDistributionNetworkLog(
                            product = currentProduct,
                            stepName = "发送配网数据",
                            customData = "归属校验通过，准备修改网络"
                        )
                        val mac = bleDevice.deviceAddress.orEmpty()
                        Observable.just(ProvisionEvent.Success(mac, "归属校验通过，可进行下一步配网")) as Observable<ProvisionEvent>
                    }
                    .onErrorResumeNext { error ->
                        // 权鉴失败 (如 UID 不匹配) 或蓝牙通信异常
                        // 权鉴失败 (如 UID 不匹配) 或蓝牙通信异常
                        saveDistributionNetworkLog(
                            product = currentProduct,
                            stepName = "发送配网数据",
                            customData = "校验失败: ${error.message}"
                        )
                        Observable.just(ProvisionEvent.Failure(4001, error.message ?: "权限校验失败"))
                    }
            }
        }.subscribeOn(Schedulers.io())
    }

    // endregion

    // region ---------------- 私有业务逻辑 (Private Logic) ----------------

    /**
     * 连接逻辑封装
     */
    private fun connectBleDevice(mac: String, emitter: ObservableEmitter<Boolean>) {
        BleManager.get().connect(mac) {
            onConnectStart {

            }
            onConnectSuccess { bleDevice, _ ->
                saveDistributionNetworkLog(currentProduct, "蓝牙连接")
                connectedBleDevice = bleDevice
                if (!emitter.isDisposed) {
                    emitter.onNext(true)
                    emitter.onComplete()
                }
            }
            onConnectFail { _, exception ->
                saveDistributionNetworkLog(
                    product = currentProduct,
                    stepName = "蓝牙连接",
                    errorCode = "DEVICE_BLE_CONNECT_FAIL",
                    customData = "蓝牙连接失败: $mac, 原因: $exception"
                )
                if (!emitter.isDisposed) {
                    emitter.tryOnError(Throwable("蓝牙连接失败: $mac, 原因: $exception"))
                }
            }
            onDisConnecting { _, _, _, _ ->

            }
            onDisConnected { _, _, _, _ ->
                connectedBleDevice = null
                saveDistributionNetworkLog(
                    product = currentProduct,
                    stepName = "获取设备信息",
                    errorCode = "BLE_DEVICE_DISCONNECT_IN_MIDWAY",
                    customData = "蓝牙连接中途断开"
                )
            }
        }
    }

    /**
     * 核心: 校验设备归属权
     *
     * 1. Read (FC01)
     * 2. Decrypt
     * 3. Compare UID
     */
    private fun verifyDeviceOwner(bleDevice: BleDevice, aesKey: ByteArray, currentUid: String): Observable<Unit> {
        return HomerunBleGattHelper.read(bleDevice, SERVICE_UUID, CHARACTERISTIC_USER_ID_UUID)
            .onErrorResumeNext { error ->
                saveDistributionNetworkLog(
                    product = currentProduct,
                    stepName = "获取设备信息",
                    errorCode = "DEVICE_BLE_GET_DEVICE_FAIL",
                    customData = "读取鉴权特征值失败: ${error.message}"
                )
                Observable.error(error)
            }
            .flatMap { bytes ->
                try {
                    saveDistributionNetworkLog(
                        product = currentProduct,
                        stepName = "发送配网数据",
                        customData = "鉴权: 收到原始数据(Hex): ${bytes.joinToString("") { "%02X".format(it) }}"
                    )

                    // 1. 解析协议包
                    // 注意：这里我们使用临时的 buffer 逻辑，因为 verify 通常是单次交互
                    val parseResult = HomerunBlePacketUtils.parsePacket(bytes)
                    if (parseResult.packets.isEmpty()) {
                        throw IllegalArgumentException("解析数据包失败: 格式错误或数据不完整")
                    }

                    // 2. 组包逻辑 (简化版，针对单次 Read 返回的一组包)
                    // 假设 Read 返回的数据包含完整的一组帧 (Frames)
                    // 我们找到第一个包，确定 MsgID 和 Frames，然后检查是否收齐
                    val distinctMsgIds = parseResult.packets.map { it.msgId }.distinct()
                    if (distinctMsgIds.size > 1) {
                        saveDistributionNetworkLog(
                            product = currentProduct,
                            stepName = "发送配网数据",
                            customData = "警告: 收到多个 MsgID 的包，仅处理第一个"
                        )
                    }
                    val msgId = distinctMsgIds.first()

                    // 筛选出该 MsgID 的所有帧
                    val frames = parseResult.packets.filter { it.msgId == msgId }
                    val totalFrames = frames.first().frames // 协议头里声明的总帧数

                    if (frames.size < totalFrames) {
                        throw IllegalArgumentException("数据不完整: 需 $totalFrames 帧，实际收到 ${frames.size} 帧")
                    }

                    // 按 Seq 排序并拼接
                    val sortedFrames = frames.sortedBy { it.seq }
                    val totalSize = sortedFrames.sumOf { it.payload.size }
                    val fullPayload = ByteArray(totalSize)
                    var offset = 0
                    for (frame in sortedFrames) {
                        System.arraycopy(frame.payload, 0, fullPayload, offset, frame.payload.size)
                        offset += frame.payload.size
                    }

                    saveDistributionNetworkLog(
                        product = currentProduct,
                        stepName = "发送配网数据",
                        customData = "鉴权: 组包完成(MsgID=$msgId, Frames=$totalFrames), Payload(Hex): ${
                            fullPayload.joinToString("") {
                                "%02X".format(
                                    it
                                )
                            }
                        }"
                    )

                    // 3. 解密
                    val decrypted = try {
                        HomerunBleCipher.decrypt(fullPayload, aesKey)
                    } catch (e: Exception) {
                        saveDistributionNetworkLog(
                            product = currentProduct,
                            stepName = "发送配网数据",
                            errorCode = "OWNER_VERIFY_DECRYPT_FAIL",
                            customData = "鉴权解密失败: ${e.message}"
                        )
                        throw e
                    }
                    saveDistributionNetworkLog(
                        product = currentProduct,
                        stepName = "发送配网数据",
                        customData = "鉴权: 解密后数据(Hex): ${decrypted.joinToString("") { "%02X".format(it) }}"
                    )

                    // 去除可能的填充或空格
                    val rawString = String(decrypted, Charsets.UTF_8).trim()

                    val deviceUid = try {
                        val jsonObject = JSONObject(rawString)
                        if (jsonObject.has("d")) {
                            jsonObject.getString("d")
                        } else {
                            rawString
                        }
                    } catch (e: Exception) {
                        rawString
                    }

                    if (deviceUid == currentUid) {
                        saveDistributionNetworkLog(
                            product = currentProduct,
                            stepName = "发送配网数据",
                            customData = "鉴权成功: UID匹配"
                        )
                        Observable.just(Unit)
                    } else {
                        saveDistributionNetworkLog(
                            product = currentProduct,
                            stepName = "发送配网数据",
                            errorCode = "OWNER_VERIFY_AUTH_FAIL",
                            customData = "鉴权失败: 设备归属用户($deviceUid) != 当前用户($currentUid)"
                        )
                        Observable.error(Throwable("无权限操作: 设备归属于其他用户"))
                    }
                } catch (e: Exception) {
                    saveDistributionNetworkLog(
                        product = currentProduct,
                        stepName = "发送配网数据",
                        errorCode = "OWNER_VERIFY_FAIL",
                        customData = "校验设备归属流程异常: ${e.message}"
                    )
                    Observable.error(Throwable("校验设备归属失败: ${e.message}"))
                }
            }
    }

    /**
     * 发送配网信息
     */
    @SuppressLint("CheckResult")
    private fun sendProvisionInfo(
        bleDevice: BleDevice,
        ssid: String,
        password: String,
        uid: String,
        brokers: String,
        randomCode: String,
        aesKey: ByteArray
    ): Observable<ProvisionEvent> {
        return Observable.create { emitter ->
            try {
                // 1. 构建 JSON
                val jsonPayload = buildProvisionJson(ssid, password, uid, brokers, randomCode)
                val rawBytes = jsonPayload.toByteArray(Charsets.UTF_8)
                // 2. 加密
                val encryptedBytes = try {
                    HomerunBleCipher.encrypt(rawBytes, aesKey)
                } catch (e: Exception) {
                    saveDistributionNetworkLog(
                        product = currentProduct,
                        stepName = "获取设备信息",
                        errorCode = "SEND_DATA_ENCRYPT_FAIL",
                        customData = "加密失败: ${e.message}"
                    )
                    throw e
                }

                // 3. 分包 (根据实际协商的 MTU 计算最大载荷)
                val packets = HomerunBlePacketUtils.packData(
                    encryptedBytes,
                    targetMtu - HomerunBlePacketUtils.MTU_OVERHEAD
                )

                saveDistributionNetworkLog(
                    product = currentProduct,
                    stepName = "发送配网数据",
                    customData = """
                        发送配网Payload: $jsonPayload
                        原始数据(Hex): ${rawBytes.joinToString("") { "%02X".format(it) }}
                        加密后数据(Hex): ${encryptedBytes.joinToString("") { "%02X".format(it) }}
                        分包数量: ${packets.size}
                    """.trimIndent()
                )

                // 4. 发送
                val sendObservables = packets.map { packet ->
                    HomerunBleGattHelper.write(
                        bleDevice,
                        SERVICE_UUID,
                        CHARACTERISTIC_CONFIG_UUID,
                        packet
                    )
                }

                Observable.concat(sendObservables)
                    .subscribe(
                        { },
                        { error ->
                            saveDistributionNetworkLog(
                                product = currentProduct,
                                stepName = "获取设备信息",
                                errorCode = "SEND_DATA_TO_BLE_DEVICE_FAIL",
                                customData = "发送数据失败: ${error.message}"
                            )
                            if (!emitter.isDisposed) emitter.onError(error)
                        },
                        {
                            if (!emitter.isDisposed) emitter.onComplete()
                        }
                    )

            } catch (e: Exception) {
                if (!emitter.isDisposed) emitter.onError(e)
            }
        }
    }

    /**
     * 等待并处理配网响应 (核心状态机)
     *
     * 该方法启动一个 BLE Indicate 监听流，负责处理设备返回的所有数据包。
     *
     * **处理流程**:
     * 1. **接收**: 监听 BLE 的 `Indicate` 通知。
     * 2. **组包**: [HomerunBlePacketUtils] 负责分包、粘包处理。
     * 3. **重组**: 缓存并重组多帧的大包数据 (MsgID/Seq)。
     * 4. **解密**: 使用 [HomerunBleCipher] (AES/PKCS5) 解密 Payload。
     * 5. **解析**: 将解密后的 JSON 转换为 [ProvisionEvent] 事件。
     *
     * **协议状态**:
     * - **`{}`**: 参数校验通过 (Progress 0%)。设备已接受 SSID/PWD，开始尝试连接。
     * - **`{"d":"1"}`**: 连接 AP 中 (Progress 20%)。
     * - **`{"d":"2"}`**: 获取 IP 中 (Progress 50%)。
     * - **`{"d":"3"}`**: 连接 MQTT 中 (Progress 80%)。
     * - **`{"d":"4"}`**: **配网成功** (Success)。保存配置成功，流程结束。
     * - **`{"e":"..."}`**: 发生错误 (Failure)。如密码错误、格式错误等。
     */
    private fun waitForProvisionResponse(
        aesKey: ByteArray,
        onEnableSuccess: () -> Unit
    ): Observable<ProvisionEvent> {
        val bleDevice = connectedBleDevice ?: return Observable.error(Throwable("设备未连接"))

        // 用于缓存断包/粘包的原始字节
        var rawStickyBuffer = ByteArray(0)

        return HomerunBleGattHelper.indicate(
            bleDevice,
            SERVICE_UUID,
            CHARACTERISTIC_CONFIG_UUID,
            onEnableSuccess = onEnableSuccess
        )
            .retry(2)
            .flatMap { newBytes ->
                // 1. 拼接新数据 (处理粘包)
                val combined = ByteArray(rawStickyBuffer.size + newBytes.size)
                System.arraycopy(rawStickyBuffer, 0, combined, 0, rawStickyBuffer.size)
                System.arraycopy(newBytes, 0, combined, rawStickyBuffer.size, newBytes.size)

                // 2. 解析 BLE 协议包
                val parseResult = HomerunBlePacketUtils.parsePacket(combined)

                // 3. 更新剩余 Buffer
                rawStickyBuffer = parseResult.remainingData

                // 4. 处理解析出的完整包
                val packets = parseResult.packets
                if (packets.isEmpty()) {
                    return@flatMap Observable.just(PacketResult.Ignore)
                }

                val results = mutableListOf<PacketResult>()
                for (packet in packets) {
                    // [MsgID] 区分不同消息: 使用 MsgID 作为 Key，防止不同消息的数据混淆
                    val buffer = packetBuffer.getOrPut(packet.msgId) { TreeMap() }

                    // [Seq] 帧排序: 使用 Seq 作为 TreeMap 的 Key，自动处理乱序到达的帧
                    buffer[packet.seq] = packet.payload

                    // [Frames] 完整性校验: 检查已收到的帧数是否等于 Header 中声明的总帧数
                    // 检查是否收齐所有 Frames (逻辑层分包重组)
                    if (buffer.size == packet.frames) {
                        val totalSize = buffer.values.sumOf { it.size }
                        val fullPayload = ByteArray(totalSize)
                        var offset = 0
                        buffer.values.forEach { chunk ->
                            System.arraycopy(chunk, 0, fullPayload, offset, chunk.size)
                            offset += chunk.size
                        }
                        // 拼装完成，移除缓存
                        packetBuffer.remove(packet.msgId)
                        results.add(PacketResult.Complete(packet.msgId, fullPayload))
                    } else {
                        // 还没收齐，继续等待下一帧
                        results.add(PacketResult.Progress(packet.msgId, buffer.size, packet.frames))
                    }
                }
                Observable.fromIterable(results)
            }
            .filter { it is PacketResult.Complete }
            .map { it as PacketResult.Complete }
            .flatMap { result ->
                try {
                    // 解密
                    val decrypted = try {
                        HomerunBleCipher.decrypt(result.payload, aesKey)
                    } catch (e: Exception) {
                        saveDistributionNetworkLog(
                            product = currentProduct,
                            stepName = "获取设备信息",
                            errorCode = "ACCEPT_DATA_DECRYPT_FAIL",
                            customData = "数据解密失败: ${e.message}"
                        )
                        throw e
                    }

                    val hexPayload = result.payload.joinToString("") { "%02X".format(it) }
                    val hexDecrypted = decrypted.joinToString("") { "%02X".format(it) }
                    val jsonStr = String(decrypted, Charsets.UTF_8)

                    saveDistributionNetworkLog(
                        product = currentProduct,
                        stepName = "发送配网数据",
                        customData = """
                            加密payload(Hex): $hexPayload
                            解密payload(Hex): $hexDecrypted
                            收到响应: $jsonStr
                        """.trimIndent()
                    )

                    val jsonObj = JSONObject(jsonStr)

                    // 检查错误 'e'
                    if (jsonObj.has("e")) {
                        val errorCode = jsonObj.getString("e")
                        var eventCode = 4000
                        var failureCode = "DEVICE_NOTI_UNKONW_ERROR"

                        val errorDesc = when {
                            errorCode.contains("INVALID_DATA") -> {
                                failureCode = "DEVICE_NOTI_INVALID_DATA"
                                "无效数据"
                            }

                            errorCode.contains("INVALID_USER_ID") -> {
                                failureCode = "DEVICE_NOTI_INVALID_USER_ID"
                                "无效用户 ID"
                            }

                            errorCode.contains("INVALID_WIFI_CREDENTIALS") -> {
                                failureCode = "DEVICE_NOTI_INVALID_WIFI_CREDENTIALS"
                                eventCode = 4002 // 映射到密码错误
//                                DeviceNetErrorManager.currentError = DeviceNetErrorManager.NetErrorType.WifiNetworkIssue(
//                                    isOnly24G = currentProduct?.network_type == 1
//                                )
                                "SSID 或密码错误"
                            }

                            errorCode.contains("INVALID_WIFI_BAND") -> {
                                failureCode = "DEVICE_NOTI_INVALID_WIFI_BAND"
//                                DeviceNetErrorManager.currentError = DeviceNetErrorManager.NetErrorType.WifiNetworkIssue(
//                                    isOnly24G = currentProduct?.network_type == 1
//                                )
                                "无效 Wi-Fi 频段"
                            }

                            errorCode.contains("IP_ADDRESS_TIMEOUT") -> {
                                failureCode = "DEVICE_NOTI_IP_ADDRESS_TIMEOUT"
//                                DeviceNetErrorManager.currentError = DeviceNetErrorManager.NetErrorType.WifiNetworkIssue(
//                                    isOnly24G = currentProduct?.network_type == 1
//                                )
                                "获取 IP 地址超时"
                            }
//
//                            errorCode.contains("MQTT_CONNECT_TIMEOUT") -> {
//                                failureCode = "DEVICE_NOTI_MQTT_CONNECT_TIMEOUT"
//                                DeviceNetErrorManager.currentError = DeviceNetErrorManager.NetErrorType.ServerIssue
//                                "连接 MQTT 超时"
//                            }
//
//                            errorCode.contains("MQTT_AUTH_FAILED") -> {
//                                failureCode = "DEVICE_NOTI_MQTT_AUTH_FAILED"
//                                DeviceNetErrorManager.currentError = DeviceNetErrorManager.NetErrorType.ServerIssue
//                                "MQTT 认证失败"
//                            }

                            errorCode.contains("MQTT_PUBLISH_FAILED") -> {
                                failureCode = "DEVICE_NOTI_MQTT_PUBLISH_FAILED"
                                "发送配网事件失败"
                            }

                            else -> {
                                failureCode = "DEVICE_NOTI_UNKONW_ERROR"
                                "未知错误"
                            }
                        }

                        saveDistributionNetworkLog(
                            product = currentProduct,
                            stepName = "获取设备信息",
                            errorCode = failureCode,
                            customData = "配网失败: $errorDesc ($errorCode)"
                        )
                        return@flatMap Observable.just(ProvisionEvent.Failure(eventCode, "配网失败: $errorDesc ($errorCode)"))
                    }

                    // 检查进度 'd'
                    if (jsonObj.has("d")) {
                        val progressCode = jsonObj.getString("d")
                        return@flatMap when (progressCode) {
                            "1" -> Observable.just(ProvisionEvent.Progress("connecting_ap", 20, "设备连接 AP 过程中"))
                            "2" -> Observable.just(ProvisionEvent.Progress("getting_ip", 50, "设备连接 MQTT 服务器中"))
                            "3" -> Observable.just(ProvisionEvent.Progress("connecting_mqtt", 80, "设备上报配网信息中"))

                            // 4: 保存配网信息成功 -> 视为配网流程结束
                            "4" -> {
                                val mac = currentProduct?.address ?: ""
                                saveDistributionNetworkLog(currentProduct, "发送配网数据")
                                Observable.just(ProvisionEvent.Success(mac, "保存配网信息成功"))
                            }

                            else -> Observable.just(ProvisionEvent.Progress("unknown", 10, "处理中: $progressCode"))
                        }
                    }

                    saveDistributionNetworkLog(
                        product = currentProduct,
                        stepName = "发送配网数据",
                        customData = "参数校验通过，等待设备连接..."
                    )
                    return@flatMap Observable.just(ProvisionEvent.Progress("verified", 0, "等待配网"))

                } catch (e: Exception) {
                    saveDistributionNetworkLog(
                        product = currentProduct,
                        stepName = "发送配网数据",
                        errorCode = "DEVICE_BLE_CONFIG_FUNC_FAIL",
                        customData = "解析响应失败: ${e.message}"
                    )
                    return@flatMap Observable.error(Throwable("解析响应失败: ${e.message}"))
                }
            }
    }

    private fun buildProvisionJson(ssid: String, psw: String, uid: String, brokers: String, randomCode: String): String {
        val root = JSONObject()
        root.put("ssid", ssid)
        root.put("pwd", psw)
        if (uid.isNotEmpty()) root.put("uid", uid)
        if (brokers.isNotEmpty()) {
            try {
                root.put("brokers", JSONArray(brokers))
            } catch (ignored: Exception) {
            }
        }
        root.put("code", randomCode)
        // JSONObject 默认会将 '/' 转义为 '\/'，部分嵌入式 Parser 可能不支持，手动替换回 '/'
        return root.toString().replace("\\/", "/")
    }


    // endregion

    // region ---------------- 内部实体类 ----------------

    sealed class PacketResult {
        object Ignore : PacketResult()
        data class Progress(val msgId: Int, val current: Int, val total: Int) : PacketResult()
        data class Complete(val msgId: Int, val payload: ByteArray) : PacketResult()
        data class Error(val error: Throwable) : PacketResult()
    }

    // endregion
}
