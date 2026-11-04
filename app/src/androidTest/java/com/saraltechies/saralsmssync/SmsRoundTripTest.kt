package com.saraltechies.saralsmssync

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saraltechies.saralsmssync.core.SyncCrypto
import com.saraltechies.saralsmssync.data.AppPrefs
import com.saraltechies.saralsmssync.data.InboxDb
import com.saraltechies.saralsmssync.sync.SyncService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL

/**
 * The whole path on one emulator, through the real relay: a real incoming SMS is noticed, encrypted, sent,
 * received back and shown. .github/scripts/round-trip.sh runs this and delivers the SMS when the phone says
 * it's ready. The second SMS looks like an app's automatic login code, which recent Android versions may
 * hold back from apps for hours, so its timing is measured and reported rather than required.
 */
@RunWith(AndroidJUnit4::class)
class SmsRoundTripTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun smsTravelsEncryptedAndComesBack() {
        val prefs = AppPrefs.get(context)
        val db = InboxDb.get(context)
        db.clearAll()
        val key = SyncCrypto.newGroupKey()
        prefs.joinGroup(key)
        prefs.forwarding = true
        prefs.reading = true

        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.startActivitySync(open)
        SyncService.update(context)
        Thread.sleep(5_000)
        Log.i(TAG, "READY")

        val plain = waitFor(180_000) { db.newest().firstOrNull { it.message.body.contains(PLAIN_CODE) } }
        assertNotNull("The plain OTP SMS never came back from the relay", plain)
        assertEquals("5551234", plain!!.message.sender.takeLast(7))
        assertEquals(prefs.deviceId, plain.message.deviceId)
        Log.i(TAG, "PLAIN_SMS_SECONDS=" + (plain.arrivedAt - plain.message.smsTime) / 1000)

        // What the relay holds must be unreadable.
        val held = URL("https://ntfy.sh/${SyncCrypto.topic(key)}/json?poll=1&since=all").readText()
        assertTrue("The relay should hold the encrypted message", held.contains("\"message\""))
        assertFalse("The relay must not see the code", held.contains(PLAIN_CODE))
        assertFalse("The relay must not see the sender", held.contains("5551234"))
        Log.i(TAG, "RELAY_SEES_ONLY_ENCRYPTED_TEXT")

        val hash = waitFor(180_000) { db.newest().firstOrNull { it.message.body.contains(HASH_CODE) } }
        Log.i(
            TAG,
            if (hash == null) "HASH_SMS_NOT_SEEN_WITHIN_180S"
            else "HASH_SMS_SECONDS=" + (hash.arrivedAt - hash.message.smsTime) / 1000,
        )

        prefs.leaveGroup()
        SyncService.update(context)
        db.clearAll()
    }

    private fun <T : Any> waitFor(millis: Long, check: () -> T?): T? {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            check()?.let { return it }
            Thread.sleep(1_000)
        }
        return null
    }

    private companion object {
        const val TAG = "SaralRoundTrip"
        const val PLAIN_CODE = "482913"
        const val HASH_CODE = "771204"
    }
}
