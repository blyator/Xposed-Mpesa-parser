package com.blyator.mpesa

/**
 * Result of parsing one M-PESA SMS. Any field may be null when not present /
 */
data class MpesaTxn(
    val amount: Double?,
    val currency: String,
    val type: String,
    val direction: String,   // "out" = spend, "in" = money received
    val phone: String?,
    val name: String?,
    val balance: Double?,
    val txnId: String?,
    val timestamp: Long,
    val rawMessage: String
)

/**
 * Regex-based extractor for Safaricom M-PESA confirmation SMS.
 */
object MPesaParser {

    // e.g. "SED4XYZ123"
    private val RE_TXN_ID = Regex("\\b([A-Z0-9]{10})\\b")

    // "Ksh1,000.00" / "KES 500" / "KSH 1,234.56"
    private val RE_MONEY = Regex("(?:Ksh|KES|KSH)\\s?\\.?\\s?([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)

    // "New M-PESA balance is Ksh5,000.00" / "balance is Ksh..."
    private val RE_BALANCE = Regex("balance(?:\\s+is)?\\s*(?:Ksh|KES|KSH)\\s?\\.?\\s?([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)

    // +2547XXXXXXXX / 07XXXXXXXX / 01XXXXXXXX / 7XXXXXXXX
    private val RE_PHONE = Regex("(\\+?254\\d{9}|0[17]\\d{8}|\\b[17]\\d{8}\\b)")

    // "from Billy 0712345678" / "to yator 0722..." — capture a name between keyword and number
    private val RE_NAME = Regex("(?:from|to|by)\\s+([A-Za-z][A-Za-z .'-]{1,40}?)\\s+(?:\\+?254\\d{9}|0[17]\\d{8})", RegexOption.IGNORE_CASE)

    fun parse(body: String, sender: String, timestamp: Long): MpesaTxn {
        val type = classify(body)

        // Amount = first money token that is NOT the balance figure.
        val balance = RE_BALANCE.find(body)?.groupValues?.get(1)?.let(::toAmount)
        val amount = firstAmountExcludingBalance(body, balance)

        val phone = RE_PHONE.find(body)?.value?.let(::normalizePhone)
        val name = RE_NAME.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('.')
        val txnId = RE_TXN_ID.find(body)?.groupValues?.get(1)

        return MpesaTxn(
            amount = amount,
            currency = "KES",
            type = type,
            direction = directionOf(type),
            phone = phone,
            name = name,
            balance = balance,
            txnId = txnId,
            timestamp = timestamp,
            rawMessage = body
        )
    }

    // Maps a transaction type to spend direction. Unknown defaults to "out" so a
    // genuine spend is never silently dropped from categorization.
    private fun directionOf(type: String): String = when (type) {
        "receive", "deposit", "reversal" -> "in"
        else -> "out"   // send, withdraw, paybill, buygoods, airtime, fuliza, unknown
    }

    private fun classify(body: String): String {
        // Drop M-PESA's trailing marketing taglines ("Buy goods with M-PESA.",
        // "Download My OneApp...") so promo wording never drives classification.
        val b = body.lowercase()
            .substringBefore("buy goods with m-pesa")
            .substringBefore("download")
            .substringBefore("dial *")

        return when {
            // Directional verbs first — they describe the actual transaction and
            // must win over noun-based matches elsewhere in the text.
            b.contains("you have received") || b.contains("you received") -> "receive"
            b.contains("sent to") || b.contains("you have sent") || b.contains("you sent") -> "send"
            b.contains("withdraw") -> "withdraw"
            b.contains("give") && b.contains("deposit") -> "deposit"
            b.contains("airtime") -> "airtime"
            // Paybill carries an account number; buy-goods/till does not.
            b.contains("pay bill") || b.contains("paybill") ||
                (b.contains("paid to") && b.contains("account")) -> "paybill"
            b.contains("paid to") || b.contains("bought goods") || b.contains("till") -> "buygoods"
            b.contains("reversal") || b.contains("reversed") -> "reversal"
            b.contains("fuliza") -> "fuliza"
            b.contains("deposit") -> "deposit"
            else -> "unknown"
        }
    }

    /** First money figure in body that isn't equal to the parsed balance. */
    private fun firstAmountExcludingBalance(body: String, balance: Double?): Double? {
        for (m in RE_MONEY.findAll(body)) {
            val v = toAmount(m.groupValues[1]) ?: continue
            if (balance != null && v == balance) continue
            return v
        }
        // fallback: first money token even if it equals balance
        return RE_MONEY.find(body)?.groupValues?.get(1)?.let(::toAmount)
    }

    private fun toAmount(raw: String): Double? =
        raw.replace(",", "").toDoubleOrNull()

    /** Normalize +2547XXXXXXXX where possible. */
    private fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("254") -> "+$digits"
            digits.startsWith("0") && digits.length == 10 -> "+254" + digits.substring(1)
            digits.length == 9 -> "+254$digits"
            else -> raw
        }
    }
}
