package com.saraltechies.saralsmssync.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.saraltechies.saralsmssync.core.SyncCrypto
import com.saraltechies.saralsmssync.core.SyncMessage
import com.saraltechies.saralsmssync.data.AppPrefs
import com.saraltechies.saralsmssync.data.InboxDb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Encrypts messages at once, saves them, and sends them now or as soon as there's a connection. */
object Forwarder {
    private val sending = Mutex()
    private val _lastSent = MutableStateFlow<Long?>(null)

    /** When this phone last handed a message to the relay since the app started. */
    val lastSent: StateFlow<Long?> = _lastSent

    /** Saves a message for sending. Returns false only if this phone isn't in a group. */
    fun enqueue(
        context: Context,
        sender: String,
        body: String,
        smsTime: Long,
        simSlot: Int,
    ): Boolean {
        val prefs = AppPrefs.get(context)
        val key = prefs.groupKey ?: return false
        val message = SyncMessage(
            id = UUID.randomUUID().toString(),
            sender = sender,
            body = body,
            smsTime = smsTime,
            deviceId = prefs.deviceId,
            deviceName = prefs.deviceName,
            simSlot = simSlot,
        ).fittedToRelay()
        InboxDb.get(context).enqueue(SyncCrypto.encrypt(key, message.toJson()))
        scheduleRetry(context)
        return true
    }

    /** Sends everything waiting. Returns true if nothing is left. */
    suspend fun flush(context: Context): Boolean = sending.withLock {
        val key = AppPrefs.get(context).groupKey ?: return@withLock true
        val topic = SyncCrypto.topic(key)
        val db = InboxDb.get(context)
        for (item in db.outbox()) {
            try {
                Relay.publish(topic, item.cipherText)
            } catch (_: IOException) {
                return@withLock false
            }
            db.removeFromOutbox(item.rowId)
            _lastSent.value = System.currentTimeMillis()
        }
        true
    }

    /** A safety net: sends once there's a connection, even if the app has been stopped by then. */
    private fun scheduleRetry(context: Context) {
        val request = OneTimeWorkRequestBuilder<FlushWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setInitialDelay(20, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("flush", ExistingWorkPolicy.KEEP, request)
    }
}

class FlushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (Forwarder.flush(applicationContext)) Result.success() else Result.retry()
}
