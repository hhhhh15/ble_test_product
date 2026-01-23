package com.homerunpet.homerun_pet_android_productiontest.base

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.blankj.utilcode.util.ActivityUtils
import com.homerunpet.homerun_pet_android_productiontest.common.ext.getVmClazz


abstract class BaseVmFragment<VM : BaseViewModel> : Fragment() {

    // 是否第一次加载
    private var isFirst: Boolean = true

    // 当前Fragment绑定的泛型类ViewModel
    protected lateinit var mViewModel: VM

    // 父类activity
    protected lateinit var mActivity: AppCompatActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isFirst = true
        val dataBindView = initViewDataBind(inflater, container)
        val rootView = dataBindView ?: inflater.inflate(getLayoutId(), container, false)
        return rootView
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mActivity = context as AppCompatActivity
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = (activity ?: ActivityUtils.getTopActivity()) as AppCompatActivity
        mViewModel = ViewModelProvider(activity)[getVmClazz(this)]
        // view加载完成后执行
        initView(savedInstanceState)
        onBindViewClick()
    }

    /**
     * 获取布局ID，子类可以重写
     */
    protected open fun getLayoutId(): Int {
        return 0 // 子类应重写此方法或使用 ViewBinding/DataBinding
    }

    /**
     * 初始化view操作
     */
    abstract fun initView(savedInstanceState: Bundle?)

    /**
     * 懒加载
     */
    protected open fun lazyLoadData() {}

    override fun onResume() {
        super.onResume()
        onVisible()
    }

    /**
     * 是否需要懒加载
     */
    private fun onVisible() {
        if (lifecycle.currentState == Lifecycle.State.STARTED && isFirst && !isHidden) {
            view?.post {
                lazyLoadData()
                isFirst = false
            }
        }
    }

    /**
     * 点击事件方法 配合setOnclick()拓展函数调用，做到黄油刀类似的点击事件
     */
    protected open fun onBindViewClick() {}

    /**
     * 供子类初始化 DataBinding 或 ViewBinding 操作
     */
    protected open fun initViewDataBind(inflater: LayoutInflater, container: ViewGroup?): View? {
        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFirst = true
    }

}