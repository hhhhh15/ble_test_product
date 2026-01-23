package com.homerunpet.homerun_pet_android_productiontest.base

import android.view.View
import androidx.viewbinding.ViewBinding
import com.homerunpet.homerun_pet_android_productiontest.common.ext.inflateBinding


abstract class BaseVBActivity<VM : BaseViewModel, VB : ViewBinding> : BaseVmActivity<VM>() {

    protected lateinit var mBind: VB
        protected set

    override fun initViewDataBind(): View? {
        return try {
            // 利用反射根据泛型得到 ViewDataBinding
            mBind = inflateBinding()
            return mBind.root
        } catch (e: Exception) {
            // 添加错误处理，防止反射失败导致崩溃
            handleBindingError(e)
            null
        }
    }

    protected open fun handleBindingError(exception: Exception) {
        // 子类可以重写此方法来自定义错误处理
    }

    override fun onDestroy() {
        // 清理 ViewBinding 引用，防止内存泄漏
        if (::mBind.isInitialized) {
            // 如果需要额外清理可以在这里处理
        }
        super.onDestroy()
    }
}