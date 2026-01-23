package com.homerunpet.homerun_pet_android_productiontest.common.utils

import android.annotation.SuppressLint
import android.provider.Settings
import com.blankj.utilcode.util.SPUtils
import com.blankj.utilcode.util.Utils
import java.util.UUID

/**
 * 设备相关的工具类
 */
object AppDeviceUtils {

    private const val SP_KEY_UNIQUE_ID = "app_unique_device_id"

    /**
     * 获取 Android 唯一设备标识符
     * 实现逻辑：
     * 1. 优先从内存/SP缓存获取
     * 2. 缓存不存在则获取系统 ANDROID_ID
     * 3. 如果 ANDROID_ID 无效，则生成一个随机 UUID 并持久化存储
     */
    @SuppressLint("HardwareIds")
    fun getUniqueId(): String {
        // 1. 尝试从 SP 中读取已存储的 ID
        var uniqueId = SPUtils.getInstance().getString(SP_KEY_UNIQUE_ID)
        if (uniqueId.isNullOrBlank()) {
            // 2. 获取系统的 ANDROID_ID
            try {
                uniqueId = Settings.Secure.getString(
                    Utils.getApp().contentResolver,
                    Settings.Secure.ANDROID_ID
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. 校验 ANDROID_ID 的合法性 (排除已知的无效/测试 ID 以及空值)
            if (uniqueId.isNullOrBlank() || uniqueId == "9774d56d682e549c") {
                // 4. 生成一个新的随机 UUID
                uniqueId = UUID.randomUUID().toString().replace("-", "")
            }

            // 5. 将结果持久化存储，确保只要应用不卸载重装，该 ID 就保持一致
            SPUtils.getInstance().put(SP_KEY_UNIQUE_ID, uniqueId)
        }
        return uniqueId
    }
}
