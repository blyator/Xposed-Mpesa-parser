package com.blyator.mpesa

import android.content.Context
import android.content.Intent
import de.robv.android.xposed.XposedBridge

/**
 * Runs INSIDE the host process (called from SMSHook). Fires an explicit broadcast
 * to our own app's ShowCategoryReceiver so our app posts the notification. We
 * never post the notification from here, because the host can't cancel it later.
 *
 * Only references framework + string class names — no app-only classes are
 * touched in the host process.
 */
object CategoryNotifier {

    fun trigger(txn: MpesaTxn) {
        try {
            // Incoming money needs no spend category — just gets recorded with direction="in".
            if (txn.direction == "in") return
            val ctx: Context = android.app.AndroidAppHelper.currentApplication() ?: return
            // Send by ACTION (not explicit class) + setPackage: this resolves via
            // ShowCategoryReceiver's intent-filter, which grants the host visibility
            // of our package and lets the broadcast through AppsFilter.
            val intent = Intent(Cat.ACTION_SHOW).apply {
                setPackage(Cat.PKG)
                putExtra(Cat.EX_TXN, txn.txnId)
                putExtra(Cat.EX_AMOUNT, txn.amount ?: 0.0)
                putExtra(Cat.EX_NAME, txn.name ?: txn.phone ?: "M-PESA")
                putExtra(Cat.EX_TS, txn.timestamp)
            }
            ctx.sendBroadcast(intent)
        } catch (t: Throwable) {
            XposedBridge.log("[MPESA] CategoryNotifier failed: $t")
        }
    }
}
