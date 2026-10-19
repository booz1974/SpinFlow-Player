package nl.jeroen.massqueue

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject

/**
 * Nep Music Assistant: antwoordt op POST /api per `command` met een vooraf ingestelde JSON-tekst.
 * Onbekende commando's krijgen HTTP 500 (zoals een server die het commando niet kent).
 */
class FakeMaServer : AutoCloseable {
    val server = MockWebServer()
    private val responses = mutableMapOf<String, String>()
    val commands = mutableListOf<Pair<String, JSONObject>>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = JSONObject(request.body.readUtf8())
                val cmd = body.getString("command")
                synchronized(commands) { commands.add(cmd to body.getJSONObject("args")) }
                val r = responses[cmd] ?: return MockResponse().setResponseCode(500).setBody("unknown command")
                return MockResponse().setBody(r)
            }
        }
        server.start()
    }

    /** Basis-URL zoals MassApiClient die gebruikt (zonder slash aan het eind). */
    val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    /** [resultJson] is de waarde onder "result" (array of object). */
    fun on(command: String, resultJson: String) {
        responses[command] = "{\"result\": $resultJson}"
    }

    fun client() = MassApiClient(baseUrl, null)

    override fun close() = server.shutdown()
}
