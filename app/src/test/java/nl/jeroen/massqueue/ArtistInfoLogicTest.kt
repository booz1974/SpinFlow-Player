package nl.jeroen.massqueue

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ArtistInfoLogicTest {
    private val api = MassApiClient("http://127.0.0.1:1")

    private fun guess(t: String): String? {
        val m = MassApiClient::class.java.getDeclaredMethod("guessLanguage", String::class.java)
        m.isAccessible = true
        return m.invoke(api, t) as String?
    }

    @Test fun guessLanguage_nl() =
        assertEquals("nl", guess("De band werd opgericht in 1990 en is een van de bekendste van het land met een grote fanbase."))

    @Test fun guessLanguage_en() =
        assertEquals("en", guess("The band was formed in 1990 and is one of the best known of the country with a big fanbase and their songs."))

    @Test fun guessLanguage_leegEnKort() {
        assertNull(guess(""))
        assertNull(guess("Rock"))
    }

    @Test fun guessLanguage_andereTaal_isNull() =
        assertNull(guess("Die Band wurde 1990 gegründet und ist eine der bekanntesten des Landes."))

    @Test fun songStory_nullOfLeegGeeftNullZonderNetwerk() = runBlocking {
        assertNull(api.getSongStory(null, "X"))
        assertNull(api.getSongStory("  ", "X"))
        assertNull(api.getSongStory("Song", null))
        assertNull(api.getSongStory("Song", " , x"))
        assertNull(api.getSongStory("(Remix)", "X"))   // titel wordt na schoonmaak leeg
    }

    @Test fun artistInfo_geenNaamGeenUri_isNull() = runBlocking {
        assertNull(api.getArtistInfo(null, null))
        assertNull(api.getArtistInfo(null, "  "))
        assertNull(api.getArtistInfo(null, ", x"))
    }
}
