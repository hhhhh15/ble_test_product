package com.homerunpet.homerun_pet_android_productiontest.common.widget

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.common.ext.visibleOrInvisible

class TitleBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val root: LinearLayout
    private val bar: RelativeLayout
    private val leftContainer: FrameLayout
    private val leftButton: ImageView
    private val titleText: TextView
    private val rightContainer: LinearLayout

    private var leftClickListener: ((android.view.View) -> Unit)? = null
    private var attachedActivity: Activity? = null

    init {
        orientation = VERTICAL
        val rootView = LayoutInflater.from(context).inflate(R.layout.layout_title_bar, this, false)

        // 通过inflate返回的根视图来查找子视图
        root = rootView as LinearLayout
        bar = rootView.findViewById(R.id.title_bar)
        leftContainer = rootView.findViewById(R.id.fl_left)
        leftButton = rootView.findViewById(R.id.iv_left)
        titleText = rootView.findViewById(R.id.tv_title)
        rightContainer = rootView.findViewById(R.id.layout_right)

        // 读取自定义属性
        val ta = context.obtainStyledAttributes(attrs, R.styleable.TitleBar, defStyleAttr, 0)

        val titleStr = ta.getString(R.styleable.TitleBar_titleText)
        val titleColor = ta.getColor(
            R.styleable.TitleBar_titleColor,
            titleText.currentTextColor
        )
        val titleSizeDp = ta.getDimension(
            R.styleable.TitleBar_titleSize,
            pxToDp(titleText.textSize)
        )
        val leftIcon = ta.getResourceId(R.styleable.TitleBar_leftIcon, 0)

        if (ta.hasValue(R.styleable.TitleBar_titleBarBackground)) {
            val bg = ta.getColor(
                R.styleable.TitleBar_titleBarBackground,
                ContextCompat.getColor(context, android.R.color.transparent)
            )
            root.setBackgroundColor(bg)
        }

        if (ta.hasValue(R.styleable.TitleBar_titleBarHeight)) {
            val h = ta.getDimensionPixelSize(
                R.styleable.TitleBar_titleBarHeight,
                bar.layoutParams.height
            )
            bar.layoutParams = bar.layoutParams.apply { height = h }
        }

        ta.recycle()

        if (!titleStr.isNullOrEmpty()) setTitle(titleStr)
        setTitleColor(titleColor)
        setTitleSizeDp(titleSizeDp)
        if (leftIcon != 0) setLeftIcon(leftIcon)

        // 默认左侧点击：调用 Activity.finish()
        leftContainer.setOnClickListener { view ->
            leftClickListener?.invoke(view) ?: run {
                attachedActivity?.finish()
            }
        }

        // 将根视图添加到TitleBar中
        addView(rootView)
    }

    /* -------------------- 对外 API -------------------- */

    fun setTitle(text: String): TitleBar {
        titleText.text = text
        return this
    }

    fun setTitleColor(@ColorInt color: Int): TitleBar {
        titleText.setTextColor(color)
        return this
    }

    fun setTitleSizeDp(sizeDp: Float): TitleBar {
        titleText.textSize = sizeDp
        return this
    }

    fun setLeftIcon(@DrawableRes resId: Int): TitleBar {
        leftButton.setImageResource(resId)
        return this
    }

    fun setLeftIconVisible(visible: Boolean): TitleBar {
        leftButton.visibleOrInvisible(visible)
        return this
    }

    /**
     * 设置左侧点击事件
     * 如果不调用，默认自动返回 Activity
     */
    fun setOnLeftClick(listener: (android.view.View) -> Unit): TitleBar {
        leftClickListener = listener
        return this
    }

    fun addRightText(
        text: String,
        textSizeDp: Float = 14f,
        listener: (android.view.View) -> Unit,
        configureTextView: ((TextView) -> Unit)? = null
    ): TitleBar {
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(titleText.currentTextColor)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(44)).apply {
                gravity = Gravity.CENTER
            }
            setPadding(dp(20), dp(0), dp(20), dp(0))
            gravity = Gravity.CENTER
            textSize = textSizeDp
            setOnClickListener(listener)
        }
        configureTextView?.invoke(tv)
        rightContainer.addView(tv)
        return this
    }

    fun addRightIcon(@DrawableRes resId: Int, listener: (android.view.View) -> Unit): TitleBar {
        val iv = ImageView(context).apply {
            setImageResource(resId)
            layoutParams = LayoutParams(dp(44), dp(44)).apply {
                gravity = Gravity.CENTER
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
            gravity = Gravity.CENTER
            setOnClickListener(listener)
        }
        rightContainer.addView(iv)
        return this
    }

    /**
     * 添加自定义 View 到右侧容器
     * @param view 要添加的 View
     * @param layoutParams 可选的布局参数,不传则使用 WRAP_CONTENT
     * @param configureView 可选的配置回调,用于进一步配置 View
     * @return Pair<TitleBar, View> - first 为 TitleBar 实例支持链式调用, second 为添加的 View 引用
     */
    fun addRightView(
        view: android.view.View,
        layoutParams: LayoutParams? = null,
        configureView: (android.view.View.() -> Unit)? = null
    ): Pair<TitleBar, android.view.View> {
        view.layoutParams = layoutParams ?: LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        configureView?.invoke(view)
        rightContainer.addView(view)
        return Pair(this, view)
    }

    /**
     * 通过布局资源 ID 添加自定义布局到右侧容器
     * @param layoutResId 布局资源 ID
     * @param layoutParams 可选的布局参数,不传则使用 WRAP_CONTENT
     * @param configureView 可选的配置回调,用于配置 inflate 后的 View
     * @return Pair<TitleBar, View> - first 为 TitleBar 实例支持链式调用, second 为添加的 View 引用
     */
    fun addRightCustomLayout(
        @androidx.annotation.LayoutRes layoutResId: Int,
        layoutParams: LayoutParams? = null,
        configureView: (android.view.View.() -> Unit)? = null
    ): Pair<TitleBar, android.view.View> {
        val customView = LayoutInflater.from(context).inflate(layoutResId, rightContainer, false)
        customView.layoutParams = layoutParams ?: LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        configureView?.invoke(customView)
        rightContainer.addView(customView)
        return Pair(this, customView)
    }

    fun controlRightLayout(isShow: Boolean): TitleBar {
        rightContainer.visibleOrInvisible(isShow)
        return this
    }

    /* -------------------- ImmersionBar 封装 -------------------- */

    fun bindActivity(activity: Activity) {
        attachedActivity = activity
    }

    fun bindFragment(fragment: Fragment) {
        attachedActivity = fragment.requireActivity()
    }

    /* -------------------- 工具方法 -------------------- */

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    private fun pxToDp(px: Float): Float = px / resources.displayMetrics.density
}
