package com.sortsnap.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sortsnap.classifier.Category
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val hideClutter: Boolean = true,
    /** Category given to images below the confidence threshold. OTHER (default) or PHOTO. */
    val unsureCategory: Category = Category.OTHER,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val gridColumns: Int = 4,
    val onboardingDone: Boolean = false,
    /** ClassifierConfig.VERSION the index was last fully classified with. */
    val indexedClassifierVersion: Int = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val HIDE_CLUTTER = booleanPreferencesKey("hide_clutter")
        val UNSURE = stringPreferencesKey("unsure_category")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val COLUMNS = intPreferencesKey("grid_columns")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val INDEXED_VERSION = intPreferencesKey("indexed_classifier_version")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            hideClutter = p[Keys.HIDE_CLUTTER] ?: true,
            unsureCategory = Category.fromNameOrNull(p[Keys.UNSURE]) ?: Category.OTHER,
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC] ?: true,
            gridColumns = (p[Keys.COLUMNS] ?: 4).coerceIn(3, 5),
            onboardingDone = p[Keys.ONBOARDING] ?: false,
            indexedClassifierVersion = p[Keys.INDEXED_VERSION] ?: 0,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setHideClutter(value: Boolean) = context.dataStore.edit { it[Keys.HIDE_CLUTTER] = value }
    suspend fun setUnsureCategory(value: Category) = context.dataStore.edit { it[Keys.UNSURE] = value.name }
    suspend fun setThemeMode(value: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = value.name }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC] = value }
    suspend fun setGridColumns(value: Int) = context.dataStore.edit { it[Keys.COLUMNS] = value.coerceIn(3, 5) }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.ONBOARDING] = true }
    suspend fun setIndexedClassifierVersion(value: Int) = context.dataStore.edit { it[Keys.INDEXED_VERSION] = value }
}
