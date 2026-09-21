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
