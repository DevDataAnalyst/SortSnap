package com.sortsnap.classifier

/** Turns stored evidence into the plain-English "Why?" list shown in the details sheet. */
object SignalExplainer {

    fun explain(signal: Signal): String = when (signal.id) {
        ScreenshotFolderRule.id -> "Saved in a Screenshots folder"
        ScreenshotFilenameRule.id -> "File name starts with \"Screenshot\""
        ScreenshotDimensionRule.DEVICE_ID -> "Exactly the size of this phone's screen"
        ScreenshotDimensionRule.COMMON_ID -> "Same size as a common phone screen"
        PngMimeRule.id -> "Saved as PNG, typical for screenshots"
        NoCameraExifRule.id -> "No camera information in the file"
        ChatAppRule.id -> "Saved in ${signal.detail ?: "a chat app"} folder"
        CameraFolderRule.id -> "Saved in the camera folder"
        CameraExifRule.id -> signal.detail?.let { "Taken with a camera ($it)" } ?: "Has camera information"
        FocalLengthRule.id -> "Has lens focal length, so it came from a camera"
        GpsRule.id -> "Has a GPS location"
        DownloadsRule.id -> "Saved from ${signal.detail ?: "a download"}"
        MemeCaptionRule.id -> "Text at top and bottom"
        MemeCaptionRule.SINGLE_ID -> "Short caption at the top or bottom"
        MemeLabelRule.id -> "Looks like ${signal.detail?.lowercase()?.let { "a $it" } ?: "a cartoon or poster"}"
        GreetingRule.id -> "Contains greeting or quote words"
        ReceiptKeywordRule.id -> "Contains receipt or bill words (total, GST, invoice…)"
        DocumentLayoutRule.id -> "Lots of text on a plain light background"
        UiChromeRule.id -> "Shows app buttons or a status bar"
        PhotoLabelRule.id -> "Contains ${signal.detail?.lowercase() ?: "people or scenery"} with little text"
        HeavyTextRule.id -> "Mostly text, unlike a camera photo"
        SortSnapClassifier.FOLDER_PRIOR_ID -> "You often sort the \"${signal.detail ?: "this"}\" folder this way"
        else -> signal.id.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /**
     * Only signals that pushed towards the chosen [category] are useful to the user; penalties and
     * evidence for other categories are dropped. Strongest evidence first.
     */
    fun explain(signals: List<Signal>, category: Category): List<String> =
        signals.filter { (it.scores[category] ?: 0f) > 0f }
            .sortedByDescending { it.scores[category] ?: 0f }
            .map(::explain)
            .distinct()
}
