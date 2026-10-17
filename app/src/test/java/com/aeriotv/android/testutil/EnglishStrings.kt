package com.aeriotv.android.testutil

import com.aeriotv.android.R
import com.aeriotv.android.core.locale.AppText
import java.io.File

/**
 * Gives [AppText] the app's English strings in plain JVM unit tests, read from
 * res/values (there is no Application to ask). Plurals use "one" for 1 and
 * "other" otherwise, as English does.
 */
object EnglishStrings {
    private val strings: Map<Int, String> by lazy { load("string", Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)) }
    private val plurals: Map<Int, Map<String, String>> by lazy { loadPlurals() }

    fun install() {
        AppText.initForTests { id, count ->
            if (count == null) {
                strings[id] ?: error("no English string for id $id")
            } else {
                val forms = plurals[id] ?: error("no English plurals for id $id")
                forms[if (count == 1) "one" else "other"] ?: forms.getValue("other")
            }
        }
    }

    private fun files(): List<File> =
        File("src/main/res/values").listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }
            .orEmpty().toList()

    private fun ids(type: Class<*>): Map<String, Int> =
        type.fields.associate { it.name to it.getInt(null) }

    private fun load(@Suppress("SameParameterValue") kind: String, regex: Regex): Map<Int, String> {
        val ids = ids(R.string::class.java)
        return files().flatMap { f -> regex.findAll(f.readText()).map { it.groupValues[1] to unescape(it.groupValues[2]) }.toList() }
            .mapNotNull { (name, text) -> ids[name]?.let { it to text } }
            .toMap()
            .also { check(it.isNotEmpty()) { "no $kind resources found" } }
    }

    private fun loadPlurals(): Map<Int, Map<String, String>> {
        val ids = ids(R.plurals::class.java)
        val block = Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
        val item = Regex("""<item quantity="(\w+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
        return files().flatMap { f ->
            block.findAll(f.readText()).map { b ->
                b.groupValues[1] to item.findAll(b.groupValues[2]).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
            }.toList()
        }.mapNotNull { (name, forms) -> ids[name]?.let { it to forms } }.toMap()
    }

    private fun unescape(raw: String): String {
        val t = raw.trim().let { if (it.length >= 2 && it.startsWith("\"") && it.endsWith("\"")) it.substring(1, it.length - 1) else it }
        return t.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }
}
