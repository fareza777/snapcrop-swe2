package com.sharesafe.app.core

import kotlin.random.Random

/**
 * Cloak mode — instead of a visible cover, the sensitive value is replaced by a
 * plausible masked substitute ("j•••@company.com", "••• ••• ••• 9010") drawn in
 * text on top of a sampled background patch. The result reads naturally in a
 * chat screenshot instead of screaming "censored".
 */
object Cloaker {

    /** Masked stand-in for the detected value, preserving its rough shape. */
    fun substitute(kind: RegionKind, source: String): String = when (kind) {
        RegionKind.EMAIL -> maskEmail(source)
        RegionKind.CARD -> maskDigitsKeepTail(source)
        RegionKind.PHONE -> maskPhone(source)
        RegionKind.SECRET -> "•".repeat(source.length.coerceIn(4, 18))
        RegionKind.CODE -> "▦"
        RegionKind.NETWORK -> maskEachGroup(source)
        RegionKind.NUMBER, RegionKind.TRACKING, RegionKind.DATETIME,
        RegionKind.ADDRESS, RegionKind.CUSTOM,
        -> maskShapePreserving(source)
        else -> maskShapePreserving(source)
    }

    /** jane.doe@company.com → j•••@c•••.com */
    private fun maskEmail(s: String): String {
        val at = s.indexOf('@')
        if (at <= 0) return maskShapePreserving(s)
        val user = s.take(1) + "•••"
        val domain = s.substring(at + 1)
        val dot = domain.lastIndexOf('.')
        val host = if (dot > 0) domain.take(1) + "•••" + domain.substring(dot)
        else domain.take(1) + "•••"
        return "$user@$host"
    }

    /** 4532 1234 5678 9010 → •••• •••• •••• 9010 */
    private fun maskDigitsKeepTail(s: String): String {
        val totalDigits = s.count { it.isDigit() }
        if (totalDigits < 6) return maskShapePreserving(s)
        var seen = 0
        return s.map { ch ->
            if (!ch.isDigit()) ch else {
                seen++
                if (seen <= totalDigits - 4) '•' else ch
            }
        }.joinToString("")
    }

    /** +62 812-3456-7890 → +62 8••-••••-••90 */
    private fun maskPhone(s: String): String {
        val digits = s.filter { it.isDigit() }
        if (digits.length < 6) return maskShapePreserving(s)
        var pos = 0
        return s.map { ch ->
            if (!ch.isDigit()) ch else {
                pos++
                if (pos <= digits.length - 4) '•' else ch
            }
        }.joinToString("")
    }

    /** 192.168.1.24 → 192.•••.•.•• */
    private fun maskEachGroup(s: String): String =
        s.split(Regex("([.:-])")).joinToString("") { part ->
            if (part.any { it.isLetterOrDigit() } && part.length > 1) {
                part.take(1) + "•".repeat(part.length - 1)
            } else part
        }

    /** Generic: keep character classes, mask middles — "Budi Santoso" → "B••• S••••••". */
    private fun maskShapePreserving(s: String): String =
        s.split(Regex("\\s+")).joinToString(" ") { word ->
            when {
                word.length <= 2 -> "•".repeat(word.length)
                word.all { it.isDigit() } -> word.take(1) + "•".repeat(word.length - 2) + word.takeLast(1)
                else -> word.take(1) + "•".repeat(word.length - 1)
            }
        }

    /** Deterministic plausible fake for the value (unused middle chars random-seeded). */
    fun fakeDigits(length: Int): String =
        (1..length).joinToString("") { Random.nextInt(10).toString() }
}
