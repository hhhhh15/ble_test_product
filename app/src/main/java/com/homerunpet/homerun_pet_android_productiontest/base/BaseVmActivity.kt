package com.homerunpet.homerun_pet_android_productiontest.base

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.common.ext.getVmClazz

abstract class BaseVmActivity<VM : BaseViewModel> : AppCompatActivity(), BaseIView {

    // toolbar 这个可替换成自己想要的标题栏
    private var mTitleBarView: View? = null

    private var dataBindView: View? = null

    // 当前Activity绑定的 ViewModel
    protected lateinit var mViewModel: VM

    protected var isResume = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base_new)
        // 生成ViewModel
        mViewModel = ViewModelProvider(this)[getVmClazz(this)]

        // 初始化 status View
        initStatusView()

        // 注册界面响应事件
        initView(savedInstanceState)

        // 初始化绑定点击方法
        onBindViewClick()
    }

    override fun onResume() {
        super.onResume()
        isResume = true
    }

    override fun onPause() {
        super.onPause()
        isResume = false
    }

    private fun initStatusView() {
        mTitleBarView = getTitleBarView()
        dataBindView = initViewDataBind()
        mTitleBarView?.let {
            if (showToolBar()) {
                findViewById<LinearLayout>(R.id.baseRootView).addView(it, 0)
            }
        }
        initImmersionBar()
        findViewById<FrameLayout>(R.id.baseContentView).addView(dataBindView)
    }

    /**
     * 是否隐藏 标题栏 默认显示
     */
    open fun showToolBar(): Boolean {
        return true
    }

    /**
     * 初始化沉浸式
     * Init immersion bar.
     */
    protected open fun initImmersionBar() {}

    /**
     * 初始化view
     */
    abstract fun initView(savedInstanceState: Bundle?)

    /**
     * 点击事件方法 配合setOnclick()拓展函数调用，做到黄油刀类似的点击事件
     */
    protected open fun onBindViewClick() {}

    /**
     * 供子类BaseVmDbActivity 初始化 DataBinding ViewBinding操作
     */
    protected open fun initViewDataBind(): View? {
        return null
    }

    /**
     * 是否需要点击空白处隐藏软键盘
     */
    protected open fun shouldHideKeyboardTouchOutside(): Boolean {
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (shouldHideKeyboardTouchOutside() && ev.action == MotionEvent.ACTION_DOWN) {
            val currentFocusView = currentFocus
            if (currentFocusView != null && isOutsideEditTextClick(currentFocusView, ev)) {
                hideSoftKeyboard(currentFocusView)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideSoftKeyboard(view: View) {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (e: Exception) {
            // 忽略隐藏键盘时的异常
        }
    }

    private fun isOutsideEditTextClick(view: View, event: MotionEvent): Boolean {
        if (view !is EditText) return false

        val location = IntArray(2)
        view.getLocationInWindow(location)

        val left = location[0]
        val top = location[1]
        val right = left + view.width
        val bottom = top + view.height

        // 点击的是输入框区域，保留点击EditText的事件
        return event.x < left || event.x > right || event.y < top || event.y > bottom
    }

}