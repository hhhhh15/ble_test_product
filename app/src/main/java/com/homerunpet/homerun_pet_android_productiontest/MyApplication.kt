package com.homerunpet.homerun_pet_android_productiontest

import android.app.Application
import com.drake.net.NetConfig
import com.drake.net.okhttp.setConverter
import com.drake.net.okhttp.setDebug
import com.drake.net.okhttp.setErrorHandler
import com.drake.net.okhttp.setRequestInterceptor
import com.drake.net.okhttp.trustSSLCertificate
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.XLog
import com.elvishew.xlog.flattener.ClassicFlattener
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.naming.ChangelessFileNameGenerator
import com.hjq.toast.Toaster
import com.homerunpet.homerun_pet_android_productiontest.base.net.EncryptDataInterceptor
import com.homerunpet.homerun_pet_android_productiontest.base.net.GlobalHeaderInterceptor
import com.homerunpet.homerun_pet_android_productiontest.base.net.GsonConverter
import com.homerunpet.homerun_pet_android_productiontest.base.net.HMNetErrorHandler
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.safframework.http.interceptor.AndroidLoggingInterceptor
import com.videogo.openapi.EZGlobalSDK
import com.videogo.openapi.EZOpenSDK
import okhttp3.Protocol
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

/**
 * 自定义Application
 */
open class MyApplication : Application() {

    companion object {
        @JvmStatic
        var instance: MyApplication by Delegates.notNull()
            private set

        @JvmStatic
        val openSDK: EZOpenSDK
            get() = EZOpenSDK.getInstance()

        @JvmStatic
        val globalSDK: EZGlobalSDK
            get() = EZGlobalSDK.getInstance()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化各个组件
        Toaster.init(this)
        initXLog()
        initNet()
    }

    /**
     * 初始化日志采集
     */
    private fun initXLog() {
        val filePrinter = FilePrinter.Builder(filesDir.absolutePath)
            .flattener(ClassicFlattener())
            .fileNameGenerator(ChangelessFileNameGenerator("hm_distribution_network_log"))
            .build()
            
        XLog.init(
            LogConfiguration.Builder().enableBorder().build(),
            AndroidPrinter(true),
            filePrinter
        )
    }

    /**
     * 初始化网络请求配置
     */
    private fun initNet() {
        NetConfig.initialize(HmApi.getBaseUrl(), this) {
            protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            // 超时设置
            connectTimeout(15, TimeUnit.SECONDS)
            readTimeout(15, TimeUnit.SECONDS)
            writeTimeout(15, TimeUnit.SECONDS)
            setDebug(BuildConfig.DEBUG)
            trustSSLCertificate() // 信任所有证书
            
            // 日志拦截器
            addInterceptor(
                AndroidLoggingInterceptor.build(
                    hideVerticalLine = true,
                    isDebug = BuildConfig.DEBUG,
                    requestTag = "HM_Request",
                    responseTag = "HM_Response"
                )
            )
            // 请求拦截器（添加 Header 等）
            setRequestInterceptor(GlobalHeaderInterceptor())
            // 加密拦截器
            // 8946644a4eb35e50f83f96dbb6403dab
            addInterceptor(EncryptDataInterceptor("bff382ebd7feee2badedae6eb66d7be2"))
            // 转换器与异常处理
            setConverter(GsonConverter())
            setErrorHandler(HMNetErrorHandler())
        }
    }

}


