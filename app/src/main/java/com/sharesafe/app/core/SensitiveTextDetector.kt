package com.sharesafe.app.core

import android.graphics.Rect
import kotlin.math.ln

data class SensitiveHit(
    val kind: RegionKind,
    val range: IntRange,
    val label: String,
    val confidence: Float = 1f,
    /** The exact matched substring — used for cloak text and apply-to-similar. */
    val matched: String = "",
)

/** Joined OCR text + per-span char ranges — shared by regex pass and entity extraction. */
class SpanIndex(val joined: String, val ranges: List<Pair<IntRange, OcrSpan>>) {
    fun rectsFor(range: IntRange): List<Rect> =
        ranges.filter { (r, _) -> range.overlaps(r) }.map { (_, s) -> s.bounds }

    private fun IntRange.overlaps(o: IntRange): Boolean = first <= o.last && o.first <= last
}

/**
 * Maps sensitive-looking text onto OCR span bounds. Adapted from SnapCrop's
 * regex pass (MIT) — Latin OCR spans joined, pattern ranges mapped back to
 * whichever OCR element boxes they overlap.
 */
object SensitiveTextDetector {

    fun buildIndex(spans: List<OcrSpan>): SpanIndex {
        val spanRanges = mutableListOf<Pair<IntRange, OcrSpan>>()
        val joined = buildString {
            spans.forEachIndexed { index, span ->
                val start = length
                append(span.text)
                spanRanges.add((start until length) to span)
                if (index != spans.lastIndex) append(span.separatorAfter)
            }
        }
        return SpanIndex(joined, spanRanges)
    }

    fun detectRegions(
        spans: List<OcrSpan>,
        imageWidth: Int,
        imageHeight: Int,
        customRules: List<CustomRule> = emptyList(),
    ): List<RedactRegion> {
        if (spans.isEmpty()) return emptyList()
        val index = buildIndex(spans)
        val hits = SensitivePatterns.match(index.joined) +
            SensitivePatterns.matchCustom(index.joined, customRules)
        return regionsFromHits(index, hits, imageWidth, imageHeight)
    }

    /** Map hits to span bounds and merge — also used for entity-extraction hits. */
    fun regionsFromHits(
        index: SpanIndex,
        hits: List<SensitiveHit>,
        imageWidth: Int,
        imageHeight: Int,
    ): List<RedactRegion> {
        val regions = mutableListOf<RedactRegion>()
        hits.forEach { hit ->
            index.rectsFor(hit.range).forEach { bounds ->
                regions += RedactRegion.new(
                    rect = bounds.padded(0.08f, 0.18f, imageWidth, imageHeight),
                    kind = hit.kind,
                    detail = hit.label,
                    confidence = hit.confidence,
                    sourceText = hit.matched,
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
                val prev = out[existing]
                val merged = Rect(prev.rect).apply { union(r.rect) }
                out[existing] = prev.copy(
                    rect = merged,
                    confidence = minOf(prev.confidence, r.confidence),
                    sourceText = prev.sourceText.ifEmpty { r.sourceText },
                )
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
                        out[i] = a.copy(
                            rect = Rect(a.rect).apply { union(b.rect) },
                            confidence = minOf(a.confidence, b.confidence),
                        )
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
    /** Regexes whose capture group 1 marks the sensitive value. */
    private data class CP(val kind: RegionKind, val regex: Regex, val label: String)

    private val patterns = listOf(
        P(RegionKind.EMAIL, Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), "Email"),
        P(RegionKind.NETWORK, Regex("\\b(?:[0-9A-F]{2}[:-]){5}[0-9A-F]{2}\\b", RegexOption.IGNORE_CASE), "MAC address"),
        P(RegionKind.SECRET, Regex("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b"), "AWS key"),
        P(RegionKind.SECRET, Regex("\\bAIza[0-9A-Za-z_-]{35}\\b"), "API key"),
        P(RegionKind.SECRET, Regex("\\b(?:gh[pousr]_[A-Za-z0-9]{36,255}|github_pat_[A-Za-z0-9_]{22,255})\\b"), "GitHub token"),
        P(RegionKind.SECRET, Regex("\\b(?:xox[baprs]-[A-Za-z0-9-]{20,255}|sk_(?:live|test)_[A-Za-z0-9]{16,255})\\b"), "Token"),
        // Additional well-known secret prefixes (ShotShield-style checksums where possible).
        P(RegionKind.SECRET, Regex("\\bsk-[A-Za-z0-9]{20,255}\\b"), "API key"),
        P(RegionKind.SECRET, Regex("\\b(?:pk|rk)_(?:live|test)_[A-Za-z0-9]{16,255}\\b"), "Key"),
        P(RegionKind.SECRET, Regex("\\bxapp-[0-9A-Za-z-]{20,255}\\b"), "Token"),
        P(RegionKind.SECRET, Regex("\\bdop_v1_[a-f0-9]{64}\\b"), "API token"),
        P(RegionKind.SECRET, Regex("\\bEAAB[A-Za-z0-9]{20,255}\\b"), "Access token"),
        P(RegionKind.SECRET, Regex("\\bSG\\.[A-Za-z0-9_-]{16,32}\\.[A-Za-z0-9_-]{16,64}\\b"), "API key"),
        P(
            RegionKind.SECRET,
            Regex("\\b(?:postgres(?:ql)?|mysql|mongodb(?:\\+srv)?|redis|amqps?)://[^\\s:/@]{1,64}:[^\\s@]{6,256}@", RegexOption.IGNORE_CASE),
            "DB credentials",
        ),
    )

    // IBAN handled separately — mod-97 checksum gate (regex shape alone is too loose).
    private val ibanCandidate = Regex("\\b[A-Z]{2}\\d{2}(?:[ ]?[A-Z0-9]){11,30}\\b")

    private val assignedSecret = Regex(
        "\\b(api[_ -]?key|access[_ -]?token|auth[_ -]?token|token|client[_ -]?secret|password|passwd|pwd|secret|pin)" +
            "\\b\\s*[:=]\\s*[\"']?([A-Za-z0-9_./+~$@!#%^&*=-]{4,256})",
        RegexOption.IGNORE_CASE,
    )
    // Indonesian context patterns — the keyword label tells the user what kind
    // of identifier was found instead of a generic "number".
    private val contextPatterns = listOf(
        CP(
            RegionKind.NUMBER,
            Regex("\\b(?:nik|no\\.?\\s?ktp|ktp|nip|noid)\\b\\s*[:#.]?\\s*(\\d{16})\\b", RegexOption.IGNORE_CASE),
            "NIK / KTP",
        ),
        CP(
            RegionKind.NUMBER,
            Regex("\\b(?:npwp)\\b\\s*[:#.]?\\s*(\\d{2}\\.\\d{3}\\.\\d{3}\\.\\d-\\d{3}\\.\\d{3})", RegexOption.IGNORE_CASE),
            "NPWP",
        ),
        CP(
            RegionKind.CARD,
            Regex("\\b(?:no\\.?\\s?rek(?:ening)?|rek(?:ening)?|a\\.?n\\.?|account|bank)\\b\\s*[:#.\\-]?\\s*(?:[a-z]{2,8}\\s)?(\\d{8,16})\\b", RegexOption.IGNORE_CASE),
            "Bank account",
        ),
        CP(
            RegionKind.NUMBER,
            Regex("\\b(?:plat|nomor polisi|nopol|tnkb|kendaraan)\\b(?:\\s+[a-z]{1,8}){0,3}\\s*[:#.]?\\s*([A-Z]{1,2}\\s?\\d{1,4}\\s?[A-Z]{0,3})\\b", RegexOption.IGNORE_CASE),
            "Plat nomor",
        ),
    )
    private val npwpShape = Regex("\\b\\d{2}\\.\\d{3}\\.\\d{3}\\.\\d-\\d{3}\\.\\d{3}\\b")

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

    /**
     * Keywords that raise a nearby match to high confidence — the value next to
     * "password:" or "OTP" is almost surely sensitive even if the shape is weak.
     */
    private val contextBoosters = listOf(
        "password", "passwd", "pwd", "pin", "otp", "kode", "verifikasi", "verification",
        "token", "secret", "cvv", "cvc", "saldo", "balance", "rekening", "account",
        "nik", "ktp", "npwp", "api key", "apikey", "ssn", "serial",
    )
    private const val CONTEXT_WINDOW = 48

    fun match(text: String): List<SensitiveHit> {
        if (text.length > MAX_SCAN_LEN) return emptyList()
        val hits = ArrayList<SensitiveHit>()
        patterns.forEach { p ->
            p.regex.findAll(text).forEach {
                hits += SensitiveHit(p.kind, it.range, p.label, matched = it.value)
            }
        }
        ibanCandidate.findAll(text).forEach { m ->
            val normalized = m.value.replace(" ", "")
            if (validIban(normalized)) {
                hits += SensitiveHit(RegionKind.CARD, m.range, "IBAN", matched = m.value)
            }
        }
        npwpShape.findAll(text).forEach { m ->
            hits += SensitiveHit(RegionKind.NUMBER, m.range, "NPWP", matched = m.value)
        }
        contextPatterns.forEach { cp ->
            cp.regex.findAll(text).forEach { m ->
                val g = m.groups[1] ?: return@forEach
                if (hits.none { it.range.covers(g.range) }) {
                    hits += SensitiveHit(cp.kind, g.range, cp.label, matched = g.value)
                }
            }
        }
        assignedSecret.findAll(text).forEach { m ->
            val key = m.groups[1]?.value.orEmpty()
            val value = m.groups[2]?.value.orEmpty()
            if (looksLikeAssignedSecret(key, value)) {
                hits += SensitiveHit(RegionKind.SECRET, m.range, "Assigned secret", matched = m.value)
            }
        }
        bearer.findAll(text).forEach { m ->
            if (looksLikeToken(m.groups[1]?.value.orEmpty())) {
                hits += SensitiveHit(RegionKind.SECRET, m.range, "Bearer token", matched = m.value)
            }
        }
        jwt.findAll(text).forEach { m ->
            hits += SensitiveHit(RegionKind.SECRET, m.range, "JWT", matched = m.value)
        }
        privateKeyBegin.findAll(text).forEach { begin ->
            val searchEnd = (begin.range.last + 1 + 8_192).coerceAtMost(text.length)
            val end = privateKeyEnd.find(text, begin.range.last + 1)
                ?.takeIf { it.range.last < searchEnd }
            hits += SensitiveHit(
                RegionKind.SECRET,
                if (end == null) begin.range else begin.range.first..end.range.last,
                "Private key",
                matched = begin.value,
            )
        }
        ipv4Candidate.findAll(text).forEach { m ->
            if (m.value.split('.').all { it.toIntOrNull() in 0..255 }) {
                hits += SensitiveHit(RegionKind.NETWORK, m.range, "IPv4", matched = m.value)
            }
        }
        ipv6Candidate.findAll(text).forEach { m ->
            if (!macShape.matches(m.value)) {
                hits += SensitiveHit(RegionKind.NETWORK, m.range, "IPv6", matched = m.value)
            }
        }
        cardCandidate.findAll(text).forEach { m ->
            val digits = m.value.filter(Char::isDigit)
            if (digits.length in 13..19 && passesLuhn(digits)) {
                hits += SensitiveHit(RegionKind.CARD, m.range, "Payment card", matched = m.value)
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
                // A bare run without + or country hint is plausible but not certain.
                val conf = if (m.value.startsWith('+')) 1f else 0.8f
                hits += SensitiveHit(RegionKind.PHONE, m.range, "Phone", conf, matched = m.value)
            }
        }
        numberCandidate.findAll(text).forEach { m ->
            val digits = m.value.filter(Char::isDigit)
            val alreadyCovered = hits.any { it.range.covers(m.range) && it.kind != RegionKind.NUMBER }
            if (digits.length in 8..19 && !alreadyCovered &&
                !ipv4Candidate.matches(m.value.trim())
            ) {
                // 16 digits with a valid province-prefix shape is very likely a NIK;
                // a bare digit run with no label is a "maybe" the user should review.
                val isNikShape = digits.length == 16
                hits += SensitiveHit(
                    RegionKind.NUMBER, m.range,
                    if (isNikShape) "NIK / ID number" else "Number sequence",
                    confidence = if (isNikShape) 0.85f else 0.55f,
                    matched = m.value,
                )
            }
        }

        // Context boost: a keyword near a weak match upgrades it to confident.
        val lowered = text.lowercase()
        return hits.map { h ->
            if (h.confidence >= 1f) h else {
                val from = (h.range.first - CONTEXT_WINDOW).coerceAtLeast(0)
                val to = (h.range.last + 1 + CONTEXT_WINDOW).coerceAtMost(text.length)
                val window = lowered.substring(from, to)
                if (contextBoosters.any { it in window }) h.copy(confidence = 1f) else h
            }
        }.distinctBy { Triple(it.kind, it.range.first, it.range.last) }
    }

    /** User rules — every regex/wildcard hit becomes a CUSTOM region. */
    fun matchCustom(text: String, rules: List<CustomRule>): List<SensitiveHit> {
        if (text.length > MAX_SCAN_LEN || rules.isEmpty()) return emptyList()
        val hits = ArrayList<SensitiveHit>()
        rules.forEach { rule ->
            val re = rule.toRegex() ?: return@forEach
            re.findAll(text).forEach { m ->
                if (m.value.isNotBlank()) {
                    hits += SensitiveHit(
                        RegionKind.CUSTOM, m.range, rule.label,
                        confidence = 0.9f, matched = m.value,
                    )
                }
            }
        }
        return hits
    }

    /** ISO 13616 mod-97 check. */
    fun validIban(iban: String): Boolean {
        if (iban.length !in 15..34) return false
        var remainder = 0
        (iban.substring(4) + iban.substring(0, 4)).forEach { ch ->
            val code = when {
                ch.isDigit() -> ch - '0'
                ch.isLetter() -> ch.uppercaseChar() - 'A' + 10
                else -> return false
            }
            // Two-digit letters produce two chars worth of digits — fold them in.
            val digits = code.toString()
            digits.forEach { d -> remainder = (remainder * 10 + (d - '0')) % 97 }
        }
        return remainder == 1
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
