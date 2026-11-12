package com.aeriotv.android.core.playback

/**
 * The language generated captions are asked in (Dispatch More v248 translates them, its
 * fork/subtitles.md §9 step 4). "" = as spoken (no translation), "tv" = this TV's own language,
 * else a two-letter code -- the server's `&lang=`.
 */
object CaptionLanguage {
    val CHOICES: List<Pair<String, String>> = listOf(
        "" to "Original",
        "tv" to "This TV's language",
        "en" to "English",
        "nl" to "Dutch",
        "de" to "German",
        "fr" to "French",
        "it" to "Italian",
        "es" to "Spanish",
    )

    fun label(value: String): String {
        val name = CHOICES.firstOrNull { it.first == value }?.second ?: value
        return if (value == "tv") "$name (${java.util.Locale.getDefault().displayLanguage})" else name
    }

    /** What the poll sends: null for the original, else two letters. */
    fun code(value: String): String? = when (value) {
        "" -> null
        "tv" -> java.util.Locale.getDefault().language.takeIf { it.length == 2 }
        else -> value.take(2)
    }
}
