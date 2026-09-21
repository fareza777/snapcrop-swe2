package com.sharesafe.app

import android.graphics.Rect
import com.sharesafe.app.core.OcrSpan
import com.sharesafe.app.core.RegionKind
import com.sharesafe.app.core.SensitivePatterns
import com.sharesafe.app.core.SensitiveTextDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for detector post-processing and Indonesian patterns.
 * Rect/detectRegions need android.graphics → run under Robolectric.
 */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class DetectorLogicTest {

    @Test
    fun `same-kind same-line spans merge into one region`() {
        val spans = listOf(
            OcrSpan("4242", Rect(0, 10, 40, 30), ' '),
            OcrSpan("4242", Rect(46, 10, 86, 30), ' '),
            OcrSpan("4242", Rect(92, 10, 132, 30), ' '),
            OcrSpan("4242", Rect(138, 10, 178, 30), '\n'),
        )
        val regions = SensitiveTextDetector.detectRegions(spans, 500, 500)
        assertEquals(1, regions.size)
        assertEquals(RegionKind.CARD, regions[0].kind)
        assertTrue(regions[0].rect.width() > 100)
    }

    @Test
    fun `different lines do not merge`() {
        val spans = listOf(
            OcrSpan("email1@a.com", Rect(0, 10, 100, 30), '\n'),
            OcrSpan("email2@a.com", Rect(0, 60, 100, 80), '\n'),
        )
        val regions = SensitiveTextDetector.detectRegions(spans, 500, 500)
        assertEquals(2, regions.size)
    }

    @Test
    fun `NIK with context keyword detected`() {
        val kinds = SensitivePatterns.match("NIK: 3201234567890123")
        assertTrue(kinds.any { it.kind == RegionKind.NUMBER && it.label == "NIK / KTP" })
    }

    @Test
    fun `bare 16 digits flagged as NIK-shaped number`() {
        val kinds = SensitivePatterns.match("3201234567890123")
        assertTrue(kinds.any { it.kind == RegionKind.NUMBER && it.label == "NIK / ID number" })
    }

    @Test
    fun `npwp dotted format detected`() {
        val kinds = SensitivePatterns.match("NPWP 12.345.678.9-012.345")
        assertTrue(kinds.any { it.kind == RegionKind.NUMBER && it.label == "NPWP" })
    }

    @Test
    fun `bank account with rek keyword detected`() {
        val kinds = SensitivePatterns.match("transfer ke rek 1234567890 a.n. Budi")
        assertTrue(kinds.any { it.kind == RegionKind.CARD && it.label == "Bank account" })
    }

    @Test
    fun `plate number with context detected`() {
        val kinds = SensitivePatterns.match("plat nomor B 1234 XYZ")
        assertTrue(kinds.any { it.label == "Plat nomor" })
    }

    @Test
    fun `bare plate without context not detected`() {
        val kinds = SensitivePatterns.match("B 1234 XYZ")
        assertTrue(kinds.none { it.label == "Plat nomor" })
    }
}
