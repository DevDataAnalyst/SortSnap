package com.sortsnap.classifier

/**
 * Everything the classifier knows about one image. Built by the Android data layer from MediaStore,
 * EXIF, a small thumbnail and (optionally) ML Kit output, but itself free of Android types so it can
 * be unit-tested and stored as fixtures.
 */
data class ImageFeatures(
    /** MediaStore RELATIVE_PATH, e.g. "DCIM/Camera/" or "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/". */
    val relativePath: String = "",
    /** MediaStore BUCKET_DISPLAY_NAME, e.g. "Camera", "Screenshots", "WhatsApp Images". */
    val bucket: String = "",
    val displayName: String = "",
    val mimeType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val exif: ExifFeatures = ExifFeatures(),
    /** Physical screen size of the device in pixels, if known. */
    val screen: ScreenSize? = null,
    /** Cheap pixel statistics from a downscaled thumbnail. */
    val visual: VisualFeatures? = null,
    /** ML Kit text recognition output (second pass only). */
    val text: TextFeatures? = null,
    /** ML Kit image labeling output (second pass only). */
    val labels: List<ImageLabel>? = null,
) {
    val hasMlFeatures: Boolean get() = text != null || labels != null

    /** Lower-cased "path/bucket" haystack used by folder rules. */
    internal val folderHaystack: String by lazy {
        (relativePath.replace('\\', '/') + "/" + bucket).lowercase()
    }
}

data class ExifFeatures(
    val make: String? = null,
    val model: String? = null,
    val hasFocalLength: Boolean = false,
    val hasGps: Boolean = false,
) {
    val hasCameraMakeModel: Boolean get() = !make.isNullOrBlank() || !model.isNullOrBlank()
    val isEmpty: Boolean get() = !hasCameraMakeModel && !hasFocalLength && !hasGps
}

data class ScreenSize(val width: Int, val height: Int) {
    fun matches(w: Int, h: Int): Boolean = (w == width && h == height) || (w == height && h == width)
}

data class VisualFeatures(
    /** Mean luminance 0..1. */
    val meanBrightness: Float,
    /** Fraction (0..1) of pixels that fall into the dominant luminance bin: high for flat backgrounds. */
    val backgroundUniformity: Float,
)

data class TextFeatures(
    val fullText: String,
    val lineCount: Int,
    /** Fraction (0..1) of the image area covered by text bounding boxes. */
    val textCoverage: Float,
    /** Some text block sits in the top band of the image (classic meme caption). */
    val topBandText: Boolean,
    /** Some text block sits in the bottom band of the image. */
    val bottomBandText: Boolean,
) {
    companion object {
        val NONE = TextFeatures("", 0, 0f, topBandText = false, bottomBandText = false)
    }
}

data class ImageLabel(val name: String, val confidence: Float)
