package com.sortsnap.data.media

/** One row from MediaStore.Images. Uses a String uri so the mapping logic is JVM-testable. */
data class MediaStoreImage(
    val id: Long,
    val uri: String,
    val displayName: String,
    val relativePath: String,
    val bucket: String,
    /** Epoch millis. Falls back to DATE_ADDED when DATE_TAKEN is missing. */
    val dateTaken: Long,
    /** Epoch millis. */
    val dateAdded: Long,
    /** Epoch seconds, as MediaStore stores it; used for change detection only. */
    val dateModified: Long,
    /** Display width/height, already swapped for 90°/270° orientation. */
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String,
)
