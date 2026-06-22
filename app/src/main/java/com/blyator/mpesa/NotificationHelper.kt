package com.blyator.mpesa

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

/**
 * Builds and posts the "categorize this transaction" notification with a custom
 * RemoteViews layout (standard notifications cap at 3 action buttons; we want 4,
 * so a custom view is required). Each button fires a broadcast to
 * CategoryActionReceiver in THIS app, which POSTs the category and self-cancels.
 */
object NotificationHelper {

    fun show(
        ctx: Context, txnId: String?, amount: Double, name: String, timestamp: Long,
        suggested: String? = null
    ) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ensureChannel(nm)

            val notifId = Cat.notifId(txnId, timestamp)

            val rv = RemoteViews(ctx.packageName, R.layout.notification_categories)
            val amountTxt = if (amount > 0) "Ksh%,.2f".format(amount) else "M-PESA"
            val titleTxt = if (suggested != null) "✓ $amountTxt · $name (auto)" else "$amountTxt · $name"
            rv.setTextViewText(R.id.notif_title, titleTxt)

            // Wire each button to a unique PendingIntent broadcast. The suggested
            // category (already auto-posted) gets a checkmark; tapping any button,
            // including a different one, still works and overrides the auto pick.
            for (c in Cat.CATEGORIES) {
                val viewId = ctx.resources.getIdentifier(c.viewId, "id", ctx.packageName)
                if (viewId == 0) continue
                val label = if (c.value == suggested) "✓ ${c.label}" else c.label
                rv.setTextViewText(viewId, label)
                rv.setOnClickPendingIntent(viewId, pickIntent(ctx, txnId, c.value, timestamp, notifId))
            }

            val builder = NotificationCompat.Builder(ctx, Cat.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_mpesa)
                .setContentTitle(titleTxt)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(rv)
                .setCustomBigContentView(rv)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                // DEFAULT priority keeps the status-bar icon; setSilent kills sound +
                // vibration regardless of the channel (NotificationCompat, API 24+).
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSilent(true)

            nm.notify(notifId, builder.build())
            Log.i(HttpClient.TAG, "notification posted id=$notifId txn=$txnId")
        } catch (t: Throwable) {
            Log.e(HttpClient.TAG, "show() failed: ${t.message}")
        }
    }

    private fun pickIntent(
        ctx: Context, txnId: String?, category: String, timestamp: Long, notifId: Int
    ): PendingIntent {
        val intent = Intent(Cat.ACTION_PICK).apply {
            setClassName(Cat.PKG, "${Cat.PKG}.CategoryActionReceiver")
            setPackage(Cat.PKG)
            putExtra(Cat.EX_TXN, txnId)
            putExtra(Cat.EX_CATEGORY, category)
            putExtra(Cat.EX_TS, timestamp)
            putExtra(Cat.EX_NOTIF_ID, notifId)
        }
        // requestCode unique per (txn, category) and the timestamp so distinct txns with a null txnId don't collapse to the same code.
        val reqCode = ((txnId ?: "ts$timestamp") + "|" + category).hashCode()
        return PendingIntent.getBroadcast(
            ctx, reqCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(Cat.CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            // DEFAULT keeps the status-bar icon visible (LOW gets it hidden on some
            Cat.CHANNEL_ID, Cat.CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "M-PESA transaction categorization"
            setShowBadge(true)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        nm.createNotificationChannel(ch)
    }
}
