package com.sortsnap.ui.components

import androidx.annotation.StringRes
import com.sortsnap.R
import com.sortsnap.classifier.Category

@StringRes
fun Category.pluralLabel(): Int = when (this) {
    Category.PHOTO -> R.string.category_photo
    Category.SCREENSHOT -> R.string.category_screenshot
    Category.MEME -> R.string.category_meme
    Category.FORWARD -> R.string.category_forward
    Category.DOCUMENT -> R.string.category_document
    Category.OTHER -> R.string.category_other
}

@StringRes
fun Category.singleLabel(): Int = when (this) {
    Category.PHOTO -> R.string.category_photo_single
    Category.SCREENSHOT -> R.string.category_screenshot_single
    Category.MEME -> R.string.category_meme_single
    Category.FORWARD -> R.string.category_forward_single
    Category.DOCUMENT -> R.string.category_document_single
    Category.OTHER -> R.string.category_other_single
}
