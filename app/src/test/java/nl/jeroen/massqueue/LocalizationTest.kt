package nl.jeroen.massqueue

import java.util.Locale
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocalizationTest {
    private lateinit var oldLocale: Locale
    @Before fun setUp() { oldLocale = Locale.getDefault() }
    @After fun tearDown() { Locale.setDefault(oldLocale); Lang.choice = AppLanguage.SYSTEM }

    @Test fun fromKey_nullEnUnknown_valtTerugOpSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromKey(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromKey(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromKey("nl"))   // hoofdlettergevoelig
        assertEquals(AppLanguage.NL, AppLanguage.fromKey("NL"))
        assertEquals(AppLanguage.EN, AppLanguage.fromKey("EN"))
    }

    @Test fun tr_expliciteKeuze() {
        Lang.choice = AppLanguage.NL; assertEquals("a", tr("a", "b"))
        Lang.choice = AppLanguage.EN; assertEquals("b", tr("a", "b"))
    }

    @Test fun tr_systeemVolgtTelefoon() {
        Lang.choice = AppLanguage.SYSTEM
        Locale.setDefault(Locale("nl", "BE")); assertEquals("a", tr("a", "b"))
        Locale.setDefault(Locale("nl")); assertEquals("a", tr("a", "b"))
        Locale.setDefault(Locale.GERMAN); assertEquals("b", tr("a", "b"))
        Locale.setDefault(Locale.ENGLISH); assertEquals("b", tr("a", "b"))
    }

    @Test fun locale_volgtKeuze() {
        Lang.choice = AppLanguage.NL; assertEquals("nl", Lang.locale.language)
        Lang.choice = AppLanguage.EN; assertEquals("en", Lang.locale.language)
    }

    @Test fun largeImageUrl_basis() {
        assertEquals("http://x/img?size=1024", largeImageUrl("http://x/img?size=256"))
        assertEquals("http://x/img", largeImageUrl("http://x/img"))
    }

    /** Verwachting: alleen exact size=256 vervangen. Werkelijk: substring-replace. */
    @Test fun largeImageUrl_size2560_wordtNietVerminkt() {
        assertEquals("http://x/img?size=2560", largeImageUrl("http://x/img?size=2560"))
    }

    @Test fun isOwnPlayer_nullEnProtocolIds() {
        val p = MassPlayer("up1", "Telefoon", null, null, null, outputProtocolIds = setOf("abc"))
        assertFalse(UiState().isOwnPlayer(p))
        assertTrue(UiState(phonePlayerClientId = "up1").isOwnPlayer(p))
        assertTrue(UiState(phonePlayerClientId = "abc").isOwnPlayer(p))
        assertFalse(UiState(phonePlayerClientId = "zzz").isOwnPlayer(p))
    }
}
