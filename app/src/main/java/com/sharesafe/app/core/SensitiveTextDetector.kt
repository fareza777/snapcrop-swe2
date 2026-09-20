package com.sharesafe.app.core

import android.graphics.Rect
import kotlin.math.ln

data class SensitiveHit(val kind: RegionKind, val range: IntRange, val label: String)

/**
 * Maps sensitive-looking text onto OCR span bounds. Adapted from SnapCrop's
 * regex pass (MIT) — Latin OCR spans joined, pattern ranges mapped back to
 * whichever OCR element boxes they overlap.
 */
object SensitiveTextDetector {

    fun detectRegions(
        spans: List<OcrSpan>,
        imageWidth: Int,
        imageHeight: Int,
    ): List<RedactRegion> {
        if (spans.isEmpty()) return emptyList()

        val spanRanges = mutableListOf<Pair<IntRange, OcrSpan>>()
        val joined = buildString {
            spans.forEachIndexed { index, span ->
                val start = length
                append(span.text)
                spanRanges.add((start until length) to span)
                if (index != spans.lastIndex) append(span.separatorAfter)
            }
        }

        val hits = SensitivePatterns.match(joined)
        val regions = mutableListOf<RedactRegion>()
        hits.forEach { hit ->
            spanRanges
                .filter { (range, _) -> hit.range.overlaps(range) }
                .forEach { (_, span) ->
                    regions += RedactRegion.new(
                        rect = span.bounds.padded(0.08f, 0.18f, imageWidth, imageHeight),
                        kind = hit.kind,
                        detail = hit.label,
                    )
                }
        }
        return mergeOverlapping(regions)
    }

    /** Union regions of the same kind that heavily overlap so one secret = one region. */
    private fun mergeOverlapping(regions: List<RedactRegion>): List<RedactRegion> {
        val out = mutableListOf<RedactRegion>()
        regions.forEach { r ->
            val existing = out.indexOfFirst { it.kind == r.kind && it.rect.iou(r.rect) > 0.5f }
            if (existing >= 0) {
                val merged = Rect(out[existing].rect).apply { union(r.rect) }
                out[existing] = out[existing].copy(rect = merged)
            } else {
                out += r
            }
        }
        return mergeSameLine(out)
    }

    /**
     * One sensitive value is often split into several OCR elements on a single
     * line ("4242 4242 4242 4242" → four card rects). Union same-kind rects that
     * sit on the same line with a small gap so each value gets one region.
     */
    private fun mergeSameLine(regions: List<RedactRegion>): List<RedactRegion> {
        val out = regions.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            outer@ for (i in out.indices) {
                for (j in i + 1 until out.size) {
                    val a = out[i]; val b = out[j]
                    if (a.kind != b.kind) continue
                    if (!sameLine(a.rect, b.rect)) continue
                    val gap = maxOf(b.rect.left - a.rect.right, a.rect.left - b.rect.right)
                    val lineH = minOf(a.rect.height(), b.rect.height())
                    if (gap <= lineH * 1.5f) {
                        out[i] = a.copy(rect = Rect(a.rect).apply { union(b.rect) })
                        out.removeAt(j)
                        merged = true
                        break@outer
                    }
                }
            }
        }
        return out
    }

    private fun sameLine(a: Rect, b: Rect): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return overlap >= minOf(a.height(), b.height()) * 0.5f
    }

    private fun Rect.iou(o: Rect): Float {
        val l = maxOf(left, o.left); val t = maxOf(top, o.top)
        val r = minOf(right, o.right); val b = minOf(bottom, o.bottom)
        if (l >= r || t >= b) return 0f
        val inter = (r - l).toFloat() * (b - t)
        val union = width() * height() + o.width() * o.height() - inter
        return inter / union
    }

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last
}

object SensitivePatterns {
    private data class P(val kind: RegionKind, val regex: Regex, val label: String)

    private val patterns = listOf(
        P(RegionKind.EMAIL, Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "Email"),
        P(RegionKind.NETWORK, Regex("\\b(?:[0-9A-F]{2}[:-]){5}[0-9A-F]{2}\\b", RegexOption.IGNORE_CASE), "MAC address"),
        P(RegionKind.CARD, Regex("\\b[A-Z]{2}\\d{2}(?:[ ]?[A-Z0-9]){11,30}\\b"), "IBAN"),
        P(RegionKind.SECRET, Regex("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b"), "AWS key"),
        P(RegionKind.SECRET, Regex("\\bAIza[0-9A-Za-z_-]{35}\\b"), "API key"),
        P(RegionKind.SECRET, Regex("\\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{22,255})\\b"), "GitHub token"),
        P(RegionKind.SECRET, Regex("\\b(?:xox[baprs]-[A-Za-z0-9-]{20,255}|sk_(?:live|test)_[A-Za-z0-9]{16,255})\\b"), "Token"),
        P(
            RegionKind.SECRET,
            Regex("\\b(?:postgres(?:ql)?|mysql|mongodb(?:\\+srv)?|redis|amqps?)://[^\\s:/@]{1,64}:[^\\s@]{6,256}@", RegexOption.IGNORE_CASE),
            "DB credentials",
        ),
    )

    private val assignedSecret = Regex(
        "\\b(api[_ -]?key|access[_ -]?token|auth[_ -]?token|token|client[_ -]?secret|password|passwd|pwd|secret|pin)" +
            "\\b\\s*[:=]\\s*[\"']?([A-Za-z0-9_./+~$@!#%^&*=-]{4,256})",
        RegexOption.IGNORE_CASE,
    )
    private val bearer = Regex("\\bBearer\\s+([A-Za-z0-9._~+/=-]{16,255})\\b", RegexOption.IGNORE_CASE)
    private val jwt = Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b")
    private val privateKeyBegin = Regex("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")
    private val privateKeyEnd = Regex("-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")

    private val phoneCandidate = Regex(
        "(?<!\\d)(?:\\+\\d{1,3}[\\s.-]?)?(?:\\(\\d{2,4}\\)|\\d{2,4})[\\s.-]\\d{3,4}[\\s.-]\\d{3,4}(?!\\d)"
    )
    private val ipv4Candidate = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val ipv6Candidate = Regex(
        "(?<![0-9A-F:])(?:[0-9A-F]{0,4}:){2,7}[0-9A-F]{0,4}(?![0-9A-F:])",
        RegexOption.IGNORE_CASE,
    )
    private val macShape = Regex("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$", RegexOption.IGNORE_CASE)
    private val cardCandidate = Regex("(?<!\\d)(?:\\d[ -]*?){13,19}(?!\\d)")
    // Long bare digit runs: NIK (16), account/ID numbers, serials.
    private val numberCandidate = Regex("(?<![\\d.])(?:\\d[ -]?){8,19}\\d?(?![\\d.])")

    private const val MAX_SCAN_LEN = 200_000

    fun match(text: String): List<SensitiveHit> {
        if (text.length > MAX_SCAN_LEN) return emptyList()
        val hits = ArrayList<SensitiveHit>()
        patterns.forEach { p ->
            p.regex.findAll(text).forEach { hits += SensitiveHit(p.kind, it.range, p.label) }
        }
        assignedSecret.findAll(text).forEach { m ->
            val key = m.groups[1]?.value.orEmpty()
            val value = m.groups[2]?.value.orEmpty()
            if (looksLikeAssignedSecret(key, value)) {
                hits += SensitiveHit(RegionKind.SECRET, m.range, "Assigned secret")
            }
        }
        bearer.findAll(text).forEach { m ->
            if (looksLikeToken(m.groups[1]?.value.orEmpty())) {
                hits += SensitiveHit(RegionKind.SECRET, m.range, "Bearer token")
            }
        }
        jwt.findAll(text).forEach { m ->
            hits += SensitiveHit(RegionKind.SECRET, m.range, "JWT")
        }
        privateKeyBegin.findAll(text).forEach { begin ->
            val searchEnd = (begin.range.last + 1 + 8_192).coerceAtMost(text.length)
            val end = privateKeyEnd.find(text, begin.range.last + 1)
                ?.takeIf { it.range.last < searchEnd }
            hits += SensitiveHit(
                RegionKind.SECRET,
                if (end == null) begin.range else begin.range.first..end.range.last,
                "Private key",
            )
        }
        ipv4Candidate.findAll(text).forEach { m ->
            if (m.value.split('.').all { it.toIntOrNull() in 0..255 }) {
                hits += SensitiveHit(RegionKind.NETWORK, m.range, "IPv4")
            }
        }
        ipv6Candidate.findAll(text).forEach { m ->
            if (!macShape.matches(m.value)) {
                hits += SensitiveHit(RegionKind.NETWORK, m.range, "IPv6")
            }
        }
        cardCandidate.findAll(text).forEach { m ->
            val digits = m.value.filter(Char::isDigit)
            if (digits.length in 13..19 && passesLuhn(digits)) {
                hits += SensitiveHit(RegionKind.CARD, m.range, "Payment card")
            }
        }
        phoneCandidate.findAll(text).forEach { m ->
            val digits = m.value.filter(Char::isDigit)
            val isCard = digits.length in 13..19 && passesLuhn(digits)
            val hasPhoneSyntax = m.value.startsWith('+') ||
                m.value.any { it == '(' || it == ')' || it == '-' || it == '.' }
            if (digits.length in 10..15 && hasPhoneSyntax && !isCard &&
                !ipv4Candidate.matches(m.value)
            ) {
                hits += SensitiveHit(RegionKind.PHONE, m.range, "Phone")
            }
        }
        numberCandidate.findAll(text).forEach { m ->
            val digits = m.value.filter(Char::isDigit)
            val alreadyCovered = hits.any { it.range.covers(m.range) && it.kind != RegionKind.NUMBER }
            if (digits.length in 8..19 && !alreadyCovered &&
                !ipv4Candidate.matches(m.value.trim())
            ) {
                hits += SensitiveHit(RegionKind.NUMBER, m.range, "Number sequence")
            }
        }
        return hits.distinctBy { Triple(it.kind, it.range.first, it.range.last) }
    }

    private fun IntRange.covers(o: IntRange): Boolean = first <= o.first && last >= o.last

    fun passesLuhn(digits: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum > 0 && sum % 10 == 0
    }

    private fun looksLikeAssignedSecret(key: String, value: String): Boolean {
        if (isPlaceholder(value)) return false
        if (key.equals("password", true) || key.equals("passwd", true) ||
            key.equals("pwd", true) || key.equals("pin", true)
        ) {
            return value.length >= 4
        }
        return looksLikeToken(value)
    }

    private fun looksLikeToken(value: String): Boolean {
        if (value.length < 16 || isPlaceholder(value)) return false
        return characterClasses(value) >= 2 && shannonEntropy(value) >= 3.0
    }

    private fun characterClasses(value: String): Int = listOf(
        value.any(Char::isLowerCase),
        value.any(Char::isUpperCase),
        value.any(Char::isDigit),
        value.any { !it.isLetterOrDigit() },
    ).count { it }

    private fun shannonEntropy(value: String): Double =
        value.groupingBy { it }.eachCount().values.sumOf { count ->
            val p = count.toDouble() / value.length
            -p * (ln(p) / ln(2.0))
        }

    private fun isPlaceholder(value: String): Boolean {
        val normalized = value.lowercase().trim('*', '•', '-', '_')
        if (normalized.isBlank() || normalized.toSet().size <= 3) return true
        return listOf("example", "changeme", "placeholder", "yourkey", "your-key", "password")
            .any { normalized.contains(it) }
    }
}
