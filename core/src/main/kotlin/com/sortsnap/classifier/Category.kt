package com.sortsnap.classifier

/** The single primary category every image is assigned to. */
enum class Category {
    PHOTO,
    SCREENSHOT,
    MEME,
    FORWARD,
    DOCUMENT,

    /** Low-confidence bucket. Never auto-hidden from the main gallery. */
    OTHER;

    val isClutter: Boolean get() = this in CLUTTER

    companion object {
        /** Categories hidden by the "Hide clutter from main gallery" setting. OTHER is deliberately absent. */
        val CLUTTER: Set<Category> = setOf(SCREENSHOT, MEME, FORWARD, DOCUMENT)

        /** Categories a classifier can actually score (OTHER is only ever a fallback). */
        val SCORABLE: List<Category> = listOf(PHOTO, SCREENSHOT, MEME, FORWARD, DOCUMENT)

        fun fromNameOrNull(name: String?): Category? = entries.firstOrNull { it.name == name }
    }
}
