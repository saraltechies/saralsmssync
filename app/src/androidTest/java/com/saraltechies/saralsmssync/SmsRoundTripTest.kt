package com.saraltechies.saralsmssync

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Base64
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
import java.security.MessageDigest

/**
 * The whole path on one emulator, through the real relay: a real incoming SMS is noticed, encrypted, sent,
 * received back and shown. .github/scripts/round-trip.sh runs this and delivers the SMS when the phone says
 * it's ready. The other two SMS use the automatic login formats, which recent Android versions may hold back
 * from apps for hours, so their timing is measured and reported rather than required.
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
        // A real retriever hash, belonging to another installed app, so this app isn't the intended reader.
        Log.i(TAG, "HASH=" + retrieverHash(instrumentation.context.packageName))
        Log.i(TAG, "READY")

        val plain = waitFor(180_000) { db.newest().firstOrNull { it.message.body.contains(PLAIN_CODE) } }
        assertNotNull("The plain OTP SMS never came back from the relay", plain)
        assertEquals("5551234", plain!!.message.sender.takeLast(7))
        assertEquals(prefs.deviceId, plain.message.deviceId)
        Log.i(TAG, "PLAIN_SMS_SECONDS=" + (plain.arrivedAt - plain.message.smsTime) / 1000)

        // What the relay holds must be unreadable.
        val relayUrl = URL("https://ntfy.sh/${SyncCrypto.topic(key)}/json?poll=1&since=all")
        val held = waitFor(30_000) { relayUrl.readText().takeIf { it.contains("\"message\"") } }
        assertNotNull("The relay should hold the encrypted message so offline phones can catch up", held)
        Log.i(TAG, "RELAY_HOLDS_BYTES=" + held!!.length)
        assertFalse("The relay must not see the code", held.contains(PLAIN_CODE))
        assertFalse("The relay must not see the sender", held.contains("5551234"))
        Log.i(TAG, "RELAY_SEES_ONLY_ENCRYPTED_TEXT")

        report("RETRIEVER_SMS", HASH_CODE)
        report("WEBOTP_SMS", WEBOTP_CODE)

        prefs.leaveGroup()
        SyncService.update(context)
        db.clearAll()
    }

    /** Reports how long a code took, as these may legitimately be held back for hours. */
    private fun report(label: String, code: String) {
        val db = InboxDb.get(context)
        val found = waitFor(180_000) { db.newest().firstOrNull { it.message.body.contains(code) } }
        Log.i(
            TAG,
            if (found == null) label + "_NOT_SEEN_WITHIN_180S"
            else label + "_SECONDS=" + (found.arrivedAt - found.message.smsTime) / 1000,
        )
    }

    @Suppress("DEPRECATION")
    private fun retrieverHash(packageName: String): String {
        val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        val text = packageName + " " + info.signatures!![0].toCharsString()
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return Base64.encodeToString(digest.copyOf(9), Base64.NO_PADDING or Base64.NO_WRAP).substring(0, 11)
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
        const val WEBOTP_CODE = "650388"
    }
}
