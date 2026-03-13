package com.homerunpet.homerun_pet_android_productiontest.base.net

import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.DeviceUtils
import com.drake.net.interceptor.RequestInterceptor
import com.drake.net.request.BaseRequest
import com.hjq.device.compat.DeviceBrand
import com.homerunpet.homerun_pet_android_productiontest.common.utils.AppDeviceUtils

/** 添加全局请求头/参数 */
class GlobalHeaderInterceptor : RequestInterceptor {

    /**
     * 本方法每次请求发起都会调用, 这里添加的参数可以是动态参数
     *
     * Authorization	否	访问凭证，例如 Bearer <token>
     * Content-Type	是	固定为 application/json
     * X-Application-Id	是	应用ID
     * X-Client-Platform	是	操作系统平台，支持Android, iOS, Harmony
     * X-Client-OS-Version	是	操作系统完整版本号
     * X-Client-App-Version	是	应用的发布版本号
     * X-Signature	是	签名值，使用 X-Sign-Method 方法计算
     * X-Sign-Method	否	签名方法，默认 HMAC-SHA256
     * X-Sign-Timestamp	是	时间戳，防止重放攻击
     * X-Sign-Nonce	是	每次请求都生成一个唯一随机值（UUID 或随机数），防止重放攻击
     *
     * */
    override fun interceptor(request: BaseRequest) {
        request.setHeader("Accept-Language", "zh")
        // token后面做登录再实现，sp实现
        request.setHeader(
            "Authorization", ""
        )
        request.setHeader("Content-Type", "application/json")
        // 厂测暂时用国内的
        //f73b2c3ca9031565e6d6b9337d762bd9
        //
        request.setHeader("X-Application-Id", "4f6ec5e2607a9ec678530b9068381b38")
        request.setHeader("X-Client-Platform", "Android")
        request.setHeader("X-Client-OS-Version", DeviceUtils.getSDKVersionName())
        request.setHeader("X-Client-App-Version", AppUtils.getAppVersionName())
        request.setHeader("X-Client-Device-Model", DeviceUtils.getModel())
        request.setHeader("X-Client-Device-Brand", DeviceBrand.getBrandName())
        request.setHeader("X-Client-Device-Id", AppDeviceUtils.getUniqueId())
    }

}