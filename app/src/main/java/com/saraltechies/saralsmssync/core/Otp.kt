package com.saraltechies.saralsmssync.core

/** Finds the code in a verification SMS, to offer a one-tap copy. It's only ever a convenience. */
object Otp {
    private val wordsNearCodes = Regex(
        "otp|one[- ]time|code|verification|verify|password|passcode|\\bpin\\b|ओटीपी|कोड",
        RegexOption.IGNORE_CASE,
    )
    private val digits = Regex("(?<![\\d.,])\\d{4,8}(?![\\d]|[.,]\\d)")

    fun find(body: String): String? {
        if (!wordsNearCodes.containsMatchIn(body)) return null
        return digits.find(body)?.value
    }
}
