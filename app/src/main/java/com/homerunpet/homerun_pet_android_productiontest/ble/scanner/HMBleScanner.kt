package com.homerunpet.homerun_pet_android_productiontest.ble.scanner

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.bhm.ble.BleManager
import com.bhm.ble.attribute.BleOptions
import com.bhm.ble.data.BleScanFailType
import com.bhm.ble.device.BleDevice
import com.homerunpet.homerun_pet_android_productiontest.ble.model.HMFastBleDevice
import com.homerunpet.homerun_pet_android_productiontest.ble.model.ProvisionProtocol
import com.homerunpet.homerun_pet_android_productiontest.ble.scanner.packet.HomerunBlePacket
import com.homerunpet.homerun_pet_android_productiontest.common.ext.saveScanLog
import com.homerunpet.v2.ble.scanner.DeviceIdentifier
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一BLE扫描器 - 唯一的扫描入口
 * 所有配网流程都使用此扫描器
 *
 * 特性：
 * - 基于 BleCore (com.github.buhuiming:BleCore) 实现
 * - 自动识别设备协议
 * - 支持设备去重
 * - 响应式流式返回
 *
 * @date 2026-01-08
 */
class HMBleScanner private constructor(
    private val context: Context
) {

    init {
        // 初始化配置
        val options = BleOptions.builder()
            .setEnableLog(false)
            .build()

        BleManager.get().init(context.applicationContext as Application, options)
    }

    // region ------------------------------ 静态常量与单例 ------------------------------
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: HMBleScanner? = null

        // PD50 原始PID (Hex) -> 映射PID
        private const val PID_PD50_RAW = "CA1D3150"
        private const val PID_PD50_MAPPED = "54528FB7E4C44EFCAD9E10"

        // PF20 原始PID (Hex) -> 映射PID
        private const val PID_PF20_RAW = "C434E1B7"
        private const val PID_PF20_MAPPED = "GDA8052E04F99490CB6826"

        fun getInstance(context: Context): HMBleScanner {
            return instance ?: synchronized(this) {
                instance ?: HMBleScanner(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    // endregion

    // region ------------------------------ 扫描逻辑 ------------------------------

    /**
     * 扫描所有BLE设备
     *
     * @param timeout 扫描超时时间(秒) - 注意：BleCore 使用全局配置超时，此处仅影响 Observable 的生命周期
     * @return Observable<HMFastBleDevice> 统一的设备模型流
     */
    fun scanDevices(timeout: Long = 1800): Observable<HMFastBleDevice> {
        return Observable.create { emitter ->
            // 开始扫描
            BleManager.get().startScan(scanMillisTimeOut = timeout * 1000L, 0, 1000) {
                onScanStart {
                    // 扫描开始
                }

                onLeScan { bleDevice, currentScanCount ->
                    val domainDevice = mapToDomainDevice(bleDevice)
                    emitter.onNext(domainDevice)
                }

                onLeScanDuplicateRemoval { bleDevice, currentScanCount ->
                    // 如需去重逻辑可在此处理
                }

                onScanComplete { bleDeviceList, bleDeviceDuplicateRemovalList ->
                    if (!emitter.isDisposed) {
                        emitter.onComplete()
                    }
                }

                onScanFail { failType ->
                    val msg: String = when (failType) {
                        is BleScanFailType.UnSupportBle -> "BleScanFailType.UnSupportBle: 设备不支持蓝牙"
                        is BleScanFailType.NoBlePermission -> "BleScanFailType.NoBlePermission: 权限不足，请检查"
                        is BleScanFailType.GPSDisable -> "BleScanFailType.GPSDisable: 设备未打开GPS定位"
                        is BleScanFailType.BleDisable -> "BleScanFailType.BleDisable: 蓝牙未打开"
                        is BleScanFailType.AlReadyScanning -> "BleScanFailType.AlReadyScanning: 正在扫描"
                        is BleScanFailType.ScanError -> {
                            "BleScanFailType.ScanError: ${failType.throwable?.message}"
                        }

                        else -> "Unknown Error"
                    }
                    if (!emitter.isDisposed) {
                        emitter.onError(Throwable("Scan failed with type: $msg"))
                    }
                }
            }

        }
    }

    /**
     * 扫描特定协议的设备
     *
     * @param protocol 配网协议类型
     * @param timeout 扫描超时时间(秒)
     */
    fun scanDevicesByProtocol(protocol: ProvisionProtocol, timeout: Long = 30): Observable<HMFastBleDevice> {
        return scanDevices(timeout)
            .filter { it.protocol == protocol }
    }

    /**
     * 停止扫描
     */
    fun stopScan() {
        BleManager.get().stopScan()
    }
    // endregion

    // region ------------------------------ 解析与日志逻辑 ------------------------------
    // 日志缓存：Mac -> (LastContent, LastTime)
    private val logCache = ConcurrentHashMap<String, Pair<String, Long>>()

    private fun mapToDomainDevice(bleDevice: BleDevice): HMFastBleDevice {
        val mac = bleDevice.deviceAddress ?: ""
        val name = bleDevice.deviceName ?: ""
        val rssi = bleDevice.rssi ?: 0
        // BleCore 的 BleDevice 对象通常包含 scanRecord
        val parsedRecord = parseScanRecord(bleDevice.scanRecord)

        // 1. 识别协议
        val protocol = DeviceIdentifier.identifyProtocol(
            name,
            parsedRecord.serviceUuids,
            parsedRecord.manufacturerItems
        )

        // 自动解析序列号(EZIOT和自研) 和 配网状态
        var deviceSerial: String? = ""
        var provisionStatus: Boolean = true
        var provisionMode: Int = 0
        var pk: String? = ""

        // Log specific vars (Custom Only)
        var logCompany = ""
        var logVer = ""
        var logDevType = ""
        var logMode = ""

        if (protocol == ProvisionProtocol.EZIOT) {
            val data2B18 = parsedRecord.manufacturerItems[0x2B18]
            if (data2B18 != null && data2B18.size >= 12) {
                // Byte 0: FMASK (Bit5: 配网状态)
                val fmask = data2B18[0].toInt()
                val isUnpaired = (fmask and 0x20) == 0
                provisionStatus = isUnpaired

                // Byte 1-6: MAC
                // Byte 7: Extend Info Header

                // Byte 8-11: Extend Info Data (PID)
                val pidBytes = data2B18.copyOfRange(8, 12)
                val pidHex = StringBuilder()
                for (b in pidBytes) {
                    pidHex.append(String.format("%02X", b))
                }
                val rawPid = pidHex.toString()

                // 特殊PID映射处理
                val finalPid = when (rawPid) {
                    // PF20的萤石设备单独映射PID
                    PID_PF20_RAW -> PID_PF20_MAPPED
                    // PD50的萤石设备单独映射PID
                    PID_PD50_RAW -> PID_PD50_MAPPED
                    else -> rawPid
                }

                // 拼接: PID:Name (符合 deviceId 格式 "A9012801:A024AA0A2")
                deviceSerial = "$finalPid:$name"
                pk = finalPid
            }
        } else if (protocol == ProvisionProtocol.HOMERUN_CUSTOM) {
            // 解析厂商自定义数据
            parsedRecord.manufacturerItems.let { items ->
                val homerunData = items[0xACE0]
                if (homerunData != null) {
                    // homerunData 已经是剔除了 CompanyID 后的 Payload (即从 Version/Type 开始)
                    val packet = HomerunBlePacket.parse(homerunData)
                    if (packet != null) {
                        deviceSerial = packet.deviceNameSuffix
                        // 配网模式:
                        // 0: Initial (首次配网，无网络信息)
                        // 1: Modify  (修改配网信息，有网络信息)
                        provisionMode = packet.provisionMode
                        provisionStatus = packet.isProvisionSupported
                        pk = packet.productKey

                        // 日志字段
                        logCompany = "ACE0"
                        logVer = packet.protocolVersion.toString()
                        logDevType = packet.deviceType.toString()
                        logMode = packet.provisionMode.toString()
                    }
                }
            }
        }

        // [HRSCAN][蓝牙广播名字][序列号][(类型：蓝牙1.3、萤石、自研）][(不可配网、可配网)][company:(自研特有，公司id)][ver:(自研特有，协议版本号)][（自研特有，蓝牙类型，GATT、Mesh、Beacon][(自研特有，配网模式：初次配网、修改配网)]
        val typeStr = when (protocol) {
            ProvisionProtocol.HOMERUN_CUSTOM -> "自研"
            ProvisionProtocol.EZIOT -> "萤石"
            ProvisionProtocol.BLUFI -> "ESP"
            else -> "未知"
        }
        val isProvStr = if (provisionStatus) "可配网" else "不可配网"

        // 转换自研协议字段为可读字符串
        val devTypeStr = when(logDevType) {
            "1" -> "GATT"
            "2" -> "Mesh"
            "3" -> "Beacon"
            else -> logDevType
        }

        val modeStr = when(logMode) {
             "0" -> "初次配网"
             "1" -> "修改配网"
             else -> logMode
        }

        // 仅在协议为非 UNKNOWN 时打印日志 (即只打印萤石、ESP/Blufi、自研)
        if (protocol != ProvisionProtocol.UNKNOWN) {
            val logContent = "[HRSCAN][$name][${deviceSerial ?: ""}][$typeStr][$isProvStr][company:$logCompany][ver:$logVer][$devTypeStr][$modeStr]"
            if (shouldPrintLog(mac, logContent)) {
                saveScanLog(logContent)
            }
        }

        return HMFastBleDevice(
            mac = mac,
            name = name,
            rssi = rssi,
            serviceUuids = parsedRecord.serviceUuids,
            manufacturerItems = parsedRecord.manufacturerItems,
            provisionStatus = provisionStatus,
            protocol = protocol,
            deviceSerial = deviceSerial,
            provisionMode = provisionMode,
            pk = pk
        )
    }

    private fun shouldPrintLog(mac: String, content: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastRecord = logCache[mac]

        if (lastRecord != null) {
            val (lastContent, lastTime) = lastRecord
            // 去重：只要内容与上一次一致，就永远不再打印
            if (lastContent == content) {
                return false
            }
        }

        // 更新缓存并允许打印
        logCache[mac] = Pair(content, currentTime)
        return true
    }

    private data class ParsedScanRecord(
        val serviceUuids: List<String>,
        val manufacturerData: ByteArray?,
        val manufacturerItems: Map<Int, ByteArray>
    )

    /**
     * 手动解析 BLE 广播包 (兼容所有 Android 版本)
     */
    private fun parseScanRecord(bytes: ByteArray?): ParsedScanRecord {
        val serviceUuids = mutableListOf<String>()
        val manufacturerItems = mutableMapOf<Int, ByteArray>()
        var manufacturerData: ByteArray? = null

        if (bytes == null) {
            return ParsedScanRecord(serviceUuids, manufacturerData, manufacturerItems)
        }

        var currentPos = 0
        try {
            while (currentPos < bytes.size) {
                // 1. 获取长度 (Length field itself is not included in length value)
                val length = bytes[currentPos].toInt() and 0xFF
                if (length == 0) {
                    break
                }

                // 2. 检查边界 (Length field (1) + Body (length))
                if (currentPos + 1 + length > bytes.size) {
                    break
                }

                // 3. 获取类型 (Ad Type is the first byte of Body)
                val type = bytes[currentPos + 1].toInt() and 0xFF

                // 数据内容长度 = 总长度 - 1 (减去类型占用的1字节)
                val dataLen = length - 1

                when (type) {
                    0x02, 0x03 -> { // 16位服务UUID (0x02:非完整列表; 0x03:完整列表)
                        var i = 0
                        // 每次读取2字节
                        while (i + 1 < dataLen) {
                            // 数据起点是 currentPos + 2 (Len + Type)
                            val lsb = bytes[currentPos + 2 + i].toInt() and 0xFF
                            val msb = bytes[currentPos + 2 + i + 1].toInt() and 0xFF
                            // BLE是小端序(Little Endian), 低字节在前. 格式化为 "MSB LSB" (e.g. ACE0)
                            val uuid = String.format("%02X%02X", msb, lsb)
                            serviceUuids.add(uuid)
                            i += 2
                        }
                    }

                    0xFF -> { // 厂商自定义数据
                        if (dataLen >= 2) {
                            // 前2字节是Company ID (小端序)
                            val lsb = bytes[currentPos + 2].toInt() and 0xFF
                            val msb = bytes[currentPos + 3].toInt() and 0xFF
                            val companyId = (msb shl 8) or lsb

                            // 剩余是数据内容 (dataLen - 2)
                            val contentLen = dataLen - 2
                            val content = ByteArray(contentLen)
                            System.arraycopy(bytes, currentPos + 4, content, 0, contentLen)
                            manufacturerItems[companyId] = content
                            // 兼容老逻辑：保留第一个厂商数据
                            if (manufacturerData == null) {
                                manufacturerData = content
                            }
                        }
                    }
                }

                // 移动指针: 1字节长度头 + 长度值
                currentPos += 1 + length
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ParsedScanRecord(serviceUuids, manufacturerData, manufacturerItems)
    }
    // endregion

}
