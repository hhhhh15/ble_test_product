package com.homerunpet.homerun_pet_android_productiontest.common.utils

import androidx.lifecycle.LifecycleOwner
import com.drake.net.Get
import com.drake.net.utils.scopeNetLife
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi

/**
 * 统一封装
 */
object HmCommonNetUtils {

    /**
     * 获取品类产品
     */
    @JvmStatic
    @JvmOverloads
    fun fetchCategoryProducts(
        lifecycleOwner: LifecycleOwner,
        callback: HmNetworkCallback<String>,
        isHandlerError: Boolean = true,
    ) {
        lifecycleOwner.scopeNetLife {
            Get<String?>(HmApi.CATEGORY_PRODUCTS).await()?.apply {
                callback.onSuccess(this)
            }
        }.catch {
            if (it is Exception) {
                callback.onError(it)
            }
            if (isHandlerError) {
                handleError(it)
            }
        }
    }

}

interface HmNetworkCallback<T> {
    fun onSuccess(result: T)
    fun onError(error: Exception)
}