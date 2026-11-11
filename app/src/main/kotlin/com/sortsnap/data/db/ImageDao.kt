package com.sortsnap.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sortsnap.classifier.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {

    // ---- Sync ----

    @Query("SELECT mediaId, dateModified, size FROM images")
    suspend fun stamps(): List<StampRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<ImageRecord>)

    @Query("DELETE FROM images WHERE mediaId IN (:ids)")
    suspend fun deleteByIdsChunk(ids: List<Long>)

    @Transaction
    suspend fun deleteByIds(ids: List<Long>) {
        ids.chunked(900).forEach { deleteByIdsChunk(it) }
    }

    /**
     * File changed on disk: refresh metadata. Rows with a user override keep their category;
     * everything else is queued for reclassification by resetting classifierVersion.
     */
    @Query(
        """UPDATE images SET uri = :uri, displayName = :displayName, path = :path, bucket = :bucket,
           dateTaken = :dateTaken, dateModified = :dateModified, width = :width, height = :height, size = :size,
           mime = :mime, contentHash = NULL, blurScore = NULL,
           classifierVersion = CASE WHEN userOverride IS NULL THEN 0 ELSE classifierVersion END
           WHERE mediaId = :mediaId""",
    )
    suspend fun refreshMetadata(
        mediaId: Long, uri: String, displayName: String, path: String, bucket: String, dateTaken: Long,
        dateModified: Long, width: Int, height: Int, size: Long, mime: String,
    )

    // ---- Classification queue ----

    @Query(
        """SELECT * FROM images WHERE userOverride IS NULL AND classifierVersion < :version AND mediaId > :afterId
           ORDER BY mediaId LIMIT :limit""",
    )
    suspend fun pendingBatch(version: Int, afterId: Long, limit: Int): List<ImageRecord>

    @Query("SELECT COUNT(*) FROM images WHERE userOverride IS NULL AND classifierVersion < :version")
    suspend fun pendingCount(version: Int): Long

    @Query("SELECT COUNT(*) FROM images WHERE userOverride IS NULL AND classifierVersion < :version")
    fun pendingCountFlow(version: Int): Flow<Long>

    /** Guarded by `userOverride IS NULL`: a scan can never overwrite a manual correction. */
    @Query(
        """UPDATE images SET category = :category, effectiveCategory = :category, confidence = :confidence,
           signalsJson = :signalsJson, contentHash = :contentHash, blurScore = :blurScore,
           lastScannedAt = :scannedAt, classifierVersion = :version, mlProcessed = :mlProcessed
           WHERE mediaId = :mediaId AND userOverride IS NULL""",
    )
    suspend fun updateClassification(
        mediaId: Long, category: Category, confidence: Float, signalsJson: String, contentHash: Long?,
        blurScore: Double?, scannedAt: Long, version: Int, mlProcessed: Boolean,
    ): Int

    /** Images the user sorted by hand, newest first, with their stored evidence (personal learning). */
    @Query(
        """SELECT userOverride AS category, signalsJson FROM images
           WHERE userOverride IS NOT NULL AND userOverride != 'OTHER' AND signalsJson IS NOT NULL AND signalsJson != '[]'
           ORDER BY lastScannedAt DESC, mediaId DESC LIMIT :limit""",
    )
    suspend fun labelledExamples(limit: Int): List<LabelledRow>

    /** Hand-sorted images that personal learning can use. */
    @Query(
        """SELECT COUNT(*) FROM images WHERE userOverride IS NOT NULL AND userOverride != 'OTHER'
           AND signalsJson IS NOT NULL AND signalsJson != '[]'""",
    )
    fun labelledCountFlow(): Flow<Int>

    /** "Re-sort unsure pictures": requeue only uncorrected rows the classifier was unsure about. */
    @Query("UPDATE images SET classifierVersion = 0 WHERE userOverride IS NULL AND (category = 'OTHER' OR confidence < :below)")
    suspend fun requeueUnsure(below: Float): Int

    /** "Rescan library": requeue everything the user has not corrected. */
    @Query("UPDATE images SET classifierVersion = 0 WHERE userOverride IS NULL")
    suspend fun requeueAll()

    // ---- Corrections ----

    @Query("UPDATE images SET userOverride = :category, effectiveCategory = :category WHERE mediaId IN (:ids)")
    suspend fun setOverride(ids: List<Long>, category: Category)

    @Query("SELECT * FROM images WHERE mediaId IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<ImageRecord>

    @Query("SELECT * FROM images WHERE mediaId = :id")
    fun observe(id: Long): Flow<ImageRecord?>

    @Query("SELECT * FROM images WHERE mediaId = :id")
    suspend fun get(id: Long): ImageRecord?

    // ---- Gallery ----

    @Query("SELECT * FROM images WHERE effectiveCategory IN (:categories) ORDER BY dateTaken DESC, mediaId DESC")
    fun pagingIn(categories: List<Category>): PagingSource<Int, ImageRecord>

    /** Unclassified rows (effectiveCategory NULL) stay visible: nothing is hidden before it is classified. */
    @Query(
        """SELECT * FROM images WHERE effectiveCategory IS NULL OR effectiveCategory NOT IN (:excluded)
           ORDER BY dateTaken DESC, mediaId DESC""",
    )
    fun pagingExcluding(excluded: List<Category>): PagingSource<Int, ImageRecord>

    /** "Select all" for a category album: id, uri and size only, so 50k rows stay small. */
    @Query("SELECT mediaId, uri, size FROM images WHERE effectiveCategory IN (:categories) ORDER BY dateTaken DESC, mediaId DESC")
    suspend fun targetsIn(categories: List<Category>): List<TargetRow>

    /** "Select all" for the Photos-only view; same rows as [pagingExcluding]. */
    @Query(
        """SELECT mediaId, uri, size FROM images WHERE effectiveCategory IS NULL OR effectiveCategory NOT IN (:excluded)
           ORDER BY dateTaken DESC, mediaId DESC""",
    )
    suspend fun targetsExcluding(excluded: List<Category>): List<TargetRow>

    @Query("SELECT COUNT(*) FROM images")
    fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM images")
    suspend fun count(): Int

    @Query(
        """SELECT effectiveCategory AS category, COUNT(*) AS count, COALESCE(SUM(size), 0) AS totalSize,
           (SELECT i2.uri FROM images i2 WHERE i2.effectiveCategory = images.effectiveCategory
              ORDER BY i2.dateTaken DESC LIMIT 1) AS coverUri
           FROM images WHERE effectiveCategory IS NOT NULL GROUP BY effectiveCategory""",
    )
    fun categoryStats(): Flow<List<CategoryStatRow>>

    // ---- Review / clean-up ----

    @Query(
        """SELECT * FROM images WHERE effectiveCategory = :category AND dateTaken < :before
           ORDER BY dateTaken DESC, mediaId DESC""",
    )
    fun categoryBefore(category: Category, before: Long): Flow<List<ImageRecord>>

    @Query(
        """SELECT COUNT(*) AS count, SUM(size) AS totalSize FROM images
           WHERE effectiveCategory = :category AND dateTaken < :before""",
    )
    fun summaryBefore(category: Category, before: Long): Flow<SummaryRow>

    @Query(
        """SELECT * FROM images WHERE effectiveCategory = 'PHOTO' AND blurScore IS NOT NULL AND blurScore < :threshold
           ORDER BY dateTaken DESC""",
    )
    fun blurry(threshold: Double): Flow<List<ImageRecord>>

    @Query(
        """SELECT COUNT(*) AS count, SUM(size) AS totalSize FROM images
           WHERE effectiveCategory = 'PHOTO' AND blurScore IS NOT NULL AND blurScore < :threshold""",
    )
    fun blurrySummary(threshold: Double): Flow<SummaryRow>

    @Query("SELECT mediaId, contentHash FROM images WHERE contentHash IS NOT NULL")
    suspend fun hashes(): List<HashRow>

    @Query("SELECT mediaId FROM images WHERE contentHash IS NOT NULL ORDER BY mediaId DESC LIMIT 1")
    fun latestHashedIdFlow(): Flow<Long?>

    @Query(
        """SELECT mediaId FROM images WHERE userOverride IS NULL AND confidence >= :min AND confidence <= :max
           AND mediaId NOT IN (SELECT mediaId FROM review_answers)""",
    )
    suspend fun unsureCandidates(min: Float, max: Float): List<Long>

    @Query(
        """SELECT * FROM images WHERE userOverride IS NULL AND effectiveCategory IN (:categories)
           AND mediaId NOT IN (SELECT mediaId FROM review_answers) ORDER BY dateTaken ASC LIMIT :limit""",
    )
    suspend fun swipeQueue(categories: List<Category>, limit: Int): List<ImageRecord>
}

@Dao
interface CorrectionDao {
    @Insert
    suspend fun insertAll(corrections: List<CorrectionEntity>)

    @Query("SELECT * FROM corrections WHERE bucket = :bucket")
    suspend fun forBucket(bucket: String): List<CorrectionEntity>

    @Query("SELECT COUNT(*) FROM corrections")
    fun countFlow(): Flow<Int>
}

@Dao
interface FolderPriorDao {
    @Query("SELECT * FROM folder_priors WHERE active = 1 ORDER BY bucket")
    fun activeFlow(): Flow<List<FolderPriorEntity>>

    @Query("SELECT * FROM folder_priors WHERE active = 1")
    suspend fun active(): List<FolderPriorEntity>

    @Query("SELECT * FROM folder_priors WHERE bucket = :bucket")
    suspend fun get(bucket: String): FolderPriorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prior: FolderPriorEntity)

    @Query("UPDATE folder_priors SET active = 0, dismissedAtCount = sampleCount WHERE bucket = :bucket")
    suspend fun dismiss(bucket: String)

    @Query("UPDATE folder_priors SET active = 0, dismissedAtCount = sampleCount WHERE active = 1")
    suspend fun dismissAll()
}

@Dao
interface ScanStateDao {
    @Query("SELECT * FROM scan_state WHERE `key` = :key")
    suspend fun get(key: String): ScanStateEntity?

    @Query("SELECT * FROM scan_state WHERE `key` = :key")
    fun observe(key: String): Flow<ScanStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: ScanStateEntity)
}

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun answer(answer: ReviewAnswerEntity)

    @Query("SELECT mediaId FROM review_answers WHERE answeredDay = :day")
    fun answeredOn(day: Long): Flow<List<Long>>
}
