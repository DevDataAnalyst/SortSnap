package com.sortsnap.classifier

/** Folder/path matching helpers shared by rules. Everything is matched case-insensitively. */
internal object FolderMatchers {

    val CHAT_APPS: List<Pair<String, String>> = listOf(
        "whatsapp" to "WhatsApp",
        "telegram" to "Telegram",
        "instagram" to "Instagram",
        "messenger" to "Messenger",
        "snapchat" to "Snapchat",
    )

    /** Chat-app sub-folders that are only meaningful as whole path segments. */
    val CHAT_SEGMENTS: List<Pair<String, String>> = listOf("sent" to "Sent", "received" to "Received")

    val SOCIAL_SAVE_FOLDERS: List<Pair<String, String>> = listOf(
        "download" to "Download",
        "pictures/instagram" to "Instagram",
        "pictures/twitter" to "Twitter",
        "twitter" to "Twitter",
        "reddit" to "Reddit",
        "pinterest" to "Pinterest",
        "9gag" to "9GAG",
    )

    private val CAMERA_FOLDER = Regex("""(^|/)dcim/(camera|\d{3}[a-z_]*)(/|$)""")

    fun segments(f: ImageFeatures): List<String> = f.folderHaystack.split('/').filter { it.isNotBlank() }

    fun isScreenshotFolder(f: ImageFeatures): Boolean =
        f.folderHaystack.contains("screenshot") || f.folderHaystack.contains("screen_capture") ||
            f.folderHaystack.contains("screencap")

    fun isCameraFolder(f: ImageFeatures): Boolean = CAMERA_FOLDER.containsMatchIn(f.folderHaystack) ||
        (f.bucket.equals("camera", ignoreCase = true) && f.relativePath.startsWith("DCIM", ignoreCase = true))

    fun chatApp(f: ImageFeatures): String? {
        CHAT_APPS.firstOrNull { f.folderHaystack.contains(it.first) }?.let { return it.second }
        val segs = segments(f)
        return CHAT_SEGMENTS.firstOrNull { it.first in segs }?.second
    }

    fun socialSave(f: ImageFeatures): String? =
        SOCIAL_SAVE_FOLDERS.firstOrNull { f.folderHaystack.contains(it.first) }?.second
}
