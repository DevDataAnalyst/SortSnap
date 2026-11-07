package com.sortsnap.data.media

import android.database.Cursor

/** Minimal read-only view of a row so the mapper can be unit-tested without a real Cursor. */
interface RowReader {
    fun getLong(column: String): Long?
    fun getInt(column: String): Int?
    fun getString(column: String): String?
}

class CursorRowReader(private val cursor: Cursor) : RowReader {
    private val indexCache = HashMap<String, Int>()
    private fun index(column: String): Int = indexCache.getOrPut(column) { cursor.getColumnIndex(column) }

    override fun getLong(column: String): Long? = index(column).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
    override fun getInt(column: String): Int? = index(column).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getInt)
    override fun getString(column: String): String? = index(column).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString)
}

/**
 * Maps MediaStore.Images rows to [MediaStoreImage]. Column names are literal strings (they are
 * stable public API) so this object has no Android dependency beyond the [RowReader] it is given.
 */
object MediaStoreCursorMapper {
    const val ID = "_id"
    const val DISPLAY_NAME = "_display_name"
    const val RELATIVE_PATH = "relative_path"
    const val DATA = "_data"
    const val BUCKET = "bucket_display_name"
    const val DATE_TAKEN = "datetaken"
    const val DATE_ADDED = "date_added"
    const val DATE_MODIFIED = "date_modified"
    const val WIDTH = "width"
    const val HEIGHT = "height"
    const val SIZE = "_size"
    const val MIME = "mime_type"
    const val ORIENTATION = "orientation"

    /** Columns to request. [DATA] is only used on Android 9 and below to derive a relative path. */
    fun projection(includeRelativePath: Boolean): Array<String> = buildList {
        addAll(listOf(ID, DISPLAY_NAME, BUCKET, DATE_TAKEN, DATE_ADDED, DATE_MODIFIED, WIDTH, HEIGHT, SIZE, MIME, ORIENTATION))
        add(if (includeRelativePath) RELATIVE_PATH else DATA)
    }.toTypedArray()

    val STAMP_PROJECTION = arrayOf(ID, DATE_MODIFIED, SIZE)

    private val STORAGE_ROOT = Regex("""^/storage/(emulated/\d+|[^/]+)/""")

    /** Returns null for rows without an id (should never happen, but MediaStore can be odd). */
    fun map(row: RowReader, contentUriBase: String): MediaStoreImage? {
        val id = row.getLong(ID) ?: return null
        val displayName = row.getString(DISPLAY_NAME).orEmpty()
        val relativePath = row.getString(RELATIVE_PATH) ?: relativePathFromData(row.getString(DATA), displayName)
        val dateAddedMillis = (row.getLong(DATE_ADDED) ?: 0L) * 1000L
        val dateModifiedSeconds = row.getLong(DATE_MODIFIED) ?: 0L
        val dateTaken = row.getLong(DATE_TAKEN)?.takeIf { it > 0L }
            ?: dateAddedMillis.takeIf { it > 0L }
            ?: dateModifiedSeconds * 1000L
        var width = row.getInt(WIDTH) ?: 0
        var height = row.getInt(HEIGHT) ?: 0
        val orientation = row.getInt(ORIENTATION) ?: 0
        if (orientation == 90 || orientation == 270) {
            val t = width; width = height; height = t
        }
        return MediaStoreImage(
            id = id,
            uri = contentUriBase.trimEnd('/') + "/" + id,
            displayName = displayName,
            relativePath = relativePath,
            bucket = row.getString(BUCKET) ?: relativePath.trimEnd('/').substringAfterLast('/'),
            dateTaken = dateTaken,
            dateAdded = dateAddedMillis,
            dateModified = dateModifiedSeconds,
            width = width,
            height = height,
            sizeBytes = row.getLong(SIZE) ?: 0L,
            mimeType = row.getString(MIME).orEmpty(),
        )
    }

    /** "/storage/emulated/0/DCIM/Camera/IMG_1.jpg" -> "DCIM/Camera/". */
    fun relativePathFromData(data: String?, displayName: String): String {
        if (data.isNullOrBlank()) return ""
        val withoutRoot = data.replace(STORAGE_ROOT, "")
        val dir = if (displayName.isNotEmpty() && withoutRoot.endsWith(displayName)) {
            withoutRoot.removeSuffix(displayName)
        } else {
            withoutRoot.substringBeforeLast('/', "") + "/"
        }
        return if (dir == "/") "" else dir
    }
}
