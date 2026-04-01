package com.homerunpet.homerun_pet_android_productiontest.base.net

import com.drake.net.BuildConfig
import com.drake.net.okhttp.setRequestInterceptor
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi.PRODUCT_TEST_TOKEN
import com.safframework.http.interceptor.AndroidLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object RefreshTokenClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // 请求拦截器（添加 Header）
        .setRequestInterceptor(GlobalHeaderInterceptor())
        // 加密拦截器
        .addInterceptor(EncryptDataInterceptor("bff382ebd7feee2badedae6eb66d7be2"))
        // 日志拦截器（放最后，记录完整的请求/响应）
        .addInterceptor(
            AndroidLoggingInterceptor.build(
                hideVerticalLine = true,
                isDebug = BuildConfig.DEBUG,
                requestTag = "Refresh_Token_Request",
                responseTag = "Refresh_Token_Response"
            )
        )
        .build()

    suspend fun refreshToken(): String {
        val refreshToken = TokenManager.getRefreshToken()
            ?: throw IllegalStateException("RefreshToken 不存在")
//构建请求request
        val request = Request.Builder()
            .url("${HmApi.getBaseUrl()}$PRODUCT_TEST_TOKEN")
            .post(
                JSONObject().apply {
                    put("refresh_token", refreshToken)
                }.toString().toRequestBody("application/json".toMediaType())
            )
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()  //发送请求

            response.use {
                if (!response.isSuccessful) {
                    throw Exception("刷新 token 失败: ${response.code}")
                }

                val body = response.body?.string() ?: throw Exception("响应体为空")

                val json = JSONObject(body)
                val accessToken = json.optString("access_token")
                val newRefreshToken = json.optString("refresh_token")
                val expirationTime = json.optInt("expiration_time")

                if (
                    accessToken.isEmpty() ||
                    newRefreshToken.isEmpty() ||
                    expirationTime <= 0
                ) {
                    throw Exception("token 数据不完整")
                }

                //保存登录后refeash_token更新后的access_token和refresh_token
                SpManager.token=accessToken
                SpManager.refreshToken=newRefreshToken

                accessToken
            }

        }
    }
}