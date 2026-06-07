package com.blyator.mpesa

/**
 * Static merchant-name → category rules for high-confidence auto-tagging.
 * Matched against the recipient/sender name (case-insensitive substring).
 * First match wins. Returns null when nothing matches → user picks manually.
 */
object Suggester {

    private val RULES: List<Pair<String, String>> = listOf(
        "SAFARICOM DATA BUNDLES" to "bundles",
        "AIRTEL MONEY" to "bundles",
        "KPLC" to "bills",
    )

    fun suggest(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val upper = name.uppercase()
        for ((pattern, category) in RULES) {
            if (upper.contains(pattern)) return category
        }
        return null
    }
}
