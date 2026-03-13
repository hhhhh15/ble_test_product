package com.homerunpet.homerun_pet_android_productiontest.base.net

import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor:Interceptor {

    private val noAuthUrls = listOf(
        "/v1/auth/login/phone",      // 登录接口
        "/v1/users/refresh-token"    //刷新token的接口也不加header
    )
    private val TAG="AuthInterceptor"

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest  = chain.request()
        val url = originalRequest.url.encodedPath  // 获取请求路径

        Log.d(TAG, "intercept: 此时发起请求时的请求api是${url}")
        if (noAuthUrls.any { url.contains(it) }){   //url为啥用的是contains，类型是map？
            return chain.proceed(originalRequest)  //直接发送，不添加token，
        }


        //第一次登录获取到token,用来发送请求
        val firstToken = TokenManager.getAccessToken()
        val newRequest = originalRequest.newBuilder()
            .header("Authorization","Bearer ${firstToken}")
            .build()


        val response=chain.proceed(newRequest )
        if (response.code!=401) return response   //不是401,正常的话返回response，表示发送成功，结束代码块

        //是401.表示token国企，需要关闭旧的响应
        response.close()
        val newToken = try {
            runBlocking {
                RefreshTokenClient.refreshToken() //已经更新Sp存储的数据了
            }
        } catch (e: Exception) {
            // 刷新失败，可能 refreshToken 也过期了，需要重新登录
            throw Exception("Token 刷新失败，请重新登录: ${e.message}")
        }
        val retryRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer $newToken")
            .build()

        return chain.proceed(retryRequest)
    }
}