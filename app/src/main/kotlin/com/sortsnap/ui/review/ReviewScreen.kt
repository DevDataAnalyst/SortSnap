package com.sortsnap.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sortsnap.R
import com.sortsnap.data.db.ImageRecord
import com.sortsnap.domain.CleanupSection
import com.sortsnap.domain.Formatting
import com.sortsnap.domain.SectionSummary
import com.sortsnap.ui.components.CategoryPickerDialog
import com.sortsnap.ui.components.singleLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onOpenSection: (CleanupSection) -> Unit,
    onOpenSwipe: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    ReviewContent(
        sections = sections,
        cards = cards,
        onOpenSection = onOpenSection,
        onOpenSwipe = onOpenSwipe,
        onConfirm = viewModel::confirm,
        onCorrect = viewModel::correct,
        onSkip = viewModel::skip,
    )
}

/** Stateless body of [ReviewScreen]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewContent(
    sections: List<SectionSummary>?,
    cards: List<ImageRecord>?,
    onOpenSection: (CleanupSection) -> Unit,
    onOpenSwipe: () -> Unit,
    onConfirm: (Long) -> Unit,
    onCorrect: (Long, com.sortsnap.classifier.Category) -> Unit,
    onSkip: (Long) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.review_title)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().testTag("review_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "confirm") {
                QuickConfirmCard(
                    cards = cards,
                    onYes = onConfirm,
                    onCorrect = onCorrect,
                    onSkip = onSkip,
                )
            }
            item(key = "cleanup_title") {
                Text(stringResource(R.string.review_cleanup_title), style = MaterialTheme.typography.titleMedium)
            }
            item(key = "swipe") {
                Card(onClick = onOpenSwipe, modifier = Modifier.fillMaxWidth().testTag("swipe_entry")) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.swipe_mode)) },
                        supportingContent = { Text(stringResource(R.string.swipe_mode_sub)) },
                    )
                }
            }
            val list = sections
            if (list == null) {
                item { LoadingRow() }
            } else {
                items(list, key = { it.section.name }) { s -> SectionCard(s, onClick = { onOpenSection(s.section) }) }
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Card(Modifier.fillMaxWidth().height(72.dp)) {}
}

@Composable
fun SectionCard(summary: SectionSummary, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("section_${summary.section.name}"), enabled = summary.count > 0) {
        ListItem(
            headlineContent = { Text(stringResource(summary.section.titleRes())) },
            supportingContent = {
                val sub = summary.section.subtitleRes()?.let { stringResource(it) + " · " } ?: ""
                Text(sub + stringResource(R.string.section_reclaimable, summary.count, Formatting.bytes(summary.bytes)))
            },
        )
    }
}

fun CleanupSection.titleRes(): Int = when (this) {
    CleanupSection.OLD_SCREENSHOTS -> R.string.section_old_screenshots
    CleanupSection.MEMES -> R.string.section_memes
    CleanupSection.FORWARDS -> R.string.section_forwards
    CleanupSection.NEAR_DUPLICATES -> R.string.section_near_duplicates
    CleanupSection.BLURRY -> R.string.section_blurry
}

fun CleanupSection.subtitleRes(): Int? = when (this) {
    CleanupSection.OLD_SCREENSHOTS -> R.string.section_old_screenshots_sub
    CleanupSection.NEAR_DUPLICATES -> R.string.section_near_duplicates_sub
    else -> null
}

@Composable
fun QuickConfirmCard(
    cards: List<ImageRecord>?,
    onYes: (Long) -> Unit,
    onCorrect: (Long, com.sortsnap.classifier.Category) -> Unit,
    onSkip: (Long) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth().testTag("quick_confirm")) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.review_confirm_title), style = MaterialTheme.typography.titleMedium)
            val card = cards?.firstOrNull()
            if (cards == null) {
                Spacer(Modifier.height(120.dp))
                return@Column
            }
            if (card == null) {
                Row(Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = null)
                    Text(stringResource(R.string.review_confirm_done), modifier = Modifier.padding(start = 8.dp))
                }
                return@Column
            }
            Text(stringResource(R.string.review_confirm_progress, cards.size), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = card.uri,
                contentDescription = card.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(8.dp))
            val category = card.effectiveCategory ?: com.sortsnap.classifier.Category.OTHER
            Text(stringResource(R.string.review_confirm_question, stringResource(category.singleLabel()), Formatting.percent(card.confidence)))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onYes(card.mediaId) }, modifier = Modifier.testTag("confirm_yes")) { Text(stringResource(R.string.review_yes)) }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.testTag("confirm_no")) { Text(stringResource(R.string.review_no)) }
                TextButton(onClick = { onSkip(card.mediaId) }) { Text(stringResource(R.string.review_skip)) }
            }
            if (picking) {
                CategoryPickerDialog(
                    current = category,
                    onPick = { picking = false; onCorrect(card.mediaId, it) },
                    onDismiss = { picking = false },
                )
            }
        }
    }
}
