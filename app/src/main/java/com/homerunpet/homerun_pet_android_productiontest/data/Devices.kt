package com.homerunpet.homerun_pet_android_productiontest.data

data class DeviceListResponse(
    val data: List<Devices>
)
data class Devices(
    val device_name:String,
    val alias:String,
    val product_key:String,
    val is_online:Boolean

)
data class LogUploadData(
    val log_upload_url: String
)
