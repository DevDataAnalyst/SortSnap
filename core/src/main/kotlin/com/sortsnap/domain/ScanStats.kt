package com.sortsnap.domain

/** Running scanner statistics for the debug screen. */
data class ScanStats(
    val processed: Long = 0,
    val elapsedMillis: Long = 0,
    val mlRuns: Long = 0,
    val mlMillis: Long = 0,
    val queueLength: Long = 0,
) {
    val imagesPerSecond: Double get() = if (elapsedMillis <= 0) 0.0 else processed * 1000.0 / elapsedMillis
    val averageMlMillis: Double get() = if (mlRuns == 0L) 0.0 else mlMillis.toDouble() / mlRuns

    fun plusBatch(count: Int, millis: Long, mlCount: Int, mlTotalMillis: Long, remaining: Long) = copy(
        processed = processed + count,
        elapsedMillis = elapsedMillis + millis,
        mlRuns = mlRuns + mlCount,
        mlMillis = mlMillis + mlTotalMillis,
        queueLength = remaining,
    )
}
