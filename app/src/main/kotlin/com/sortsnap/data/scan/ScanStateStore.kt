package com.sortsnap.data.scan

import com.sortsnap.data.db.ScanStateDao
import com.sortsnap.data.db.ScanStateEntity
import com.sortsnap.domain.ScanStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the resumable scan cursor and debug statistics. */
@Singleton
class ScanStateStore @Inject constructor(private val dao: ScanStateDao) {

    suspend fun cursor(): Long = dao.get(CURSOR)?.longValue ?: 0L
    suspend fun setCursor(value: Long) = dao.put(ScanStateEntity(CURSOR, value))

    suspend fun stats(): ScanStats = ScanStats(
        processed = long(PROCESSED), elapsedMillis = long(ELAPSED), mlRuns = long(ML_RUNS), mlMillis = long(ML_MS),
    )

    suspend fun saveStats(stats: ScanStats) {
        dao.put(ScanStateEntity(PROCESSED, stats.processed))
        dao.put(ScanStateEntity(ELAPSED, stats.elapsedMillis))
        dao.put(ScanStateEntity(ML_RUNS, stats.mlRuns))
        dao.put(ScanStateEntity(ML_MS, stats.mlMillis))
    }

    val statsFlow: Flow<ScanStats> = combine(
        dao.observe(PROCESSED), dao.observe(ELAPSED), dao.observe(ML_RUNS), dao.observe(ML_MS),
    ) { p, e, r, m ->
        ScanStats(p?.longValue ?: 0, e?.longValue ?: 0, r?.longValue ?: 0, m?.longValue ?: 0)
    }

    val lastFinishedAt: Flow<Long> = dao.observe(FINISHED_AT).map { it?.longValue ?: 0L }
    suspend fun markFinished(now: Long) = dao.put(ScanStateEntity(FINISHED_AT, now))

    private suspend fun long(key: String) = dao.get(key)?.longValue ?: 0L

    private companion object {
        const val CURSOR = "cursor"
        const val PROCESSED = "stats_processed"
        const val ELAPSED = "stats_elapsed_ms"
        const val ML_RUNS = "stats_ml_runs"
        const val ML_MS = "stats_ml_ms"
        const val FINISHED_AT = "finished_at"
    }
}
