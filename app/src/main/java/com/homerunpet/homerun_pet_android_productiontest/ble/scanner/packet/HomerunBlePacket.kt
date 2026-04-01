package com.homerunpet.v2.ble.scanner.packet

/**
 * 霍曼自研协议广播包解析类
 * 对应 Manufacturer Specific Data (AD Type 0xFF)
 *
 * 数据结构参考文档《设备扫描响应定义》:
 * Byte 0: Version & Type
 * Byte 1: Function Mask
 * Byte 2: Reserved Mask
 * Byte 3-6: Product Key (4 bytes)
 * Byte 7-N: Device Name / Suffix (Variable length, remaining bytes)
 */
data class HomerunBlePacket(
    /**
     * 自定义协议版本 (Byte 0 的 Bit 0-3)
     * e.g. 0b0001 = 1.0
     */
    val protocolVersion: Int,

    /**
     * 设备类型 (Byte 0 的 Bit 4-7)
     * e.g. 0b1000 = 蓝牙 GATT 设备
     */
    val deviceType: Int,

    /**
     * 蓝牙版本 (Byte 1 的 Bit 0-3)
     * e.g. 0b0001 = BLE 4.2
     */
    val bleVersion: Int,

    /**
     * 配网标识 (Byte 1 的 Bit 4-6)
     * 0b000: Initial (首次配网)
     * 0b001: Modify (修改配网信息)
     */
    val provisionMode: Int,

    /**
     * 是否支持配网 (Byte 1 的 Bit 7)
     * true: 支持
     * false: 不支持
     */
    val isProvisionSupported: Boolean,

    /**
     * 产品Key (4 bytes Hex string)
     */
    val productKey: String,

    /**
     * 设备名称/标识后缀 (变长 Hex string)
     * 用于生成 deviceSerial (e.g. def678901234...)
     */
    val deviceNameSuffix: String
) {
    companion object {
        fun parse(data: ByteArray?): HomerunBlePacket? {
            // 数据长度校验：至少包含前 7 字节 (Version to Product Key)
            if (data == null || data.size < 7) return null

            // --- Byte 0: Version & Type (如 0x83) ---
            val byte0 = data[0].toInt()
            // 取低4位 (0000 1111) -> 协议版本
            val protocolVersion = byte0 and 0x0F
            // 取高4位 (1111 0000) -> 右移4位 -> 设备类型
            val deviceType = (byte0 shr 4) and 0x0F

            // --- Byte 1: Function Mask (如 0x81) ---
            val byte1 = data[1].toInt()
            // 取低4位 -> 蓝牙版本
            val bleVersion = byte1 and 0x0F
            // 取 Bit 4-6 -> 右移4位并与 0x07(0111) 与操作 -> 配网标识
            val provisionMode = (byte1 shr 4) and 0x07
            // 取 Bit 7 -> 检查最高位是否为1 -> 是否支持配网
            val isProvisionSupported = (byte1 and 0x80) != 0

            // --- Byte 2: Reserved (0x00) 保留位，直接跳过 ---

            // --- Byte 3-6: Product Key (4 bytes) ---
            val productKey = bytesToHex(data, 3, 4)

            // --- Byte 7-N: Device Name/ID (Remaining bytes) ---
            val deviceId = bytesToHex(data, 7, data.size - 7)

            return HomerunBlePacket(
                protocolVersion = protocolVersion,
                deviceType = deviceType,
                bleVersion = bleVersion,
                provisionMode = provisionMode,
                isProvisionSupported = isProvisionSupported,
                productKey = productKey,
                deviceNameSuffix = deviceId
            )
        }

        private fun bytesToHex(bytes: ByteArray, start: Int, length: Int): String {
            val sb = StringBuilder()
            for (i in start until (start + length)) {
                if (i < bytes.size) {
                    sb.append(String.format("%02X", bytes[i]))
                }
            }
            return sb.toString()
        }
    }
}
