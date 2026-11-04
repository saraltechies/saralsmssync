package com.saraltechies.saralsmssync.core

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * All phones in a group share one random 256-bit key, passed from phone to phone by QR code and never
 * over the internet. Separate keys are derived for encryption and for the relay topic, so the relay
 * learns nothing that helps it read a message.
 */
object SyncCrypto {
    private const val PAIRING_PREFIX = "saralsync1:"
    private const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    fun newGroupKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    fun pairingCode(groupKey: ByteArray): String = PAIRING_PREFIX + encode(groupKey)

    /** The group key in a pairing code, or null if this isn't a valid code. */
    fun parsePairingCode(code: String): ByteArray? {
        val text = code.trim()
        if (!text.startsWith(PAIRING_PREFIX)) return null
        val key = decode(text.removePrefix(PAIRING_PREFIX)) ?: return null
        return key.takeIf { it.size == KEY_BYTES }
    }

    /** The relay topic. Long and random, and of no use for decrypting anything. */
    fun topic(groupKey: ByteArray): String =
        "ss-" + derive(groupKey, "topic v1").take(16).joinToString("") { "%02x".format(it) }

    /** A short code people can compare to confirm that two phones are in the same group. */
    fun groupCheck(groupKey: ByteArray): String {
        val hex = derive(groupKey, "check v1").take(4).joinToString("") { "%02X".format(it) }
        return hex.substring(0, 4) + "-" + hex.substring(4)
    }

    fun encrypt(groupKey: ByteArray, plain: ByteArray): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(groupKey), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(topic(groupKey).toByteArray())
        return encode(nonce + cipher.doFinal(plain))
    }

    /** The original bytes, or null if the text was changed or was made with a different key. */
    fun decrypt(groupKey: ByteArray, text: String): ByteArray? {
        val data = decode(text) ?: return null
        if (data.size < NONCE_BYTES + TAG_BITS / 8) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey(groupKey),
                GCMParameterSpec(TAG_BITS, data, 0, NONCE_BYTES),
            )
            cipher.updateAAD(topic(groupKey).toByteArray())
            cipher.doFinal(data, NONCE_BYTES, data.size - NONCE_BYTES)
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    private fun encryptionKey(groupKey: ByteArray) =
        SecretKeySpec(derive(groupKey, "encryption v1"), "AES")

    private fun derive(groupKey: ByteArray, purpose: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(groupKey, "HmacSHA256"))
        return mac.doFinal("saral-sms-sync $purpose".toByteArray())
    }

    private fun encode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decode(text: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }
}
