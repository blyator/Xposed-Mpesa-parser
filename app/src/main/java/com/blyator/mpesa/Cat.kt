package com.blyator.mpesa

/**
 * important for LSPosed
 *  - The SMS hook runs INSIDE the host app (Google Messages), not our own app.
 *  - A notification can only be cancelled by the app that posted it, so OUR app
 *    must be the poster. The host therefore only sends a wake broadcast
 *    (ACTION_SHOW) to ShowCategoryReceiver; everything else happens in our app.
 */
object Cat {
    const val PKG = "com.blyator.mpesa"

    const val ACTION_SHOW = "com.blyator.mpesa.action.SHOW_CATEGORIES"
    const val ACTION_PICK = "com.blyator.mpesa.action.PICK_CATEGORY"
    // Host fires this when n8n POST exhausts retries; QueueReceiver enqueues WorkManager job
    const val ACTION_QUEUE_TXN = "com.blyator.mpesa.action.QUEUE_TXN"

    // Extras (also used as WorkManager input data keys)
    const val EX_TXN = "txnId"
    const val EX_AMOUNT = "amount"
    const val EX_NAME = "name"
    const val EX_TS = "timestamp"
    const val EX_CATEGORY = "category"
    const val EX_NOTIF_ID = "notifId"
    const val WK_PAYLOAD = "payload"   // serialized txn JSON for TxnPostWorker

    // channel importance is locked at creation. DEFAULT (silenced) is used
    // so the status-bar icon actually shows — LOW gets its top-bar icon suppressed

    const val CHANNEL_ID = "mpesa_notifications_bar"
    const val CHANNEL_NAME = "M-PESA transactions"

    const val CATEGORY_WEBHOOK = "webhook" // insert server url

    // button layout id name, emoji+label, category value
    data class Category(val viewId: String, val label: String, val value: String)

    val CATEGORIES = listOf(
        Category("btn_food", "🥗 Food", "food"),
        Category("btn_transport", "🚗 Transport", "transport"),
        Category("btn_entertainment", "🎬 Fun", "entertainment"),
        Category("btn_other", "💸 Other", "other"),
    )

    // Stable per-transaction notification id. 
    fun notifId(txnId: String?, timestamp: Long): Int =
        (txnId ?: "ts$timestamp").hashCode()
}
