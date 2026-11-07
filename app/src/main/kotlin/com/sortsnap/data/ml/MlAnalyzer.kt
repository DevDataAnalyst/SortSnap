package com.sortsnap.data.ml

import android.content.ContentResolver
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sortsnap.classifier.ImageLabel
import com.sortsnap.classifier.TextFeatures
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class MlOutput(val text: TextFeatures, val labels: List<ImageLabel>, val millis: Long)

/**
 * ML Kit second pass with bundled, offline models. Images are decoded at most [MAX_PX] on the long
 * edge, at most [MAX_CONCURRENCY] at a time, and bitmaps are recycled immediately: this bounds
 * memory regardless of library size.
 */
@Singleton
class MlAnalyzer @Inject constructor() {
    private val semaphore = Semaphore(MAX_CONCURRENCY)
    private var textRecognizer: TextRecognizer? = null
    private var labeler: ImageLabeler? = null

    @Synchronized
    private fun clients(): Pair<TextRecognizer, ImageLabeler> {
        val t = textRecognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { textRecognizer = it }
        val l = labeler ?: ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(LABEL_THRESHOLD).build(),
        ).also { labeler = it }
        return t to l
    }

    suspend fun analyze(resolver: ContentResolver, uri: Uri): MlOutput? = semaphore.withPermit {
        val start = System.currentTimeMillis()
        val bitmap = ThumbnailAnalyzer.decodeSmall(resolver, uri, MAX_PX) ?: return@withPermit null
        try {
            val (recognizer, labeler) = clients()
            val image = InputImage.fromBitmap(bitmap, 0)
            val text = runCatching { recognizer.process(image).await() }.getOrNull()
            val labels = runCatching { labeler.process(image).await() }.getOrNull().orEmpty()

            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            var area = 0f
            var top = false
            var bottom = false
            var lines = 0
            text?.textBlocks?.forEach { block ->
                lines += block.lines.size
                block.boundingBox?.let { box ->
                    area += box.width().toFloat() * box.height()
                    if (box.top < h * BAND) top = true
                    if (box.bottom > h * (1 - BAND)) bottom = true
                }
            }
            MlOutput(
                text = TextFeatures(
                    fullText = text?.text.orEmpty(),
                    lineCount = lines,
                    textCoverage = if (w * h > 0) (area / (w * h)).coerceIn(0f, 1f) else 0f,
                    topBandText = top,
                    bottomBandText = bottom,
                ),
                labels = labels.map { ImageLabel(it.text, it.confidence) },
                millis = System.currentTimeMillis() - start,
            )
        } finally {
            bitmap.recycle()
        }
    }

    /** Releases the native models (called when a scan finishes). */
    @Synchronized
    fun close() {
        textRecognizer?.close(); textRecognizer = null
        labeler?.close(); labeler = null
    }

    companion object {
        const val MAX_PX = 1024
        const val MAX_CONCURRENCY = 2
        const val LABEL_THRESHOLD = 0.5f
        /** Top/bottom band height as a fraction of the image (classic meme caption area). */
        const val BAND = 0.22f
    }
}
