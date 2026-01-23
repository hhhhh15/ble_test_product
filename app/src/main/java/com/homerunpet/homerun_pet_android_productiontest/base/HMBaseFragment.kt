package com.homerunpet.homerun_pet_android_productiontest.base

import androidx.viewbinding.ViewBinding

/**
 * 描述　: 需要自定义修改什么就重写什么
 */
abstract class HMBaseFragment<VM : BaseViewModel,VB: ViewBinding> : BaseVBFragment<VM, VB>()