package com.sortsnap.data.ml

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.core.net.toUri
import com.sortsnap.classifier.ImageFeatures
import com.sortsnap.classifier.ScreenSize
import com.sortsnap.data.db.ImageRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds classifier input from an index row plus EXIF and thumbnail statistics. */
@Singleton
class FeatureExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exifReader: ExifReader,
    private val thumbnails: ThumbnailAnalyzer,
) {
    val screenSize: ScreenSize by lazy { realScreenSize() }

    fun extract(record: ImageRecord): Pair<ImageFeatures, ThumbnailStats?> {
        val uri = record.uri.toUri()
        val resolver = context.contentResolver
        val exif = exifReader.read(resolver, uri, record.mime)
        val stats = thumbnails.analyze(resolver, uri)
        return ImageFeatures(
            relativePath = record.path,
            bucket = record.bucket,
            displayName = record.displayName,
            mimeType = record.mime,
            width = record.width,
            height = record.height,
            sizeBytes = record.size,
            exif = exif,
            screen = screenSize,
            visual = stats?.visual,
        ) to stats
    }

    @Suppress("DEPRECATION")
    private fun realScreenSize(): ScreenSize {
        val wm = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            ScreenSize(minOf(b.width(), b.height()), maxOf(b.width(), b.height()))
        } else {
            val m = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(m)
            ScreenSize(minOf(m.widthPixels, m.heightPixels), maxOf(m.widthPixels, m.heightPixels))
        }
    }
}
