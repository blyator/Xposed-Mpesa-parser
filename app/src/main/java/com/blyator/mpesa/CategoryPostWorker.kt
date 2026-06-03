package com.blyator.mpesa

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager worker that retries a failed category POST when network is available.
 * Enqueued by CategoryPostService when the foreground-service attempt fails offline.
 */
class CategoryPostWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val txnId = inputData.getString(Cat.EX_TXN)
        val category = inputData.getString(Cat.EX_CATEGORY) ?: "other"
        val timestamp = inputData.getLong(Cat.EX_TS, System.currentTimeMillis())

        return if (HttpClient.postCategory(txnId, category, timestamp)) {
            Log.i(HttpClient.TAG, "CategoryPostWorker: queued category posted ok txn=$txnId")
            Result.success()
        } else {
            Log.w(HttpClient.TAG, "CategoryPostWorker: post failed, will retry (run ${runAttemptCount + 1})")
            Result.retry()
        }
    }
}
