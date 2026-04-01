package com.homerunpet.homerun_pet_android_productiontest.ble.model

/**
 * 统一的BLE设备模型
 * 所有扫描到的设备都统一使用这个模型
 *
 * @date 2026-01-08
 */
data class HMFastBleDevice(
    val mac: String,                        // MAC地址
    val name: String,                       // 设备名称
    val rssi: Int,                          // 信号强度
    val serviceUuids: List<String> = emptyList(),       // 服务UUID列表
    val manufacturerItems: Map<Int, ByteArray> = emptyMap(), // 完整的厂商数据列表 {CompanyId -> Data}
    val provisionStatus: Boolean = true,       // 配网状态 (true表示可配网)

    // 自动识别的配网协议
    var protocol: ProvisionProtocol = ProvisionProtocol.UNKNOWN,
    // 扫描出来的序列号（不一定是准确的，针对PF20这种，冒号后会少三位，真正的序列号是在获取设备信息的时候）
    var deviceSerial: String? = null,       // 设备序列号
    // 只针对自研协议
    var provisionMode: Int? = 0,
    // Product Key 相当于 萤石的pid
    var pk: String? = "",
    var dn: String? = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HMFastBleDevice
        return mac == other.mac
    }

    override fun hashCode(): Int {
        return mac.hashCode()
    }

    override fun toString(): String {
        return "HMFastBleDevice(name='$name', mac='$mac', protocol=$protocol, rssi=$rssi)"
    }
}

/**
 * 配网协议枚举
 */
enum class ProvisionProtocol {
    EZIOT,              // 萤石IoT协议
    BLUFI,              // ESP BluFi协议
    HOMERUN_CUSTOM,     // 霍曼自研协议（新）
    AP,                 // AP配网协议
    UNKNOWN             // 未知协议
}
