package com.homerunpet.homerun_pet_android_productiontest.base

import android.os.Bundle
import android.view.View
import androidx.viewbinding.ViewBinding
import com.gyf.immersionbar.ktx.immersionBar
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.common.widget.TitleBar

/**
 * 描述　: 需要自定义修改什么就重写什么
 */
abstract class HMBaseActivity<VM : BaseViewModel, VB : ViewBinding> : BaseVBActivity<VM, VB>() {

    lateinit var mToolbar: TitleBar

    protected val TAG: String by lazy { this::class.java.simpleName }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun getTitleBarView(): View? {
        mToolbar = TitleBar(this).apply {
            bindActivity(this@HMBaseActivity)
        }
        return mToolbar
    }

    override fun initImmersionBar() {
        if (showToolBar()) {
            immersionBar {
                barColor(R.color.colors_f2f2f2)
                autoDarkModeEnable(true)
                titleBar(mToolbar)
            }
        }
    }

}