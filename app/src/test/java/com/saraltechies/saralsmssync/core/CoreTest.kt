package com.saraltechies.saralsmssync.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    private val key = SyncCrypto.newGroupKey()

    private fun message(body: String) = SyncMessage("id", "VM-BANK", body, 1000L, "dev", "Home phone", 1)

    @Test
    fun encryptedTextOpensWithTheSameKey() {
        val plain = "Your OTP is 482913".toByteArray()
        assertArrayEquals(plain, SyncCrypto.decrypt(key, SyncCrypto.encrypt(key, plain)))
    }

    @Test
    fun encryptedTextDoesNotShowTheMessage() {
        val cipher = SyncCrypto.encrypt(key, "Your OTP is 482913".toByteArray())
        assertFalse(cipher.contains("482913"))
        assertNotEquals(cipher, SyncCrypto.encrypt(key, "Your OTP is 482913".toByteArray()))
    }

    @Test
    fun anotherKeyCannotOpenIt() {
        val cipher = SyncCrypto.encrypt(key, "secret".toByteArray())
        assertNull(SyncCrypto.decrypt(SyncCrypto.newGroupKey(), cipher))
    }

    @Test
    fun changedTextIsRejected() {
        val cipher = SyncCrypto.encrypt(key, "secret".toByteArray())
        // A character in the middle: the last one can carry unused bits.
        val flipped = if (cipher[20] == 'A') "B" else "A"
        assertNull(SyncCrypto.decrypt(key, cipher.replaceRange(20, 21, flipped)))
        assertNull(SyncCrypto.decrypt(key, "not a message!"))
        assertNull(SyncCrypto.decrypt(key, "AAAA"))
    }

    @Test
    fun pairingCodeRoundTrips() {
        assertArrayEquals(key, SyncCrypto.parsePairingCode("  " + SyncCrypto.pairingCode(key) + "\n"))
    }

    @Test
    fun badPairingCodesAreRefused() {
        assertNull(SyncCrypto.parsePairingCode("https://example.com"))
        assertNull(SyncCrypto.parsePairingCode("saralsync1:short"))
        assertNull(SyncCrypto.parsePairingCode("saralsync1:!!!!"))
    }

    @Test
    fun topicAndCheckStayTheSameAndDoNotRevealTheKey() {
        assertEquals(SyncCrypto.topic(key), SyncCrypto.topic(key.copyOf()))
        assertEquals(35, SyncCrypto.topic(key).length)
        assertNotEquals(SyncCrypto.topic(key), SyncCrypto.topic(SyncCrypto.newGroupKey()))
        assertEquals(9, SyncCrypto.groupCheck(key).length)
        assertFalse(SyncCrypto.pairingCode(key).contains(SyncCrypto.topic(key)))
    }

    @Test
    fun messageSurvivesTheTrip() {
        val original = message("आपका ओटीपी 482913 है \"quoted\"\nsecond line")
        assertEquals(original, SyncMessage.fromJson(original.toJson()))
    }

    @Test
    fun brokenMessageIsIgnored() {
        assertNull(SyncMessage.fromJson("not json".toByteArray()))
        assertNull(SyncMessage.fromJson("{}".toByteArray()))
    }

    @Test
    fun shortMessageIsLeftAlone() {
        val original = message("Hello")
        assertEquals(original, original.fittedToRelay())
    }

    @Test
    fun longMessagesFitTheRelayLimit() {
        for (body in listOf("a".repeat(20_000), "ओ".repeat(20_000), "\"".repeat(20_000))) {
            val fitted = message(body).fittedToRelay()
            assertTrue(fitted.shortened)
            assertTrue(fitted.toJson().size <= SyncMessage.MAX_JSON_BYTES)
            assertTrue(fitted.body.length > 500)
            assertTrue(SyncCrypto.encrypt(key, fitted.toJson()).toByteArray().size < 4096)
        }
    }

    @Test
    fun otpIsFound() {
        assertEquals("482913", Otp.find("Your OTP is 482913. Do not share."))
        assertEquals("1234", Otp.find("1234 is your verification code"))
        assertEquals("482913", Otp.find("G-482913 is your Google verification code."))
        assertEquals("482913", Otp.find("आपका ओटीपी 482913 है"))
        assertEquals("482913", Otp.find("Your code: 482913."))
    }

    @Test
    fun ordinaryNumbersAreNotCodes() {
        assertNull(Otp.find("Rs 5000 debited from your account"))
        assertNull(Otp.find("Call 9876543210 for your offer"))
        assertNull(Otp.find("Use code SAVE for 10% off"))
        assertNull(Otp.find("Your code expires on 9876543210"))
        assertNull(Otp.find("Hello, how are you?"))
    }
}
