package com.sortsnap.domain

import com.sortsnap.classifier.Category
import com.sortsnap.data.db.ImageRecord
import com.sortsnap.data.delete.DeleteTarget
import com.sortsnap.data.media.MediaStoreImage

/** What the grid and viewer show, whether it came from MediaStore directly or from the index. */
data class GalleryItem(
    val id: Long,
    val uri: String,
    val displayName: String,
    val dateTaken: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val folder: String,
    val category: Category? = null,
    val confidence: Float = 0f,
) {
    fun toDeleteTarget() = DeleteTarget(id, uri, sizeBytes)
}

fun MediaStoreImage.toGalleryItem() = GalleryItem(
    id = id, uri = uri, displayName = displayName, dateTaken = dateTaken, sizeBytes = sizeBytes,
    width = width, height = height, folder = relativePath.ifBlank { bucket },
)

fun ImageRecord.toGalleryItem() = GalleryItem(
    id = mediaId, uri = uri, displayName = displayName, dateTaken = dateTaken, sizeBytes = size,
    width = width, height = height, folder = path.ifBlank { bucket }, category = effectiveCategory,
    confidence = if (userOverride != null) 1f else confidence,
)

/** Which images a grid/viewer shows. Encoded to a string for navigation arguments. */
sealed interface GalleryFilter {
    data object All : GalleryFilter
    /** Everything except SCREENSHOT/MEME/FORWARD/DOCUMENT. OTHER and unclassified stay visible. */
    data object HideClutter : GalleryFilter
    data class Only(val category: Category) : GalleryFilter

    fun encode(): String = when (this) {
        All -> "all"
        HideClutter -> "photos"
        is Only -> "cat:${category.name}"
    }

    companion object {
        fun decode(raw: String?): GalleryFilter = when {
            raw == "photos" -> HideClutter
            raw != null && raw.startsWith("cat:") -> Category.fromNameOrNull(raw.removePrefix("cat:"))?.let(::Only) ?: All
            else -> All
        }
    }
}
