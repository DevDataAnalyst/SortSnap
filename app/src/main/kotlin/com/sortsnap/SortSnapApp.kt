package com.sortsnap

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sortsnap.data.media.MediaStoreRepository
import com.sortsnap.data.scan.ScanScheduler
import com.sortsnap.di.AppScope
import com.sortsnap.ui.permission.PhotoAccess
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SortSnapApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: ScanScheduler
    @Inject lateinit var media: MediaStoreRepository
    @Inject @AppScope lateinit var appScope: CoroutineScope

    private var observerJob: Job? = null

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        onPhotoAccessMaybeChanged()
    }

    /** Called at start and whenever the permission state may have changed. Idempotent. */
    @OptIn(FlowPreview::class)
    fun onPhotoAccessMaybeChanged() {
        if (PhotoAccess.current(this) == PhotoAccess.NONE) return
        appScope.launch { scheduler.onAppStart() }
        if (observerJob?.isActive == true) return
        observerJob = appScope.launch {
            // Incremental scan whenever images are added or removed; debounced for burst imports.
            media.changes().debounce(OBSERVER_DEBOUNCE_MS).collect { scheduler.onMediaChanged() }
        }
    }

    private companion object {
        const val OBSERVER_DEBOUNCE_MS = 3_000L
    }
}
