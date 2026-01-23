package com.homerunpet.homerun_pet_android_productiontest.base

import android.view.View

interface BaseIView {

    /**
     * 子类可传入自己的标题栏 不给默认是null
     * @return View?
     */
    fun getTitleBarView(): View? {
        return null
    }

}