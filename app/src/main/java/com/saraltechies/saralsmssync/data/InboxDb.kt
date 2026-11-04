package com.saraltechies.saralsmssync.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.saraltechies.saralsmssync.core.SyncMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit

data class InboxMessage(val message: SyncMessage, val arrivedAt: Long)

data class OutboxItem(val rowId: Long, val cipherText: String)

/**
 * Messages this phone has received from the group, deleted automatically after [KEEP_DAYS], and encrypted
 * messages still waiting to be sent. The outbox never holds readable text.
 */
class InboxDb private constructor(context: Context) :
    SQLiteOpenHelper(context, "inbox.db", null, 1) {

    private val _changes = MutableStateFlow(0)

    /** Changes whenever the inbox does, so the screen knows to reload. */
    val changes: StateFlow<Int> = _changes

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                sender TEXT NOT NULL,
                body TEXT NOT NULL,
                sms_time INTEGER NOT NULL,
                device_id TEXT NOT NULL,
                device_name TEXT NOT NULL,
                sim_slot INTEGER NOT NULL,
                shortened INTEGER NOT NULL,
                arrived_at INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX messages_time ON messages(sms_time)")
        db.execSQL("CREATE TABLE outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, cipher_text TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Adds a message. Returns false if it was already here: relays can repeat themselves. */
    fun add(message: SyncMessage, arrivedAt: Long): Boolean {
        val values = ContentValues().apply {
            put("id", message.id)
            put("sender", message.sender)
            put("body", message.body)
            put("sms_time", message.smsTime)
            put("device_id", message.deviceId)
            put("device_name", message.deviceName)
            put("sim_slot", message.simSlot)
            put("shortened", if (message.shortened) 1 else 0)
            put("arrived_at", arrivedAt)
        }
        val added = writableDatabase.insertWithOnConflict(
            "messages", null, values, SQLiteDatabase.CONFLICT_IGNORE,
        ) != -1L
        if (added) {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(KEEP_DAYS)
            writableDatabase.delete("messages", "sms_time < ?", arrayOf(cutoff.toString()))
            _changes.update { it + 1 }
        }
        return added
    }

    fun newest(limit: Int = 300): List<InboxMessage> =
        readableDatabase.query(
            "messages", null, null, null, null, null, "sms_time DESC", limit.toString(),
        ).use { row ->
            buildList {
                while (row.moveToNext()) {
                    add(
                        InboxMessage(
                            SyncMessage(
                                id = row.getString(row.getColumnIndexOrThrow("id")),
                                sender = row.getString(row.getColumnIndexOrThrow("sender")),
                                body = row.getString(row.getColumnIndexOrThrow("body")),
                                smsTime = row.getLong(row.getColumnIndexOrThrow("sms_time")),
                                deviceId = row.getString(row.getColumnIndexOrThrow("device_id")),
                                deviceName = row.getString(row.getColumnIndexOrThrow("device_name")),
                                simSlot = row.getInt(row.getColumnIndexOrThrow("sim_slot")),
                                shortened = row.getInt(row.getColumnIndexOrThrow("shortened")) != 0,
                            ),
                            arrivedAt = row.getLong(row.getColumnIndexOrThrow("arrived_at")),
                        ),
                    )
                }
            }
        }

    fun clearMessages() {
        writableDatabase.delete("messages", null, null)
        _changes.update { it + 1 }
    }

    fun clearAll() {
        writableDatabase.delete("outbox", null, null)
        clearMessages()
    }

    fun enqueue(cipherText: String) {
        writableDatabase.insert("outbox", null, ContentValues().apply { put("cipher_text", cipherText) })
    }

    fun outbox(): List<OutboxItem> =
        readableDatabase.query("outbox", null, null, null, null, null, "id").use { row ->
            buildList {
                while (row.moveToNext()) add(OutboxItem(row.getLong(0), row.getString(1)))
            }
        }

    fun removeFromOutbox(rowId: Long) {
        writableDatabase.delete("outbox", "id = ?", arrayOf(rowId.toString()))
    }

    companion object {
        const val KEEP_DAYS = 7L

        @Volatile
        private var instance: InboxDb? = null

        fun get(context: Context): InboxDb = instance ?: synchronized(this) {
            instance ?: InboxDb(context.applicationContext).also { instance = it }
        }
    }
}
