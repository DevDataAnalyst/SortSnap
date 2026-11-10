package com.sortsnap.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortsnap.BuildConfig
import com.sortsnap.R
import com.sortsnap.classifier.Category
import com.sortsnap.data.db.FolderPriorEntity
import com.sortsnap.data.settings.AppSettings
import com.sortsnap.data.settings.ThemeMode
import com.sortsnap.ui.components.pluralLabel
import kotlinx.coroutines.launch

/** Everything the Settings screen can ask for; the screen itself holds no state. */
class SettingsActions(
    val setHideClutter: (Boolean) -> Unit = {},
    val setUnsure: (Category) -> Unit = {},
    val rescan: () -> Unit = {},
    val scanNowAnyway: () -> Unit = {},
    /** Re-sorts unsure pictures; calls back with how many were queued. */
    val resortUnsure: ((Int) -> Unit) -> Unit = {},
    val deleteRule: (String) -> Unit = {},
    val clearRules: () -> Unit = {},
    val setTheme: (ThemeMode) -> Unit = {},
    val setDynamic: (Boolean) -> Unit = {},
    val openAbout: () -> Unit = {},
    val openDebug: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    onOpenAbout: () -> Unit,
    onOpenDebug: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val rules by viewModel.learnedRules.collectAsStateWithLifecycle()
    val personalExamples by viewModel.personalExamples.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onOpenAbout, onOpenDebug) {
        SettingsActions(
            setHideClutter = { viewModel.setHideClutter(it) },
            setUnsure = { viewModel.setUnsure(it) },
            rescan = { viewModel.rescan() },
            scanNowAnyway = { viewModel.scanNowAnyway() },
            resortUnsure = { viewModel.resortUnsure(it) },
            deleteRule = { viewModel.deleteRule(it) },
            clearRules = { viewModel.clearRules() },
            setTheme = { viewModel.setTheme(it) },
            setDynamic = { viewModel.setDynamic(it) },
            openAbout = onOpenAbout,
            openDebug = onOpenDebug,
        )
    }
    SettingsContent(s, rules, personalExamples, actions, showDebug = BuildConfig.DEBUG)
}

/** Stateless body of [SettingsScreen]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    s: AppSettings,
    rules: List<FolderPriorEntity>,
    personalExamples: Int,
    actions: SettingsActions,
    showDebug: Boolean,
    scrollState: ScrollState = rememberScrollState(),
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun toast(res: Int) = scope.launch { snackbar.showSnackbar(context.getString(res)) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(scrollState).testTag("settings")) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_hide_clutter)) },
                supportingContent = { Text(stringResource(R.string.settings_hide_clutter_sub)) },
                trailingContent = {
                    Switch(checked = s.hideClutter, onCheckedChange = actions.setHideClutter, modifier = Modifier.testTag("switch_hide_clutter"))
                },
            )
            SectionTitle(stringResource(R.string.settings_unsure))
            listOf(Category.OTHER, Category.PHOTO).forEach { c ->
                RadioRow(stringResource(c.pluralLabel()), selected = s.unsureCategory == c) { actions.setUnsure(c) }
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_rescan)) },
                supportingContent = { Text(stringResource(R.string.settings_rescan_sub)) },
                modifier = Modifier.selectable(false, onClick = { actions.rescan(); toast(R.string.rescan_started) }, role = Role.Button),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_scan_now)) },
                supportingContent = { Text(stringResource(R.string.settings_scan_now_sub)) },
                modifier = Modifier.selectable(false, onClick = { actions.scanNowAnyway(); toast(R.string.rescan_started) }, role = Role.Button),
            )
            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_personal_learning))
            ListItem(
                headlineContent = {
                    Text(
                        if (personalExamples == 0) {
                            stringResource(R.string.settings_personal_learning_empty)
                        } else {
                            pluralStringResource(R.plurals.settings_personal_learning_count, personalExamples, personalExamples)
                        },
                    )
                },
                supportingContent = { Text(stringResource(R.string.settings_resort_unsure_sub)) },
                modifier = Modifier.selectable(
                    false,
                    onClick = {
                        actions.resortUnsure { n ->
                            if (n == 0) toast(R.string.resort_nothing) else toast(R.string.resort_started)
                        }
                    },
                    role = Role.Button,
                ),
            )
            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_learned_rules))
            if (rules.isEmpty()) {
                Text(
                    stringResource(R.string.settings_learned_rules_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                rules.forEach { rule ->
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_learned_rule, rule.bucket, stringResource(rule.category.pluralLabel()))) },
                        supportingContent = { Text(stringResource(R.string.settings_learned_rule_sub, rule.sampleCount)) },
                        trailingContent = {
                            IconButton(onClick = { actions.deleteRule(rule.bucket) }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.settings_delete_rule))
                            }
                        },
                        modifier = Modifier.testTag("rule_${rule.bucket}"),
                    )
                }
                TextButton(onClick = { actions.clearRules(); toast(R.string.rules_cleared) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.settings_clear_rules))
                }
            }
            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_theme))
            ThemeMode.entries.forEach { t ->
                val label = when (t) {
                    ThemeMode.SYSTEM -> R.string.theme_system
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                }
                RadioRow(stringResource(label), selected = s.themeMode == t) { actions.setTheme(t) }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                trailingContent = { Switch(checked = s.dynamicColor, onCheckedChange = actions.setDynamic) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                modifier = Modifier.selectable(false, onClick = actions.openAbout, role = Role.Button),
            )
            if (showDebug) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_debug)) },
                    modifier = Modifier.selectable(false, onClick = actions.openDebug, role = Role.Button),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    )
}
