package com.sortsnap.data.ml

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Size
import com.sortsnap.classifier.ImageMath
import com.sortsnap.classifier.VisualFeatures
import javax.inject.Inject
import javax.inject.Singleton

data class ThumbnailStats(val dHash: Long, val blurScore: Double, val visual: VisualFeatures)

/** Decodes tiny thumbnails (never full-size images) for hashing, blur, brightness and colour statistics. */
@Singleton
class ThumbnailAnalyzer @Inject constructor() {

    fun analyze(resolver: ContentResolver, uri: Uri): ThumbnailStats? {
        val bitmap = decodeSmall(resolver, uri, THUMB_PX) ?: return null
        try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val gray = IntArray(pixels.size) { ImageMath.luma(pixels[it]) }
            return ThumbnailStats(
                dHash = ImageMath.dHash(gray, w, h),
                blurScore = ImageMath.laplacianVariance(gray, w, h),
                visual = ImageMath.visualFeatures(gray, pixels),
            )
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        const val THUMB_PX = 256

        /** Downscaled, software (non-hardware) decode so pixels can be read and memory stays bounded. */
        fun decodeSmall(resolver: ContentResolver, uri: Uri, maxPx: Int): Bitmap? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && maxPx <= 512) {
                val thumb = resolver.loadThumbnail(uri, Size(maxPx, maxPx), null)
                if (thumb.config == Bitmap.Config.HARDWARE) thumb.copy(Bitmap.Config.ARGB_8888, false).also { thumb.recycle() } else thumb
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > maxPx) {
                        val scale = maxPx.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }
        }.getOrNull()
    }
}
