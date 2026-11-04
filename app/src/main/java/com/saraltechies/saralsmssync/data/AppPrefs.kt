package com.saraltechies.saralsmssync.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** This phone's settings. The group key is kept encrypted by a key held in the Android Keystore. */
class AppPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { id -> prefs.edit { putString(KEY_DEVICE_ID, id) } }

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null) ?: Build.MODEL
        set(value) = prefs.edit { putString(KEY_DEVICE_NAME, value.trim().take(40)) }

    var forwarding: Boolean
        get() = prefs.getBoolean(KEY_FORWARDING, false)
        set(value) = prefs.edit { putBoolean(KEY_FORWARDING, value) }

    var reading: Boolean
        get() = prefs.getBoolean(KEY_READING, false)
        set(value) = prefs.edit { putBoolean(KEY_READING, value) }

    /** The last relay message seen, so listening picks up exactly where it stopped. */
    var lastRelayId: String?
        get() = prefs.getString(KEY_LAST_RELAY_ID, null)
        set(value) = prefs.edit { putString(KEY_LAST_RELAY_ID, value) }

    val pairedAtSeconds: Long get() = prefs.getLong(KEY_PAIRED_AT, 0)

    val groupKey: ByteArray?
        get() {
            val stored = prefs.getString(KEY_GROUP_KEY, null) ?: return null
            return try {
                val data = Base64.getDecoder().decode(stored)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, data, 0, 12))
                cipher.doFinal(data, 12, data.size - 12)
            } catch (_: GeneralSecurityException) {
                null
            }
        }

    fun joinGroup(key: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val stored = Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(key))
        prefs.edit {
            putString(KEY_GROUP_KEY, stored)
            putLong(KEY_PAIRED_AT, System.currentTimeMillis() / 1000)
            remove(KEY_LAST_RELAY_ID)
        }
    }

    fun leaveGroup() {
        prefs.edit {
            remove(KEY_GROUP_KEY)
            remove(KEY_PAIRED_AT)
            remove(KEY_LAST_RELAY_ID)
            putBoolean(KEY_FORWARDING, false)
            putBoolean(KEY_READING, false)
        }
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEYSTORE_ALIAS = "group-key-wrap"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_FORWARDING = "forwarding"
        private const val KEY_READING = "reading"
        private const val KEY_LAST_RELAY_ID = "last_relay_id"
        private const val KEY_PAIRED_AT = "paired_at"
        private const val KEY_GROUP_KEY = "group_key"

        @Volatile
        private var instance: AppPrefs? = null

        fun get(context: Context): AppPrefs = instance ?: synchronized(this) {
            instance ?: AppPrefs(context.applicationContext).also { instance = it }
        }
    }
}
