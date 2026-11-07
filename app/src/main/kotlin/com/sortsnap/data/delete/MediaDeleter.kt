package com.sortsnap.data.delete

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import com.sortsnap.data.db.ImageDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeleteTarget(val mediaId: Long, val uri: String, val size: Long)

/** Next step of a deletion. The UI renders it; nothing is deleted without a confirmation step. */
sealed interface DeleteStep {
    /** Launch the system trash/delete confirmation dialog. */
    data class LaunchSystem(val intentSender: IntentSender, val count: Int, val trash: Boolean) : DeleteStep

    /** Android 9 and below has no system dialog: the app must show its own confirmation. */
    data class ConfirmInApp(val count: Int, val bytes: Long) : DeleteStep

    data class Done(val removedIds: List<Long>, val freedBytes: Long, val trashed: Boolean) : DeleteStep

    data object Cancelled : DeleteStep

    data class Failed(val message: String) : DeleteStep
}

/** Drives one deletion at a time. Implemented by [MediaStoreDeleter]; faked in UI tests. */
interface MediaDeleter {
    suspend fun start(targets: List<DeleteTarget>): DeleteStep
    suspend fun onSystemResult(approved: Boolean): DeleteStep
    suspend fun confirmInApp(): DeleteStep
}

/**
 * - Android 11+: MediaStore.createTrashRequest (recoverable for 30 days), falling back to
 *   createDeleteRequest if trash is unavailable. Large selections are split into chunks so the
 *   IntentSender stays under the Binder limit; each chunk gets its own system dialog.
 * - Android 10: delete one by one; a RecoverableSecurityException yields the system consent dialog
 *   for that item, then we retry it and continue.
 * - Android 9 and below: WRITE_EXTERNAL_STORAGE delete after an in-app confirmation dialog.
 * After success the rows are removed from Room immediately (the observer-triggered sync would also
 * catch it).
 */
@Singleton
class MediaStoreDeleter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val images: ImageDao,
) : MediaDeleter {
    private val resolver get() = context.contentResolver

    private var chunks: ArrayDeque<List<DeleteTarget>> = ArrayDeque()
    private var current: List<DeleteTarget> = emptyList()
    private var trash = false
    private var removed = mutableListOf<DeleteTarget>()
    private var q: ArrayDeque<DeleteTarget> = ArrayDeque()

    override suspend fun start(targets: List<DeleteTarget>): DeleteStep {
        reset()
        val unique = targets.distinctBy { it.mediaId }
        if (unique.isEmpty()) return DeleteStep.Cancelled
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                chunks = ArrayDeque(unique.chunked(CHUNK))
                nextSystemChunk()
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                q = ArrayDeque(unique)
                continueQ()
            }
            else -> {
                current = unique
                DeleteStep.ConfirmInApp(unique.size, unique.sumOf { it.size })
            }
        }
    }

    override suspend fun onSystemResult(approved: Boolean): DeleteStep {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (approved) removed += current
            // A declined chunk cancels the rest; anything already approved still counts.
            return if (approved) nextSystemChunk() else finish(cancelledIfEmpty = true)
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            if (!approved) return finish(cancelledIfEmpty = true)
            return continueQ()
        }
        return DeleteStep.Cancelled
    }

    override suspend fun confirmInApp(): DeleteStep = withContext(Dispatchers.IO) {
        current.forEach { t ->
            val ok = runCatching { resolver.delete(t.uri.toUri(), null, null) > 0 }.getOrDefault(false)
            if (ok) removed += t
        }
        finish(cancelledIfEmpty = false)
    }

    private suspend fun nextSystemChunk(): DeleteStep {
        val chunk = chunks.removeFirstOrNull() ?: return finish(cancelledIfEmpty = false)
        current = chunk
        val uris = chunk.map { it.uri.toUri() }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return DeleteStep.Failed("unsupported")
        return runCatching {
            trash = true
            DeleteStep.LaunchSystem(MediaStore.createTrashRequest(resolver, uris, true).intentSender, chunk.size, trash = true)
        }.recoverCatching {
            trash = false
            DeleteStep.LaunchSystem(MediaStore.createDeleteRequest(resolver, uris).intentSender, chunk.size, trash = false)
        }.getOrElse { DeleteStep.Failed(it.message ?: "Could not create delete request") }
    }

    private suspend fun continueQ(): DeleteStep = withContext(Dispatchers.IO) {
        while (q.isNotEmpty()) {
            val t = q.first()
            try {
                resolver.delete(t.uri.toUri(), null, null)
                removed += t
                q.removeFirst()
            } catch (e: SecurityException) {
                val rse = e as? RecoverableSecurityException
                    ?: return@withContext DeleteStep.Failed(e.message ?: "Permission denied")
                // After the user approves, the loop retries this same item.
                return@withContext DeleteStep.LaunchSystem(rse.userAction.actionIntent.intentSender, q.size, trash = false)
            }
        }
        finish(cancelledIfEmpty = false)
    }

    private suspend fun finish(cancelledIfEmpty: Boolean): DeleteStep {
        val done = removed.toList()
        val wasTrash = trash
        reset()
        if (done.isEmpty()) return if (cancelledIfEmpty) DeleteStep.Cancelled else DeleteStep.Done(emptyList(), 0, wasTrash)
        images.deleteByIds(done.map { it.mediaId })
        return DeleteStep.Done(done.map { it.mediaId }, done.sumOf { it.size }, wasTrash)
    }

    private fun reset() {
        chunks = ArrayDeque(); current = emptyList(); removed = mutableListOf(); q = ArrayDeque(); trash = false
    }

    companion object {
        /** Keeps each IntentSender's Parcel comfortably below the 1 MB Binder limit. */
        const val CHUNK = 1000
    }
}
