package com.sharesafe.app.core

import android.content.Context
import android.net.Uri

/** Thin SharedPreferences wrapper for user settings + resumable batch state. */
object Prefs {
    private const val FILE = "sharesafe_prefs"
    private const val KEY_BLACKLIST = "blacklist"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_APPLY_ALL = "apply_to_all"
    private const val KEY_HINTS_SEEN = "hints_seen"
    private const val KEY_DISABLED_KINDS = "disabled_kinds"
    private const val KEY_QUEUE = "queue_uris"
    private const val KEY_QUEUE_POS = "queue_pos"
    private const val KEY_CUSTOM_RULES = "custom_rules"
    private const val KEY_WATCH_SHOTS = "watch_screenshots"
    private const val KEY_LAST_SHOT = "last_shot_seen"
    private const val KEY_LAST_EMOJI = "last_emoji"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- personal blacklist ("always redact") ----
    fun blacklist(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BLACKLIST, emptySet()).orEmpty()
            .map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun setBlacklist(context: Context, words: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_BLACKLIST, words.map { it.trim() }.filter { it.isNotEmpty() }.toSet())
            .apply()
    }

    // ---- settings ----
    fun dynamicColor(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DYNAMIC_COLOR, false)

    fun setDynamicColor(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_DYNAMIC_COLOR, on).apply()
    }

    fun applyToAll(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APPLY_ALL, true)

    fun setApplyToAll(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_APPLY_ALL, on).apply()
    }

    fun hintsSeen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HINTS_SEEN, false)

    fun markHintsSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_HINTS_SEEN, true).apply()
    }

    /** Kinds the user disabled — re-applied to the next image when applyToAll is on. */
    fun disabledKinds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_DISABLED_KINDS, emptySet()).orEmpty()

    fun setDisabledKinds(context: Context, kinds: Set<String>) {
        prefs(context).edit().putStringSet(KEY_DISABLED_KINDS, kinds).apply()
    }

    // ---- custom detection rules ("label\u0001pattern" entries) ----
    fun customRules(context: Context): List<CustomRule> =
        prefs(context).getStringSet(KEY_CUSTOM_RULES, emptySet()).orEmpty()
            .mapNotNull { raw ->
                val sep = raw.indexOf('\u0001')
                if (sep <= 0) null
                else CustomRule(raw.take(sep).trim(), raw.substring(sep + 1).trim())
            }
            .filter { it.label.isNotEmpty() && it.pattern.isNotEmpty() }
            .sortedBy { it.label.lowercase() }

    fun setCustomRules(context: Context, rules: List<CustomRule>) {
        prefs(context).edit().putStringSet(
            KEY_CUSTOM_RULES,
            rules.map { it.label.trim() + "\u0001" + it.pattern.trim() }.toSet(),
        ).apply()
    }

    // ---- screenshot watcher (opt-in, needs media read permission) ----
    fun watchScreenshots(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WATCH_SHOTS, false)

    fun setWatchScreenshots(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_WATCH_SHOTS, on).apply()
    }

    /** MediaStore DATE_ADDED (seconds) of the newest screenshot we've surfaced. */
    fun lastShotSeen(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SHOT, 0L)

    fun setLastShotSeen(context: Context, sec: Long) {
        prefs(context).edit().putLong(KEY_LAST_SHOT, sec).apply()
    }

    fun lastEmoji(context: Context): String =
        prefs(context).getString(KEY_LAST_EMOJI, ImageRedactor.DEFAULT_EMOJI)
            ?: ImageRedactor.DEFAULT_EMOJI

    fun setLastEmoji(context: Context, emoji: String) {
        prefs(context).edit().putString(KEY_LAST_EMOJI, emoji).apply()
    }

    // ---- resumable queue (best effort — only usable while URI grants live) ----
    fun saveQueue(context: Context, uris: List<Uri>, pos: Int) {
        prefs(context).edit()
            .putString(KEY_QUEUE, uris.joinToString("\n") { it.toString() })
            .putInt(KEY_QUEUE_POS, pos)
            .apply()
    }

    fun savedQueue(context: Context): Pair<List<Uri>, Int>? {
        val raw = prefs(context).getString(KEY_QUEUE, null) ?: return null
        val uris = raw.split('\n').filter { it.isNotBlank() }.map { Uri.parse(it) }
        if (uris.isEmpty()) return null
        return uris to prefs(context).getInt(KEY_QUEUE_POS, 0).coerceIn(0, uris.size - 1)
    }

    fun clearQueue(context: Context) {
        prefs(context).edit().remove(KEY_QUEUE).remove(KEY_QUEUE_POS).apply()
    }
}
