package com.sortsnap.data.db

import com.sortsnap.classifier.Category

data class StampRow(val mediaId: Long, val dateModified: Long, val size: Long)

data class CategoryStatRow(val category: Category, val count: Int, val totalSize: Long, val coverUri: String?)

data class SummaryRow(val count: Int, val totalSize: Long?)

data class HashRow(val mediaId: Long, val contentHash: Long)

data class TargetRow(val mediaId: Long, val uri: String, val size: Long)
