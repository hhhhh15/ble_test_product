package com.homerunpet.homerun_pet_android_productiontest.ble.provision.impl

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.bhm.ble.BleManager
import com.bhm.ble.device.BleDevice
import com.drake.net.Get
import com.drake.net.utils.scopeNet
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.ble.model.DeviceInfoDetailBean
import com.homerunpet.homerun_pet_android_productiontest.ble.model.DevicesProductsDetailBean
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.IProvisionProtocol
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionEvent
import com.homerunpet.v2.ble.provision.util.HomerunBleCipher
import com.homerunpet.v2.ble.provision.util.HomerunBleGattHelper
import com.homerunpet.v2.ble.provision.util.HomerunBlePacketUtils
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.json.JSONArray
import org.json.JSONObject
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
        private const val TAG = "HomerunProvision"

        // 服务 UUID
        const val SERVICE_UUID = "0000ace0-0000-1000-8000-00805f9b34fb"

        // 特征值 UUID 定义
        // Read - 获取设备所属用户 ID
        const val CHARACTERISTIC_USER_ID_UUID = "0000fc01-0000-1000-8000-00805f9b34fb"

        // Write/Indicate - 恢复设备出厂设置 (Indicate 用于接收结果)
        const val CHARACTERISTIC_FACTORY_RESET_UUID = "0000fc02-0000-1000-8000-00805f9b34fb"

        // Write/Indicate - 配网信息下发与状态监听
        const val CHARACTERISTIC_CONFIG_UUID = "0000fc04-0000-1000-8000-00805f9b34fb"
    }

    // 当前操作的产品信息
    private var currentProduct: Product? = null

    // 当前连接的 BLE 设备
    private var connectedBleDevice: BleDevice? = null

    // 设备详情信息
    private var isDeviceDetailFetched = false
    private var deviceDetail: DevicesProductsDetailBean? = null

    // 连接开始时间 (秒级时间戳)，用于校验设备上线时间
    private var connectionStartTime: Long = 0L

    // 接收包缓存: MsgID -> (Seq -> Payload)
    private val packetBuffer = ConcurrentHashMap<Int, TreeMap<Int, ByteArray>>()

    // endregion

    // region ---------------- 接口实现 (IProvisionProtocol) ----------------

    override fun setProduct(product: Product) {
        this.currentProduct = product
    }

    override fun getProduct(): Product? {
        return currentProduct
    }

    /**
     * 连接设备
     * 1. 强制断开旧连接
     * 2. 发起新连接
     * 3. 打印服务列表 (调试用)
     */
    override fun connect(): Observable<ProvisionEvent> {
        val product = currentProduct ?: return Observable.error(Throwable("未设置产品信息"))
        val mac = product.address ?: return Observable.error(Throwable("设备MAC地址为空"))
        val productKey = product.product_key ?: return Observable.error(Throwable("产品Key为空"))

        // 每次连接前重置详情状态
        isDeviceDetailFetched = false
        deviceDetail = null

        // 1. 先执行蓝牙连接
        return Observable.create<Boolean> { emitter ->
            HmLog("[连接] 正在连接设备: $mac")
            // 确保断开之前的连接
            BleManager.get().disConnect(mac)
            Thread.sleep(100)
            connectBleDevice(mac, emitter)
        }
            .flatMap { connectSuccess ->
                if (connectSuccess) {
                    // 记录连接成功的本地时间 (秒级，与接口字段单位一致)
                    connectionStartTime = System.currentTimeMillis() / 1000
                    HmLog("[连接] 记录连接启动时间: $connectionStartTime")

                    // 2. 连接成功后，获取设备详情
                    fetchDeviceDetail(productKey).map { detailSuccess ->
                        if (!detailSuccess) {
                            HmLog("[连接] 获取设备详情失败，配网前将再次尝试")
                        }
                        true // 返回 true 表示连接成功，允许继续后续流程
                    }
                } else {
                    Observable.just(false)
                }
            }
            .flatMap { success ->
                if (success) {
                    val bleDevice = connectedBleDevice ?: return@flatMap Observable.error<ProvisionEvent>(Throwable("Unexpected: Device null"))

                    HmLog("[连接] 连接成功，开始协商 MTU...")
                    HomerunBleGattHelper.setMtu(bleDevice, 247)
                        .map { mtu ->
                            HmLog("[连接] MTU协商完成: $mtu, 准备服务Dump...")
                            HomerunBleGattHelper.dumpGattServices(bleDevice)
                            ProvisionEvent.Success(mac) as ProvisionEvent
                        }
                } else {
                    HmLog("[连接] 连接失败")
                    Observable.error(Throwable("蓝牙连接失败"))
                }
            }
            .subscribeOn(Schedulers.io())
    }

    private fun fetchDeviceDetail(productKey: String): Observable<Boolean> {
        return Observable.create { emitter ->
            val scope = scopeNet {
                try {
                    val detail = Get<DevicesProductsDetailBean?>(HmApi.getDeviceDetail(productKey)).await()
                    if (!emitter.isDisposed) {
                        if (detail != null) {
                            HmLog("[详情] 获取设备详情成功: $productKey")
                            deviceDetail = detail
                            isDeviceDetailFetched = true
                            emitter.onNext(true)
                        } else {
                            HmLog("[详情] 获取设备详情返回 Null: $productKey")
                            emitter.onNext(false)
                        }
                    }
                } catch (e: Exception) {
                    HmLog("[详情] 获取设备详情异常: ${e.message}")
                    if (!emitter.isDisposed) emitter.onNext(false)
                }
                if (!emitter.isDisposed) emitter.onComplete()
            }
            emitter.setCancellable {
                HmLog("[详情] 外部取消获取详情 - $productKey")
                scope.cancel()
            }
        }
    }

    /**
     * 执行首次配网流程
     *
     * 流程:
     * 1. 密钥准备 (Secret -> AES Key).
     * 2. 开启 Indicate 监听 (FC04).
     * 3. 发送配网信息 (SSID/PWD/UID/Brokers).
     */
    override fun configureWifi(ssid: String, password: String, extraParams: Map<String, Any>?): Observable<ProvisionEvent> {
        return Observable.defer {
            val productKey = currentProduct?.product_key ?: return@defer Observable.error(Throwable("产品Key为空"))

            // 如果之前获取详情失败了，这里再尝试一次
            val ensureDetailObservable = if (!isDeviceDetailFetched) {
                HmLog("[配网] 详情未获取，尝试重新获取...")
                fetchDeviceDetail(productKey).flatMap { success ->
                    if (success) {
                        Observable.just(Unit)
                    } else {
                        HmLog("[配网] 再次尝试获取详情依然失败，配网流程终止")
                        Observable.error(Throwable("获取设备密钥详情失败"))
                    }
                }
            } else {
                Observable.just(Unit)
            }

            ensureDetailObservable.flatMap {
                // 增加缓冲时间，等待连接稳定/MTU协商完成
                try {
                    Thread.sleep(500)
                } catch (ignored: Exception) {
                }

                val bleDevice = connectedBleDevice ?: return@flatMap Observable.error(Throwable("设备未连接"))
                currentProduct ?: return@flatMap Observable.error(Throwable("未设置产品信息"))

                // TODO: 登录后获取 
                val uid = ""
                // 必须从详情中获取 Brokers
                val detailBrokers = deviceDetail?.brokers
                if (detailBrokers.isNullOrEmpty()) {
                    HmLog("[配网] 错误: Brokers 为空，无法配网")
                    return@flatMap Observable.error(Throwable("服务器地址配置缺失"))
                }
                val brokers = JSONArray(detailBrokers).toString()

                // 生成随机 Code 用于校验
                val randomCode = (100000..999999).random().toString()
                HmLog("[配网] 流程启动: SSID=$ssid, Code=$randomCode")

                // 必须从详情中获取 Secret
                val secretHex = deviceDetail?.product_secret
                if (secretHex.isNullOrEmpty()) {
                    HmLog("[配网] 错误: 设备密钥为空，无法配网")
                    return@flatMap Observable.error(Throwable("设备密钥缺失"))
                }
                val aesKey = HomerunBleCipher.generateKey(secretHex)

                val sendTrigger = PublishSubject.create<ProvisionEvent>()

                // 首次配网：直接开始监听并发送
                val listenObservable = waitForProvisionResponse(aesKey) {
                    HmLog("[配网] 监听就绪，开始发送配置数据...")
                    sendProvisionInfo(bleDevice, ssid, password, uid, brokers, randomCode, aesKey)
                        .subscribe(sendTrigger)
                }

                Observable.merge(listenObservable, sendTrigger)
            }
        }
            .subscribeOn(Schedulers.io())
    }

    /**
     * 查询配网信息和设备状态（自研的话默认成功，合并到checkOnlineStatus一个方法中）
     */
    override fun checkBindStatus(deviceSerial: String): Observable<ProvisionEvent> {
        HmLog("[查询] 检查绑定状态(Mock): 默认成功")
        return Observable.just(ProvisionEvent.Success(deviceSerial, "绑定成功"))
    }

    /**
     * 查询配网信息和设备状态 (使用设备详情校验上线)
     */
    override fun checkOnlineStatus(deviceSerial: String): Observable<ProvisionEvent> {
        return fetchDeviceInfoDetail(deviceSerial)
            .flatMap { detail ->
                val isOnline = detail.is_online ?: false
                val lastOnlineTime = detail.last_online_time ?: 0

                HmLog("[查询] 设备状态: isOnline=$isOnline, lastOnline=$lastOnlineTime, startBound=$connectionStartTime")

                // 条件：1. 在线 2. 最后上线时间 >= 本次配网连接启动时间
                if (isOnline && lastOnlineTime.toLong() >= connectionStartTime) {
                    HmLog("[查询] 设备已真正上线 (新激活)")
                    Observable.just(ProvisionEvent.Success(deviceSerial, "设备已上线", detail))
                } else {
                    HmLog("[查询] 设备尚未上线或仍为旧状态")
                    Observable.empty<ProvisionEvent>()
                }
            }
            .subscribeOn(Schedulers.io())
    }

    private fun fetchDeviceInfoDetail(deviceSerial: String): Observable<DeviceInfoDetailBean> {
        return Observable.create { emitter ->
            val scope = scopeNet {
                try {
                    val detail = Get<DeviceInfoDetailBean?>(HmApi.getDeviceInfoDetail(deviceSerial)).await()
                    if (!emitter.isDisposed && detail != null) {
                        emitter.onNext(detail)
                    }
                } catch (e: Exception) {
                    HmLog("[详情] 获取设备信息详情异常: ${e.message}")
                }
                if (!emitter.isDisposed) {
                    emitter.onComplete()
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
     * 修改/重置配网 - 第一步
     *
     * * 独立流程，包含设备归属权校验。
     * * 校验成功后：
     *   - `isReset=false`: 仅返回成功，后续由外部调用 [configureWifi] 修改网络。
     *   - `isReset=true`: 自动执行 [performFactoryReset] 发送重置指令。
     *
     * @param isReset true: 重置设备; false: 修改网络
     */
    fun modifyNetwork(isReset: Boolean = false): Observable<ProvisionEvent> {
        return Observable.defer {
            val bleDevice = connectedBleDevice ?: return@defer Observable.error(Throwable("设备未连接"))
            val productKey = currentProduct?.product_key ?: return@defer Observable.error(Throwable("产品Key为空"))

            // 同样确保详情已获取
            val ensureDetailObservable = if (!isDeviceDetailFetched) {
                HmLog("[修改] 详情未获取，尝试重新获取...")
                fetchDeviceDetail(productKey).flatMap { success ->
                    if (success) {
                        Observable.just(Unit)
                    } else {
                        HmLog("[修改] 再次尝试获取详情依然失败")
                        Observable.error(Throwable("获取设备密钥详情失败"))
                    }
                }
            } else {
                Observable.just(Unit)
            }

            ensureDetailObservable.flatMap {
                // TODO: 登录后获取 
                val uid = ""

                val secretHex = deviceDetail?.product_secret
                if (secretHex.isNullOrEmpty()) {
                    HmLog("[修改] 错误: 设备密钥为空")
                    return@flatMap Observable.error(Throwable("设备密钥缺失"))
                }
                val aesKey = HomerunBleCipher.generateKey(secretHex)

                // 先校验归属权
                verifyDeviceOwner(bleDevice, aesKey, uid)
                    .flatMap {
                        // 校验通过
                        if (isReset) {
                            // 重置配网
                            HmLog("[重置] 归属校验通过，执行重置指令(FC02)...")
                            performFactoryReset(bleDevice, aesKey, uid)
                        } else {
                            HmLog("[修改] 归属校验通过，流程结束 (等待外部调用配网)")
                            Observable.just(
                                ProvisionEvent.Success(
                                    bleDevice.deviceAddress.orEmpty(),
                                    "校验通过(修改)"
                                ) as ProvisionEvent
                            )
                        }
                    }
            }
        }.subscribeOn(Schedulers.io())
    }

    // endregion

    // region ---------------- 私有业务逻辑 (Private Logic) ----------------

    /**
     * 连接逻辑封装
     */
    private fun connectBleDevice(mac: String, emitter: io.reactivex.rxjava3.core.ObservableEmitter<Boolean>) {
        BleManager.get().connect(mac) {
            onConnectStart {
                HmLog("[连接] 蓝牙连接开始: $mac")
            }
            onConnectSuccess { bleDevice, _ ->
                HmLog("[连接] 蓝牙连接成功: $mac")
                connectedBleDevice = bleDevice
                if (!emitter.isDisposed) {
                    emitter.onNext(true)
                    emitter.onComplete()
                }
            }
            onConnectFail { _, exception ->
                HmLog("[连接] 蓝牙连接失败: $mac, 原因: $exception")
                if (!emitter.isDisposed) {
                    emitter.tryOnError(Throwable("连接失败: $exception"))
                }
            }
            onDisConnecting { _, _, _, _ ->
                HmLog("[连接] 蓝牙正在断开: $mac")
            }
            onDisConnected { _, _, _, _ ->
                HmLog("[连接] 蓝牙已断开: $mac")
                connectedBleDevice = null
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
            .flatMap { bytes ->
                try {
                    // 解密
                    val decrypted = HomerunBleCipher.decrypt(bytes, aesKey)
                    // 去除可能的填充或空格
                    val deviceUid = String(decrypted, Charsets.UTF_8).trim()

                    HmLog("[鉴权] 校验归属: Device=$deviceUid, App=$currentUid")

                    if (deviceUid == currentUid) {
                        Observable.just(Unit)
                    } else {
                        Observable.error(Throwable("无权限操作: 设备归属于其他用户"))
                    }
                } catch (e: Exception) {
                    HmLog("[鉴权] 校验归属失败(解析): ${e.message}")
                    Observable.error(Throwable("校验设备归属失败: 数据解析错误"))
                }
            }
    }

    /**
     * 核心: 执行恢复出厂设置
     *
     * 1. 开启 Indicate (FC02).
     * 2. 发送重置指令 (Write FC02).
     * 3. 等待响应 ({"d":...} 或 {"e":...}).
     */
    private fun performFactoryReset(bleDevice: BleDevice, aesKey: ByteArray, uid: String): Observable<ProvisionEvent> {
        val sendTrigger = PublishSubject.create<Boolean>()

        // 1. 开启 Indicate
        return HomerunBleGattHelper.indicate(
            bleDevice,
            SERVICE_UUID,
            CHARACTERISTIC_FACTORY_RESET_UUID
        ) {
            // Indicate 开启成功，信号触发发送
            sendTrigger.onNext(true)
        }
            // 2. 接收数据处理
            .flatMap { packet ->
                // 解析包结构 (处理粘包/分包逻辑由 Utils 完成)
                val result = HomerunBlePacketUtils.parsePacket(packet)
                if (result.packets.isEmpty()) {
                    Observable.empty()
                } else {
                    Observable.fromIterable(result.packets)
                        .map { it.payload }
                }
            }
            .map { encryptedPayload ->
                // 解密
                val decrypted = HomerunBleCipher.decrypt(encryptedPayload, aesKey)
                val jsonStr = String(decrypted, Charsets.UTF_8).trim()
                HmLog("[重置] 收到响应: $jsonStr")
                JSONObject(jsonStr)
            }
            .map { json ->
                if (json.has("e")) {
                    throw Throwable("重置失败: ${json.getString("e")}")
                }
                if (json.has("d")) {
                    // 成功，提取 auth 和 nonce
                    val dataObj = json.getJSONObject("d")
                    val auth = dataObj.optString("auth", "")
                    val nonce = dataObj.optString("nonce", "")

                    val msg = "设备已重置 (Auth=$auth, Nonce=$nonce)"
                    return@map ProvisionEvent.Success(bleDevice.deviceAddress.orEmpty(), msg) as ProvisionEvent
                }
                throw Throwable("无效响应")
            }
            // 3. 合并发送逻辑
            .mergeWith(
                sendTrigger.take(1).flatMap {
                    try {
                        val cmdJson = JSONObject()
                        cmdJson.put("uid", uid)
                        val cmdStr = cmdJson.toString()
                        HmLog("[重置] 发送指令: $cmdStr")

                        // 加密 & 打包
                        val encrypted = HomerunBleCipher.encrypt(cmdStr.toByteArray(Charsets.UTF_8), aesKey)
                        val packets = HomerunBlePacketUtils.packData(encrypted)

                        // 串行发送所有包
                        Observable.fromIterable(packets)
                            .concatMap { pkt ->
                                HomerunBleGattHelper.write(bleDevice, SERVICE_UUID, CHARACTERISTIC_FACTORY_RESET_UUID, pkt)
                            }
                            .ignoreElements()
                            .toObservable()
                    } catch (e: Exception) {
                        Observable.error(e)
                    }
                }
            )
            // 4. 只要收到一个有效 Success Event 就结束
            .filter { it is ProvisionEvent.Success }
            .take(1)
            .timeout(10, TimeUnit.SECONDS)
    }

    /**
     * 发送配网信息 (Write FC04)
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
                HmLog("[配网] 发送Payload: $jsonPayload")

                val rawBytes = jsonPayload.toByteArray(Charsets.UTF_8)
                HmLog("[配网] 原始数据(Hex): ${rawBytes.joinToString("") { "%02X".format(it) }}")

                // 2. 加密
                val encryptedBytes = HomerunBleCipher.encrypt(rawBytes, aesKey)
                HmLog("[配网] 加密后数据(Hex): ${encryptedBytes.joinToString("") { "%02X".format(it) }}")

                // 3. 分包
                val packets = HomerunBlePacketUtils.packData(encryptedBytes)

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
                        { error -> if (!emitter.isDisposed) emitter.onError(error) },
                        { if (!emitter.isDisposed) emitter.onComplete() }
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
                    val decrypted = HomerunBleCipher.decrypt(result.payload, aesKey)

                    // Debug Log
                    val hexPayload = result.payload.joinToString("") { "%02X".format(it) }
                    val hexDecrypted = decrypted.joinToString("") { "%02X".format(it) }
                    HmLog("[配网] 加密payload(Hex): $hexPayload")
                    HmLog("[配网] 解密payload(Hex): $hexDecrypted")

                    val jsonStr = String(decrypted, Charsets.UTF_8)
                    HmLog("[配网] 收到响应: $jsonStr")

                    val jsonObj = JSONObject(jsonStr)

                    // 检查错误 'e'
                    if (jsonObj.has("e")) {
                        val errorCode = jsonObj.getString("e")
                        val errorDesc = when (errorCode) {
                            "INVALID_DATA" -> "无效数据"
                            "INVALID_USER_ID" -> "无效用户ID"
                            "INVALID_WIFI_CREDENTIALS" -> "SSID 或密码错误"
                            "MQTT_CONNECT_TIMEOUT" -> "连接 MQTT 超时"
                            "MQTT_AUTH_FAILED" -> "MQTT 认证失败"
                            "MQTT_PUBLISH_FAILED" -> "发送配网事件失败"
                            "INVALID_CONFIRM_CODE" -> "无效确认码"
                            else -> "未知错误"
                        }
                        return@flatMap Observable.error(Throwable("配网失败: $errorDesc ($errorCode)"))
                    }

                    // 检查进度 'd'
                    if (jsonObj.has("d")) {
                        val progressCode = jsonObj.getString("d")
                        return@flatMap when (progressCode) {
                            "1" -> Observable.just(ProvisionEvent.Progress("connecting_ap", 20, "设备连接路由中"))
                            "2" -> Observable.just(ProvisionEvent.Progress("getting_ip", 50, "获取网络配置中"))
                            "3" -> Observable.just(ProvisionEvent.Progress("connecting_mqtt", 80, "连接服务器中"))

                            // 4: 保存配网信息成功 -> 视为配网流程结束
                            "4" -> {
                                val mac = currentProduct?.address ?: ""
                                Observable.just(ProvisionEvent.Success(mac, "配网成功"))
                            }

                            else -> Observable.just(ProvisionEvent.Progress("unknown", 10, "处理中: $progressCode"))
                        }
                    }

                    // 空对象 {} -> 参数校验通过，开始等待进度通知
                    HmLog("[配网] 参数校验通过，等待设备连接...")
                    return@flatMap Observable.just(ProvisionEvent.Progress("verified", 0, "等待配网"))

                } catch (e: Exception) {
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

    private fun HmLog(msg: String) {
        Log.d(TAG, msg)
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
