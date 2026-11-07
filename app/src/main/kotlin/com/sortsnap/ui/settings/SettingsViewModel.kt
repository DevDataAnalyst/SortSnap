package com.sortsnap.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortsnap.classifier.Category
import com.sortsnap.classifier.ClassifierConfig
import com.sortsnap.data.db.FolderPriorDao
import com.sortsnap.data.db.FolderPriorEntity
import com.sortsnap.data.db.ImageDao
import com.sortsnap.data.scan.ScanScheduler
import com.sortsnap.data.scan.ScanStateStore
import com.sortsnap.data.settings.AppSettings
import com.sortsnap.data.settings.SettingsRepository
import com.sortsnap.data.settings.ThemeMode
import com.sortsnap.domain.ScanStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebugStats(val stats: ScanStats, val indexed: Int, val queue: Long, val version: Int = ClassifierConfig.VERSION)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val priors: FolderPriorDao,
    private val scheduler: ScanScheduler,
    images: ImageDao,
    scanState: ScanStateStore,
) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val learnedRules: StateFlow<List<FolderPriorEntity>> = priors.activeFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val debug: StateFlow<DebugStats?> = combine(
        scanState.statsFlow, images.countFlow(), images.pendingCountFlow(ClassifierConfig.VERSION),
    ) { stats, indexed, queue -> DebugStats(stats, indexed, queue) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setHideClutter(v: Boolean) = viewModelScope.launch { settings.setHideClutter(v) }
    fun setUnsure(c: Category) = viewModelScope.launch {
        settings.setUnsureCategory(c)
        scheduler.rescanLibrary() // unsure images need re-deciding with the new fallback
    }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { settings.setThemeMode(t) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { settings.setDynamicColor(v) }
    fun rescan() = viewModelScope.launch { scheduler.rescanLibrary() }
    fun scanNowAnyway() = scheduler.scanNowAnyway()
    fun deleteRule(bucket: String) = viewModelScope.launch { priors.dismiss(bucket) }
    fun clearRules() = viewModelScope.launch { priors.dismissAll() }
}
