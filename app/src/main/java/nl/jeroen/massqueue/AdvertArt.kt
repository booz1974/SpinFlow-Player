package nl.jeroen.massqueue

/**
 * Reclameblokken op de radio. Zenders sturen dan een titel als "Advert" mee; Music Assistant (en
 * iTunes) zoeken daar een willekeurige hoes bij, zoals een album van Blur. SpinFlow toont in dat
 * geval altijd de eigen "Commercial Break"-hoes die in de app zit, voor iedereen en ook offline.
 */
object AdvertArt {
    /** Hoes uit de app zelf (res/drawable-nodpi/commercial_break.jpg); Coil en Android Auto kunnen dit laden. */
    val URI: String get() = "android.resource://${BuildConfig.APPLICATION_ID}/drawable/commercial_break"

    private val pattern = Regex("""\b(advert|advertentie|advertisement|reclame|reclameblok|commercial break)\b""", RegexOption.IGNORE_CASE)

    /** True als artiest of titel van de radiometadata op een reclameblok wijst. */
    fun isAdvert(vararg parts: String?): Boolean = parts.any { !it.isNullOrBlank() && pattern.containsMatchIn(it) }
}
