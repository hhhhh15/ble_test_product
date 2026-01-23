package com.homerunpet.homerun_pet_android_productiontest.common.ext

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import java.security.MessageDigest

/**
 * Glide 图片加载扩展封装
 *
 * 设计目标：
 * 1. **Kotlin 友好**：提供 ImageView 的扩展函数，调用简洁：`imageView.load(url)`
 * 2. **Java 可用**：所有核心方法都提供 @JvmStatic 版本，Java 中可直接调用 `GlideExt.load(imageView, url, ...)`
 * 3. **功能丰富**：
 *    - 普通图片加载（支持 URL / File / Uri / 资源 id）
 *    - 占位图、错误图、缩略图、过渡动画
 *    - 圆形头像、圆角图片、模糊、灰度、指定优先级
 *    - 指定尺寸加载（避免大图 OOM）/ 视频首帧 / 缩略图占位
 *    - GIF 加载 / 只加载 GIF 第一帧 / 不同缓存策略
 *    - 控制磁盘缓存、内存缓存（支持一键清理）
 *    - 清理某个 ImageView 的请求
 *    - 在 Activity / Fragment 中统一暂停 / 恢复 Glide 请求
 *
 * 使用示例（Kotlin）：
 * ```kotlin
 * imageView.load("https://xxx.jpg")
 * imageView.load("https://xxx.jpg", placeholder = R.drawable.ic_placeholder, error = R.drawable.ic_error)
 * imageView.loadCircle(avatarUrl, R.drawable.ic_avatar_default)
 * imageView.loadRound(url, radiusDp = 8f)
 * imageView.loadGif(gifUrl)
 * imageView.loadNoCache(url)
 * ```
 *
 * 使用示例（Java）：
 * ```java
 * GlideExt.load(imageView, "https://xxx.jpg");
 * GlideExt.load(imageView, "https://xxx.jpg", R.drawable.ic_placeholder, R.drawable.ic_error);
 * GlideExt.loadCircle(imageView, avatarUrl, R.drawable.ic_avatar_default);
 * GlideExt.loadRound(imageView, url, 8f, R.drawable.ic_placeholder, R.drawable.ic_error);
 * GlideExt.loadGif(imageView, gifUrl, R.drawable.ic_placeholder, R.drawable.ic_error);
 * GlideExt.clear(imageView);
 * GlideExt.pause(activity);
 * GlideExt.resume(activity);
 * ```
 */
object GlideExt {

    /*---------------------------------- 内部工具方法 ----------------------------------*/

    /**
     * dp 转 px
     */
    @JvmStatic
    fun dp2px(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    /**
     * 构建通用的 RequestBuilder，避免重复配置
     *
     * @param imageView  目标 ImageView，用于获取 Context 和生命周期绑定
     * @param data       数据源（URL / File / Uri / 资源 id 等）
     * @param placeholder 占位图资源 id，可为 0 表示不设置
     * @param error      错误图资源 id，可为 0 表示不设置
     * @param centerCrop 是否 CenterCrop
     * @param crossFade  是否淡入淡出动画
     * @param diskCacheStrategy 磁盘缓存策略
     * @param overrideWidth  指定宽度（px），<=0 表示不指定
     * @param overrideHeight 指定高度（px），<=0 表示不指定
     * @param skipMemoryCache 是否跳过内存缓存
     * @param extraOptionsBlock 额外的 RequestOptions 配置（圆形、圆角等）
     */
    private fun buildRequest(
        imageView: ImageView,
        data: Any?,
        placeholder: Int = 0,
        error: Int = 0,
        centerCrop: Boolean = true,
        crossFade: Boolean = true,
        diskCacheStrategy: DiskCacheStrategy = DiskCacheStrategy.AUTOMATIC,
        overrideWidth: Int = 0,
        overrideHeight: Int = 0,
        skipMemoryCache: Boolean = false,
        extraOptionsBlock: (RequestOptions.() -> RequestOptions)? = null
    ): RequestBuilder<Drawable> {
        val ctx = imageView.context
        val requestManager = Glide.with(ctx)
        var request = requestManager.load(data)

        var options = RequestOptions()
            .diskCacheStrategy(diskCacheStrategy)
            .skipMemoryCache(skipMemoryCache)

        if (centerCrop) {
            options = options.transform(CenterCrop())
        }

        if (placeholder != 0) {
            options = options.placeholder(placeholder)
        }
        if (error != 0) {
            options = options.error(error)
        }
        if (overrideWidth > 0 && overrideHeight > 0) {
            options = options.override(overrideWidth, overrideHeight)
        }

        if (extraOptionsBlock != null) {
            options = options.extraOptionsBlock()
        }

        request = request.apply(options)

        if (crossFade) {
            request = request.transition(DrawableTransitionOptions.withCrossFade())
        }

        return request
    }

    /*---------------------------------- Java 友好的静态方法 ----------------------------------*/

    /**
     * 基础加载（Java 推荐入口）
     */
    @JvmStatic
    @JvmOverloads
    fun load(
        imageView: ImageView,
        data: Any?,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        if (data == null) {
            if (error != 0) {
                imageView.setImageResource(error)
            } else if (placeholder != 0) {
                imageView.setImageResource(placeholder)
            }
            return
        }

        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error
        ).into(imageView)
    }

    /**
     * 加载圆形头像（Java）
     */
    @JvmStatic
    @JvmOverloads
    fun loadCircle(
        imageView: ImageView,
        data: Any?,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        if (data == null) {
            if (error != 0) {
                imageView.setImageResource(error)
            } else if (placeholder != 0) {
                imageView.setImageResource(placeholder)
            }
            return
        }

        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error,
            extraOptionsBlock = {
                this.transform(CenterCrop(), CircleCrop())
            }
        ).into(imageView)
    }

    /**
     * 加载圆角图片（Java）
     *
     * @param radiusDp 圆角半径，单位 dp
     */
    @JvmStatic
    @JvmOverloads
    fun loadRound(
        imageView: ImageView,
        data: Any?,
        radiusDp: Float,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        if (data == null) {
            if (error != 0) {
                imageView.setImageResource(error)
            } else if (placeholder != 0) {
                imageView.setImageResource(placeholder)
            }
            return
        }

        val radiusPx = dp2px(imageView.context, radiusDp)

        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error,
            extraOptionsBlock = {
                this.transform(CenterCrop(), RoundedCorners(radiusPx))
            }
        ).into(imageView)
    }

    /**
     * 加载 GIF（Java）
     */
    @JvmStatic
    @JvmOverloads
    fun loadGif(
        imageView: ImageView,
        data: Any?,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        val ctx = imageView.context
        var request = Glide.with(ctx).asGif().load(data)

        var options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)

        if (placeholder != 0) options = options.placeholder(placeholder)
        if (error != 0) options = options.error(error)

        request = request.apply(options)
            .transition(DrawableTransitionOptions.withCrossFade())

        request.into(imageView)
    }

    /**
     * 不使用缓存加载（Java）——适合验证码、头像等频繁变化资源
     */
    @JvmStatic
    @JvmOverloads
    fun loadNoCache(
        imageView: ImageView,
        data: Any?,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        if (data == null) {
            if (error != 0) {
                imageView.setImageResource(error)
            } else if (placeholder != 0) {
                imageView.setImageResource(placeholder)
            }
            return
        }

        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error,
            diskCacheStrategy = DiskCacheStrategy.NONE,
            skipMemoryCache = true
        ).into(imageView)
    }

    /**
     * 加载指定尺寸（Java）
     *
     * @param widthPx  目标宽度（px）
     * @param heightPx 目标高度（px）
     */
    @JvmStatic
    @JvmOverloads
    fun loadWithSize(
        imageView: ImageView,
        data: Any?,
        widthPx: Int,
        heightPx: Int,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        if (widthPx <= 0 || heightPx <= 0) {
            load(imageView, data, placeholder, error)
            return
        }

        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error,
            overrideWidth = widthPx,
            overrideHeight = heightPx
        ).into(imageView)
    }

    /**
     * 加载并指定 Glide Priority（Java）
     */
    @JvmStatic
    @JvmOverloads
    fun loadWithPriority(
        imageView: ImageView,
        data: Any?,
        priority: Priority = Priority.NORMAL,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error
        ).priority(priority).into(imageView)
    }

    /**
     * 加载主图 + 缩略图（Java）
     *
     * @param thumbnailData 缩略图资源（可为 url/file/uri），为 null 时使用 thumbnailScale
     * @param thumbnailScale 缩略图比例（0~1）
     */
    @JvmStatic
    @JvmOverloads
    fun loadWithThumbnail(
        imageView: ImageView,
        mainData: Any?,
        thumbnailData: Any? = null,
        thumbnailScale: Float = 0.2f,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        if (mainData == null) {
            if (error != 0) {
                imageView.setImageResource(error)
            } else if (placeholder != 0) {
                imageView.setImageResource(placeholder)
            }
            return
        }

        val ctx = imageView.context
        var builder = buildRequest(
            imageView = imageView,
            data = mainData,
            placeholder = placeholder,
            error = error
        )

        builder = if (thumbnailData != null) {
            val thumbRequest = Glide.with(ctx).load(thumbnailData)
            builder.thumbnail(thumbRequest)
        } else {
            builder.thumbnail(thumbnailScale)
        }

        builder.into(imageView)
    }

    /**
     * 加载视频某一帧（Java）
     *
     * @param frameTimeMicros 指定帧的时间（微秒），默认 0 取首帧
     */
    @JvmStatic
    @JvmOverloads
    fun loadVideoFrame(
        imageView: ImageView,
        data: Any?,
        frameTimeMicros: Long = 0L,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        val ctx = imageView.context
        var request = Glide.with(ctx).asBitmap().load(data)
        var options = RequestOptions()
            .frame(frameTimeMicros)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .centerCrop()
        if (placeholder != 0) options = options.placeholder(placeholder)
        if (error != 0) options = options.error(error)
        request = request.apply(options)
        request.into(imageView)
    }

    /**
     * 只加载 GIF 的第一帧（当作静态图）
     */
    @JvmStatic
    @JvmOverloads
    fun loadGifFirstFrame(
        imageView: ImageView,
        data: Any?,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        load(imageView, data, placeholder, error)
    }

    /**
     * 加载灰度图（Java）
     */
    @JvmStatic
    @JvmOverloads
    fun loadGray(
        imageView: ImageView,
        data: Any?,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        if (data == null) {
            if (error != 0) {
                imageView.setImageResource(error)
            } else if (placeholder != 0) {
                imageView.setImageResource(placeholder)
            }
            return
        }

        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error,
            centerCrop = false,
            extraOptionsBlock = {
                this.transform(CenterCrop(), GrayScaleTransformation())
            }
        ).into(imageView)
    }

    /**
     * 加载模糊图（Java）
     *
     * @param radius 模糊半径（1~25，越大越模糊）
     */
    @JvmStatic
    @JvmOverloads
    fun loadBlur(
        imageView: ImageView,
        data: Any?,
        radius: Float = 15f,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        if (data == null) {
            if (error != 0) {
                imageView.setImageResource(error)
            } else if (placeholder != 0) {
                imageView.setImageResource(placeholder)
            }
            return
        }

        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error,
            centerCrop = false,
            extraOptionsBlock = {
                this.transform(CenterCrop(), BlurTransformation(imageView.context, radius))
            }
        ).into(imageView)
    }

    /**
     * 加载颗粒化像素效果（Java）
     *
     * @param pixelSize 单个像素块大小，越大越模糊（推荐 8~50）
     */
    @JvmStatic
    @JvmOverloads
    fun loadPixelate(
        imageView: ImageView,
        data: Any?,
        pixelSize: Int = 16,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error,
            centerCrop = false,
            extraOptionsBlock = {
                this.transform(CenterCrop(), PixelationTransformation(pixelSize))
            }
        ).into(imageView)
    }

    /**
     * 加载怀旧/棕褐色（Java）
     */
    @JvmStatic
    @JvmOverloads
    fun loadSepia(
        imageView: ImageView,
        data: Any?,
        intensity: Float = 1.0f,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error,
            centerCrop = false,
            extraOptionsBlock = {
                this.transform(CenterCrop(), SepiaTransformation(intensity))
            }
        ).into(imageView)
    }

    /**
     * 加载彩色蒙层（Java）
     *
     * @param color ARGB 颜色值
     * @param alpha 0f~1f，透明度
     */
    @JvmStatic
    @JvmOverloads
    fun loadColorOverlay(
        imageView: ImageView,
        data: Any?,
        color: Int,
        alpha: Float = 0.5f,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error,
            centerCrop = false,
            extraOptionsBlock = {
                this.transform(CenterCrop(), ColorOverlayTransformation(color, alpha))
            }
        ).into(imageView)
    }

    /**
     * 加载圆形头像并添加边框（Java）
     */
    @JvmStatic
    @JvmOverloads
    fun loadCircleWithBorder(
        imageView: ImageView,
        data: Any?,
        borderWidthDp: Float = 2f,
        borderColor: Int,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        val borderWidthPx = dp2px(imageView.context, borderWidthDp)
        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error,
            extraOptionsBlock = {
                this.transform(
                    CenterCrop(),
                    CircleCrop(),
                    BorderTransformation(borderWidthPx, borderColor)
                )
            }
        ).into(imageView)
    }

    /**
     * 加载自定义四角圆角（Java）
     *
     * @param topLeftDp     左上角圆角
     * @param topRightDp    右上角圆角
     * @param bottomRightDp 右下角圆角
     * @param bottomLeftDp  左下角圆角
     */
    @JvmStatic
    @JvmOverloads
    fun loadRoundCustom(
        imageView: ImageView,
        data: Any?,
        topLeftDp: Float = 0f,
        topRightDp: Float = 0f,
        bottomRightDp: Float = 0f,
        bottomLeftDp: Float = 0f,
        placeholder: Int = 0,
        error: Int = 0
    ) {
        val ctx = imageView.context
        val transform = MultiRoundedCornersTransformation(
            dp2px(ctx, topLeftDp),
            dp2px(ctx, topRightDp),
            dp2px(ctx, bottomRightDp),
            dp2px(ctx, bottomLeftDp)
        )

        buildRequest(
            imageView = imageView,
            data = data,
            placeholder = placeholder,
            error = error,
            centerCrop = false,
            extraOptionsBlock = {
                this.transform(CenterCrop(), transform)
            }
        ).into(imageView)
    }

    /**
     * 使用 Drawable 占位图/错误图加载（Java）
     */
    @JvmStatic
    fun loadWithDrawable(
        imageView: ImageView,
        data: Any?,
        placeholderDrawable: Drawable?,
        errorDrawable: Drawable?
    ) {
        val ctx = imageView.context
        var request = Glide.with(ctx).load(data)
        var options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .centerCrop()
        if (placeholderDrawable != null) options = options.placeholder(placeholderDrawable)
        if (errorDrawable != null) options = options.error(errorDrawable)
        request = request.apply(options)
            .transition(DrawableTransitionOptions.withCrossFade())
        request.into(imageView)
    }


    /**
     * 清除某个 ImageView 上的 Glide 请求（Java）
     */
    @JvmStatic
    fun clear(imageView: ImageView) {
        Glide.with(imageView.context).clear(imageView)
    }

    /**
     * 清除磁盘缓存（需在子线程调用）
     */
    @JvmStatic
    fun clearDiskCache(context: Context) {
        Thread {
            Glide.get(context.applicationContext).clearDiskCache()
        }.start()
    }

    /**
     * 清除内存缓存（需在主线程调用）
     */
    @JvmStatic
    fun clearMemory(context: Context) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Glide.get(context.applicationContext).clearMemory()
        } else {
            Handler(Looper.getMainLooper()).post {
                Glide.get(context.applicationContext).clearMemory()
            }
        }
    }

    /**
     * 暂停当前 Activity 相关的所有 Glide 请求（Java）
     */
    @JvmStatic
    fun pause(activity: Activity) {
        Glide.with(activity).pauseRequests()
    }

    /**
     * 恢复当前 Activity 相关的所有 Glide 请求（Java）
     */
    @JvmStatic
    fun resume(activity: Activity) {
        Glide.with(activity).resumeRequests()
    }

    /**
     * 暂停当前 Fragment 相关的所有 Glide 请求（Java）
     */
    @JvmStatic
    fun pause(fragment: Fragment) {
        Glide.with(fragment).pauseRequests()
    }

    /**
     * 恢复当前 Fragment 相关的所有 Glide 请求（Java）
     */
    @JvmStatic
    fun resume(fragment: Fragment) {
        Glide.with(fragment).resumeRequests()
    }
}

/*---------------------------------- Kotlin 扩展函数封装（委托给 GlideExt） ----------------------------------*/

/**
 * Kotlin：加载任意图片资源
 */
fun ImageView.load(
    data: Any?,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.load(this, data, placeholder, error)
}

/**
 * Kotlin：加载 URL 字符串
 */
fun ImageView.loadUrl(
    url: String?,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.load(this, url, placeholder, error)
}

/**
 * Kotlin：从 Uri 加载
 */
fun ImageView.loadUri(
    uri: Uri?,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.load(this, uri, placeholder, error)
}

/**
 * Kotlin：从资源 id 加载
 */
fun ImageView.loadRes(
    resId: Int,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.load(this, resId, placeholder, error)
}

/**
 * Kotlin：加载圆形头像
 */
fun ImageView.loadCircle(
    data: Any?,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadCircle(this, data, placeholder, error)
}

/**
 * Kotlin：加载圆角图片
 */
fun ImageView.loadRound(
    data: Any?,
    radiusDp: Float,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadRound(this, data, radiusDp, placeholder, error)
}

/**
 * Kotlin：加载 GIF
 */
fun ImageView.loadGif(
    data: Any?,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadGif(this, data, placeholder, error)
}

/**
 * Kotlin：不缓存加载
 */
fun ImageView.loadNoCache(
    data: Any?,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadNoCache(this, data, placeholder, error)
}

/**
 * Kotlin：指定尺寸加载
 */
fun ImageView.loadWithSize(
    data: Any?,
    widthPx: Int,
    heightPx: Int,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadWithSize(this, data, widthPx, heightPx, placeholder, error)
}

/**
 * Kotlin：指定优先级加载
 */
fun ImageView.loadWithPriority(
    data: Any?,
    priority: Priority = Priority.NORMAL,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadWithPriority(this, data, priority, placeholder, error)
}

/**
 * Kotlin：主图 + 缩略图
 */
fun ImageView.loadWithThumbnail(
    mainData: Any?,
    thumbnailData: Any? = null,
    thumbnailScale: Float = 0.2f,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadWithThumbnail(this, mainData, thumbnailData, thumbnailScale, placeholder, error)
}

/**
 * Kotlin：视频帧
 */
fun ImageView.loadVideoFrame(
    data: Any?,
    frameTimeMicros: Long = 0L,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadVideoFrame(this, data, frameTimeMicros, placeholder, error)
}

/**
 * Kotlin：GIF 第一帧
 */
fun ImageView.loadGifFirstFrame(
    data: Any?,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadGifFirstFrame(this, data, placeholder, error)
}

/**
 * Kotlin：灰度图
 */
fun ImageView.loadGray(
    data: Any?,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadGray(this, data, placeholder, error)
}

/**
 * Kotlin：模糊图
 */
fun ImageView.loadBlur(
    data: Any?,
    radius: Float = 15f,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadBlur(this, data, radius, placeholder, error)
}

/**
 * Kotlin：像素化
 */
fun ImageView.loadPixelate(
    data: Any?,
    pixelSize: Int = 16,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadPixelate(this, data, pixelSize, placeholder, error)
}

/**
 * Kotlin：复古色
 */
fun ImageView.loadSepia(
    data: Any?,
    intensity: Float = 1.0f,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadSepia(this, data, intensity, placeholder, error)
}

/**
 * Kotlin：彩色蒙层
 */
fun ImageView.loadColorOverlay(
    data: Any?,
    color: Int,
    alpha: Float = 0.5f,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadColorOverlay(this, data, color, alpha, placeholder, error)
}

/**
 * Kotlin：带边框的圆形头像
 */
fun ImageView.loadCircleWithBorder(
    data: Any?,
    borderWidthDp: Float = 2f,
    borderColor: Int,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadCircleWithBorder(this, data, borderWidthDp, borderColor, placeholder, error)
}

/**
 * Kotlin：自定义四角圆角
 */
fun ImageView.loadRoundCustom(
    data: Any?,
    topLeftDp: Float = 0f,
    topRightDp: Float = 0f,
    bottomRightDp: Float = 0f,
    bottomLeftDp: Float = 0f,
    placeholder: Int = 0,
    error: Int = 0
) {
    GlideExt.loadRoundCustom(
        this,
        data,
        topLeftDp,
        topRightDp,
        bottomRightDp,
        bottomLeftDp,
        placeholder,
        error
    )
}

/**
 * Kotlin：清除当前 ImageView 的 Glide 请求
 */
fun ImageView.clearGlide() {
    GlideExt.clear(this)
}

/**
 * Kotlin：清除磁盘缓存（需放在子线程）
 */
fun Context.clearGlideDiskCache() {
    GlideExt.clearDiskCache(this)
}

/**
 * Kotlin：清除内存缓存（需主线程调用）
 */
fun Context.clearGlideMemory() {
    GlideExt.clearMemory(this)
}

/*---------------------------------- 自定义 Transformation ----------------------------------*/

private class GrayScaleTransformation : BitmapTransformation() {
    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update((ID).toByteArray(Key.CHARSET))
    }

    override fun equals(other: Any?): Boolean = other is GrayScaleTransformation

    override fun hashCode(): Int = ID.hashCode()

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val bitmap = pool.get(toTransform.width, toTransform.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(toTransform, 0f, 0f, paint)
        return bitmap
    }

    companion object {
        private const val ID = "GlideExt.GrayScaleTransformation"
    }
}

private class BlurTransformation(
    context: Context,
    private val radius: Float = 15f
) : BitmapTransformation() {

    private val appContext = context.applicationContext

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update((ID + radius).toByteArray(Key.CHARSET))
    }

    override fun equals(other: Any?): Boolean {
        return other is BlurTransformation && other.radius == radius
    }

    override fun hashCode(): Int = (ID.hashCode() + radius * 10).toInt()

    @SuppressLint("ObsoleteSdkInt")
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val safeRadius = radius.coerceIn(1f, 25f)
        val bitmap = toTransform.copy(Bitmap.Config.ARGB_8888, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                val rs = RenderScript.create(appContext)
                val input = Allocation.createFromBitmap(rs, bitmap)
                val output = Allocation.createTyped(rs, input.type)
                val intrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
                intrinsic.setRadius(safeRadius)
                intrinsic.setInput(input)
                intrinsic.forEach(output)
                output.copyTo(bitmap)
                rs.destroy()
            } catch (e: Exception) {
                // ignore，返回原图
            }
        }
        return bitmap
    }

    companion object {
        private const val ID = "GlideExt.BlurTransformation"
    }
}

/**
 * Sepia 复古色
 */
private class SepiaTransformation(
    private val intensity: Float = 1.0f
) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update((ID + intensity).toByteArray(Key.CHARSET))
    }

    override fun equals(other: Any?): Boolean {
        return other is SepiaTransformation && other.intensity == intensity
    }

    override fun hashCode(): Int = (ID.hashCode() + intensity * 10).toInt()

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val bitmap = pool.get(toTransform.width, toTransform.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = ColorMatrix()
        matrix.set(
            floatArrayOf(
                0.393f + 0.607f * (1 - intensity), 0.769f - 0.769f * (1 - intensity), 0.189f - 0.189f * (1 - intensity), 0f, 0f,
                0.349f - 0.349f * (1 - intensity), 0.686f + 0.314f * (1 - intensity), 0.168f - 0.168f * (1 - intensity), 0f, 0f,
                0.272f - 0.272f * (1 - intensity), 0.534f - 0.534f * (1 - intensity), 0.131f + 0.869f * (1 - intensity), 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(toTransform, 0f, 0f, paint)
        return bitmap
    }

    companion object {
        private const val ID = "GlideExt.SepiaTransformation"
    }
}

/**
 * 像素化
 */
private class PixelationTransformation(
    private val pixelSize: Int = 16
) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update((ID + pixelSize).toByteArray(Key.CHARSET))
    }

    override fun equals(other: Any?): Boolean {
        return other is PixelationTransformation && other.pixelSize == pixelSize
    }

    override fun hashCode(): Int = (ID.hashCode() + pixelSize * 10).toInt()

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val bitmap = pool.get(toTransform.width, toTransform.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val scaledBitmap = Bitmap.createScaledBitmap(
            toTransform,
            toTransform.width / pixelSize,
            toTransform.height / pixelSize,
            false
        )
        val tempBitmap = Bitmap.createScaledBitmap(
            scaledBitmap,
            toTransform.width,
            toTransform.height,
            false
        )
        canvas.drawBitmap(tempBitmap, 0f, 0f, paint)
        scaledBitmap.recycle()
        tempBitmap.recycle()
        return bitmap
    }

    companion object {
        private const val ID = "GlideExt.PixelationTransformation"
    }
}

/**
 * 彩色蒙层
 */
private class ColorOverlayTransformation(
    private val color: Int,
    private val alpha: Float = 0.5f
) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update((ID + color + alpha).toByteArray(Key.CHARSET))
    }

    override fun equals(other: Any?): Boolean {
        return other is ColorOverlayTransformation &&
                other.color == color &&
                other.alpha == alpha
    }

    override fun hashCode(): Int = (ID.hashCode() + color + alpha * 10).toInt()

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val bitmap = pool.get(toTransform.width, toTransform.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(toTransform, 0f, 0f, null)
        paint.color = color
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        return bitmap
    }

    companion object {
        private const val ID = "GlideExt.ColorOverlayTransformation"
    }
}

/**
 * 圆形边框
 */
private class BorderTransformation(
    private val borderWidth: Int,
    private val borderColor: Int
) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update((ID + borderWidth + borderColor).toByteArray(Key.CHARSET))
    }

    override fun equals(other: Any?): Boolean {
        return other is BorderTransformation &&
                other.borderWidth == borderWidth &&
                other.borderColor == borderColor
    }

    override fun hashCode(): Int = (ID.hashCode() + borderWidth + borderColor).toInt()

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val bitmap = pool.get(toTransform.width, toTransform.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(toTransform, 0f, 0f, null)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        borderPaint.style = Paint.Style.STROKE
        borderPaint.color = borderColor
        borderPaint.strokeWidth = borderWidth.toFloat()
        val radius = (toTransform.width.coerceAtMost(toTransform.height) / 2f) - borderWidth / 2f
        canvas.drawCircle(
            toTransform.width / 2f,
            toTransform.height / 2f,
            radius,
            borderPaint
        )
        return bitmap
    }

    companion object {
        private const val ID = "GlideExt.BorderTransformation"
    }
}

/**
 * 自定义四角圆角
 */
private class MultiRoundedCornersTransformation(
    private val topLeft: Int,
    private val topRight: Int,
    private val bottomRight: Int,
    private val bottomLeft: Int
) : BitmapTransformation() {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update((ID + topLeft + topRight + bottomRight + bottomLeft).toByteArray(Key.CHARSET))
    }

    override fun equals(other: Any?): Boolean {
        return other is MultiRoundedCornersTransformation &&
                other.topLeft == topLeft &&
                other.topRight == topRight &&
                other.bottomRight == bottomRight &&
                other.bottomLeft == bottomLeft
    }

    override fun hashCode(): Int = (ID.hashCode() + topLeft + topRight + bottomRight + bottomLeft)

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val bitmap = pool.get(toTransform.width, toTransform.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = BitmapShader(
            toTransform,
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP
        )
        paint.shader = shader

        val path = Path()
        val radii = floatArrayOf(
            topLeft.toFloat(), topLeft.toFloat(),
            topRight.toFloat(), topRight.toFloat(),
            bottomRight.toFloat(), bottomRight.toFloat(),
            bottomLeft.toFloat(), bottomLeft.toFloat()
        )
        path.addRoundRect(
            RectF(
                0f,
                0f,
                toTransform.width.toFloat(),
                toTransform.height.toFloat()
            ),
            radii,
            Path.Direction.CW
        )
        canvas.drawPath(path, paint)
        return bitmap
    }

    companion object {
        private const val ID = "GlideExt.MultiRoundedCornersTransformation"
    }
}


