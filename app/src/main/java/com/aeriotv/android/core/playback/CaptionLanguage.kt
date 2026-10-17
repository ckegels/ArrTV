package com.aeriotv.android.core.playback

import com.aeriotv.android.R
import com.aeriotv.android.core.locale.AppText

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

    /** The choice's name in the app's language (the English names in [CHOICES]
     *  are what an English device shows). */
    fun label(value: String): String {
        if (CHOICES.none { it.first == value }) return value
        val locale = java.util.Locale.getDefault()
        return when (value) {
            "" -> AppText.get(R.string.player_caption_language_original)
            "tv" -> AppText.get(R.string.player_caption_language_tv, locale.displayLanguage)
            else -> java.util.Locale(value).getDisplayLanguage(locale)
                .replaceFirstChar { it.titlecase(locale) }
        }
    }

    /** What the poll sends: null for the original, else two letters. */
    fun code(value: String): String? = when (value) {
        "" -> null
        "tv" -> java.util.Locale.getDefault().language.takeIf { it.length == 2 }
        else -> value.take(2)
    }
}
