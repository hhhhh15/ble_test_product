package com.homerunpet.homerun_pet_android_productiontest.common.ext

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.drake.net.request.BodyRequest
import com.drake.net.request.MediaConst
import com.drake.net.utils.mediaType
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.lang.Float.parseFloat
import java.util.WeakHashMap


/**
 * 给ViewPager绑定数据
 */

fun ViewPager.bind(
    count: Int,
    bindView: (container: ViewGroup, position: Int) -> View,
    pageTitles: List<String>? = null,
    pageWidth: Float = 1f
): ViewPager {
    offscreenPageLimit = count
    adapter = object : PagerAdapter() {
        override fun isViewFromObject(v: View, p: Any) = v == p
        override fun getCount() = count
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = bindView(container, position)
            container.addView(view)
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as View)
        }

        override fun getPageTitle(position: Int) =
            if (pageTitles == null) null else pageTitles[position]

        override fun getPageWidth(position: Int): Float {
            return pageWidth
        }
    }
    return this
}

/**
 * 给ViewPager绑定Fragment
 */
fun ViewPager.bindFragment(
    fm: FragmentManager,
    fragments: List<Fragment>,
    pageTitles: List<String>? = null,
    pageWidth: Float = 1f,
    limit: Int? = null
): ViewPager {
    offscreenPageLimit = limit ?: fragments.size
    adapter = object : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getItem(p: Int) = fragments[p]
        override fun getCount() = fragments.size
        override fun getPageTitle(p: Int) = if (pageTitles == null) null else pageTitles[p]
        override fun getPageWidth(position: Int): Float {
            return pageWidth
        }
    }
    return this
}

/**
 * 设置点击事件
 * @param views 需要设置点击事件的view
 * @param onClick 点击触发的方法
 */
internal const val DEFAULT_SINGLE_CLICK_INTERVAL = 500L

internal val viewClickTimeMap = WeakHashMap<View, Long>()

fun setOnClick(
    vararg views: View?,
    intervalMillis: Long = DEFAULT_SINGLE_CLICK_INTERVAL,
    onClick: (View) -> Unit
) {
    views.forEach { it.singleClick(intervalMillis, onClick) }
}

/**
 * 防重复点击扩展
 * @param intervalMillis 两次点击最小间隔，默认 500ms
 */
internal inline fun View?.singleClick(
    intervalMillis: Long = DEFAULT_SINGLE_CLICK_INTERVAL,
    crossinline onClick: (View) -> Unit
) {
    this?.setOnClickListener { view ->
        val now = SystemClock.elapsedRealtime()
        val last = viewClickTimeMap[view] ?: 0L
        if (now - last >= intervalMillis) {
            viewClickTimeMap[view] = now
            onClick(view)
        }
    }
}

/**
 * 安全转换成Float类型
 */
fun String?.toFloatSafe(): Float {
    if (this == null) {
        return 0F
    }
    runCatching {
        return parseFloat(this)
    }
    return 0F
}

/**
 * 安全转换成Long类型
 */
fun String?.toLongSafe(): Long {
    if (this == null) {
        return 0L
    }
    runCatching {
        return this.toLong()
    }
    return 0L
}

/**
 * 安全转换成Int类型
 */
fun String?.toIntSafe(): Int {
    if (this == null) {
        return 0
    }
    runCatching {
        return Integer.parseInt(this)
    }
    return 0
}

/**
 * 安全转换成Double类型
 */
fun String?.toDoubleSafe(): Double {
    if (this == null) {
        return 0.0
    }
    runCatching {
        return this.toDouble()
    }
    return 0.0
}


fun Any?.toJson(): String {
    return this?.let {
        runCatching {
            Gson().toJson(it)
        }.getOrElse {
            // Handle the exception or provide a default value if needed
            ""
        }
    } ?: ""
}

inline fun <reified T : Any> String?.fromJson(): T? {
    return this?.takeIf { it.isNotBlank() }?.runCatching {
        Gson().fromJson<T>(this, object : TypeToken<T>() {}.type)
    }?.getOrElse {
        null
    }
}

/**
 * View显示
 */
fun View?.visible() {
    this?.visibility = View.VISIBLE
}

/**
 * View隐藏
 */
fun View?.gone() {
    this?.visibility = View.GONE
}

/**
 * View占位隐藏
 */
fun View?.inVisible() {
    this?.visibility = View.INVISIBLE
}

/**
 * View是否显示
 */
fun View?.isVisible(): Boolean {
    return this?.visibility == View.VISIBLE
}

/**
 * View是否隐藏
 */
fun View?.isGone(): Boolean {
    return this?.visibility == View.GONE
}

/**
 * View是否占位隐藏
 */
fun View?.isInVisible(): Boolean {
    return this?.visibility == View.INVISIBLE
}

/**
 * @param visible 如果为true 该View显示 否则隐藏
 */
fun View?.visibleOrGone(visible: Boolean) {
    if (visible) {
        this.visible()
    } else {
        this.gone()
    }
}

/**
 * @param visible 如果为true 该View显示 否则占位隐藏
 */
fun View?.visibleOrInvisible(visible: Boolean) {
    if (visible) {
        this.visible()
    } else {
        this.inVisible()
    }
}

/**
 * 显示传入的view集合
 */
fun visibleViews(vararg views: View?) {
    views.forEach {
        it?.visible()
    }
}

/**
 * 隐藏传入的view集合
 */
fun goneViews(vararg views: View?) {
    views.forEach {
        it?.gone()
    }
}


fun Any?.toJsonBody(): RequestBody {
    return this.toJson().toRequestBody(MediaConst.JSON)
}

fun BodyRequest.gson(vararg body: Pair<String, Any?>) {
    this.body = Gson().toJson(body.toMap()).toRequestBody(MediaConst.JSON)
}

/**
 * 开启全屏沉浸式体验（Edge-to-Edge）
 */
fun Activity.enableEdgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
}

/**
 * 设置状态栏文字图标颜色
 * @param isDark true 为黑色，false 为白色
 */
fun Activity.setStatusBarDarkFont(isDark: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = isDark
}

/**
 * 为View设置安全距离导航栏
 */
fun View.hasSafeDistanceNavigationBars() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
        val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

        v.setPadding(
            navigationBars.left,
            navigationBars.top,
            navigationBars.right,
            if (isImeVisible) imeHeight else navigationBars.bottom
        )
        insets
    }
}

/**
 * 为View设置安全距离状态栏
 */
fun View.hasSafeDistanceStatusBars() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        v.setPadding(statusBars.left, statusBars.top, statusBars.right, v.paddingBottom)
        insets
    }
}

fun File.toRequestBody(contentType: MediaType? = null): RequestBody {
    val fileMediaType = contentType ?: mediaType()
    return object : RequestBody() {

        // 文件类型
        override fun contentType(): MediaType? {
            return fileMediaType
        }

        // 文件长度, 不确定返回-1
        override fun contentLength() = length()

        // 写入数据
        override fun writeTo(sink: BufferedSink) {
            source().use { source ->
                sink.writeAll(source)
            }
        }
    }
}

/**
 * 统一日志记录：同时输出到控制台和文件
 */
fun Any.saveLog(message: String) {
    val tag = this::class.java.simpleName
    val fullMessage = "[$tag] $message"
    XLog.e(fullMessage)
}









