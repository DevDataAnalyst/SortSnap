package com.sortsnap.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.sortsnap.domain.MediaStamp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** All MediaStore access. Content URIs only; never raw file paths. */
@Singleton
class MediaStoreRepository @Inject constructor(
    @ApplicationContext context: android.content.Context,
) {
    private val resolver: ContentResolver = context.contentResolver

    val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private val projection = MediaStoreCursorMapper.projection(hasRelativePath)
    private val sortOrder = "${MediaStoreCursorMapper.DATE_TAKEN} DESC, ${MediaStoreCursorMapper.ID} DESC"

    fun uriFor(id: Long): Uri = ContentUris.withAppendedId(collection, id)

    /** One page, newest first. Uses Bundle query args (LIMIT/OFFSET), supported from API 26. */
    suspend fun queryPage(offset: Int, limit: Int): List<MediaStoreImage> = withContext(Dispatchers.IO) {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        resolver.query(collection, projection, args, null)?.use { c ->
            val reader = CursorRowReader(c)
            val base = collection.toString()
            buildList(c.count) { while (c.moveToNext()) MediaStoreCursorMapper.map(reader, base)?.let(::add) }
        }.orEmpty()
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        resolver.query(collection, arrayOf(MediaStoreCursorMapper.ID), null, null, null)?.use { it.count } ?: 0
    }

    /** Lightweight id/modified/size for every image: the input to SyncDiff. */
    suspend fun stamps(): List<MediaStamp> = withContext(Dispatchers.IO) {
        resolver.query(collection, MediaStoreCursorMapper.STAMP_PROJECTION, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStoreCursorMapper.ID)
            val modCol = c.getColumnIndexOrThrow(MediaStoreCursorMapper.DATE_MODIFIED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStoreCursorMapper.SIZE)
            buildList(c.count) {
                while (c.moveToNext()) add(MediaStamp(c.getLong(idCol), c.getLong(modCol), c.getLong(sizeCol)))
            }
        }.orEmpty()
    }

    /** Full rows for specific ids, chunked to stay under SQLite's variable limit. */
    suspend fun queryByIds(ids: List<Long>): List<MediaStoreImage> = withContext(Dispatchers.IO) {
        val base = collection.toString()
        ids.chunked(500).flatMap { chunk ->
            val selection = "${MediaStoreCursorMapper.ID} IN (${chunk.joinToString(",")})"
            resolver.query(collection, projection, selection, null, null)?.use { c ->
                val reader = CursorRowReader(c)
                buildList(c.count) { while (c.moveToNext()) MediaStoreCursorMapper.map(reader, base)?.let(::add) }
            }.orEmpty()
        }
    }

    /** Emits whenever images are added, changed or removed. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(collection, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()
}
