package com.blyator.mpesa

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Main LSPosed entry point. Declared in assets/xposed_init.
 *
 * Per-package callback: LSPosed invokes this once for every scoped process.
 * We don't filter by package here — SMSHook hooks BroadcastReceiver.onReceive
 * and filters on the SMS intent action, so it works in whatever process the
 * platform actually delivers the SMS to (default SMS app, telephony, etc).
 */
class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            SMSHook.install(lpparam)
        } catch (t: Throwable) {
            // Never propagate — a thrown hook can crash the host process.
            XposedBridge.log("[MPESA] install failed in ${lpparam.packageName}: $t")
        }
    }
}
