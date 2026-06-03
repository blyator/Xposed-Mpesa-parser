package com.blyator.mpesa

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Short-lived foreground service that performs the category POST. Started from
 * CategoryActionReceiver on a button tap. A foreground service gets network even
 * when our app's background process is otherwise network-restricted 
 */
class CategoryPostService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground promptly after a startForegroundService().
        startForegroundCompat()

        val txnId = intent?.getStringExtra(Cat.EX_TXN)
        val category = intent?.getStringExtra(Cat.EX_CATEGORY) ?: "other"
        val timestamp = intent?.getLongExtra(Cat.EX_TS, System.currentTimeMillis())
            ?: System.currentTimeMillis()

        Thread {
            try {
                HttpClient.postCategory(txnId, category, timestamp)
            } catch (t: Throwable) {
                Log.e(HttpClient.TAG, "service post crashed: ${t.message}")
            } finally {
                stopForegroundCompat()
                stopSelf(startId)
            }
        }.apply { isDaemon = true }.start()

        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        ensureChannel()
        val n = NotificationCompat.Builder(this, FGS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mpesa)
            .setContentTitle("Saving category…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FGS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FGS_ID, n)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(FGS_CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(FGS_CHANNEL, "Saving", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val FGS_CHANNEL = "mpesa_fgs"
        private const val FGS_ID = 4711
    }
}
