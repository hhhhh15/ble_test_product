package com.homerunpet.homerun_pet_android_productiontest.base.net

import android.view.View
import com.blankj.utilcode.util.ResourceUtils
import com.drake.net.Net
import com.drake.net.exception.ResponseException
import com.drake.net.interfaces.NetErrorHandler
import com.hjq.toast.Toaster
import com.homerunpet.homerun_pet_android_productiontest.MyApplication
import com.homerunpet.homerun_pet_android_productiontest.R

class HMNetErrorHandler : NetErrorHandler {
    override fun onError(e: Throwable) {
        val message = when (e) {
            is ResponseException -> {
                val key = e.tag.toString()
                ResourceUtils.getStringIdByName(key)
            }

            else -> MyApplication.instance.getString(R.string.NETWORK_ERROR)
        }
        Net.debug(e)
        Toaster.show(message)
    }

    override fun onStateError(e: Throwable, view: View) {
        val message = when (e) {
            is ResponseException -> {
                val key = e.tag.toString()
                ResourceUtils.getStringIdByName(key)
            }

            else -> e.message
        }
        Net.debug(e)
        Toaster.show(message)
    }

}