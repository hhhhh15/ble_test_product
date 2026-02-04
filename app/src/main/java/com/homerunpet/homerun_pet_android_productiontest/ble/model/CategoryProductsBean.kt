package com.homerunpet.homerun_pet_android_productiontest.ble.model

import com.drake.brv.item.ItemExpand

/**
 * @project: homerunpet
 * @author: homerun
 * @date: 2025/11/29 14:00
 * @description:
 */
data class CategoryProductsBean(
    var `data`: List<CategoryProductData?>? = null,
    var version: String? = null
)

data class CategoryProductData(
    var category_key: String? = null,
    var name: String? = null,
    var products: List<Product?>? = null,
    var update_time: Int? = null
) : ItemExpand {
    // 同级别分组的索引位置
    override var itemGroupPosition: Int = 0

    // 当前条目是否展开
    override var itemExpand: Boolean = true

    // 返回子列表
    override fun getItemSublist(): List<Product?>? {
        return products
    }

}

data class Product(
    var category_key: String? = null,
    // 1:Wi-Fi+蓝牙, 2:Cellular+蓝牙, 3:蓝牙
    var comm_mode: Int? = null,
    // 1:标准协议, 2:萤石协议
    var data_proto: Int? = null,
    var icon_url: String? = null,
    var is_visible: Boolean? = null,
    var name: String? = null,
    // 1:2.4G, 2:2.4G+5G, 3:Cellular
    var network_type: Int? = null,
    // 1:直连设备, 2:网关设备, 3:网关子设备
    var node_type: Int? = null,
    var product_key: String? = null,
    var product_keys: List<String?>? = null,
    var update_time: Int? = null,
    var isSelected: Boolean = false,//✔云端没有，UI 列表选择状态
    var address: String? = null,//✔云端没有，BLE MAC 地址
    // 配网图
    var prov_video_url: String? = null,
    // 配网提示语
    var prov_tips: String? = null,
    // 设备wifi名指引图
    var ap_wifi_img_url: String? = null,
    // 序列号 带冒号
    var deviceSerial: String? = null,//✔云端没有，扫描或设备返回的序列号
    var is_ipc: Boolean? = false,
    var allowed_keys: List<String?>? = null,
    // "VF20/PF20"
    var product_model: String? = null,
    // 完整的 HMFastBleDevice 对象，包含协议、RSSI等信息
    var hmFastBleDevice: HMFastBleDevice? = null,//✔云端没有
    // 最后一次扫描到的时间戳 (ms)，用于超时移除
    var lastSeenTime: Long = 0L//✔云端没有
)