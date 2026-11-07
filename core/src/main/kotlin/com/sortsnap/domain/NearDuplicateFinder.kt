package com.sortsnap.domain

import com.sortsnap.classifier.ImageMath

/**
 * Groups images whose 64-bit perceptual hashes are within [maxDistance] bits.
 *
 * Uses multi-index hashing: split the hash into maxDistance + 1 chunks; by the pigeonhole principle
 * two hashes within the distance share at least one identical chunk, so we only compare items that
 * share a chunk bucket. Near-linear on real libraries instead of O(n²).
 */
object NearDuplicateFinder {

    fun groups(items: List<Pair<Long, Long>>, maxDistance: Int = 4): List<List<Long>> {
        require(maxDistance in 0..15) { "maxDistance must be 0..15" }
        if (items.size < 2) return emptyList()
        val chunks = maxDistance + 1
        val ranges = chunkRanges(chunks)
        val parent = IntArray(items.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }
            return r
        }
        fun union(a: Int, b: Int) { val ra = find(a); val rb = find(b); if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb) }

        for ((start, len) in ranges) {
            val mask = if (len == 64) -1L else (1L shl len) - 1
            val buckets = HashMap<Long, MutableList<Int>>()
            items.forEachIndexed { i, (_, hash) -> buckets.getOrPut((hash ushr start) and mask) { mutableListOf() } += i }
            for (members in buckets.values) {
                if (members.size < 2) continue
                for (a in 0 until members.size - 1) for (b in a + 1 until members.size) {
                    val i = members[a]; val j = members[b]
                    if (find(i) != find(j) && ImageMath.hamming(items[i].second, items[j].second) <= maxDistance) union(i, j)
                }
            }
        }
        return items.indices.groupBy { find(it) }.values
            .filter { it.size > 1 }
            .map { idx -> idx.map { items[it].first }.sorted() }
            .sortedBy { it.first() }
    }

    internal fun chunkRanges(chunks: Int): List<Pair<Int, Int>> {
        val base = 64 / chunks
        val extra = 64 % chunks
        var start = 0
        return (0 until chunks).map { i ->
            val len = base + if (i < extra) 1 else 0
            (start to len).also { start += len }
        }
    }
}
