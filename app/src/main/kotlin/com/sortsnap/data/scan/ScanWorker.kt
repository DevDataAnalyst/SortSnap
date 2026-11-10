package com.sortsnap.data.scan

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sortsnap.classifier.Category
import com.sortsnap.classifier.ClassifierConfig
import com.sortsnap.classifier.LabelledExample
import com.sortsnap.classifier.PersonalModel
import com.sortsnap.classifier.SignalCodec
import com.sortsnap.data.db.FolderPriorDao
import com.sortsnap.data.db.ImageDao
import com.sortsnap.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Syncs MediaStore with Room, then classifies every queued row in batches of [BATCH_SIZE].
 *
 * - Resumable: the last processed mediaId is persisted after every batch, so a killed worker
 *   continues where it stopped.
 * - Rows with a userOverride are never selected or updated (guarded in SQL as well); they are read
 *   only as training examples for the [PersonalModel].
 * - The initial full scan only runs while charging or above 30% battery unless forced.
 */
@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: IndexSync,
    private val images: ImageDao,
    private val priors: FolderPriorDao,
    private val classifierService: ImageClassifierService,
    private val state: ScanStateStore,
    private val settings: SettingsRepository,
    private val scheduler: ScanScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val full = inputData.getBoolean(KEY_FULL, false)
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (full && !force && !batteryAllowsFullScan()) {
            scheduler.enqueueWhenCharging()
            return Result.success(workDataOf(KEY_DEFERRED to true))
        }

        setProgress(workDataOf(KEY_PHASE to PHASE_SYNC, KEY_DONE to 0, KEY_TOTAL to 0))
        try {
            sync.sync()
        } catch (e: SecurityException) {
            return Result.failure() // permission revoked; the UI shows onboarding again
        } catch (e: IllegalStateException) {
            return Result.retry() // MediaStore unavailable (e.g. storage not mounted yet)
        }

        val version = ClassifierConfig.VERSION
        val appSettings = settings.current()
        val fallback = appSettings.unsureCategory
        val folderPriors: Map<String, Category> = priors.active().associate { it.bucket to it.category }
        val personal = loadPersonalModel()
        val total = images.pendingCount(version)
        var done = 0L
        var cursor = state.cursor()
        var stats = state.stats()

        try {
            while (!isStopped) {
                val batch = images.pendingBatch(version, cursor, BATCH_SIZE)
                if (batch.isEmpty()) {
                    if (cursor == 0L) break
                    cursor = 0L // wrap around once to pick up rows inserted below the cursor
                    state.setCursor(0L)
                    continue
                }
                val started = System.currentTimeMillis()
                val results = coroutineScope {
                    batch.map { record ->
                        async {
                            runCatching { classifierService.classify(record, folderPriors[record.bucket], fallback, personal) }.getOrNull()
                        }
                    }.awaitAll()
                }
                val now = System.currentTimeMillis()
                batch.zip(results).forEach { (record, classified) ->
                    if (classified == null) {
                        // Unreadable file: mark as scanned OTHER so it is not retried forever.
                        images.updateClassification(record.mediaId, Category.OTHER, 0f, "[]", null, null, now, version, false)
                    } else {
                        images.updateClassification(
                            mediaId = record.mediaId,
                            category = classified.result.category,
                            confidence = classified.result.confidence,
                            signalsJson = classifierService.encode(classified.result),
                            contentHash = classified.stats?.dHash,
                            blurScore = classified.stats?.blurScore,
                            scannedAt = now,
                            version = version,
                            mlProcessed = classified.result.usedMl,
                        )
                    }
                }
                cursor = batch.last().mediaId
                state.setCursor(cursor)
                done += batch.size
                val mlTimes = results.mapNotNull { it?.mlMillis }
                stats = stats.plusBatch(batch.size, now - started, mlTimes.size, mlTimes.sum(), (total - done).coerceAtLeast(0))
                state.saveStats(stats)
                setProgress(workDataOf(KEY_PHASE to PHASE_CLASSIFY, KEY_DONE to done, KEY_TOTAL to total))
            }
        } finally {
            classifierService.releaseModels()
        }

        if (isStopped) return Result.retry()
        state.setCursor(0L)
        state.markFinished(System.currentTimeMillis())
        settings.setIndexedClassifierVersion(version)
        return Result.success()
    }

    /** What the user taught by moving/confirming images. Built once per run, off the main thread. */
    private suspend fun loadPersonalModel(): PersonalModel? = runCatching {
        val config = ClassifierConfig.DEFAULT
        val rows = images.labelledExamples(config.personalMaxExamples)
        PersonalModel.build(rows.map { LabelledExample(SignalCodec.decode(it.signalsJson), it.category) }, config)
            .takeUnless { it.isEmpty }
    }.getOrNull()

    private fun batteryAllowsFullScan(): Boolean {
        val status = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return true
        val plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level < 0 || scale <= 0) 100 else level * 100 / scale
        return plugged || percent > MIN_BATTERY_PERCENT
    }

    companion object {
        const val BATCH_SIZE = 50
        const val MIN_BATTERY_PERCENT = 30
        const val KEY_FULL = "full"
        const val KEY_FORCE = "force"
        const val KEY_DEFERRED = "deferred"
        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val PHASE_SYNC = "sync"
        const val PHASE_CLASSIFY = "classify"
    }
}
