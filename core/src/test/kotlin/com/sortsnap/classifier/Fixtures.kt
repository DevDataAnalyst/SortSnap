package com.sortsnap.classifier

/** Realistic feature fixtures shared by the classifier tests. */
object Fixtures {
    val PIXEL_SCREEN = ScreenSize(1080, 2400)

    val cameraPhoto = ImageFeatures(
        relativePath = "DCIM/Camera/", bucket = "Camera", displayName = "PXL_20260912_101500123.jpg",
        mimeType = "image/jpeg", width = 4080, height = 3072, sizeBytes = 3_400_000,
        exif = ExifFeatures(make = "Google", model = "Pixel 9", hasFocalLength = true, hasGps = true),
        screen = PIXEL_SCREEN,
    )

    val screenshot = ImageFeatures(
        relativePath = "Pictures/Screenshots/", bucket = "Screenshots",
        displayName = "Screenshot_20261101-093012_Chrome.png", mimeType = "image/png",
        width = 1080, height = 2400, sizeBytes = 620_000, screen = PIXEL_SCREEN,
    )

    val whatsappImage = ImageFeatures(
        relativePath = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", bucket = "WhatsApp Images",
        displayName = "IMG-20261102-WA0007.jpg", mimeType = "image/jpeg", width = 1080, height = 1080,
        sizeBytes = 140_000, screen = PIXEL_SCREEN,
    )

    val download = ImageFeatures(
        relativePath = "Download/", bucket = "Download", displayName = "funny_cat.jpg",
        mimeType = "image/jpeg", width = 800, height = 800, sizeBytes = 90_000,
    )

    fun text(
        fullText: String,
        lines: Int = fullText.lines().size,
        coverage: Float = 0.1f,
        top: Boolean = false,
        bottom: Boolean = false,
    ) = TextFeatures(fullText, lines, coverage, top, bottom)

    fun labels(vararg pairs: Pair<String, Float>) = pairs.map { ImageLabel(it.first, it.second) }
}
