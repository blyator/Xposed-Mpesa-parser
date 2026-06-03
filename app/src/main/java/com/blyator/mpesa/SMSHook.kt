package com.blyator.mpesa

import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Hooks BroadcastReceiver.onReceive and watches for inbound SMS broadcasts.
 * Reads the message non-destructively (we do NOT call setResultData / abort —
 * the user still sees the SMS normally). M-PESA messages are parsed and POSTed.
 */
object SMSHook {

    const val TAG = "MPESA"

    // Only SMS_DELIVER: it is delivered exactly ONCE, to the default SMS app's
    // main process. SMS_RECEIVED, by contrast, fans out to every app holding the
    // permission (and to side processes like :rcs), causing duplicate POSTs that
    // per-process dedup cannot catch. DELIVER alone = one capture per message.
    private val SMS_ACTIONS = setOf(
        Telephony.Sms.Intents.SMS_DELIVER_ACTION     // default SMS app, single delivery
    )

    // Dedup guard: belt-and-suspenders within a single process.
    private val recentHashes = object : LinkedHashMap<Int, Long>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Long>?): Boolean = size > 64
    }

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        // BroadcastReceiver.onReceive is abstract -> not hookable directly.
        // Two delivery paths exist, depending on how the receiver is registered:
        //   1. Manifest (static) receivers  -> ActivityThread.handleReceiver(ReceiverData)
        //   2. Dynamic registerReceiver()   -> LoadedApk$ReceiverDispatcher$Args.getRunnable()
        // SMS_DELIVER goes to the default SMS app's MANIFEST receiver, so (1) is the
        // one that actually catches M-PESA. We hook both for completeness.
        var hooked = false

        // (1) Static / manifest receivers — the important one for SMS_DELIVER.
        try {
            val atClass = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val rdClass = XposedHelpers.findClass(
                "android.app.ActivityThread\$ReceiverData", lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(
                atClass, "handleReceiver", rdClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val intent = XposedHelpers.getObjectField(param.args[0], "intent") as? Intent
                                ?: return
                            handle(intent)
                        } catch (t: Throwable) {
                            XposedBridge.log("[MPESA] handleReceiver error: $t")
                        }
                    }
                }
            )
            hooked = true
        } catch (t: Throwable) {
            XposedBridge.log("[MPESA] handleReceiver hook failed: $t")
        }

        // (2) Dynamic receivers — fallback.
        try {
            val argsClass = XposedHelpers.findClassIfExists(
                "android.app.LoadedApk\$ReceiverDispatcher\$Args", lpparam.classLoader
            )
            if (argsClass != null) {
                XposedHelpers.findAndHookMethod(
                    argsClass, "getRunnable",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                val intent = XposedHelpers.getObjectField(param.thisObject, "mCurIntent") as? Intent
                                    ?: return
                                handle(intent)
                            } catch (t: Throwable) {
                                XposedBridge.log("[MPESA] dispatch error: $t")
                            }
                        }
                    }
                )
                hooked = true
            }
        } catch (t: Throwable) {
            XposedBridge.log("[MPESA] Args hook failed: $t")
        }

        XposedBridge.log("[MPESA] hook installed in ${lpparam.packageName} (hooked=$hooked)")
    }

    private fun handle(intent: Intent) {
        val action = intent.action ?: return
        if (action !in SMS_ACTIONS) return

        val messages: Array<SmsMessage> =
            Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].originatingAddress ?: ""
        // Concatenate multipart SMS into one body.
        val body = buildString {
            for (m in messages) append(m.displayMessageBody ?: m.messageBody ?: "")
        }
        if (body.isBlank()) return

        if (!isMpesa(sender)) return

        // Dedup
        val hash = (sender + "|" + body).hashCode()
        synchronized(recentHashes) {
            val now = System.currentTimeMillis()
            val seen = recentHashes[hash]
            if (seen != null && now - seen < 10_000) return
            recentHashes[hash] = now
        }

        val timestamp = runCatching { messages[0].timestampMillis }
            .getOrDefault(System.currentTimeMillis())

        Log.i(TAG, "M-PESA from=$sender : $body")
        XposedBridge.log("[MPESA] captured from=$sender")

        val parsed = MPesaParser.parse(body, sender, timestamp)
        // Always log the full ledger (in + out) to n8n.
        N8nClient.send(parsed)
        // Only outgoing spends get the categorize notification; received money
        // (receive/deposit/reversal) is recorded but not categorized.
        if (parsed.direction == "out") {
            CategoryNotifier.trigger(parsed)
        } else {
            Log.i(TAG, "incoming (${parsed.type}) — logged, no notification")
        }
    }

    // only the M-PESA sender ID. Safaricom marketing/airtime SMS come from
    // other sender IDs ("Safaricom", "SAFARICOM", shortcodes) and must be ignored.
    private fun isMpesa(sender: String): Boolean {
        val s = sender.trim().uppercase()
        return s == "MPESA" || s == "M-PESA"
    }
}
