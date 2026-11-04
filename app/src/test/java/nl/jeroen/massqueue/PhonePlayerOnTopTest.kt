package nl.jeroen.massqueue

import org.junit.Assert.*
import org.junit.Test

class PhonePlayerOnTopTest {
    private fun p(id: String, proto: Set<String> = emptySet(), hidden: Boolean = false) =
        MassPlayer(id, id, "idle", 10, false, outputProtocolIds = proto, hiddenInMa = hidden)

    @Test fun standaardStaatAan() {
        assertTrue(UiState().phonePlayerOnTop)
    }

    @Test fun isOwnPlayer_opIdEnOpProtocolId() {
        val s = UiState(phonePlayerClientId = "abc")
        assertTrue(s.isOwnPlayer(p("abc")))
        assertTrue(s.isOwnPlayer(p("up1", setOf("abc"))))
        assertFalse(s.isOwnPlayer(p("other")))
        assertFalse(UiState().isOwnPlayer(p("abc")))
    }

    @Test fun eigenTelefoonBlijftZichtbaarOokAlsHidden() {
        val s = UiState(phonePlayerClientId = "abc")
        assertTrue(s.isPlayerListed(p("abc", hidden = true)))
        assertFalse(s.isPlayerListed(p("x", hidden = true)))
    }
}
