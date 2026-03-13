package com.homerunpet.homerun_pet_android_productiontest.common.ext

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.drake.net.request.BodyRequest
import com.drake.net.request.MediaConst
import com.drake.net.utils.mediaType
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.ble.model.ProvisionProtocol

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
    limit: Int? = null,
    tabLayout: TabLayout?=null
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
    if (tabLayout != null) {
        tabLayout.setupWithViewPager(this)
    }
    return this
}

// ==================== ViewPager2 封装 ====================

/**
 * 在 Activity 里：ViewPager2 + Fragment 列表 + 可选 TabLayout 联动
 * @param activity 当前 Activity（需继承 FragmentActivity / AppCompatActivity）
 * @param fragments 每一页的 Fragment 列表
 * @param tabLayout 可选，若传则与 ViewPager2 联动并显示标题
 * @param pageTitles 可选，与 tabLayout 配合使用，各 Tab 的文案
 */
fun ViewPager2.bindFragment(
    activity: FragmentActivity,
    fragments: List<Fragment>,
    tabLayout: TabLayout? = null,
    pageTitles: List<String>? = null
): ViewPager2 {
    adapter = object : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = fragments.size
        override fun createFragment(position: Int): Fragment = fragments[position]
    }
    if (tabLayout != null && pageTitles != null && pageTitles.size == fragments.size) {
        TabLayoutMediator(tabLayout, this) { tab, position ->
            tab.text = pageTitles[position]
        }.attach()
    }
    return this
}

/**
 * 在 Fragment 里：ViewPager2 + 子 Fragment 列表 + 可选 TabLayout 联动
 * @param fragment 当前外层 Fragment（用于 FragmentStateAdapter 生命周期）
 * @param fragments 每一页的子 Fragment 列表
 * @param tabLayout 可选，若传则与 ViewPager2 联动
 * @param pageTitles 可选，各 Tab 的文案
 */
fun ViewPager2.bindFragment2(
    fragment: Fragment,
    fragments: List<Fragment>,
    tabLayout: TabLayout? = null,
    pageTitles: List<String>? = null
): ViewPager2 {
    adapter = object : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = fragments.size
        override fun createFragment(position: Int): Fragment = fragments[position]
    }
    if (tabLayout != null && pageTitles != null && pageTitles.size == fragments.size) {
        TabLayoutMediator(tabLayout, this) { tab, position ->
            tab.text = pageTitles[position]
        }.attach()
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
 * 为View设置安全距离导航栏
 */
fun View.hasSafeDistanceNavigationBars(activity: Activity) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
        val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

        if (isImeVisible) {
            v.setPadding(navigationBars.left, navigationBars.top, navigationBars.right, imeHeight)
        } else {
            v.setPadding(navigationBars.left, navigationBars.top, navigationBars.right, navigationBars.bottom)
        }
        insets
    }
}

/**
 * 为View设置安全距离状态栏
 */
fun View.hasSafeDistanceStatusBars() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        v.setPadding(statusBars.left, statusBars.top, statusBars.right, statusBars.bottom)
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
 * 统一配网日志记录 (旧日志)
 */
fun Any.savePwLog(message: String) {
    val tag = this::class.java.simpleName
    val fullMessage = "(旧版本日志)类名：$tag    \n$message"
    XLog.e(fullMessage)
}

/**
 * 统一配网日志记录
 * 格式: [HRCN][配网设备序列号][步骤名称][约定的错误码][原始错误][自定义需要排查错误打印的数据]
 *
 * @param deviceSn 配网设备序列号
 * @param stepName 步骤名称
 * @param errorCode 约定的错误码 (可选)
 * @param originalErrCode 原始错误码 (可选)
 * @param originalErrMsg 原始错误信息 (可选)
 * @param customData 自定义数据 (可选)
 */
fun Any.saveDistributionNetworkLog(
    product: Product?,
    stepName: String,
    errorCode: String = "",
    originalErrCode: String? = "",
    originalErrMsg: String? = "",
    customData: String = "",
    className: String = ""
) {
    val sb = StringBuilder()
    sb.append("[HRCN]")

    val netTypeTag = if (product?.hmFastBleDevice?.protocol == ProvisionProtocol.AP) {
        "[AP]"
    } else {
        "[BLE]"
    }
    sb.append(netTypeTag)

    sb.append("[${product?.deviceSerial.orEmpty()}]")
    sb.append("[$stepName]")
    sb.append("[$errorCode]")
    // 内部拼接原始错误: [code:msg]
    val originalError = if (originalErrCode.isNullOrEmpty().not() || originalErrMsg.isNullOrEmpty().not()) {
        "$originalErrCode:$originalErrMsg"
    } else {
        ""
    }
    sb.append("[$originalError]")
    sb.append("[$customData]")

    var tag = className
    if (tag.isEmpty()) {
        var clazz = this::class.java
        // 如果是匿名内部类 (RxJava Lambda 等)，向上查找外部类
        while (clazz.isAnonymousClass && clazz.enclosingClass != null) {
            clazz = clazz.enclosingClass!!
        }
        tag = clazz.simpleName
    }
    val fullMessage = "类名：$tag    \n$sb"
    XLog.e(fullMessage)

}

/**
 * 统一蓝牙扫描日志记录
 */
fun Any.saveScanLog(message: String) {
    val tag = this::class.java.simpleName
    val fullMessage = "类名：$tag    \n$message"
    XLog.e(fullMessage)

}


/**
 * 安全转换成Boolean类型, 将String "true" 转为 true，其他情况（包括null）为 false
 */
fun Any?.toSafeBoolean(): Boolean {
    return this?.toString().toBoolean()
}

/**
 * 安全转换成Int类型
 * @param default 默认值，默认为0
 */
fun Any?.toSafeInt(default: Int = 0): Int {
    return this?.toString()?.toDoubleOrNull()?.toInt() ?: default
}

/**
 * UTC时间字符串转本地时间字符串 (HH:mm)
 * @param pattern 输出格式，默认 "HH:mm"
 * @return 格式化后的时间字符串，若解析失败返回原字符串或空串
 */
fun String?.utcToLocal(pattern: String = "HH:mm"): String {
    if (this.isNullOrEmpty()) return ""
    return try {
        val utcTimeObj = java.time.LocalTime.parse(this)
        val now = java.time.LocalDate.now()
        val utcZoned = java.time.LocalDateTime.of(now, utcTimeObj).atZone(java.time.ZoneId.of("UTC"))
        val localZoned = utcZoned.withZoneSameInstant(java.time.ZoneId.systemDefault())
        localZoned.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern(pattern))
    } catch (e: Exception) {
        this
    }
}

/**
 * 本地时间字符串转UTC时间字符串 (HH:mm)
 * @param pattern 输出格式，默认 "HH:mm"
 * @return 格式化后的时间字符串，若解析失败返回原字符串或空串
 */
fun String?.localToUtc(pattern: String = "HH:mm"): String {
    if (this.isNullOrEmpty()) return ""
    return try {
        val localTimeObj = java.time.LocalTime.parse(this)
        val now = java.time.LocalDate.now()
        val localZoned = java.time.LocalDateTime.of(now, localTimeObj).atZone(java.time.ZoneId.systemDefault())
        val utcZoned = localZoned.withZoneSameInstant(java.time.ZoneId.of("UTC"))
        utcZoned.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern(pattern))
    } catch (e: Exception) {
        this
    }
}
fun Activity.enableEdgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
}
fun Activity.setStatusBarDarkFont(isDark: Boolean) {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.isAppearanceLightStatusBars = isDark
}



