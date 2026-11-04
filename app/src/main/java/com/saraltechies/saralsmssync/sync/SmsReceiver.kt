package com.saraltechies.saralsmssync.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationManagerCompat
import com.saraltechies.saralsmssync.data.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Forwards each new SMS, only while the person has forwarding turned on. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!AppPrefs.get(context).forwarding) return
        // Forwarding must always show on this phone, so it never runs with notifications turned off.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        SyncService.update(context)
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val slot = intent.getIntExtra(EXTRA_SLOT_INDEX, intent.getIntExtra(EXTRA_SLOT_LEGACY, -1))

        // A long SMS arrives in parts.
        parts.filterNotNull().groupBy { it.originatingAddress.orEmpty() }.forEach { (sender, pieces) ->
            Forwarder.enqueue(
                context,
                sender = sender,
                body = pieces.joinToString("") { it.messageBody.orEmpty() },
                smsTime = pieces.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis(),
                simSlot = slot,
            )
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Forwarder.flush(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val EXTRA_SLOT_INDEX = "android.telephony.extra.SLOT_INDEX"
        const val EXTRA_SLOT_LEGACY = "slot"
    }
}
