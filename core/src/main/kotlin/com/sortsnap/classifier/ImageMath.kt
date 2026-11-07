package com.sortsnap.classifier

/** Pixel math on luminance arrays (0..255, row-major). Pure Kotlin so it is unit-testable. */
object ImageMath {

    /** Area-average resample of a grayscale image to [outW] x [outH]. */
    fun resize(gray: IntArray, width: Int, height: Int, outW: Int, outH: Int): IntArray {
        require(gray.size == width * height) { "gray size ${gray.size} != $width x $height" }
        val out = IntArray(outW * outH)
        for (oy in 0 until outH) {
            val y0 = oy * height / outH
            val y1 = maxOf(y0 + 1, (oy + 1) * height / outH)
            for (ox in 0 until outW) {
                val x0 = ox * width / outW
                val x1 = maxOf(x0 + 1, (ox + 1) * width / outW)
                var sum = 0L
                var n = 0
                for (y in y0 until minOf(y1, height)) for (x in x0 until minOf(x1, width)) {
                    sum += gray[y * width + x]; n++
                }
                out[oy * outW + ox] = if (n == 0) 0 else (sum / n).toInt()
            }
        }
        return out
    }

    /**
     * 64-bit difference hash: resize to 9x8 and compare horizontally adjacent pixels.
     * Robust to scaling and re-compression, so it finds re-saved and forwarded copies.
     */
    fun dHash(gray: IntArray, width: Int, height: Int): Long {
        val small = resize(gray, width, height, 9, 8)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            if (small[y * 9 + x] > small[y * 9 + x + 1]) hash = hash or (1L shl bit)
            bit++
        }
        return hash
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Variance of the 4-neighbour Laplacian. Low values mean few edges, i.e. blur. */
    fun laplacianVariance(gray: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val i = y * width + x
            val lap = (gray[i - 1] + gray[i + 1] + gray[i - width] + gray[i + width] - 4 * gray[i]).toDouble()
            sum += lap; sumSq += lap * lap; n++
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    fun visualFeatures(gray: IntArray): VisualFeatures {
        if (gray.isEmpty()) return VisualFeatures(0f, 0f)
        val bins = IntArray(16)
        var sum = 0L
        for (v in gray) {
            val c = v.coerceIn(0, 255)
            sum += c
            bins[c / 16]++
        }
        return VisualFeatures(
            meanBrightness = (sum.toDouble() / gray.size / 255.0).toFloat(),
            backgroundUniformity = bins.max().toFloat() / gray.size,
        )
    }

    /** Rec. 601 luma from packed ARGB. */
    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }
}
