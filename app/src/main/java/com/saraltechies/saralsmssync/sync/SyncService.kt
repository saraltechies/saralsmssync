package com.saraltechies.saralsmssync.sync

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.saraltechies.saralsmssync.MainActivity
import com.saraltechies.saralsmssync.R
import com.saraltechies.saralsmssync.core.SyncCrypto
import com.saraltechies.saralsmssync.core.SyncMessage
import com.saraltechies.saralsmssync.data.AppPrefs
import com.saraltechies.saralsmssync.data.InboxDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection

enum class LinkState { OFF, CONNECTING, CONNECTED }

/**
 * Runs while forwarding or reading is on. Its permanent notification says so, which means nobody can
 * forward a phone's SMS without it showing on that phone.
 */
class SyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: Job? = null

    @Volatile
    private var connection: HttpURLConnection? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels(this)
        val prefs = AppPrefs.get(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        ServiceCompat.startForeground(this, ONGOING_ID, ongoingNotification(prefs), type)

        if (prefs.groupKey == null || (!prefs.forwarding && !prefs.reading)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!prefs.reading) {
            stopListening()
            _link.value = LinkState.OFF
        } else if (listener?.isActive != true) {
            listener = scope.launch { listenLoop() }
        }
        if (prefs.forwarding) scope.launch { Forwarder.flush(this@SyncService) }
        return START_STICKY
    }

    private suspend fun listenLoop() {
        var wait = 2_000L
        while (scope.isActive) {
            val prefs = AppPrefs.get(this)
            val key = prefs.groupKey ?: return
            _link.value = LinkState.CONNECTING
            try {
                Relay.listen(
                    topic = SyncCrypto.topic(key),
                    since = prefs.lastRelayId ?: prefs.pairedAtSeconds.toString(),
                    onConnection = { connection = it },
                    onOpen = {
                        _link.value = LinkState.CONNECTED
                        wait = 2_000L
                    },
                    onEvent = { id, cipherText ->
                        receive(this, key, cipherText)
                        prefs.lastRelayId = id
                    },
                )
            } catch (_: IOException) {
                // No network or the relay is busy. Keep trying, more slowly each time.
            }
            _link.value = LinkState.CONNECTING
            delay(wait)
            wait = (wait * 2).coerceAtMost(60_000L)
        }
    }

    private fun stopListening() {
        listener?.cancel()
        connection?.disconnect()
        connection = null
    }

    override fun onDestroy() {
        stopListening()
        scope.cancel()
        _link.value = LinkState.OFF
        super.onDestroy()
    }

    private fun ongoingNotification(prefs: AppPrefs) =
        NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                getString(
                    when {
                        prefs.forwarding && prefs.reading -> R.string.ongoing_both
                        prefs.forwarding -> R.string.ongoing_forwarding
                        else -> R.string.ongoing_reading
                    },
                ),
            )
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(this))
            .build()

    companion object {
        private const val ONGOING_ID = 1
        private const val CHANNEL_ONGOING = "ongoing"
        private const val CHANNEL_MESSAGES = "messages"

        private val _link = MutableStateFlow(LinkState.OFF)
        val link: StateFlow<LinkState> = _link

        /** Starts, refreshes or stops the service to match the current settings. */
        fun update(context: Context) {
            val prefs = AppPrefs.get(context)
            val intent = Intent(context, SyncService::class.java)
            if (prefs.groupKey == null || (!prefs.forwarding && !prefs.reading)) {
                context.stopService(intent)
                return
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // Android refused a start from the background. It starts next time the app opens.
                if (Build.VERSION.SDK_INT >= 31 && e !is ForegroundServiceStartNotAllowedException) throw e
            }
        }

        /** Decrypts and stores a message from the relay, and shows it if it came from another phone. */
        fun receive(context: Context, key: ByteArray, cipherText: String): Boolean {
            val plain = SyncCrypto.decrypt(key, cipherText) ?: return false
            val message = SyncMessage.fromJson(plain) ?: return false
            val added = InboxDb.get(context).add(message, System.currentTimeMillis())
            if (added && message.deviceId != AppPrefs.get(context).deviceId) notify(context, message)
            return added
        }

        private fun notify(context: Context, message: SyncMessage) {
            createChannels(context)
            val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!allowed) return
            val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(message.sender + " · " + message.deviceName)
                .setContentText(message.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
                .setWhen(message.smsTime)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // Codes shouldn't show on a locked phone left on a desk.
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .build()
            NotificationManagerCompat.from(context).notify(message.id.hashCode(), notification)
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun createChannels(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannels(
                listOf(
                    NotificationChannel(
                        CHANNEL_ONGOING,
                        context.getString(R.string.channel_ongoing),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                    NotificationChannel(
                        CHANNEL_MESSAGES,
                        context.getString(R.string.channel_messages),
                        NotificationManager.IMPORTANCE_HIGH,
                    ),
                ),
            )
        }
    }
}
