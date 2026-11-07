package com.sortsnap.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.sortsnap.R
import com.sortsnap.classifier.Category
import com.sortsnap.data.db.ImageDao
import com.sortsnap.domain.Formatting
import com.sortsnap.ui.components.GridSkeleton
import com.sortsnap.ui.components.pluralLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CategoryTile(val category: Category, val count: Int, val bytes: Long, val coverUri: String?)

@HiltViewModel
class CategoriesViewModel @Inject constructor(images: ImageDao) : ViewModel() {
    /** Live from Room: counts grow while the scanner runs. Null until the first emission. */
    val tiles: StateFlow<List<CategoryTile>?> = images.categoryStats().map { rows ->
        val byCat = rows.associateBy { it.category }
        Category.entries.map { c ->
            val r = byCat[c]
            CategoryTile(c, r?.count ?: 0, r?.totalSize ?: 0, r?.coverUri)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onOpenCategory: (Category) -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.categories_title)) }) }) { padding ->
        val list = tiles
        if (list == null) {
            GridSkeleton(columns = 2, modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize().testTag("category_tiles"),
        ) {
            items(list, key = { it.category.name }) { tile -> Tile(tile, onClick = { onOpenCategory(tile.category) }) }
        }
    }
}

@Composable
private fun Tile(tile: CategoryTile, onClick: () -> Unit) {
    val name = stringResource(tile.category.pluralLabel())
    Card(
        modifier = Modifier.padding(6.dp).fillMaxWidth().clickable(role = Role.Button, onClickLabel = name, onClick = onClick)
            .testTag("tile_${tile.category.name}"),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (tile.coverUri != null) {
                AsyncImage(model = tile.coverUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.category_count, tile.count, Formatting.bytes(tile.bytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
