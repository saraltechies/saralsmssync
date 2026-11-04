package com.saraltechies.saralsmssync.core

import org.json.JSONException
import org.json.JSONObject

/** One SMS as it travels between phones. */
data class SyncMessage(
    val id: String,
    val sender: String,
    val body: String,
    /** When the SMS reached the phone that forwarded it. */
    val smsTime: Long,
    val deviceId: String,
    val deviceName: String,
    /** SIM slot starting at 0, or -1 if the phone didn't say. */
    val simSlot: Int,
    val shortened: Boolean = false,
) {
    fun toJson(): ByteArray = JSONObject()
        .put("id", id)
        .put("sender", sender)
        .put("body", body)
        .put("smsTime", smsTime)
        .put("deviceId", deviceId)
        .put("deviceName", deviceName)
        .put("simSlot", simSlot)
        .put("shortened", shortened)
        .toString()
        .toByteArray()

    /**
     * The relay limits how big a message can be, so a very long SMS loses its end. It's marked as
     * shortened so the reader knows to check the original phone.
     */
    fun fittedToRelay(maxBytes: Int = MAX_JSON_BYTES): SyncMessage {
        var fitted = this
        while (fitted.toJson().size > maxBytes && fitted.body.isNotEmpty()) {
            fitted = fitted.copy(body = fitted.body.dropLast(maxOf(1, fitted.body.length / 10)), shortened = true)
        }
        return fitted
    }

    companion object {
        /** Encrypted and encoded, this stays under the relay's 4 KB message limit. */
        const val MAX_JSON_BYTES = 2900

        fun fromJson(bytes: ByteArray): SyncMessage? = try {
            val json = JSONObject(String(bytes))
            SyncMessage(
                id = json.getString("id"),
                sender = json.getString("sender"),
                body = json.getString("body"),
                smsTime = json.getLong("smsTime"),
                deviceId = json.getString("deviceId"),
                deviceName = json.getString("deviceName"),
                simSlot = json.optInt("simSlot", -1),
                shortened = json.optBoolean("shortened", false),
            )
        } catch (_: JSONException) {
            null
        }
    }
}
