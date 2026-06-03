package com.blyator.mpesa

import android.content.Intent
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Posts parsed M-PESA txns to the n8n webhook with bounded retry + backoff. All failures are logged,
 * never thrown.
 */
object N8nClient {

    private const val WEBHOOK_URL = "webhook url here" // insert server url
    private const val MAX_RETRIES = 3

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // Single background thread — SMS volume is tiny;
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mpesa-n8n").apply { isDaemon = true }
    }


    private val client: OkHttpClient by lazy { buildInsecureClient() }

    fun send(txn: MpesaTxn) {
        executor.execute {
            val payload = toJson(txn)
            for (attempt in 1..MAX_RETRIES) {
                if (post(payload, attempt)) return@execute
                // backoff: 1s, 2s, 4s
                try {
                    Thread.sleep(1000L * (1 shl (attempt - 1)))
                } catch (_: InterruptedException) {
                    return@execute
                }
            }
            Log.e(SMSHook.TAG, "n8n POST gave up after $MAX_RETRIES attempts — queuing for retry")
            queueForRetry(payload)
        }
    }

    private fun queueForRetry(payload: String) {
        try {
            val ctx = android.app.AndroidAppHelper.currentApplication() ?: return
            val intent = Intent(Cat.ACTION_QUEUE_TXN).apply {
                setPackage(Cat.PKG)
                putExtra(Cat.WK_PAYLOAD, payload)
            }
            ctx.sendBroadcast(intent)
        } catch (t: Throwable) {
            Log.e(SMSHook.TAG, "queueForRetry broadcast failed: ${t.message}")
        }
    }

    /** Single attempt — used by TxnPostWorker (WorkManager handles its own retry). */
    internal fun postOnce(payload: String): Boolean = post(payload, 1)

    private fun post(payload: String, attempt: Int): Boolean {
        return try {
            val req = Request.Builder()
                .url(WEBHOOK_URL)
                .post(payload.toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Log.i(SMSHook.TAG, "n8n POST ok (${resp.code})")
                    true
                } else {
                    Log.w(SMSHook.TAG, "n8n POST http ${resp.code} (attempt $attempt)")
                    false
                }
            }
        } catch (t: Throwable) {
            Log.w(SMSHook.TAG, "n8n POST failed attempt $attempt: ${t.message}")
            false
        }
    }

    private fun toJson(t: MpesaTxn): String {
        val o = JSONObject()
        // putOpt drops null keys cleanly
        o.putOpt("amount", t.amount)
        o.put("currency", t.currency)
        o.put("type", t.type)
        o.put("direction", t.direction)
        o.putOpt("phone", t.phone)
        o.putOpt("name", t.name)
        o.putOpt("balance", t.balance)
        o.putOpt("txnId", t.txnId)
        o.put("timestamp", t.timestamp)
        o.put("rawMessage", t.rawMessage)
        return o.toString()
    }

    private fun buildInsecureClient(): OkHttpClient {
        return try {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
            val ssl = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustAll), java.security.SecureRandom())
            }
            OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, trustAll)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        } catch (t: Throwable) {
            Log.e(SMSHook.TAG, "TLS setup failed, using default client: ${t.message}")
            OkHttpClient()
        }
    }
}
