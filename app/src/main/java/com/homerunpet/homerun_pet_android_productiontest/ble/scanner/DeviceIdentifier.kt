package com.homerunpet.homerun_pet_android_productiontest.ble.scanner

import com.homerunpet.homerun_pet_android_productiontest.ble.model.ProvisionProtocol

/**
 * 设备识别器
 * 根据设备特征自动判断使用哪种配网协议
 * 
 * @date 2026-01-08
 */
object DeviceIdentifier {
    
    // 霍曼自定义UUID
    private const val HOMERUN_CUSTOM_UUID = "E0AC"

    /**
     * 识别设备支持的配网协议
     * 
     * 识别规则优先级：
     * 1. [EZIOT]:  包含萤石官方 Service UUID (0xFCCC)。
     * 2. [BLUFI]:  设备广播名称以 "HOMERUN" 开头 (忽略大小写)。
     * 3. [CUSTOM]: 包含霍曼自定义 Service UUID (0xE0AC)。
     * 
     * @param name 设备名称
     * @param serviceUuids 服务UUID列表
     * @param manufacturerData 厂商数据
     * @param manufacturerItems 完整的厂商数据Map
     * @return [ProvisionProtocol] 协议枚举
     */
    fun identifyProtocol(
        name: String,
        serviceUuids: List<String>,
        manufacturerData: ByteArray?,
        manufacturerItems: Map<Int, ByteArray> = emptyMap()
    ): ProvisionProtocol {
        // 1. EZIOT (萤石/霍曼合作设备)
        // 判定规则：必须同时包含 0x2B18 和 0x2B19，且 0x2B19 解析内容包含 "HOMERUN"
        val data2B18 = manufacturerItems[0x2B18]
        val data2B19 = manufacturerItems[0x2B19]
        
        if (data2B18 != null && data2B19 != null && data2B19.size > 2) {
            // 0x2B19 数据结构: [Type(1)][Length(1)][String...]
            // 跳过前2字节解析后面的 ASCII 字符串
            val manuName = String(data2B19, 2, data2B19.size - 2, Charsets.US_ASCII)
            if (manuName.contains("HOMERUN", true)) {
                return ProvisionProtocol.EZIOT
            }
        }
        
        // 2. BLUFI (ESP BluFi / 霍曼旧设备)
        if (name.startsWith("HOMERUN", true)) {
            if (serviceUuids.contains(HOMERUN_CUSTOM_UUID)) {
                return ProvisionProtocol.HOMERUN_CUSTOM
            }
            return ProvisionProtocol.BLUFI
        }
        
        return ProvisionProtocol.UNKNOWN
    }
    

    /**
     * 添加自定义识别规则
     * 用于扩展或覆盖默认识别逻辑
     */
    private val customRules = mutableListOf<(String, List<String>, ByteArray?) -> ProvisionProtocol?>()
    
    fun addCustomRule(rule: (name: String, serviceUuids: List<String>, manufacturerData: ByteArray?) -> ProvisionProtocol?) {
        customRules.add(rule)
    }
    
    fun clearCustomRules() {
        customRules.clear()
    }
}
