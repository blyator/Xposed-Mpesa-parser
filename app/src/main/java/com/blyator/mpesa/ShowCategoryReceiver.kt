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
            NotificationHelper.show(context.applicationContext, txnId, amount, name, ts)
        } catch (t: Throwable) {
            Log.e(HttpClient.TAG, "ShowCategoryReceiver error: ${t.message}")
        }
    }
}
