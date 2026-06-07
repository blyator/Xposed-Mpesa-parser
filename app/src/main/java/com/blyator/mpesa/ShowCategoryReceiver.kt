package com.blyator.mpesa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Manifest receiver woken by the SMS hook (running in the host process) to post
 * the categorize notification as our app. Exported so the host's broadcast can
 * reach it
 */
class ShowCategoryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != Cat.ACTION_SHOW) return
            val txnId = intent.getStringExtra(Cat.EX_TXN)
            val amount = intent.getDoubleExtra(Cat.EX_AMOUNT, 0.0)
            val name = intent.getStringExtra(Cat.EX_NAME) ?: "M-PESA"
            val ts = intent.getLongExtra(Cat.EX_TS, System.currentTimeMillis())
            val ctx = context.applicationContext

            val suggested = Suggester.suggest(name)
            if (suggested != null) autoPost(ctx, txnId, suggested, ts)

            NotificationHelper.show(ctx, txnId, amount, name, ts, suggested)
        } catch (t: Throwable) {
            Log.e(HttpClient.TAG, "ShowCategoryReceiver error: ${t.message}")
        }
    }

    // High-confidence merchant match: post the category right away, same path as
    // a manual button tap. Notification still shows so the user can override.
    private fun autoPost(ctx: Context, txnId: String?, category: String, ts: Long) {
        val svc = Intent(ctx, CategoryPostService::class.java).apply {
            putExtra(Cat.EX_TXN, txnId)
            putExtra(Cat.EX_CATEGORY, category)
            putExtra(Cat.EX_TS, ts)
        }
        try {
            ctx.startForegroundService(svc)
        } catch (t: Throwable) {
            Log.e(HttpClient.TAG, "autoPost startForegroundService failed: ${t.message}")
        }
    }
}
