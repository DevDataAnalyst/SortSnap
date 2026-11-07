package com.sortsnap.classifier

/** Keyword and pattern detection over OCR text. Pure string logic, no ML. */
object TextAnalysis {

    val GREETING_KEYWORDS: List<String> = listOf(
        "good morning", "good night", "good evening", "good afternoon", "gud mrng",
        "happy birthday", "happy diwali", "happy holi", "happy new year", "happy independence",
        "happy anniversary", "eid mubarak", "merry christmas", "happy", "blessings", "blessed",
        "god bless", "have a nice day", "have a great day", "wishing you", "best wishes",
        "shubh", "suprabhat", "shubho sokal", "subho sokal", "shubho ratri", "jai shri",
        "quote", "life is", "motivation", "thought of the day", "sweet dreams",
    )

    val RECEIPT_KEYWORDS: List<String> = listOf(
        "total", "subtotal", "sub total", "gst", "gstin", "cgst", "sgst", "igst", "vat", "invoice",
        "receipt", "bill no", "bill", "amount", "qty", "quantity", "tax", "balance", "paid",
        "payment", "txn", "transaction", "upi", "order id", "pnr", "due date", "account no",
    )

    val UI_CHROME_WORDS: List<String> = listOf(
        "reply", "like", "share", "comment", "follow", "message", "type a message", "send",
        "online", "last seen", "search", "home", "settings", "notifications", "view all", "retweet",
        "subscribe",
    )

    private val CURRENCY_AMOUNT = Regex("""(₹|rs\.?|inr|\$|€|£)\s?\d[\d,]*(\.\d{1,2})?""", RegexOption.IGNORE_CASE)
    private val DATE = Regex("""\b\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}\b""")
    private val CLOCK = Regex("""\b([01]?\d|2[0-3]):[0-5]\d\b""")
    private val BATTERY = Regex("""\b\d{1,3}\s?%""")

    fun greetingHits(text: String): Int = countKeywords(text, GREETING_KEYWORDS)

    /** Receipt keywords plus currency amounts and dates (each pattern counts once). */
    fun receiptHits(text: String): Int {
        var hits = countKeywords(text, RECEIPT_KEYWORDS)
        if (CURRENCY_AMOUNT.containsMatchIn(text)) hits++
        if (DATE.containsMatchIn(text)) hits++
        return hits
    }

    /** UI words plus status-bar patterns such as a clock "10:42" or battery "85%". */
    fun uiChromeHits(text: String): Int {
        var hits = countKeywords(text, UI_CHROME_WORDS)
        if (CLOCK.containsMatchIn(text)) hits++
        if (BATTERY.containsMatchIn(text)) hits++
        return hits
    }

    /** Counts distinct keywords present as whole words / phrases (case-insensitive). */
    fun countKeywords(text: String, keywords: List<String>): Int {
        if (text.isBlank()) return 0
        val normalised = " " + text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ") + " "
        return keywords.count { normalised.contains(" $it ") }
    }
}
