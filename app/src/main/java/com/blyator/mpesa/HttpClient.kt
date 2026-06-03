package com.blyator.mpesa

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Posts category updates to the n8n category webhook. Runs in OUR app's process
 * (called from CategoryActionReceiver), so it uses a plain HTTPS client — the
 * endpoint has a valid public cert, no trust-all needed.
 */
object HttpClient {

    const val TAG = "MPESA_CAT"
    private const val MAX_RETRIES = 3

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Blocking — call on a background thread. Returns true on 2xx. */
    fun postCategory(txnId: String?, category: String, timestamp: Long): Boolean {
        val payload = JSONObject().apply {
            putOpt("txnId", txnId)
            put("category", category)
            put("timestamp", timestamp)
        }.toString()

        for (attempt in 1..MAX_RETRIES) {
            try {
                val req = Request.Builder()
                    .url(Cat.CATEGORY_WEBHOOK)
                    .post(payload.toRequestBody(JSON))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Log.i(TAG, "category POST ok (${resp.code}) $category txn=$txnId")
                        return true
                    }
                    Log.w(TAG, "category POST http ${resp.code} (attempt $attempt)")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "category POST failed attempt $attempt: ${t.message}")
            }
            // backoff 1s, 2s, 4s
            try {
                Thread.sleep(1000L * (1 shl (attempt - 1)))
            } catch (_: InterruptedException) {
                return false
            }
        }
        Log.e(TAG, "category POST gave up after $MAX_RETRIES attempts")
        return false
    }
}
