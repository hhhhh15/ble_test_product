package com.homerunpet.homerun_pet_android_productiontest.data



data class TotalData(
    val events: List<Event>?,
    val properties: List<Property>?,//属性
    val services: List<Service>?,
    val version:String?
)
sealed interface ModelData {
    val type: String?
}

data class Event(
    val id: Int,
    val identifier: String,
    val name: String,
    override val type: String,
    val desc: String?,
    val required: Boolean,
    val output_data: List<OutputData>?
):ModelData

data class OutputData(
    val desc:String?,
    val identifier:String,
    val name:String,
    val specs: Specs?,
    val type:String,
)

data class Property(
    val id: Int,
    val identifier: String,
    val name: String,
    override val type: String,
    val desc: String,
    val access_mode: String,
    val required: Boolean,
    val specs: Specs?,
):ModelData

data class InputData(
    val desc: String? = null,
    val identifier: String? = null,
    val name: String? = null,
    val specs: Specs? = null,
    val type: String? = null,
)

data class Specs(
    val max: Int?,
    val min: Int?,
    val step: Int?,
    val mult: Int?,
    val unit: String?,
    val enum: List<String>?,
    val size:Int?
)

/**
 * 注意：service 的结构通常和 property/event 类似（identifier/name/type/input_data/output_data...）。
 * 这里全部给默认值，避免后端字段增减导致 Gson 解析直接崩。
 */
data class Service(
    val id: Int = 0,
    val identifier: String? = null,
    val name: String? = null,
    override val type: String? = null,
    val desc: String? = null,
    val required: Boolean? = null,
    val input_data: List<InputData>? = null,
    val output_data: List<OutputData>? = null,
): ModelData
