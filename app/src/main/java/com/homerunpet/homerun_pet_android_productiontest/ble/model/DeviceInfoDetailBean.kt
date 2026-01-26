package com.homerunpet.homerun_pet_android_productiontest.ble.model

/**
 * @project: homerunpet
 * @author: homerun
 * @date: 2026/1/26 14:02
 * @description:
 */
data class DeviceInfoDetailBean(
    var create_time: Int? = null,
    var device_name: String? = null,
    var first_online_time: Int? = null,
    var is_online: Boolean? = null,
    var last_offline_time: Long? = null,
    var last_online_time: Int? = null,
    var product_key: String? = null
)