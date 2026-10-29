package nl.jeroen.massqueue

import org.json.JSONArray
import org.json.JSONObject

/**
 * Zet uitgaande berichten van sendspin-jvm v0.3.4 om naar de huidige Sendspin-spec, zodat
 * aiosendspin (MA) ons niet meer als "non-compliant client" logt. Eén instantie per verbinding.
 *
 * - Spec #177: player-`supported_commands` horen in `client/state`, niet in de hello.
 * - Spec #195: artwork-kanalen horen in `client/state.artwork`, niet in `artwork@v1_support`.
 * - Spec #168: artwork-maten heten `width`/`height` i.p.v. `media_width`/`media_height`.
 * - Spec #164: `static_delay_ms`/`set_static_delay` heten `output_delay_ms`/`set_output_delay`;
 *   inkomende `server/command`s zetten we terug naar de oude naam ([incoming]).
 *
 * Een onversleutelde client blijft voor de server altijd op de oude wire (audio-header e.d.),
 * dus dit verandert alleen waar de velden staan. Kan weg zodra sendspin-jvm dit zelf doet.
 */
class SendspinWireCompat {

    private var helloCommands: JSONArray? = null
    private var artworkChannels: JSONArray? = null

    @Synchronized
    fun outgoing(text: String): String {
        val msg = runCatching { JSONObject(text) }.getOrNull() ?: return text
        val payload = msg.optJSONObject("payload") ?: return text
        when (msg.optString("type")) {
            "client/hello" -> rewriteHello(payload)
            "client/state" -> rewriteState(payload)
            else -> return text
        }
        return msg.toString()
    }

    private fun rewriteHello(payload: JSONObject) {
        helloCommands = payload.optJSONObject("player@v1_support")
            ?.remove("supported_commands") as? JSONArray
        val artwork = payload.remove("artwork@v1_support") as? JSONObject
        artworkChannels = artwork?.optJSONArray("channels")?.let(::renameDimensions)
    }

    /** Spec #164 terug naar de namen die sendspin-jvm v0.3.4 kent. */
    fun incoming(text: String): String {
        if (!text.contains("set_output_delay")) return text
        val msg = runCatching { JSONObject(text) }.getOrNull() ?: return text
        if (msg.optString("type") != "server/command") return text
        val player = msg.optJSONObject("payload")?.optJSONObject("player") ?: return text
        if (player.optString("command") != "set_output_delay") return text
        player.put("command", "set_static_delay")
        if (player.has("output_delay_ms")) player.put("static_delay_ms", player.remove("output_delay_ms"))
        return msg.toString()
    }

    private fun rewriteState(payload: JSONObject) {
        payload.optJSONObject("player")?.let { player ->
            val merged = linkedSetOf<String>()
            helloCommands?.let { for (i in 0 until it.length()) merged += it.optString(i) }
            player.optJSONArray("supported_commands")?.let { for (i in 0 until it.length()) merged += it.optString(i) }
            // Spec #164: static_delay heet nu output_delay
            val commands = merged.filter { it.isNotBlank() }
                .map { if (it == "set_static_delay") "set_output_delay" else it }
                .distinct()
            player.put("supported_commands", JSONArray(commands))
            if (player.has("static_delay_ms")) player.put("output_delay_ms", player.remove("static_delay_ms"))
        }
        // De server vergelijkt de config en herstart de artwork-stream alleen bij een wijziging
        val channels = artworkChannels
        if (channels != null && !payload.has("artwork")) {
            payload.put("artwork", JSONObject().put("channels", JSONArray(channels.toString())))
        }
    }

    private fun renameDimensions(channels: JSONArray): JSONArray {
        for (i in 0 until channels.length()) {
            val ch = channels.optJSONObject(i) ?: continue
            for ((old, new) in listOf("media_width" to "width", "media_height" to "height")) {
                if (ch.has(old)) {
                    val v = ch.remove(old)
                    if (!ch.has(new)) ch.put(new, v)
                }
            }
        }
        return channels
    }
}
