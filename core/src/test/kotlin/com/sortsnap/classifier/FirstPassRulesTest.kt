package com.sortsnap.classifier

import com.google.common.truth.Truth.assertThat
import com.sortsnap.classifier.Category.DOCUMENT
import com.sortsnap.classifier.Category.FORWARD
import com.sortsnap.classifier.Category.MEME
import com.sortsnap.classifier.Category.PHOTO
import com.sortsnap.classifier.Category.SCREENSHOT
import com.sortsnap.classifier.Fixtures.cameraPhoto
import com.sortsnap.classifier.Fixtures.download
import com.sortsnap.classifier.Fixtures.screenshot
import com.sortsnap.classifier.Fixtures.whatsappImage
import org.junit.Test

class FirstPassRulesTest {
    private val c = ClassifierConfig.DEFAULT

    // --- Screenshot folder ---
    @Test fun screenshotFolder_firesForPicturesScreenshots() {
        val s = ScreenshotFolderRule.evaluate(screenshot, c)!!
        assertThat(s.scores[SCREENSHOT]).isEqualTo(c.screenshotFolderWeight)
    }

    @Test fun screenshotFolder_matchesBucketOnlyCaseInsensitive() {
        val f = ImageFeatures(relativePath = "DCIM/", bucket = "SCREENSHOTS")
        assertThat(ScreenshotFolderRule.evaluate(f, c)).isNotNull()
    }

    @Test fun screenshotFolder_ignoresCameraFolder() {
        assertThat(ScreenshotFolderRule.evaluate(cameraPhoto, c)).isNull()
    }

    // --- Screenshot filename ---
    @Test fun screenshotFilename_firesForScreenshotPrefix() {
        assertThat(ScreenshotFilenameRule.evaluate(screenshot, c)?.scores?.get(SCREENSHOT))
            .isEqualTo(c.screenshotFilenameWeight)
    }

    @Test fun screenshotFilename_ignoresWordInMiddle() {
        val f = ImageFeatures(displayName = "my_screenshot_idea.jpg")
        assertThat(ScreenshotFilenameRule.evaluate(f, c)).isNull()
    }

    @Test fun screenshotFilename_handlesHyphenatedVariant() {
        assertThat(ScreenshotFilenameRule.evaluate(ImageFeatures(displayName = "Screen-Shot 2026.png"), c)).isNotNull()
    }

    // --- Dimensions ---
    @Test fun dimension_exactDeviceMatchIsStrong() {
        val s = ScreenshotDimensionRule.evaluate(screenshot, c)!!
        assertThat(s.id).isEqualTo(ScreenshotDimensionRule.DEVICE_ID)
        assertThat(s.scores[SCREENSHOT]).isEqualTo(c.screenshotDeviceDimensionWeight)
    }

    @Test fun dimension_landscapeDeviceMatchCounts() {
        val f = screenshot.copy(width = 2400, height = 1080)
        assertThat(ScreenshotDimensionRule.evaluate(f, c)!!.id).isEqualTo(ScreenshotDimensionRule.DEVICE_ID)
    }

    @Test fun dimension_commonPhoneResolutionIsWeaker() {
        val f = ImageFeatures(width = 1170, height = 2532, screen = Fixtures.PIXEL_SCREEN)
        val s = ScreenshotDimensionRule.evaluate(f, c)!!
        assertThat(s.id).isEqualTo(ScreenshotDimensionRule.COMMON_ID)
        assertThat(s.scores[SCREENSHOT]).isLessThan(c.screenshotDeviceDimensionWeight)
    }

    @Test fun dimension_cameraSensorSizeDoesNotFire() {
        assertThat(ScreenshotDimensionRule.evaluate(cameraPhoto, c)).isNull()
    }

    @Test fun dimension_unknownSizeDoesNotFire() {
        assertThat(ScreenshotDimensionRule.evaluate(ImageFeatures(), c)).isNull()
    }

    // --- PNG ---
    @Test fun png_firesForPngOnly() {
        assertThat(PngMimeRule.evaluate(screenshot, c)).isNotNull()
        assertThat(PngMimeRule.evaluate(cameraPhoto, c)).isNull()
    }

    // --- No camera EXIF ---
    @Test fun noExif_lowersPhotoAndBoostsClutter() {
        val s = NoCameraExifRule.evaluate(download, c)!!
        assertThat(s.scores[PHOTO]).isLessThan(0f)
        assertThat(s.scores[SCREENSHOT]).isGreaterThan(0f)
    }

    @Test fun noExif_skippedWhenExifPresent() {
        assertThat(NoCameraExifRule.evaluate(cameraPhoto, c)).isNull()
    }

    @Test fun noExif_skippedInCameraFolderEvenWithoutExif() {
        assertThat(NoCameraExifRule.evaluate(cameraPhoto.copy(exif = ExifFeatures()), c)).isNull()
    }

    @Test fun noExif_onlyGpsCountsAsExif() {
        assertThat(NoCameraExifRule.evaluate(download.copy(exif = ExifFeatures(hasGps = true)), c)).isNull()
    }

    // --- Chat apps ---
    @Test fun chatApp_whatsappBoostsMemeAndForwardLowersPhoto() {
        val s = ChatAppRule.evaluate(whatsappImage, c)!!
        assertThat(s.detail).isEqualTo("WhatsApp")
        assertThat(s.scores[MEME]).isGreaterThan(0f)
        assertThat(s.scores[FORWARD]).isGreaterThan(0f)
        assertThat(s.scores[PHOTO]).isLessThan(0f)
    }

    @Test fun chatApp_detectsTelegramAndSnapchat() {
        assertThat(ChatAppRule.evaluate(ImageFeatures(relativePath = "Pictures/Telegram/"), c)?.detail).isEqualTo("Telegram")
        assertThat(ChatAppRule.evaluate(ImageFeatures(bucket = "Snapchat"), c)?.detail).isEqualTo("Snapchat")
    }

    @Test fun chatApp_sentSegmentMatchesButNotSubstring() {
        assertThat(ChatAppRule.evaluate(ImageFeatures(relativePath = "Pictures/Sent/"), c)?.detail).isEqualTo("Sent")
        assertThat(ChatAppRule.evaluate(ImageFeatures(relativePath = "Pictures/Presentations/"), c)).isNull()
    }

    @Test fun chatApp_receivedSegment() {
        assertThat(ChatAppRule.evaluate(ImageFeatures(relativePath = "Pictures/Received/"), c)?.detail).isEqualTo("Received")
    }

    // --- Camera ---
    @Test fun cameraFolder_dcimCamera() {
        assertThat(CameraFolderRule.evaluate(cameraPhoto, c)?.scores?.get(PHOTO)).isEqualTo(c.cameraFolderWeight)
    }

    @Test fun cameraFolder_numberedDcimFolder() {
        assertThat(CameraFolderRule.evaluate(ImageFeatures(relativePath = "DCIM/100ANDRO/"), c)).isNotNull()
    }

    @Test fun cameraFolder_notForDcimScreenshots() {
        assertThat(CameraFolderRule.evaluate(ImageFeatures(relativePath = "DCIM/Screenshots/", bucket = "Screenshots"), c)).isNull()
    }

    @Test fun cameraExif_makeModel() {
        val s = CameraExifRule.evaluate(cameraPhoto, c)!!
        assertThat(s.detail).isEqualTo("Google Pixel 9")
        assertThat(CameraExifRule.evaluate(download, c)).isNull()
    }

    @Test fun focalLength_and_gps() {
        assertThat(FocalLengthRule.evaluate(cameraPhoto, c)).isNotNull()
        assertThat(GpsRule.evaluate(cameraPhoto, c)).isNotNull()
        assertThat(FocalLengthRule.evaluate(download, c)).isNull()
        assertThat(GpsRule.evaluate(download, c)).isNull()
    }

    // --- Downloads ---
    @Test fun downloads_boostsMemeAndDocumentLowersPhoto() {
        val s = DownloadsRule.evaluate(download, c)!!
        assertThat(s.detail).isEqualTo("Download")
        assertThat(s.scores[MEME]).isGreaterThan(0f)
        assertThat(s.scores[DOCUMENT]).isGreaterThan(0f)
        assertThat(s.scores[PHOTO]).isLessThan(0f)
    }

    @Test fun downloads_socialSaveFolders() {
        assertThat(DownloadsRule.evaluate(ImageFeatures(relativePath = "Pictures/Twitter/"), c)?.detail).isEqualTo("Twitter")
        assertThat(DownloadsRule.evaluate(ImageFeatures(relativePath = "Pictures/Reddit/"), c)?.detail).isEqualTo("Reddit")
        assertThat(DownloadsRule.evaluate(cameraPhoto, c)).isNull()
    }
}
