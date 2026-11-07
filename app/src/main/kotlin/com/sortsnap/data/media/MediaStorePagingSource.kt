package com.sortsnap.data.media

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * Offset-based paging straight from MediaStore, so the gallery shows instantly, before any indexing.
 *
 * Keys: for Refresh/Append the key is the start offset; for Prepend the key is the exclusive end
 * offset, so prepends never overlap whatever load size Paging chooses.
 */
class MediaStorePagingSource(
    private val loadPage: suspend (offset: Int, limit: Int) -> List<MediaStoreImage>,
    /** When given, pages report itemsBefore/itemsAfter so placeholders and jumping work (viewer). */
    private val count: (suspend () -> Int)? = null,
) : PagingSource<Int, MediaStoreImage>() {

    private var total: Int? = null

    override val jumpingSupported: Boolean get() = count != null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaStoreImage> = try {
        val (start, limit) = when (params) {
            is LoadParams.Prepend -> {
                val end = params.key
                val s = maxOf(0, end - params.loadSize)
                s to (end - s)
            }
            else -> (params.key ?: 0).coerceAtLeast(0) to params.loadSize
        }
        val items = if (limit > 0) loadPage(start, limit) else emptyList()
        val totalCount = count?.let { c -> total ?: c().also { total = it } }
        if (totalCount != null) {
            LoadResult.Page(
                data = items,
                prevKey = if (start == 0) null else start,
                nextKey = if (start + items.size >= totalCount || items.isEmpty()) null else start + items.size,
                itemsBefore = start,
                itemsAfter = (totalCount - start - items.size).coerceAtLeast(0),
            )
        } else LoadResult.Page(
            data = items,
            prevKey = if (start == 0) null else start,
            nextKey = when {
                params is LoadParams.Prepend -> start + items.size
                items.size < limit -> null
                else -> start + items.size
            },
        )
    } catch (e: SecurityException) {
        LoadResult.Error(e)
    } catch (e: IllegalStateException) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, MediaStoreImage>): Int? =
        state.anchorPosition?.let { maxOf(0, it - state.config.initialLoadSize / 2) }
}
