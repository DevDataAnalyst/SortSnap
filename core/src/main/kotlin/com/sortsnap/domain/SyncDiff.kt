package com.sortsnap.domain

/** Lightweight identity of one image, from either MediaStore or the Room index. */
data class MediaStamp(val id: Long, val dateModified: Long, val sizeBytes: Long)

data class SyncPlan(
    /** In MediaStore but not indexed yet. */
    val toInsert: List<Long>,
    /** Indexed but gone from MediaStore (deleted or trashed). */
    val toDelete: List<Long>,
    /** Present in both but the file changed (edited/cropped): refresh metadata and reclassify. */
    val toRefresh: List<Long>,
) {
    val isEmpty: Boolean get() = toInsert.isEmpty() && toDelete.isEmpty() && toRefresh.isEmpty()
}

/** Computes what the scanner must change to make the Room index match MediaStore. */
object SyncDiff {
    fun compute(mediaStore: Collection<MediaStamp>, index: Collection<MediaStamp>): SyncPlan {
        val indexed = HashMap<Long, MediaStamp>(index.size * 2)
        index.forEach { indexed[it.id] = it }
        val present = HashSet<Long>(mediaStore.size * 2)
        val insert = ArrayList<Long>()
        val refresh = ArrayList<Long>()
        for (m in mediaStore) {
            present += m.id
            val old = indexed[m.id]
            when {
                old == null -> insert += m.id
                old.dateModified != m.dateModified || old.sizeBytes != m.sizeBytes -> refresh += m.id
            }
        }
        val delete = index.asSequence().map { it.id }.filter { it !in present }.toList()
        return SyncPlan(insert.sorted(), delete.sorted(), refresh.sorted())
    }
}
