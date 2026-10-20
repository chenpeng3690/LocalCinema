package com.localcinema.app.playback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * 通知与锁屏卡片的封面。
 *
 * 本地视频的 MediaMetadata 里没有 artwork（不像音乐文件带内嵌封面），
 * 系统取不到图就只能退回默认占位图，观感就是「通知里没有图标」。
 *
 * media3 的 DefaultMediaNotificationProvider 通过 session 的 BitmapLoader
 * 取大图（largeIcon），所以这里挂一个「永远有图」的 loader：
 * 不管当前视频是什么，都返回同一张内置封面。
 *
 * 刻意不用 MediaMetadata.setArtworkData 把图片字节塞进每个 MediaItem ——
 * 播放队列可能上百集，每个 item 带一份几十 KB 的图，timeline 经 Binder
 * 传给控制器时会直接撑爆事务上限。
 */
class PlaybackArtwork : BitmapLoader {

    private val artwork: Bitmap by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { drawArtwork() }

    /**
     * media3 拿到 Bitmap 后会自己 recycle，所以每个调用方都得给独立副本，
     * 否则第二个人（通知、锁屏、媒体中心）拿到的是已回收的位图。
     */
    private fun copy(): Bitmap = artwork.copy(Bitmap.Config.ARGB_8888, false)

    override fun supportsMimeType(mimeType: String): Boolean = false

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        Futures.immediateFuture(copy())

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        Futures.immediateFuture(copy())

    /** 覆盖默认实现：默认在没有 artwork 时返回 null（等于没有图），这里始终给封面 */
    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> =
        Futures.immediateFuture(copy())

    private fun drawArtwork(): Bitmap {
        val size = SIZE
        val s = size.toFloat()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 底色：与 App 的 bg(#08080A) 同一色系，加一点对角渐变免得看着像纯黑块
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, s, s,
                0xFF23262E.toInt(),
                0xFF08080A.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, s, s, background)

        // 一圈很淡的描边：锁屏是圆形裁切，有这圈才知道边界在哪
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.010f
            color = 0x26FFFFFF
        }
        canvas.drawCircle(s / 2f, s / 2f, s * 0.40f, ring)

        // 品牌色播放三角（accent #FF6B45），FILL_AND_STROKE + ROUND join 让三个角不尖
        val triangle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL_AND_STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = s * 0.055f
            color = 0xFFFF6B45.toInt()
        }
        val path = Path().apply {
            moveTo(s * 0.405f, s * 0.295f)
            lineTo(s * 0.745f, s * 0.500f)
            lineTo(s * 0.405f, s * 0.705f)
            close()
        }
        canvas.drawPath(path, triangle)

        return bitmap
    }

    private companion object {
        /** 512 足够锁屏 3x 屏显示；再大只是徒增每次拷贝的内存 */
        const val SIZE = 512
    }
}
