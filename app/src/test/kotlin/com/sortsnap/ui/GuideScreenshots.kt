package com.sortsnap.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.sortsnap.classifier.Category
import com.sortsnap.classifier.MemeCaptionRule
import com.sortsnap.classifier.MemeLabelRule
import com.sortsnap.classifier.NoCameraExifRule
import com.sortsnap.classifier.PersonalModel
import com.sortsnap.classifier.Signal
import com.sortsnap.classifier.SignalExplainer
import com.sortsnap.classifier.SocialWatermarkRule
import com.sortsnap.data.db.FolderPriorEntity
import com.sortsnap.data.db.ImageRecord
import com.sortsnap.data.scan.ScanStatus
import com.sortsnap.data.scan.ScanWorker
import com.sortsnap.data.settings.AppSettings
import com.sortsnap.domain.CleanupSection
import com.sortsnap.domain.GalleryFilter
import com.sortsnap.domain.GalleryItem
import com.sortsnap.domain.SectionSummary
import com.sortsnap.ui.categories.CategoriesContent
import com.sortsnap.ui.categories.CategoryTile
import com.sortsnap.ui.gallery.GalleryContent
import com.sortsnap.ui.gallery.GalleryViewModel
import com.sortsnap.ui.onboarding.OnboardingScreen
import com.sortsnap.ui.review.ReviewContent
import com.sortsnap.ui.settings.SettingsActions
import com.sortsnap.ui.settings.SettingsContent
import com.sortsnap.ui.theme.SortSnapTheme
import com.sortsnap.ui.viewer.DetailsSheet
import com.sortsnap.ui.viewer.ImageDetails
import com.sortsnap.ui.viewer.ViewerTopBar
import com.sortsnap.ui.viewer.ZoomableImage
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the screens used in the README's "Getting started" section to PNG files, using the real
 * composables with sample data (Robolectric native graphics, no emulator).
 *
 * Writes to `app/build/guide-screenshots` by default. To refresh the committed images:
 *
 *     ./gradlew :app:testDebugUnitTest --tests '*GuideScreenshots*' -PguideDir=docs/screenshots
 *
 * The pictures inside the screens are drawn by `tools/generate_sample_images.py`.
 */
@OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class GuideScreenshots {
    @get:Rule val compose = createComposeRule()

    private val outDir: File = File(System.getProperty("sortsnap.guideDir") ?: "build/guide-screenshots").apply { mkdirs() }

    private val day = 86_400_000L
    private val now = System.currentTimeMillis()
    private var nextId = 1L

    private fun sample(name: String, category: Category, daysAgo: Double, folder: String, confidence: Float = 0.9f, w: Int = 4000, h: Int = 3000) =
        GalleryItem(
            id = nextId++, uri = "sample://$name", displayName = fileName(name, category),
            dateTaken = now - (daysAgo * day).toLong(), sizeBytes = sizeFor(category), width = w, height = h,
            folder = folder, category = category, confidence = confidence,
        )

    private fun fileName(name: String, category: Category) = when (category) {
        Category.PHOTO -> "IMG_2026${name.hashCode().mod(10000).toString().padStart(4, '0')}.jpg"
        Category.SCREENSHOT -> "Screenshot_20261108_${name.length}4512.png"
        else -> "IMG-20261102-WA00${name.length}.jpg"
    }

    private fun sizeFor(category: Category) = when (category) {
        Category.PHOTO -> 3_400_000L
        Category.SCREENSHOT -> 820_000L
        else -> 180_000L
    }

    private val camera = "DCIM/Camera/"
    private val whatsapp = "WhatsApp/Media/WhatsApp Images/"

    private val photos = listOf(
        sample("photo_sunset", Category.PHOTO, 0.1, camera),
        sample("photo_dog", Category.PHOTO, 0.2, camera),
        sample("photo_food", Category.PHOTO, 0.3, camera),
        sample("photo_flowers", Category.PHOTO, 1.2, camera),
        sample("photo_beach", Category.PHOTO, 1.3, camera),
        sample("photo_mountains", Category.PHOTO, 3.0, camera),
        sample("photo_lake", Category.PHOTO, 3.1, camera),
        sample("photo_city", Category.PHOTO, 4.0, camera),
        sample("photo_sunset", Category.PHOTO, 40.0, camera),
        sample("photo_beach", Category.PHOTO, 40.1, camera),
        sample("photo_mountains", Category.PHOTO, 41.0, camera),
        sample("photo_dog", Category.PHOTO, 42.0, camera),
        sample("photo_flowers", Category.PHOTO, 43.0, camera),
        sample("photo_food", Category.PHOTO, 44.0, camera),
        sample("photo_lake", Category.PHOTO, 45.0, camera),
    )
    private val memes = listOf(
        sample("meme_cat", Category.MEME, 0.15, whatsapp, 0.93f, 720, 720),
        sample("meme_desk", Category.MEME, 1.4, whatsapp, 0.88f, 720, 720),
        sample("meme_panels", Category.MEME, 2.0, whatsapp, 0.81f, 720, 720),
        sample("meme_cat", Category.MEME, 9.0, whatsapp, 0.9f, 720, 720),
        sample("meme_desk", Category.MEME, 12.0, whatsapp, 0.9f, 720, 720),
        sample("meme_panels", Category.MEME, 15.0, whatsapp, 0.9f, 720, 720),
    )

    private fun allItems() = (photos.take(8) + memes.take(3) + listOf(
        sample("screenshot_chat", Category.SCREENSHOT, 0.25, "Pictures/Screenshots/", 0.98f, 1080, 2280),
        sample("forward_morning", Category.FORWARD, 0.05, whatsapp, 0.9f, 720, 720),
        sample("doc_receipt", Category.DOCUMENT, 1.25, camera, 0.86f),
    )).sortedByDescending { it.dateTaken }

    // ---------- harness ----------

    private val imageCache = HashMap<String, Bitmap>()

    private fun load(uri: String): Bitmap? {
        val name = uri.removePrefix("sample://")
        return imageCache.getOrPut(name) {
            val stream = javaClass.getResourceAsStream("/guide-samples/$name.jpg") ?: return null
            stream.use { BitmapFactory.decodeStream(it) }
        }
    }

    private fun shoot(file: String, content: @Composable () -> Unit) {
        compose.setContent {
            val handler = remember {
                AsyncImagePreviewHandler { request -> (load(request.data.toString()) ?: error("No sample for ${request.data}")).asImage() }
            }
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides handler,
            ) {
                SortSnapTheme(dynamicColor = false) { content() }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, file).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun WithTabs(route: String, content: @Composable () -> Unit) {
        Scaffold(bottomBar = { SortSnapBottomBar(currentRoute = route, onNavigate = {}) }) { padding ->
            Box(Modifier.padding(padding)) { content() }
        }
    }

    @Composable
    private fun Gallery(
        items: List<GalleryItem>,
        filter: GalleryFilter?,
        title: String = "Gallery",
        showFilters: Boolean = true,
        onBack: (() -> Unit)? = null,
        selected: Set<Long> = emptySet(),
        scan: ScanStatus = ScanStatus.IDLE,
    ) {
        val entries = remember { flowOf(GalleryViewModel.withHeaders(PagingData.from(items), now)) }.collectAsLazyPagingItems()
        GalleryContent(
            title = title, onBack = onBack, scan = scan, showFilters = showFilters, filter = filter, onSelectFilter = {},
            entries = entries, columns = 3, selected = selected, onClearSelection = {}, onMoveSelected = {},
            onTrashSelected = {}, onOpen = {}, onToggleSelect = {}, onColumnsChange = {},
        )
    }

    // ---------- the guide, in reading order ----------

    @Test fun step1_welcome() = shoot("01-welcome.png") {
        OnboardingScreen(deniedPermanently = false, onRequestAccess = {}, onOpenSettings = {})
    }

    @Test fun step2_galleryPhotosOnly() = shoot("02-gallery-photos-only.png") {
        WithTabs(Routes.GALLERY) {
            Gallery(
                items = photos,
                filter = GalleryFilter.HideClutter,
                scan = ScanStatus(running = true, phase = ScanWorker.PHASE_CLASSIFY, done = 1240, total = 3518, deferred = false),
            )
        }
    }

    @Test fun step3_galleryAll() = shoot("03-gallery-all.png") {
        WithTabs(Routes.GALLERY) { Gallery(items = allItems(), filter = GalleryFilter.All) }
    }

    @Test fun step4_categories() = shoot("04-categories.png") {
        WithTabs(Routes.CATEGORIES) {
            CategoriesContent(
                tiles = listOf(
                    CategoryTile(Category.PHOTO, 2_184, 7_420_000_000, "sample://photo_mountains"),
                    CategoryTile(Category.SCREENSHOT, 612, 498_000_000, "sample://screenshot_chat"),
                    CategoryTile(Category.MEME, 347, 61_000_000, "sample://meme_cat"),
                    CategoryTile(Category.FORWARD, 205, 37_000_000, "sample://forward_morning"),
                    CategoryTile(Category.DOCUMENT, 96, 210_000_000, "sample://doc_receipt"),
                    CategoryTile(Category.OTHER, 74, 88_000_000, "sample://photo_city"),
                ),
                onOpenCategory = {},
            )
        }
    }

    @Test fun step5_album() = shoot("05-album-memes.png") {
        Gallery(items = memes, filter = GalleryFilter.Only(Category.MEME), title = "Memes", showFilters = false, onBack = {})
    }

    @Test fun step6_why() = shoot("06-viewer-why.png") {
        val item = memes.first()
        val signals = listOf(
            Signal(MemeCaptionRule.id, mapOf(Category.MEME to 0.9f)),
            Signal(PersonalModel.SIGNAL_ID, mapOf(Category.MEME to 0.6f), detail = "7"),
            Signal(MemeLabelRule.id, mapOf(Category.MEME to 0.5f), detail = "Cartoon"),
            Signal(NoCameraExifRule.id, mapOf(Category.MEME to 0.2f, Category.PHOTO to -0.3f)),
            Signal(SocialWatermarkRule.id, mapOf(Category.PHOTO to 0.0f)),
        )
        val record = ImageRecord(
            mediaId = item.id, uri = item.uri, displayName = item.displayName, path = item.folder, bucket = "WhatsApp Images",
            dateTaken = item.dateTaken, dateModified = item.dateTaken / 1000, width = item.width, height = item.height,
            size = item.sizeBytes, mime = "image/jpeg", category = Category.MEME, confidence = 0.93f, effectiveCategory = Category.MEME,
        )
        val details = ImageDetails(record, SignalExplainer.explain(signals, Category.MEME))
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxSize().padding(bottom = 360.dp)) {
                ZoomableImage(uri = item.uri, contentDescription = item.displayName, onTap = {})
            }
            ViewerTopBar(enabled = true, onBack = {}, onInfo = {}, onMove = {}, onTrash = {})
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                    DetailsSheet(item = item, details = details, onMove = {})
                }
            }
        }
    }

    @Test fun step7_multiSelect() = shoot("07-select-and-move.png") {
        val items = allItems()
        val picked = items.filter { it.category == Category.MEME || it.category == Category.FORWARD }.map { it.id }.toSet()
        WithTabs(Routes.GALLERY) { Gallery(items = items, filter = GalleryFilter.All, selected = picked) }
    }

    @Test fun step8_review() = shoot("08-review.png") {
        val card = ImageRecord(
            mediaId = 900, uri = "sample://doc_notes", displayName = "IMG_20261104_181205.jpg", path = camera, bucket = "Camera",
            dateTaken = now - 6 * day, dateModified = 0, width = 4000, height = 3000, size = 2_100_000, mime = "image/jpeg",
            category = Category.DOCUMENT, confidence = 0.68f, effectiveCategory = Category.DOCUMENT,
        )
        WithTabs(Routes.REVIEW) {
            ReviewContent(
                sections = listOf(
                    SectionSummary(CleanupSection.OLD_SCREENSHOTS, 418, 312_000_000),
                    SectionSummary(CleanupSection.MEMES, 347, 61_000_000),
                    SectionSummary(CleanupSection.FORWARDS, 205, 37_000_000),
                    SectionSummary(CleanupSection.NEAR_DUPLICATES, 129, 402_000_000),
                    SectionSummary(CleanupSection.BLURRY, 58, 166_000_000),
                ),
                cards = List(14) { card.copy(mediaId = 900L + it) },
                onOpenSection = {}, onOpenSwipe = {}, onConfirm = {}, onCorrect = { _, _ -> }, onSkip = {},
            )
        }
    }

    @Test fun step9_settings() = shoot("09-settings-learns-from-you.png") {
        val scroll = rememberScrollState()
        LaunchedEffect(Unit) { scroll.scrollTo(220) }
        WithTabs(Routes.SETTINGS) {
            SettingsContent(
                s = AppSettings(),
                rules = listOf(
                    FolderPriorEntity("WhatsApp Images", Category.FORWARD, 23, now),
                    FolderPriorEntity("Telegram", Category.MEME, 11, now),
                ),
                personalExamples = 42,
                actions = SettingsActions(),
                showDebug = false,
                scrollState = scroll,
            )
        }
    }
}
