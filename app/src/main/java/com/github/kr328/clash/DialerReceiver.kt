package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DialerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SECRET_CODE" ||
            intent.data?.scheme != "android_secret_code" || intent.data?.host != "252746382") return
        val launchIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }
}