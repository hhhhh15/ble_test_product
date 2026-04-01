package com.homerunpet.homerun_pet_android_productiontest.data

import com.google.gson.annotations.SerializedName

data class TestReportDetailBean (
    val device_name: String,
    val sn: String,
    val status: Int,
    val url: String
)

data class DeviceMessage(
    val device_name: String,
    val firmware_data: List<FirmwareData>,
    val is_online: Boolean,
    val product_key: String,
    val sn: String
)

data class FirmwareData(
    val module: Int,
    val series: String,
    val target_version: String,
    val version: String
)
