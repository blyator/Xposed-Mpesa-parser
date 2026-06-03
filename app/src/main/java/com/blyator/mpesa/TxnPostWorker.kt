package com.blyator.mpesa

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager worker that retries a failed n8n txn POST when network is available.
 * Enqueued by QueueReceiver after N8nClient exhausts its quick retries offline.
 */
class TxnPostWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val payload = inputData.getString(Cat.WK_PAYLOAD)
            ?: return Result.failure()
        return if (N8nClient.postOnce(payload)) {
            Log.i(SMSHook.TAG, "TxnPostWorker: queued txn posted ok")
            Result.success()
        } else {
            Log.w(SMSHook.TAG, "TxnPostWorker: post failed, will retry (run ${runAttemptCount + 1})")
            Result.retry()
        }
    }
}
