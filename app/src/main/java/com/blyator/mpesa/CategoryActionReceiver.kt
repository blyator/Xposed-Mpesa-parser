package com.blyator.mpesa

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Manifest receiver for category-button taps. Runs in OUR app's process, so it
 * can both (a) cancel the notification it posted and (b) hit the network.
 * Not exported — only our own PendingIntents target it.
 */
class CategoryActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Cat.ACTION_PICK) return

        val txnId = intent.getStringExtra(Cat.EX_TXN)
        val category = intent.getStringExtra(Cat.EX_CATEGORY) ?: "other"
        val timestamp = intent.getLongExtra(Cat.EX_TS, System.currentTimeMillis())
        val notifId = intent.getIntExtra(Cat.EX_NOTIF_ID, Cat.notifId(txnId, timestamp))

        // Dismiss immediately (we posted it, so we can cancel it).
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
        } catch (t: Throwable) {
            Log.w(HttpClient.TAG, "cancel failed: ${t.message}")
        }

        // Don't POST from this receiver: our app's background process is network-
        // restricted (RESTRICTED
        // grants a brief allowlist that lets us start a foreground service, which gets
        // network even from a restricted app. The service does the actual POST.
        val svc = Intent(context, CategoryPostService::class.java).apply {
            putExtra(Cat.EX_TXN, txnId)
            putExtra(Cat.EX_CATEGORY, category)
            putExtra(Cat.EX_TS, timestamp)
        }
        try {
            context.startForegroundService(svc)
        } catch (t: Throwable) {
            Log.e(HttpClient.TAG, "startForegroundService failed: ${t.message}")
        }
    }
}
