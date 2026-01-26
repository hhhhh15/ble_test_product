package com.homerunpet.homerun_pet_android_productiontest.ble.model

/**
 * @project: homerunpet
 * @author: homerun
 * @date: 2026/1/26 11:44
 * @description:
 */
data class DevicesProductsDetailBean(
    // 服务集群
    var brokers: List<String?>? = null,
    // 1:标准协议, 2:萤石协议
    var data_proto: Int? = null,
    // 产品Key
    var product_key: String? = null,
    // 产品密钥
    var product_secret: String? = null
)