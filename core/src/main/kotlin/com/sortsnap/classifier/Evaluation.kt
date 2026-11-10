package com.sortsnap.classifier

/** A fixture row: precomputed features plus the true label. */
data class LabeledFeatures(val id: String, val label: Category, val features: ImageFeatures)

/** Confusion matrix with per-category precision/recall, used by the evaluation harness. */
class ConfusionMatrix(val categories: List<Category> = Category.entries) {
    private val counts = Array(categories.size) { IntArray(categories.size) }

    fun add(actual: Category, predicted: Category) {
        counts[categories.indexOf(actual)][categories.indexOf(predicted)]++
    }

    fun count(actual: Category, predicted: Category): Int =
        counts[categories.indexOf(actual)][categories.indexOf(predicted)]

    val total: Int get() = counts.sumOf { it.sum() }

    val accuracy: Double
        get() = if (total == 0) 0.0 else categories.indices.sumOf { counts[it][it] }.toDouble() / total

    fun precision(c: Category): Double {
        val col = categories.indexOf(c)
        val predicted = counts.sumOf { it[col] }
        return if (predicted == 0) 0.0 else counts[col][col].toDouble() / predicted
    }

    fun recall(c: Category): Double {
        val row = categories.indexOf(c)
        val actual = counts[row].sum()
        return if (actual == 0) 0.0 else counts[row][row].toDouble() / actual
    }

    fun render(): String = buildString {
        val w = 11
        append("actual \\ predicted".padEnd(20))
        categories.forEach { append(it.name.take(10).padStart(w)) }
        appendLine()
        categories.forEachIndexed { r, c ->
            append(c.name.padEnd(20))
            counts[r].forEach { append(it.toString().padStart(w)) }
            appendLine()
        }
        appendLine()
        appendLine("category".padEnd(20) + "precision".padStart(w) + "recall".padStart(w) + "support".padStart(w))
        categories.forEachIndexed { r, c ->
            appendLine(
                c.name.padEnd(20) + "%.2f".format(precision(c)).padStart(w) + "%.2f".format(recall(c)).padStart(w) +
                    counts[r].sum().toString().padStart(w),
            )
        }
        appendLine()
        append("accuracy: %.3f (%d images)".format(accuracy, total))
    }

    companion object {
        fun evaluate(
            fixtures: List<LabeledFeatures>,
            classify: (ImageFeatures) -> ClassificationResult,
        ): ConfusionMatrix = ConfusionMatrix().apply {
            fixtures.forEach { add(it.label, classify(it.features).category) }
        }
    }
}

/**
 * Reads/writes evaluation fixtures as CSV. Columns (header required):
 * id,label,relative_path,bucket,display_name,mime,width,height,size,exif_make,exif_model,focal,gps,
 * screen_w,screen_h,brightness,uniformity,text,line_count,text_coverage,top_band,bottom_band,labels,
 * colorfulness (optional; blank = not measured)
 *
 * `labels` is `Name:0.93|Other:0.71`. Empty ML columns mean "no ML features".
 */
object FixtureCsv {

    val HEADER = listOf(
        "id", "label", "relative_path", "bucket", "display_name", "mime", "width", "height", "size",
        "exif_make", "exif_model", "focal", "gps", "screen_w", "screen_h", "brightness", "uniformity",
        "text", "line_count", "text_coverage", "top_band", "bottom_band", "labels", "colorfulness",
    )

    fun parse(content: String): List<LabeledFeatures> {
        val rows = parseRows(content).filter { row -> row.any { it.isNotBlank() } }
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim() }
        val idx = HEADER.associateWith { header.indexOf(it) }
        return rows.drop(1).map { row ->
            fun col(name: String): String = idx[name]?.takeIf { it >= 0 && it < row.size }?.let { row[it] }?.trim().orEmpty()
            fun int(name: String) = col(name).toIntOrNull() ?: 0
            fun flt(name: String) = col(name).toFloatOrNull()
            fun bool(name: String) = col(name).lowercase() in setOf("1", "true", "yes", "y")

            val screenW = int("screen_w")
            val screenH = int("screen_h")
            val brightness = flt("brightness")
            val hasText = col("line_count").isNotBlank() || col("text").isNotBlank()
            val labelsRaw = col("labels")
            LabeledFeatures(
                id = col("id"),
                label = Category.valueOf(col("label").uppercase()),
                features = ImageFeatures(
                    relativePath = col("relative_path"),
                    bucket = col("bucket"),
                    displayName = col("display_name"),
                    mimeType = col("mime"),
                    width = int("width"),
                    height = int("height"),
                    sizeBytes = col("size").toLongOrNull() ?: 0L,
                    exif = ExifFeatures(
                        make = col("exif_make").ifBlank { null },
                        model = col("exif_model").ifBlank { null },
                        hasFocalLength = bool("focal"),
                        hasGps = bool("gps"),
                    ),
                    screen = if (screenW > 0 && screenH > 0) ScreenSize(screenW, screenH) else null,
                    visual = brightness?.let {
                        VisualFeatures(it, flt("uniformity") ?: 0f, flt("colorfulness") ?: VisualFeatures.UNKNOWN)
                    },
                    text = if (hasText) {
                        TextFeatures(
                            fullText = col("text"),
                            lineCount = int("line_count"),
                            textCoverage = flt("text_coverage") ?: 0f,
                            topBandText = bool("top_band"),
                            bottomBandText = bool("bottom_band"),
                        )
                    } else {
                        null
                    },
                    labels = if (labelsRaw.isBlank() && !hasText) {
                        null
                    } else {
                        labelsRaw.split('|').filter { it.contains(':') }.map {
                            ImageLabel(it.substringBeforeLast(':').trim(), it.substringAfterLast(':').toFloatOrNull() ?: 0f)
                        }
                    },
                ),
            )
        }
    }

    /** RFC-4180-ish parser: quoted fields, doubled quotes, commas and newlines inside quotes. */
    fun parseRows(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < content.length) {
            val ch = content[i]
            when {
                inQuotes && ch == '"' && i + 1 < content.length && content[i + 1] == '"' -> { field.append('"'); i++ }
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == ',' -> { row.add(field.toString()); field.clear() }
                !inQuotes && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                    row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
