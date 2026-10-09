package nl.jeroen.massqueue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Taalkeuze in Instellingen. SYSTEM volgt de telefoontaal: Nederlands als die nl is, anders Engels. */
enum class AppLanguage {
    SYSTEM, NL, EN;

    companion object {
        fun fromKey(key: String?): AppLanguage = entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

/**
 * Huidige app-taal. Compose-state, zodat schermen die [tr] gebruiken meteen opnieuw tekenen
 * bij een wisseling; meldingen en foutteksten krijgen de nieuwe taal bij hun volgende update.
 */
object Lang {
    var choice by mutableStateOf(AppLanguage.SYSTEM)

    val isEnglish: Boolean
        get() = when (choice) {
            AppLanguage.NL -> false
            AppLanguage.EN -> true
            AppLanguage.SYSTEM -> Locale.getDefault().language != "nl"
        }

    /** Locale voor datums e.d. in de gekozen taal. */
    val locale: Locale get() = if (isEnglish) Locale.ENGLISH else Locale("nl")
}

/** Tekst in de gekozen app-taal: eerst Nederlands, dan Engels. */
fun tr(nl: String, en: String): String = if (Lang.isEnglish) en else nl
