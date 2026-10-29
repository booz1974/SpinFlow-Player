package nl.jeroen.massqueue

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendspinWireCompatTest {

    // Zoals sendspin-jvm v0.3.4 ze verstuurt
    private val hello = """{"type":"client/hello","payload":{"client_id":"c1","name":"S25","version":1,
        "supported_roles":["player@v1","metadata@v1","artwork@v1","controller@v1"],
        "player@v1_support":{"supported_formats":[{"codec":"pcm","channels":2,"sample_rate":48000,"bit_depth":16}],
          "buffer_capacity":262144,"supported_commands":["volume","mute"]},
        "artwork@v1_support":{"channels":[{"source":"album","format":"jpeg","media_width":600,"media_height":600}]}}}"""

    private val state = """{"type":"client/state","payload":{"available":true,"player":{"volume":80,"muted":false,
        "static_delay_ms":0,"required_lead_time_ms":0,"min_buffer_ms":0,"supported_commands":["set_static_delay"]}}}"""

    @Test
    fun `hello verliest verouderde velden`() {
        val out = JSONObject(SendspinWireCompat().outgoing(hello)).getJSONObject("payload")
        assertFalse(out.has("artwork@v1_support"))
        assertFalse(out.getJSONObject("player@v1_support").has("supported_commands"))
        assertEquals(262144, out.getJSONObject("player@v1_support").getInt("buffer_capacity"))
        assertTrue(out.getJSONArray("supported_roles").toString().contains("artwork@v1"))
    }

    @Test
    fun `state krijgt commands en artwork met nieuwe maatnamen`() {
        val compat = SendspinWireCompat()
        compat.outgoing(hello)
        val out = JSONObject(compat.outgoing(state)).getJSONObject("payload")
        assertEquals("""["volume","mute","set_output_delay"]""",
            out.getJSONObject("player").getJSONArray("supported_commands").toString())
        assertFalse(out.getJSONObject("player").has("static_delay_ms"))
        assertEquals(0, out.getJSONObject("player").getInt("output_delay_ms"))
        val ch = out.getJSONObject("artwork").getJSONArray("channels").getJSONObject(0)
        assertEquals(600, ch.getInt("width"))
        assertEquals(600, ch.getInt("height"))
        assertFalse(ch.has("media_width"))
        assertEquals(80, out.getJSONObject("player").getInt("volume"))
    }

    @Test
    fun `state zonder player krijgt alleen artwork`() {
        val compat = SendspinWireCompat()
        compat.outgoing(hello)
        val out = JSONObject(compat.outgoing("""{"type":"client/state","payload":{"available":true}}""")).getJSONObject("payload")
        assertFalse(out.has("player"))
        assertTrue(out.has("artwork"))
    }

    @Test
    fun `inkomende set_output_delay wordt set_static_delay`() {
        val cmd = """{"type":"server/command","payload":{"player":{"command":"set_output_delay","output_delay_ms":120}}}"""
        val player = JSONObject(SendspinWireCompat().incoming(cmd)).getJSONObject("payload").getJSONObject("player")
        assertEquals("set_static_delay", player.getString("command"))
        assertEquals(120, player.getInt("static_delay_ms"))
        assertFalse(player.has("output_delay_ms"))
        val volume = """{"type":"server/command","payload":{"player":{"command":"volume","volume":40}}}"""
        assertEquals(volume, SendspinWireCompat().incoming(volume))
    }

    @Test
    fun `andere berichten blijven ongewijzigd`() {
        val time = """{"type":"client/time","payload":{"client_transmitted":123}}"""
        assertEquals(time, SendspinWireCompat().outgoing(time))
        assertEquals("geen json", SendspinWireCompat().outgoing("geen json"))
    }
}
