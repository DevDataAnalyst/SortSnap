package com.sortsnap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sortsnap.classifier.Category
import com.sortsnap.data.delete.DeleteStep
import com.sortsnap.data.delete.DeleteTarget
import com.sortsnap.data.delete.MediaDeleter
import com.sortsnap.domain.GalleryFilter
import com.sortsnap.domain.GalleryItem
import com.sortsnap.ui.components.DeleteFlowHost
import com.sortsnap.ui.components.GalleryEntry
import com.sortsnap.ui.components.PagedGalleryGrid
import com.sortsnap.ui.components.rememberDeleteFlowState
import com.sortsnap.ui.gallery.FilterChips
import com.sortsnap.ui.gallery.GalleryViewModel
import com.sortsnap.ui.onboarding.OnboardingScreen
import com.sortsnap.ui.theme.SortSnapTheme
import com.sortsnap.ui.viewer.DetailsSheet
import com.sortsnap.ui.viewer.ImageDetails
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.compose.ui.test.performScrollTo

private fun item(id: Long, daysAgo: Int = 0) = GalleryItem(
    id = id, uri = "content://media/external/images/media/$id", displayName = "IMG_$id.jpg",
    dateTaken = System.currentTimeMillis() - daysAgo * 86_400_000L, sizeBytes = 1_000_000, width = 100, height = 100,
    folder = "DCIM/Camera/", category = Category.PHOTO, confidence = 0.8f,
)

/** Fake repository-level deleter: Android 9 style in-app confirmation path. */
private class FakeDeleter : MediaDeleter {
    var started: List<DeleteTarget> = emptyList()
    var confirmed = false
    override suspend fun start(targets: List<DeleteTarget>): DeleteStep {
        started = targets
        return DeleteStep.ConfirmInApp(targets.size, targets.sumOf { it.size })
    }
    override suspend fun onSystemResult(approved: Boolean): DeleteStep = DeleteStep.Cancelled
    override suspend fun confirmInApp(): DeleteStep {
        confirmed = true
        return DeleteStep.Done(started.map { it.mediaId }, started.sumOf { it.size }, trashed = false)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ComposeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onboarding_explainsPrivacyAndRequestsAccess() {
        var requested = 0
        compose.setContent {
            SortSnapTheme { OnboardingScreen(deniedPermanently = false, onRequestAccess = { requested++ }, onOpenSettings = {}) }
        }
        compose.onNodeWithText("Everything stays on this phone").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("allow_access").performScrollTo().performClick()
        assertThat(requested).isEqualTo(1)
    }

    @Test fun onboarding_permanentDenialOffersSettings() {
        var opened = false
        compose.setContent {
            SortSnapTheme { OnboardingScreen(deniedPermanently = true, onRequestAccess = {}, onOpenSettings = { opened = true }) }
        }
        compose.onNodeWithTag("open_settings").performScrollTo().performClick()
        assertThat(opened).isTrue()
    }

    @Test fun filterChips_selectAndReflectState() {
        var current: GalleryFilter by mutableStateOf(GalleryFilter.HideClutter)
        compose.setContent { SortSnapTheme { FilterChips(current = current, onSelect = { current = it }) } }
        compose.onNodeWithTag("chip_photos").assertIsSelected()
        compose.onNodeWithTag("chip_MEME").performClick()
        assertThat(current).isEqualTo(GalleryFilter.Only(Category.MEME))
        compose.onNodeWithTag("chip_MEME").assertIsSelected()
        compose.onNodeWithTag("chip_photos").assertIsNotSelected()
        compose.onNodeWithTag("chip_all").performClick()
        assertThat(current).isEqualTo(GalleryFilter.All)
    }

    @Composable
    private fun SelectableGrid(items: List<GalleryItem>, onOpen: (Int) -> Unit) {
        var selected by remember { mutableStateOf(emptySet<Long>()) }
        val paging = remember { flowOf(GalleryViewModel.withHeaders(PagingData.from(items), System.currentTimeMillis())) }
        val entries = paging.collectAsLazyPagingItems()
        PagedGalleryGrid(
            entries = entries,
            columns = 3,
            selected = selected,
            onOpen = { index, _ -> onOpen(index) },
            onToggleSelect = { selected = if (it.id in selected) selected - it.id else selected + it.id },
            onColumnsChange = {},
        )
        androidx.compose.material3.Text("count=${selected.size}")
    }

    @Test fun multiSelect_longPressStartsSelectionThenTapToggles() {
        val opened = mutableListOf<Int>()
        compose.setContent { SortSnapTheme { SelectableGrid(listOf(item(1), item(2), item(3, daysAgo = 40))) { opened += it } } }
        // Tap opens the viewer at the image index (headers are not counted).
        compose.onNodeWithTag("thumb_3").performClick()
        assertThat(opened).containsExactly(2)
        compose.onNodeWithTag("thumb_1").performTouchInput { longClick() }
        compose.onNodeWithText("count=1").assertIsDisplayed()
        compose.onNodeWithTag("thumb_1").assertIsSelected()
        compose.onNodeWithTag("thumb_2").performClick() // in selection mode a tap selects instead of opening
        compose.onNodeWithText("count=2").assertIsDisplayed()
        compose.onNodeWithTag("thumb_1").performClick()
        compose.onNodeWithText("count=1").assertIsDisplayed()
        assertThat(opened).hasSize(1)
    }

    @Test fun gallery_showsDateHeaders() {
        compose.setContent { SortSnapTheme { SelectableGrid(listOf(item(1), item(2, daysAgo = 1))) {} } }
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("Yesterday").assertIsDisplayed()
    }

    @Test fun deleteFlow_requiresConfirmationBeforeDeleting() {
        val deleter = FakeDeleter()
        var finished: DeleteStep? = null
        compose.setContent {
            SortSnapTheme {
                val state = rememberDeleteFlowState(deleter)
                DeleteFlowHost(state) { finished = it }
                androidx.compose.material3.Button(onClick = { state.start(listOf(DeleteTarget(7, "content://x/7", 2048))) }) {
                    androidx.compose.material3.Text("trash")
                }
            }
        }
        compose.onNodeWithText("trash").performClick()
        compose.onNodeWithTag("confirm_delete_dialog").assertIsDisplayed()
        assertThat(deleter.confirmed).isFalse() // nothing deleted until the user confirms
        compose.onNodeWithTag("confirm_delete").performClick()
        compose.waitForIdle()
        assertThat(deleter.confirmed).isTrue()
        assertThat((finished as DeleteStep.Done).removedIds).containsExactly(7L)
    }

    @Test fun deleteFlow_cancelDeletesNothing() {
        val deleter = FakeDeleter()
        var finished: DeleteStep? = null
        compose.setContent {
            SortSnapTheme {
                val state = rememberDeleteFlowState(deleter)
                DeleteFlowHost(state) { finished = it }
                androidx.compose.material3.Button(onClick = { state.start(listOf(DeleteTarget(7, "content://x/7", 2048))) }) {
                    androidx.compose.material3.Text("trash")
                }
            }
        }
        compose.onNodeWithText("trash").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertThat(deleter.confirmed).isFalse()
        assertThat(finished).isEqualTo(DeleteStep.Cancelled)
    }

    @Test fun detailsSheet_showsWhyList() {
        val record = com.sortsnap.data.db.ImageRecord(
            mediaId = 1, uri = "u", displayName = "IMG-WA0001.jpg", path = "WhatsApp/", bucket = "WhatsApp Images",
            dateTaken = 0, dateModified = 0, width = 1, height = 1, size = 1, mime = "image/jpeg",
            category = Category.MEME, effectiveCategory = Category.MEME, confidence = 0.72f,
        )
        compose.setContent {
            SortSnapTheme {
                DetailsSheet(item(1), ImageDetails(record, listOf("Saved in WhatsApp folder", "Text at top and bottom")), onMove = {})
            }
        }
        compose.onNodeWithText("Why?").assertIsDisplayed()
        compose.onNodeWithText("• Text at top and bottom").assertIsDisplayed()
        compose.onNodeWithText("72%").assertIsDisplayed()
    }
}
