package com.homerunpet.homerun_pet_android_productiontest.base.net

import android.util.Log
import com.blankj.utilcode.util.EncryptUtils
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 支持JSON请求的签名拦截器
 * 功能：处理GET/POST表单/POST JSON请求的参数签名
 * 算法：HMAC-SHA256 + CanonicalRequest
 */
class EncryptDataInterceptor(
    private val appSecret: String
) : Interceptor {

    companion object {
        private const val TAG = "EncryptDataInterceptor"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

//        //密钥分支
//        val path = originalRequest.url.encodedPath
//
//        val AppSecret = if (path.startsWith("/v1/auth/login/phone")) {
//            "bff382ebd7feee2badedae6eb66d7be2"
//        } else {
//            appSecret
//        }

        // 1. 准备基础参数
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = generateNonce()  //生成随机字符串
        val httpMethod = originalRequest.method.uppercase()  //uppercase方法将方法名转换成大写

        // 2. 获取 Body 字符串并计算 ContentSha256
        val bodyString = getBodyString(originalRequest.body)
        val contentSha256 = sha256Hex(bodyString)    //根据body数据通过SHA256生成hash值

        // 3. 构造 CanonicalizedURI (Path 全量编码)
        val canonicalizedURI = encodeRfc3986(originalRequest.url.encodedPath)

        // 4. 构造 CanonicalizedQueryString
        val canonicalizedQueryString = getCanonicalizedQueryString(originalRequest.url)

        // 5. 构造 SignedHeaders
        // 只包含: x-sign-nonce, x-sign-timestamp, x-content-sha256, content-type
        val signedHeadersMap = linkedMapOf<String, String>()
        signedHeadersMap["x-sign-nonce"] = nonce
        signedHeadersMap["x-sign-timestamp"] = timestamp
        signedHeadersMap["x-content-sha256"] = contentSha256
        
        // 确保 content-type
        val contentType = originalRequest.body?.contentType()?.toString() 
            ?: originalRequest.header("Content-Type")
            ?: "application/json"
        signedHeadersMap["content-type"] = contentType

        val signedHeadersString = signedHeadersMap.entries.joinToString("\n") { "${it.key}:${it.value}" }

        // 6. 构造 CanonicalRequest
        val canonicalRequest = "$httpMethod\n$canonicalizedURI\n$canonicalizedQueryString\n$signedHeadersString\n$contentSha256"

        // 7. 生成签名
        val signature = hmacSHA256(canonicalRequest, appSecret)

        // 打印调试日志
        Log.d(TAG, "================================= Signature Debug =================================")
        Log.d(TAG, "AppSecret: $appSecret")
        Log.d(TAG, "HTTPMethod: $httpMethod")
        Log.d(TAG, "CanonicalizedURI: $canonicalizedURI")
        Log.d(TAG, "CanonicalizedQueryString: $canonicalizedQueryString")
        Log.d(TAG, "Body String: $bodyString")
        Log.d(TAG, "ContentSha256: $contentSha256")
        Log.d(TAG, "SignedHeaders:\n$signedHeadersString")
        Log.d(TAG, "CanonicalRequest:\n$canonicalRequest")
        Log.d(TAG, "Signature: $signature")
        Log.d(TAG, "===================================================================================")

        // 8. 构建新请求

        val newRequestBuilder = originalRequest.newBuilder()
            .addHeader("X-Signature", signature)
            .addHeader("X-Sign-Timestamp", timestamp)
            .addHeader("X-Sign-Nonce", nonce)
            .addHeader("Content-Type", contentType)
        // 如果读取了 Body，重新设置一下
        if (originalRequest.body != null) {
             val mediaType = originalRequest.body?.contentType()
             val newBody = bodyString.toRequestBody(mediaType)
             newRequestBuilder.method(originalRequest.method, newBody)
        }

        return chain.proceed(newRequestBuilder.build())
    }


    private fun getCanonicalizedQueryString(url: HttpUrl): String {
        if (url.querySize == 0) return ""

        val params = TreeMap<String, String>()
        for (i in 0 until url.querySize) {
            val name = url.queryParameterName(i)
            val value = url.queryParameterValue(i) ?: ""
            params[name] = value
        }
        return params.entries.joinToString("&") { "${it.key}=${it.value}" }
    }

    private fun getBodyString(requestBody: RequestBody?): String {
        if (requestBody == null) return ""
        return try {
            val buffer = okio.Buffer()
            requestBody.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: IOException) {
            e.printStackTrace()
            ""
        }
    }

    private fun encodeRfc3986(s: String): String {
        return URLEncoder.encode(s, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    private fun sha256Hex(data: String): String {
        if (data.isEmpty()) return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        try {
            val bytes = data.toByteArray(StandardCharsets.UTF_8)
            val encryptSHA256 = EncryptUtils.encryptSHA256(bytes) ?: return ""
            return bytesToHex(encryptSHA256)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    private fun hmacSHA256(data: String, secret: String): String {
        try {
            val algorithm = "HmacSHA256"
            val mac = Mac.getInstance(algorithm)
            val secretKeySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), algorithm)
            mac.init(secretKeySpec)
            val hash = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            return bytesToHex(hash)
        } catch (e: Exception) {
            throw RuntimeException("HMAC-SHA256 encryption failed", e)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun generateNonce(length: Int = 16): String {
        return UUID.randomUUID().toString().replace("-", "").take(length)
    }
}