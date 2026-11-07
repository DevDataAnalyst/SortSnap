package com.sortsnap.data

import android.Manifest
import android.os.Build
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import com.google.common.truth.Truth.assertThat
import com.sortsnap.classifier.Category
import com.sortsnap.data.media.MediaStoreImage
import com.sortsnap.data.media.MediaStorePagingSource
import com.sortsnap.domain.GalleryFilter
import com.sortsnap.ui.permission.PhotoAccess
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PhotoAccessTest {
    private fun grants(vararg p: String): (String) -> Boolean = { it in p }

    @Test fun android14FullPartialNone() {
        val sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        assertThat(PhotoAccess.fromGrants(sdk, grants(Manifest.permission.READ_MEDIA_IMAGES))).isEqualTo(PhotoAccess.FULL)
        assertThat(PhotoAccess.fromGrants(sdk, grants(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))).isEqualTo(PhotoAccess.PARTIAL)
        assertThat(PhotoAccess.fromGrants(sdk, grants())).isEqualTo(PhotoAccess.NONE)
    }

    @Test fun android13IgnoresOldStoragePermission() {
        val sdk = Build.VERSION_CODES.TIRAMISU
        assertThat(PhotoAccess.fromGrants(sdk, grants(Manifest.permission.READ_EXTERNAL_STORAGE))).isEqualTo(PhotoAccess.NONE)
        assertThat(PhotoAccess.fromGrants(sdk, grants(Manifest.permission.READ_MEDIA_IMAGES))).isEqualTo(PhotoAccess.FULL)
    }

    @Test fun android12AndBelowUseReadExternalStorage() {
        assertThat(PhotoAccess.fromGrants(Build.VERSION_CODES.S, grants(Manifest.permission.READ_EXTERNAL_STORAGE))).isEqualTo(PhotoAccess.FULL)
        assertThat(PhotoAccess.fromGrants(Build.VERSION_CODES.O, grants())).isEqualTo(PhotoAccess.NONE)
    }

    @Test fun requestSets() {
        assertThat(PhotoAccess.requestSet(34).toList()).containsAtLeast(
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        assertThat(PhotoAccess.requestSet(33).toList()).doesNotContain(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        assertThat(PhotoAccess.requestSet(30).first()).isEqualTo(Manifest.permission.READ_EXTERNAL_STORAGE)
        assertThat(PhotoAccess.requestSet(28).toList()).contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}

class GalleryFilterTest {
    @Test fun roundTrip() {
        listOf(GalleryFilter.All, GalleryFilter.HideClutter, GalleryFilter.Only(Category.MEME)).forEach {
            assertThat(GalleryFilter.decode(it.encode())).isEqualTo(it)
        }
        assertThat(GalleryFilter.decode("cat:NOPE")).isEqualTo(GalleryFilter.All)
        assertThat(GalleryFilter.decode(null)).isEqualTo(GalleryFilter.All)
    }
}

class MediaStorePagingSourceTest {
    private val all = (0 until 250).map { i ->
        MediaStoreImage(i.toLong(), "content://x/$i", "n$i", "DCIM/", "Camera", 0, 0, 0, 1, 1, 1, "image/jpeg")
    }
    private val loader: suspend (Int, Int) -> List<MediaStoreImage> = { o, l -> all.drop(o).take(l) }
    private val config = PagingConfig(pageSize = 100, initialLoadSize = 100, enablePlaceholders = false)

    @Test fun appendsUntilExhausted() = runTest {
        val pager = TestPager(config, MediaStorePagingSource(loader))
        val first = pager.refresh() as PagingSource.LoadResult.Page
        assertThat(first.data.first().id).isEqualTo(0L)
        assertThat(first.prevKey).isNull()
        val second = pager.append() as PagingSource.LoadResult.Page
        assertThat(second.data.first().id).isEqualTo(100L)
        val third = pager.append() as PagingSource.LoadResult.Page
        assertThat(third.data).hasSize(50)
        assertThat(third.nextKey).isNull()
    }

    @Test fun prependNeverOverlaps() = runTest {
        val pager = TestPager(config, MediaStorePagingSource(loader))
        val page = pager.refresh(initialKey = 130) as PagingSource.LoadResult.Page
        assertThat(page.data.first().id).isEqualTo(130L)
        val before = pager.prepend() as PagingSource.LoadResult.Page
        assertThat(before.data.first().id).isEqualTo(30L)
        assertThat(before.data.last().id).isEqualTo(129L)
        val start = pager.prepend() as PagingSource.LoadResult.Page
        assertThat(start.data.map { it.id }).isEqualTo((0L until 30L).toList())
        assertThat(start.prevKey).isNull()
    }

    @Test fun countEnablesPlaceholders() = runTest {
        val pager = TestPager(config.copyPlaceholders(), MediaStorePagingSource(loader) { all.size })
        val page = pager.refresh(initialKey = 100) as PagingSource.LoadResult.Page
        assertThat(page.itemsBefore).isEqualTo(100)
        assertThat(page.itemsAfter).isEqualTo(50)
    }

    @Test fun securityExceptionBecomesError() = runTest {
        val pager = TestPager(config, MediaStorePagingSource({ _, _ -> throw SecurityException("revoked") }))
        assertThat(pager.refresh()).isInstanceOf(PagingSource.LoadResult.Error::class.java)
    }

    private fun PagingConfig.copyPlaceholders() = PagingConfig(pageSize = pageSize, initialLoadSize = initialLoadSize, enablePlaceholders = true)
}
