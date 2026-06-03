package com.blyator.mpesa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * when n8n POST exhausts its quick retries. Enqueues a WorkManager job constrained to
 * NetworkType.CONNECTED so the txn is delivered once the device comes online.
 */
class QueueReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Cat.ACTION_QUEUE_TXN) return
        val payload = intent.getStringExtra(Cat.WK_PAYLOAD) ?: run {
            Log.w(SMSHook.TAG, "QueueReceiver: missing payload")
            return
        }

        val work = OneTimeWorkRequestBuilder<TxnPostWorker>()
            .setInputData(workDataOf(Cat.WK_PAYLOAD to payload))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueue(work)
        Log.i(SMSHook.TAG, "QueueReceiver: txn queued for retry when online")
    }
}
