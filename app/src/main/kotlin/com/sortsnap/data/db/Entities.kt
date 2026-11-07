package com.sortsnap.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sortsnap.classifier.Category

/**
 * One indexed image. [effectiveCategory] = userOverride ?: category, stored (not computed) so it
 * can be indexed and filtered cheaply on 50k+ rows.
 */
@Entity(
    tableName = "images",
    indices = [
        Index("category"),
        Index("effectiveCategory"),
        Index("dateTaken"),
        Index("bucket"),
        Index("contentHash"),
        Index("classifierVersion"),
    ],
)
data class ImageRecord(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val displayName: String,
    /** MediaStore RELATIVE_PATH. */
    val path: String,
    val bucket: String,
    val dateTaken: Long,
    /** MediaStore DATE_MODIFIED (seconds), used by the sync diff. */
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    val mime: String,
    /** 64-bit dHash of a small thumbnail. */
    val contentHash: Long? = null,
    /** Variance of Laplacian on a thumbnail; low = blurry. */
    val blurScore: Double? = null,
    val category: Category? = null,
    val confidence: Float = 0f,
    /** Why it was classified: serialised List<Signal>. */
    val signalsJson: String? = null,
    /** The user's manual choice. Always wins and is never touched by a rescan. */
    val userOverride: Category? = null,
    val effectiveCategory: Category? = null,
    val lastScannedAt: Long = 0,
    val classifierVersion: Int = 0,
    val mlProcessed: Boolean = false,
)

/** Every manual correction, with the features and prediction at the time (training data). */
@Entity(tableName = "corrections", indices = [Index("bucket"), Index("mediaId")])
data class CorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val bucket: String,
    val predicted: Category?,
    val predictedConfidence: Float,
    val corrected: Category,
    val featuresJson: String?,
    val createdAt: Long,
)

/** A learned per-folder bias ("Learned rules" in Settings). */
@Entity(tableName = "folder_priors")
data class FolderPriorEntity(
    @PrimaryKey val bucket: String,
    val category: Category,
    val sampleCount: Int,
    val learnedAt: Long,
    /** False once the user deletes the rule; [dismissedAtCount] stops it re-appearing immediately. */
    val active: Boolean = true,
    val dismissedAtCount: Int? = null,
)

/** Small key/value store for scanner state (resumable cursor, stats). */
@Entity(tableName = "scan_state")
data class ScanStateEntity(
    @PrimaryKey val key: String,
    val longValue: Long = 0,
    val stringValue: String? = null,
)

/** Images answered on the Review "Is this right?" card. */
@Entity(tableName = "review_answers")
data class ReviewAnswerEntity(
    @PrimaryKey val mediaId: Long,
    val answeredDay: Long,
    val wasCorrect: Boolean,
)
