package com.homerunpet.homerun_pet_android_productiontest.base.net

import android.util.Log
import com.drake.net.convert.NetConverter
import com.drake.net.exception.ConvertException
import com.drake.net.exception.ResponseException
import com.google.gson.Gson
import com.homerunpet.homerun_pet_android_productiontest.common.bean.HMBaseResponse
import okhttp3.Response
import org.json.JSONObject
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class GsonConverter : NetConverter {
    companion object {
        private val gson = Gson()
    }

    override fun <R> onConvert(succeed: Type, response: Response): R? {
        try {
            return NetConverter.onConvert<R>(succeed, response)
        } catch (e: ConvertException) {
            val code = response.code
            when {
                code in 200..299 -> { // 请求成功
                    val bodyString = response.body?.string()

                    Log.e("JSON_DEBUG", "========== 原始 JSON 开始 ==========")
                    Log.e("JSON_DEBUG", bodyString ?: "body 为空")
                    Log.e("JSON_DEBUG", "========== 原始 JSON 结束 ==========")
                    Log.e("JSON_DEBUG", "目标类型: $succeed")

                    try {
                        return bodyString?.parseBody<R>(succeed)
                    } catch (e: Exception) {
                        Log.e("JSON_DEBUG", "解析失败原因: ${e.message}")
                        Log.e("JSON_DEBUG", "异常类型: ${e.javaClass.simpleName}")
                        e.printStackTrace()

                        throw ResponseException(response, response.message , tag = response.code)
                    }
                }

                else -> { // 请求失败
                    val bodyString = response.body?.string()
                    if (bodyString.isNullOrEmpty()) {
                        throw ResponseException(response, message = response.message, tag = response.code)
                    } else {
                        // 将业务错误码作为tag传递/业务信息
                        val json = JSONObject(bodyString)
                        val hmCode = json.optString("error_code", "UNKNOWN_ERROR")
                        val hmMsg = json.optString("error_msg", bodyString)
                        throw ResponseException(response, hmMsg, tag = hmCode)
                    }
                }
            }
        }
    }


    private fun <R> String.parseBody(succeed: Type): R? {
        val string =  if (succeed is ParameterizedType && succeed.rawType is Class<*> && HMBaseResponse::class.java.isAssignableFrom(succeed.rawType as Class<*>)) {
            // 处理泛型情况，如 HMBaseResponse<Data> 类型
            this
        } else {
            // 否则解析 data 字段
            try {
                JSONObject(this).getString("data")
            } catch (e: Exception) {
                this
            }
        }
        return gson.fromJson<R>(string, succeed)
    }
}