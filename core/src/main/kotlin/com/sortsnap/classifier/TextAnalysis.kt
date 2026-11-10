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
        // v3: more Indian festivals and devotional greetings (transliterated: OCR model is Latin-only).
        "good day", "good luck", "congratulations", "congrats", "stay blessed", "keep smiling", "positive vibes",
        "never give up", "believe in yourself", "radhe radhe", "ram ram", "jai mata di", "jai hanuman",
        "har har mahadev", "om namah shivaya", "namaste", "navratri", "dussehra", "ganesh chaturthi",
        "raksha bandhan", "rakhi", "pongal", "onam", "lohri", "durga puja", "bhai dooj", "karwa chauth",
        "janmashtami", "makar sankranti", "guru purnima", "ramadan kareem", "season s greetings",
    )

    /** Non-receipt paperwork: notes, IDs, tickets, medical, school. */
    val DOCUMENT_KEYWORDS: List<String> = listOf(
        "notes", "meeting", "agenda", "minutes", "chapter", "page", "section", "signature", "signed",
        "address", "date of birth", "dob", "gender", "aadhaar", "aadhar", "passport", "licence", "license",
        "certificate", "government", "boarding pass", "ticket", "seat", "gate", "flight", "departure",
        "arrival", "prescription", "patient", "diagnosis", "report", "roll no", "marks", "syllabus",
        "assignment", "homework", "exam", "to do", "todo", "checklist", "warranty", "policy no", "ifsc",
    )

    /** Phrases that are almost only used in memes. */
    val MEME_PHRASES: List<String> = listOf(
        "be like", "when you", "when your", "that feeling when", "how it started", "how it s going", "pov",
        "tfw", "mfw", "lol", "lmao", "rofl", "nobody", "me after", "me before", "expectation", "reality",
        "starter pack", "plot twist", "change my mind", "no one", "my brain", "meanwhile",
    )

    val SOCIAL_WATERMARKS: List<String> = listOf(
        "instagram", "9gag", "reddit", "tiktok", "imgflip", "ifunny", "sharechat", "memes", "meme", "moj",
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
    private val HANDLE = Regex("""(^|\s)@[A-Za-z0-9_.]{3,}""")
    private val LIST_LINE = Regex("""^\s*([-•*·▪●]|\d{1,2}[.)]|\[[ xX]?])\s*\S""")

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

    fun documentHits(text: String): Int = countKeywords(text, DOCUMENT_KEYWORDS)

    fun memePhraseHits(text: String): Int = countKeywords(text, MEME_PHRASES)

    /** Social-media handles ("@funny_memes") or meme-site watermarks. */
    fun socialWatermarkHits(text: String): Int =
        countKeywords(text, SOCIAL_WATERMARKS) + if (HANDLE.containsMatchIn(text)) 1 else 0

    /** Lines that look like bullets, numbered items or checkboxes. */
    fun listLines(text: String): Int = text.lineSequence().count { LIST_LINE.containsMatchIn(it) }

    /** Counts distinct keywords present as whole words / phrases (case-insensitive). */
    fun countKeywords(text: String, keywords: List<String>): Int {
        if (text.isBlank()) return 0
        val normalised = " " + text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ") + " "
        return keywords.count { normalised.contains(" $it ") }
    }
}
