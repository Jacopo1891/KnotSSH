package com.knotssh.presentation.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Languages shipped with the app.
 *
 * Adding one means: a new entry here, a matching `values-xx/strings.xml`, and a `<locale>` line
 * in `res/xml/locales_config.xml`. Nothing else in the app needs to change.
 */
enum class AppLanguage(val tag: String, val displayName: String) {
    /** Follows the system; its label comes from resources, not from here. */
    SYSTEM("", ""),
    ENGLISH("en", "English"),
    ITALIAN("it", "Italiano"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    SPANISH("es", "Español");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.isNotEmpty() && tag?.startsWith(it.tag) == true } ?: SYSTEM
    }
}

/**
 * AppCompat is the single source of truth: it persists the choice itself (through the system
 * locale service on API 33+, through its own store below it), so no app preference mirrors it.
 */
object LocaleManager {

    fun current(): AppLanguage =
        AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            if (language.tag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            }
        )
    }
}
