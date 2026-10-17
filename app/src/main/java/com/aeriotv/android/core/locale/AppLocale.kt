package com.aeriotv.android.core.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

/**
 * The app's language. [System] follows the device (French when the TV is
 * set to any French, English otherwise); the others override it for this
 * app only. Names are shown in their own language, so they are not
 * translated.
 */
enum class AppLanguage(val tag: String, val nativeName: String?) {
    System("", null),
    English("en", "English"),
    French("fr", "Français");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it != System && it.tag == tag } ?: System
    }
}

/**
 * Applies [AppLanguage]. Android 13+ has a per-app language of its own (also
 * set from the TV's App languages page), so the system applies it everywhere.
 * Older Android (the Shield is on 11) has none: the choice is kept in a small
 * SharedPreferences file (read before anything starts, and per device, so
 * settings sync never carries it) and applied to the application and each
 * activity here.
 */
object AppLocale {
    private const val PREFS = "app_language"
    private const val KEY = "language"

    fun current(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) AppLanguage.System else AppLanguage.fromTag(locales[0].language)
        } else {
            AppLanguage.fromTag(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null),
            )
        }

    /**
     * Sets the language. Android 13+ restarts the activity by itself; older
     * Android updates the application's resources and recreates [activity].
     */
    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == AppLanguage.System) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(language.tag)
            return
        }
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, language.tag).commit()
        applyToApp(activity.applicationContext, overrideLocale(activity) ?: systemLocale())
        activity.recreate()
    }

    /** Application.attachBaseContext: the application's context in the chosen language. */
    fun wrap(base: Context): Context {
        val locale = overrideLocale(base) ?: return base
        Locale.setDefault(locale)
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(locale) },
        )
    }

    /**
     * Activity.attachBaseContext, after super: the activity's resources in the
     * chosen language. An override configuration survives the configuration
     * changes the activity handles itself.
     */
    fun applyTo(activity: Activity, base: Context) {
        val locale = overrideLocale(base) ?: return
        activity.applyOverrideConfiguration(Configuration().apply { setLocale(locale) })
    }

    /**
     * Application.onConfigurationChanged: a device configuration change resets
     * the default locale and the application's resources to the device's.
     */
    fun keepOverride(app: Context) {
        overrideLocale(app)?.let { applyToApp(app, it) }
    }

    private fun applyToApp(app: Context, locale: Locale) {
        Locale.setDefault(locale)
        @Suppress("DEPRECATION")
        app.resources.updateConfiguration(
            Configuration(app.resources.configuration).apply { setLocale(locale) },
            app.resources.displayMetrics,
        )
    }

    /**
     * The locale to force, or null to follow the device. The device's own
     * locale is kept when it already has the chosen language (fr-BE stays
     * fr-BE), so dates and numbers keep the region.
     */
    private fun overrideLocale(context: Context): Locale? {
        if (Build.VERSION.SDK_INT >= 33) return null
        val language = current(context)
        if (language == AppLanguage.System) return null
        val device = systemLocale()
        return if (device.language == language.tag) device else Locale.forLanguageTag(language.tag)
    }

    private fun systemLocale(): Locale =
        android.content.res.Resources.getSystem().configuration.locales[0]
}

/**
 * Text for code with no Composable or Context at hand (repositories, network
 * errors, notifications built far from a screen). Composables use
 * stringResource, code with a Context uses context.getString.
 */
object AppText {
    private lateinit var app: Context

    /** Plain JVM unit tests have no Application: they hand in a lookup instead. */
    private var testLookup: ((id: Int, count: Int?) -> String)? = null

    /** Application.attachBaseContext, so it is ready before anything runs. */
    fun init(application: Context) {
        app = application
    }

    /** Unit tests: resolve ids with [lookup] (the plural count is null for plain strings). */
    @androidx.annotation.VisibleForTesting
    fun initForTests(lookup: (id: Int, count: Int?) -> String) {
        testLookup = lookup
    }

    fun get(@StringRes id: Int): String = testLookup?.invoke(id, null) ?: app.getString(id)

    fun get(@StringRes id: Int, vararg args: Any): String =
        testLookup?.let { String.format(it(id, null), *args) } ?: app.getString(id, *args)

    fun plural(id: Int, count: Int, vararg args: Any): String =
        testLookup?.let { String.format(it(id, count), *args) }
            ?: app.resources.getQuantityString(id, count, *args)
}
