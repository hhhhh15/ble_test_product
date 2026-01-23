package com.homerunpet.homerun_pet_android_productiontest.base

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.homerunpet.homerun_pet_android_productiontest.common.ext.inflateBinding

abstract class BaseVBFragment<VM : BaseViewModel, VB : ViewBinding> : BaseVmFragment<VM>() {

    private var _binding: VB? = null
    val mBind: VB get() = _binding!!

    /**
     * 创建 ViewBinding
     */
    override fun initViewDataBind(inflater: LayoutInflater, container: ViewGroup?): View? {
        _binding = inflateBinding(inflater, container, false)
        return mBind.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}