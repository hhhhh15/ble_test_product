package com.homerunpet.homerun_pet_android_productiontest.ble.scanner

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.bhm.ble.BleManager
import com.bhm.ble.attribute.BleOptions
import com.bhm.ble.data.BleScanFailType
import com.bhm.ble.device.BleDevice
import com.homerunpet.homerun_pet_android_productiontest.BuildConfig
import com.homerunpet.homerun_pet_android_productiontest.ble.model.HMFastBleDevice
import com.homerunpet.homerun_pet_android_productiontest.ble.model.ProvisionProtocol
import com.homerunpet.homerun_pet_android_productiontest.ble.scanner.packet.HomerunBlePacket
import io.reactivex.rxjava3.core.Observable

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
            .setEnableLog(BuildConfig.DEBUG)
            .setMtu(247)
            .build()

        BleManager.get().init(context.applicationContext as Application, options)
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: HMBleScanner? = null

        // PF20 原始PID (Hex) -> 映射PID
        private const val PID_PF20_RAW = "C434E1B7"
        private const val PID_PF20_MAPPED = "GDA8052E04F99490CB6826"

        // PD50 原始PID (Hex) -> 映射PID
        private const val PID_PD50_RAW = "CA1D3150"
        private const val PID_PD50_MAPPED = "54528FB7E4C44EFCAD9E10"

        fun getInstance(context: Context): HMBleScanner {
            return instance ?: synchronized(this) {
                instance ?: HMBleScanner(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * 扫描所有BLE设备
     *
     * @param timeout 扫描超时时间(秒) - 注意：BleCore 使用全局配置超时，此处仅影响 Observable 的生命周期
     * @return Observable<HMFastBleDevice> 统一的设备模型流
     */
    fun scanDevices(timeout: Long = 90): Observable<HMFastBleDevice> {
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
            parsedRecord.manufacturerData,
            parsedRecord.manufacturerItems
        )

        // 自动解析序列号(EZIOT和自研) 和 配网状态
        var deviceSerial: String? = ""
        var provisionStatus: Boolean = true
        var provisionMode: Int = 0
        var pk: String? = ""

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
                for ((id, data) in items) {
                    // Android 会把厂商数据的前2个字节解析为 Company ID (Key)
                    // 但在该协议中，这两个字节是 Version 和 Function Mask，需要还原
                    val byte0 = (id and 0xFF).toByte()
                    val byte1 = ((id shr 8) and 0xFF).toByte()

                    val fullData = ByteArray(2 + data.size)
                    fullData[0] = byte0
                    fullData[1] = byte1
                    System.arraycopy(data, 0, fullData, 2, data.size)

                    val packet = HomerunBlePacket.parse(fullData)
                    if (packet != null) {
                        deviceSerial = packet.deviceNameSuffix
                        // 配网模式: 
                        // 0: Initial (首次配网，无网络信息)
                        // 1: Modify  (修改配网信息，有网络信息)
                        // 3: Reset   (重置设备，即使有网络信息也重新配网)
                        provisionMode = packet.provisionMode
                        provisionStatus = packet.isProvisionSupported
                        pk = packet.productKey
                        break
                    }
                }
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
            deviceSerial = deviceSerial?.uppercase(),
            provisionMode = provisionMode,
            pk = pk?.uppercase()
        )
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
                            // BLE是小端序(Little Endian), 低字节在前. 格式化为 "MSB LSB" (e.g. FCCC)
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

}
