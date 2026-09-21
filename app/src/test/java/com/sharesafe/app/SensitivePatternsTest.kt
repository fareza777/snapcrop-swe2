package com.sharesafe.app

import com.sharesafe.app.core.CustomRule
import com.sharesafe.app.core.RegionKind
import com.sharesafe.app.core.SensitivePatterns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitivePatternsTest {

    private fun kinds(text: String) = SensitivePatterns.match(text).map { it.kind }

    @Test
    fun `email is detected`() {
        assertTrue(RegionKind.EMAIL in kinds("reach me at jane.doe+1@example.co.uk"))
    }

    @Test
    fun `international phone is detected`() {
        assertTrue(RegionKind.PHONE in kinds("call +62 812-3456-7890 now"))
        assertTrue(RegionKind.PHONE in kinds("tel: (415) 555-0132"))
    }

    @Test
    fun `luhn-valid card is detected, invalid is not`() {
        // 4242 4242 4242 4242 is the canonical Luhn-valid test card
        assertTrue(RegionKind.CARD in kinds("card 4242 4242 4242 4242"))
        assertTrue(RegionKind.CARD !in kinds("card 4242 4242 4242 4241"))
    }

    @Test
    fun `long digit run is flagged as sensitive number`() {
        assertTrue(RegionKind.NUMBER in kinds("NIK 3201234567890123"))
    }

    @Test
    fun `ip and ipv6 detected`() {
        assertTrue(RegionKind.NETWORK in kinds("server 192.168.1.24 up"))
        assertTrue(RegionKind.NETWORK in kinds("addr fe80::1ff:fe23:4567:890a"))
    }

    @Test
    fun `assigned secret detected, placeholder skipped`() {
        assertTrue(RegionKind.SECRET in kinds("api_key = \"kX9f27bQzLmV4wPnR8sT\""))
        assertTrue(RegionKind.SECRET !in kinds("password = example123"))
    }

    @Test
    fun `plain text yields nothing`() {
        assertEquals(0, SensitivePatterns.match("the quick brown fox jumps").size)
    }

    @Test
    fun `iban with valid mod97 is detected`() {
        // GB29 NWBK 6016 1331 9268 19 is a canonical valid IBAN.
        assertTrue(
            kinds("payout IBAN GB29 NWBK 6016 1331 9268 19")
                .contains(RegionKind.CARD),
        )
        // Bad checksum → not a card hit.
        assertTrue(
            !kinds("payout IBAN GB29 NWBK 6016 1331 9268 10")
                .contains(RegionKind.CARD),
        )
    }

    @Test
    fun `secret key prefixes are detected`() {
        // sk- requires 20+ alnum after the dash; dop_v1_ is exactly 64 hex.
        assertTrue(RegionKind.SECRET in kinds("key sk-9f8e7d6c5b4a3f2e1d0c9b8a"))
        assertTrue(
            RegionKind.SECRET in kinds(
                "token dop_v1_" + "a1b2c3d4".repeat(8),
            ),
        )
    }

    @Test
    fun `context words boost weak matches`() {
        // A bare 16-digit run starts at "maybe" confidence; the nearby label
        // "NIK" sits in the booster list and should push it to full.
        val hits = SensitivePatterns.match("silakan isi NIK 3201234567890123 ya")
        val numberHit = hits.firstOrNull { it.kind == RegionKind.NUMBER }
        assertTrue(numberHit != null && numberHit.confidence >= 1f)
    }

    @Test
    fun `custom wildcard rule matches and custom kind`() {
        val rules = listOf(CustomRule("emp", "EMP-####"))
        val hits = SensitivePatterns.matchCustom("see EMP-4821 desk", rules)
        assertEquals(1, hits.size)
        assertEquals(RegionKind.CUSTOM, hits[0].kind)
    }

    @Test
    fun `custom raw regex rule matches`() {
        val rules = listOf(CustomRule("seat", "seat-[A-Z]\\d{2}"))
        val hits = SensitivePatterns.matchCustom("seat-B12 booked", rules)
        assertEquals(1, hits.size)
    }
}
