package com.sortsnap.data.ml

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.sortsnap.classifier.ExifFeatures
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExifReader @Inject constructor(@ApplicationContext private val context: Context) {

    private val canReadLocation: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun read(resolver: ContentResolver, uri: Uri, mime: String): ExifFeatures {
        // PNG/GIF/WebP screenshots and stickers have no camera EXIF worth reading.
        if (!mime.equals("image/jpeg", true) && !mime.equals("image/heic", true) && !mime.equals("image/heif", true)) {
            return ExifFeatures()
        }
        return runCatching {
            // Without ACCESS_MEDIA_LOCATION, Android 10+ redacts GPS; we only need "has GPS" so we ask for the original.
            val source = if (canReadLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
            } else {
                uri
            }
            resolver.openInputStream(source)?.use { stream ->
                val exif = ExifInterface(stream)
                ExifFeatures(
                    make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.ifBlank { null },
                    model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.ifBlank { null },
                    hasFocalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) != null,
                    hasGps = exif.latLong?.let { (lat, lon) -> lat != 0.0 || lon != 0.0 } ?: false,
                )
            } ?: ExifFeatures()
        }.getOrDefault(ExifFeatures())
    }
}
