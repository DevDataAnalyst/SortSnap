package com.sortsnap.data.scan

import com.sortsnap.data.db.ImageDao
import com.sortsnap.data.db.ImageRecord
import com.sortsnap.data.media.MediaStoreImage
import com.sortsnap.data.media.MediaStoreRepository
import com.sortsnap.domain.MediaStamp
import com.sortsnap.domain.SyncDiff
import com.sortsnap.domain.SyncPlan
import javax.inject.Inject
import javax.inject.Singleton

/** Makes the Room index match MediaStore: insert new, delete removed, refresh changed. */
@Singleton
class IndexSync @Inject constructor(
    private val media: MediaStoreRepository,
    private val images: ImageDao,
) {
    suspend fun sync(): SyncPlan {
        val plan = SyncDiff.compute(
            mediaStore = media.stamps(),
            index = images.stamps().map { MediaStamp(it.mediaId, it.dateModified, it.size) },
        )
        if (plan.toDelete.isNotEmpty()) images.deleteByIds(plan.toDelete)
        plan.toInsert.chunked(INSERT_CHUNK).forEach { chunk ->
            images.insertAll(media.queryByIds(chunk).map { it.toRecord() })
        }
        plan.toRefresh.chunked(INSERT_CHUNK).forEach { chunk ->
            media.queryByIds(chunk).forEach { m ->
                images.refreshMetadata(
                    m.id, m.uri, m.displayName, m.relativePath, m.bucket, m.dateTaken, m.dateModified,
                    m.width, m.height, m.sizeBytes, m.mimeType,
                )
            }
        }
        return plan
    }

    companion object {
        const val INSERT_CHUNK = 500
    }
}

fun MediaStoreImage.toRecord() = ImageRecord(
    mediaId = id,
    uri = uri,
    displayName = displayName,
    path = relativePath,
    bucket = bucket,
    dateTaken = dateTaken,
    dateModified = dateModified,
    width = width,
    height = height,
    size = sizeBytes,
    mime = mimeType,
)
