package com.sortsnap.data.scan

import android.content.Context
import androidx.core.net.toUri
import com.sortsnap.classifier.Category
import com.sortsnap.classifier.ClassificationResult
import com.sortsnap.classifier.SignalCodec
import com.sortsnap.classifier.SortSnapClassifier
import com.sortsnap.data.db.ImageRecord
import com.sortsnap.data.ml.FeatureExtractor
import com.sortsnap.data.ml.MlAnalyzer
import com.sortsnap.data.ml.ThumbnailStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ClassifiedImage(
    val record: ImageRecord,
    val result: ClassificationResult,
    val stats: ThumbnailStats?,
    val mlMillis: Long?,
)

/** Rule pass for every image, ML pass only when the rule pass is unsure (< mlSkipConfidence). */
@Singleton
class ImageClassifierService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val features: FeatureExtractor,
    private val ml: MlAnalyzer,
) {
    val classifier = SortSnapClassifier()

    suspend fun classify(record: ImageRecord, folderPrior: Category?, fallback: Category): ClassifiedImage =
        withContext(Dispatchers.Default) {
            val (base, stats) = features.extract(record)
            val first = classifier.firstPass(base, folderPrior, fallback)
            if (!classifier.needsMlPass(first)) return@withContext ClassifiedImage(record, first, stats, null)
            val out = ml.analyze(context.contentResolver, record.uri.toUri())
                ?: return@withContext ClassifiedImage(record, first, stats, null)
            val fused = classifier.classify(base.copy(text = out.text, labels = out.labels), folderPrior, fallback)
            ClassifiedImage(record, fused, stats, out.millis)
        }

    fun encode(result: ClassificationResult): String = SignalCodec.encode(result.evidence)

    fun releaseModels() = ml.close()
}
