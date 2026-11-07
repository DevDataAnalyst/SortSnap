package com.sortsnap.data.scan

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sortsnap.classifier.ClassifierConfig
import com.sortsnap.data.db.ImageDao
import com.sortsnap.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ScanStatus(val running: Boolean, val phase: String?, val done: Long, val total: Long, val deferred: Boolean) {
    val fraction: Float? get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null

    companion object {
        val IDLE = ScanStatus(running = false, phase = null, done = 0, total = 0, deferred = false)
    }
}

@Singleton
class ScanScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val images: ImageDao,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** App start / permission granted: full scan if never indexed or the classifier changed. */
    suspend fun onAppStart() {
        val indexedVersion = settings.current().indexedClassifierVersion
        val needsFull = indexedVersion != ClassifierConfig.VERSION || images.count() == 0
        enqueue(full = needsFull, force = false, policy = ExistingWorkPolicy.KEEP)
    }

    /** ContentObserver fired: incremental sync after whatever is running. */
    fun onMediaChanged() = enqueue(full = false, force = false, policy = ExistingWorkPolicy.APPEND_OR_REPLACE)

    /** Settings > Rescan library: requeue everything not manually corrected. */
    suspend fun rescanLibrary() {
        images.requeueAll()
        enqueue(full = true, force = false, policy = ExistingWorkPolicy.REPLACE)
    }

    /** Settings > Scan now anyway: ignores the battery rule. */
    fun scanNowAnyway() = enqueue(full = true, force = true, policy = ExistingWorkPolicy.REPLACE)

    /** Battery too low for the initial full scan: wait for the charger. */
    fun enqueueWhenCharging() {
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
            .setInputData(workDataOf(ScanWorker.KEY_FULL to true, ScanWorker.KEY_FORCE to true))
            .addTag(TAG)
            .addTag(CHARGING_TAG)
            .build()
        workManager.enqueueUniqueWork(CHARGING_WORK, ExistingWorkPolicy.KEEP, request)
    }

    private fun enqueue(full: Boolean, force: Boolean, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(!force).build()
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(ScanWorker.KEY_FULL to full, ScanWorker.KEY_FORCE to force))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    val status: Flow<ScanStatus> = workManager.getWorkInfosByTagFlow(TAG).map { infos ->
        val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
        if (running == null) {
            val deferred = infos.any { it.state == WorkInfo.State.ENQUEUED && it.tags.contains(CHARGING_TAG) }
            ScanStatus.IDLE.copy(deferred = deferred)
        } else {
            val p = running.progress
            ScanStatus(
                running = true,
                phase = p.getString(ScanWorker.KEY_PHASE),
                done = p.getLong(ScanWorker.KEY_DONE, 0),
                total = p.getLong(ScanWorker.KEY_TOTAL, 0),
                deferred = false,
            )
        }
    }

    companion object {
        const val WORK_NAME = "sortsnap_scan"
        const val CHARGING_WORK = "sortsnap_scan_when_charging"
        const val TAG = "sortsnap_scan_tag"
        const val CHARGING_TAG = "sortsnap_scan_waiting_for_charger"
    }
}
