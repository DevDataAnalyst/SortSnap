package com.sortsnap.domain

import androidx.room.withTransaction
import com.sortsnap.classifier.Category
import com.sortsnap.data.db.CorrectionEntity
import com.sortsnap.data.db.FolderPriorEntity
import com.sortsnap.data.db.ImageRecord
import com.sortsnap.data.db.SortSnapDatabase
import com.sortsnap.data.scan.IndexSync
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Move to category": sets userOverride (which no rescan ever touches), logs a correction with the
 * prediction and evidence at the time, and updates per-folder learned rules.
 */
@Singleton
class CorrectionUseCase @Inject constructor(
    private val db: SortSnapDatabase,
    private val indexSync: IndexSync,
) {

    suspend fun moveToCategory(ids: Collection<Long>, category: Category, now: Long = System.currentTimeMillis()) {
        if (ids.isEmpty()) return
        // Images picked from the unindexed "All" view may not be in Room yet: index them first.
        val known = ids.toList().chunked(900).sumOf { db.images().byIds(it).size }
        if (known < ids.size) indexSync.sync()
        db.withTransaction {
            val records = ids.toList().chunked(900).flatMap { db.images().byIds(it) }
            val corrections = records.filter { it.effectiveCategory != category }.map { it.toCorrection(category, now) }
            ids.toList().chunked(900).forEach { db.images().setOverride(it, category) }
            if (corrections.isNotEmpty()) {
                db.corrections().insertAll(corrections)
                corrections.map { it.bucket }.distinct().forEach { updatePrior(it, now) }
            }
        }
    }

    /** "Is this right?" → Yes. Locks the current category as the user's decision. */
    suspend fun confirm(id: Long) {
        val record = db.images().get(id) ?: return
        val category = record.effectiveCategory ?: return
        db.images().setOverride(listOf(id), category)
    }

    private suspend fun updatePrior(bucket: String, now: Long) {
        val events = db.corrections().forBucket(bucket).map { CorrectionEvent(it.bucket, it.predicted, it.corrected) }
        val existing = db.priors().get(bucket)
        val dismissedAt = existing?.takeIf { !it.active }?.dismissedAtCount
        val learned = FolderPriorLogic.learnForBucket(bucket, events, dismissedAt) ?: return
        db.priors().upsert(
            FolderPriorEntity(
                bucket = bucket,
                category = learned.category,
                sampleCount = learned.sampleCount,
                learnedAt = existing?.takeIf { it.active && it.category == learned.category }?.learnedAt ?: now,
                active = true,
                dismissedAtCount = null,
            ),
        )
    }

    private fun ImageRecord.toCorrection(corrected: Category, now: Long) = CorrectionEntity(
        mediaId = mediaId,
        bucket = bucket,
        predicted = category,
        predictedConfidence = confidence,
        corrected = corrected,
        featuresJson = signalsJson,
        createdAt = now,
    )
}
