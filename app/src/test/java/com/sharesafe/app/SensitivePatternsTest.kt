package com.sharesafe.app

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
}
