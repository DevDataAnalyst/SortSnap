package com.sortsnap.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** FULL: whole library. PARTIAL: Android 14+ "selected photos only". NONE: no access. */
enum class PhotoAccess {
    FULL, PARTIAL, NONE;

    companion object {
        fun current(context: Context, sdk: Int = Build.VERSION.SDK_INT): PhotoAccess {
            fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
            return fromGrants(sdk, ::granted)
        }

        /** Pure decision logic so it can be unit-tested for every API level. */
        fun fromGrants(sdk: Int, granted: (String) -> Boolean): PhotoAccess = when {
            sdk >= Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_MEDIA_IMAGES) -> FULL
            sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PARTIAL
            sdk >= Build.VERSION_CODES.TIRAMISU -> NONE
            granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> FULL
            else -> NONE
        }

        /** Permissions to request together on this API level. */
        fun requestSet(sdk: Int = Build.VERSION.SDK_INT): Array<String> = when {
            sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            )
            sdk >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            )
            sdk >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        }
    }
}
