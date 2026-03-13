package com.homerunpet.homerun_pet_android_productiontest.common.bean

data class HMBaseResponse<T>(
    var page: Int? = 0,
    var limit: Int? = 0,
    var total: Int? = 0,
    var data: T? = null,
    var error_msg: String? = null,
    var error_code: String? = null,
    var version: String? = null
)
