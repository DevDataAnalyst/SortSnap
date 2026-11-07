package com.sortsnap.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.sortsnap.R
import com.sortsnap.domain.DateBucket
import com.sortsnap.domain.GalleryItem
import java.text.DateFormat
import java.time.Month
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** Grid rows: date headers interleaved with images (via PagingData.insertSeparators). */
sealed interface GalleryEntry {
    val key: String

    data class Header(val bucket: DateBucket) : GalleryEntry {
        override val key: String get() = "h_$bucket"
    }

    data class Image(val item: GalleryItem) : GalleryEntry {
        override val key: String get() = "i_${item.id}"
    }
}

@Composable
fun DateBucket.label(): String = when (this) {
    DateBucket.Today -> stringResource(R.string.date_today)
    DateBucket.Yesterday -> stringResource(R.string.date_yesterday)
    DateBucket.ThisWeek -> stringResource(R.string.date_this_week)
    is DateBucket.Month -> Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) + " " + year
}

/**
 * Paged thumbnail grid shared by the Gallery, smart albums and filtered views.
 * Pinch with two fingers to change the column count (3/4/5); long-press to start multi-select.
 */
@Composable
fun PagedGalleryGrid(
    entries: LazyPagingItems<GalleryEntry>,
    columns: Int,
    selected: Set<Long>,
    onOpen: (imageIndex: Int, item: GalleryItem) -> Unit,
    onToggleSelect: (GalleryItem) -> Unit,
    onColumnsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    emptyContent: @Composable () -> Unit = {},
) {
    val refresh = entries.loadState.refresh
    when {
        refresh is LoadState.Loading && entries.itemCount == 0 -> { GridSkeleton(columns, modifier); return }
        refresh is LoadState.Error && entries.itemCount == 0 -> {
            ErrorState(refresh.error.message.orEmpty(), onRetry = entries::retry, modifier = modifier); return
        }
        refresh is LoadState.NotLoading && entries.itemCount == 0 -> { emptyContent(); return }
    }
    val selectionMode = selected.isNotEmpty()
    val thumbPx = thumbnailPx(columns)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier.fillMaxSize().pinchToChangeColumns(columns, onColumnsChange).testTag("gallery_grid"),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(
            count = entries.itemCount,
            key = entries.itemKey { it.key },
            contentType = entries.itemContentType { if (it is GalleryEntry.Header) 0 else 1 },
            span = { index ->
                val e = entries.peek(index)
                GridItemSpan(if (e is GalleryEntry.Header) maxLineSpan else 1)
            },
        ) { index ->
            when (val entry = entries[index]) {
                is GalleryEntry.Header -> Text(
                    entry.bucket.label(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 16.dp, bottom = 8.dp),
                )
                is GalleryEntry.Image -> Thumbnail(
                    item = entry.item,
                    sizePx = thumbPx,
                    isSelected = entry.item.id in selected,
                    onClick = {
                        if (selectionMode) {
                            onToggleSelect(entry.item)
                        } else {
                            val headersBefore = entries.itemSnapshotList.items.take(index).count { it is GalleryEntry.Header }
                            onOpen(index - headersBefore, entry.item)
                        }
                    },
                    onLongClick = { onToggleSelect(entry.item) },
                )
                null -> Box(Modifier.aspectRatio(1f).padding(1.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
    }
}

/** Non-paged variant for clean-up sections (lists are already bounded). */
@Composable
fun StaticGalleryGrid(
    groups: List<List<GalleryItem>>,
    columns: Int,
    selected: Set<Long>,
    onToggle: (GalleryItem) -> Unit,
    modifier: Modifier = Modifier,
    groupTitle: (@Composable (Int) -> String)? = null,
) {
    val thumbPx = thumbnailPx(columns)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize().testTag("section_grid"),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        groups.forEachIndexed { g, group ->
            if (groupTitle != null) {
                item(key = "g_$g", span = { GridItemSpan(maxLineSpan) }) {
                    Text(groupTitle(g), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
                }
            }
            items(group.size, key = { group[it].id }) { i ->
                val item = group[i]
                Thumbnail(item, thumbPx, isSelected = item.id in selected, onClick = { onToggle(item) }, onLongClick = { onToggle(item) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumbnail(
    item: GalleryItem,
    sizePx: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val request = remember(item.uri, sizePx) {
        ImageRequest.Builder(context).data(item.uri).size(sizePx).memoryCacheKey("t${sizePx}_${item.id}").build()
    }
    val date = remember(item.dateTaken) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.dateTaken)) }
    val description = stringResource(R.string.cd_thumbnail, item.displayName.ifBlank { stringResource(R.string.cd_image, item.id.toString()) }, date)
    val selectedLabel = stringResource(R.string.cd_selected)
    Box(
        modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            )
            .semantics {
                contentDescription = if (isSelected) "$description, $selectedLabel" else description
                this.selected = isSelected
            }
            .testTag("thumb_${item.id}"),
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(if (isSelected) 6.dp else 0.dp),
        )
        if (isSelected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
    }
}

@Composable
private fun thumbnailPx(columns: Int): Int {
    val density = LocalDensity.current
    val width = LocalWindowInfo.current.containerSize.width
    return remember(columns, width) {
        val px = if (width > 0) width / columns else with(density) { 120.dp.roundToPx() }
        // Round up to a bucket so Coil's memory cache is reused across small layout changes.
        ((px + 63) / 64) * 64
    }
}

/** Two-finger pinch: spread = fewer, bigger columns; pinch = more columns. Single-finger scrolls normally. */
fun Modifier.pinchToChangeColumns(columns: Int, onChange: (Int) -> Unit): Modifier = pointerInput(columns) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                if (zoom > 1.3f && columns > 3) { onChange(columns - 1); zoom = 1f; break }
                if (zoom < 0.75f && columns < 5) { onChange(columns + 1); zoom = 1f; break }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
