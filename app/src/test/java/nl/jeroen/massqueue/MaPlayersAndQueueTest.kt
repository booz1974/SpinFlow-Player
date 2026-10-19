package nl.jeroen.massqueue

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** players/all, player_queues/all en player_queues/get+items uitlezen tegen een nep-MA. */
class MaPlayersAndQueueTest {
    private lateinit var ma: FakeMaServer

    @Before fun setUp() { ma = FakeMaServer() }
    @After fun tearDown() { ma.close() }

    private fun players(json: String): List<MassPlayer> = runBlocking {
        ma.on("players/all", json)
        ma.client().getAllPlayers()
    }

    @Test fun spelers_basisvelden_enSorteringOpNaam() {
        val l = players("""[
            {"player_id":"b","display_name":"zolder","name":"raw-b","playback_state":"playing","volume_level":30,"volume_muted":false,"powered":true,"provider":"sonos","type":"player","device_info":{"model":"Sonos One"}},
            {"player_id":"a","display_name":"Woonkamer","playback_state":"idle","volume_level":10}]""")
        assertEquals(listOf("a", "b"), l.map { it.id })
        val b = l[1]
        assertEquals("zolder", b.name)
        assertEquals("playing", b.playbackState)
        assertEquals(30, b.volumeLevel)
        assertEquals(false, b.volumeMuted)
        assertEquals(true, b.powered)
        assertEquals("sonos", b.provider)
        assertEquals("Sonos One", b.model)
    }

    @Test fun speler_zonderPlayerId_wordtOvergeslagen() {
        assertEquals(1, players("""[{"display_name":"x"},{"player_id":"","display_name":"y"},{"player_id":"ok","display_name":"z"}]""").size)
    }

    @Test fun speler_displayNameOntbreekt_valtTerugOpNaamDanId() {
        val l = players("""[{"player_id":"p1","name":"Naam"},{"player_id":"p2"}]""")
        assertEquals("Naam", l.first { it.id == "p1" }.name)
        assertEquals("p2", l.first { it.id == "p2" }.name)
    }

    @Test fun hide_in_ui_true_betekentVerborgen() {
        assertTrue(players("""[{"player_id":"p","hide_in_ui":true}]""").single().hiddenInMa)
    }

    @Test fun hide_player_in_ui_always_betekentVerborgen() {
        assertTrue(players("""[{"player_id":"p","hide_in_ui":false,"hide_player_in_ui":["always"]}]""").single().hiddenInMa)
        assertTrue(players("""[{"player_id":"p","hide_player_in_ui":["when_unavailable","always"]}]""").single().hiddenInMa)
    }

    @Test fun hide_player_in_ui_whenUnavailable_isNietVerborgen() {
        assertFalse(players("""[{"player_id":"p","hide_in_ui":false,"hide_player_in_ui":["when_unavailable","when_synced"]}]""").single().hiddenInMa)
        assertFalse(players("""[{"player_id":"p","hide_in_ui":false,"hide_player_in_ui":[]}]""").single().hiddenInMa)
        assertFalse(players("""[{"player_id":"p"}]""").single().hiddenInMa)
        assertFalse(players("""[{"player_id":"p","hide_player_in_ui":null}]""").single().hiddenInMa)
    }

    @Test fun can_group_with_nullLeegEnGevuld() {
        val l = players("""[
            {"player_id":"a"},
            {"player_id":"b","can_group_with":[]},
            {"player_id":"c","can_group_with":["x","y","","x"]}]""")
        assertNull(l.first { it.id == "a" }.canGroupWith)
        assertEquals(emptySet<String>(), l.first { it.id == "b" }.canGroupWith)
        assertEquals(setOf("x", "y"), l.first { it.id == "c" }.canGroupWith)
    }

    @Test fun can_group_with_jsonNull_isOnbekend() {
        assertNull(players("""[{"player_id":"a","can_group_with":null}]""").single().canGroupWith)
    }

    @Test fun volume_jsonNull_blijftNull_nietNul() {
        val p = players("""[{"player_id":"lamp","volume_level":null,"volume_muted":null,"powered":null,"group_volume":null}]""").single()
        assertNull(p.volumeLevel); assertNull(p.volumeMuted); assertNull(p.powered); assertNull(p.groupVolume)
    }

    @Test fun groep_membersEnOudeGroupChilds() {
        val l = players("""[
            {"player_id":"g1","type":"group","group_members":["a","b"],"group_volume":40,"volume_level":0},
            {"player_id":"g2","group_childs":["c","d"]},
            {"player_id":"a","synced_to":"g1","active_group":"g1"},
            {"player_id":"b","synced_to":null,"active_group":null}]""")
        val g1 = l.first { it.id == "g1" }
        assertEquals(listOf("a", "b"), g1.groupMembers); assertTrue(g1.isGroup); assertEquals(40, g1.groupVolume)
        assertEquals(listOf("c", "d"), l.first { it.id == "g2" }.groupMembers)
        assertEquals("g1", l.first { it.id == "a" }.syncedTo)
        assertEquals("g1", l.first { it.id == "a" }.activeGroup)
        assertNull(l.first { it.id == "b" }.syncedTo)
        assertNull(l.first { it.id == "b" }.activeGroup)
    }

    @Test fun universalPlayer_sendspinProtocolIds_zonderNative() {
        val p = players("""[{"player_id":"up1a2b","display_name":"Telefoon Bob","output_protocols":[
            {"output_protocol_id":"native"},{"output_protocol_id":"sendspin-abc"},{"output_protocol_id":""},{}]}]""").single()
        assertEquals(setOf("sendspin-abc"), p.outputProtocolIds)
    }

    @Test fun resultLeegOfGeenArray_geeftLegeLijst() {
        assertTrue(players("[]").isEmpty())
        assertTrue(players("{}").isEmpty())
    }

    @Test fun serverFout500_gooitMassApiException() {
        try {
            runBlocking { ma.client().getAllPlayers() }
            fail("verwachtte MassApiException")
        } catch (e: MassApiException) {
            // verwacht
        }
    }

    // ---------- Wachtrij ----------

    @Test fun queueSummaries() = runBlocking {
        ma.on("player_queues/all", """[{"queue_id":"q1","items":3,"state":"playing"},{"queue_id":"q2","items":0,"state":"idle"},{"queue_id":""},{"queue_id":"q3","state":"PLAYING"}]""")
        val s = ma.client().getAllQueueSummaries()
        assertEquals(listOf("q1", "q2", "q3"), s.map { it.queueId })
        assertTrue(s[0].hasItems && s[0].isPlaying)
        assertFalse(s[1].hasItems); assertFalse(s[1].isPlaying)
        assertFalse(s[2].hasItems); assertTrue(s[2].isPlaying)
    }

    @Test fun lege_wachtrij_haaltGeenItemsOp() = runBlocking {
        ma.on("player_queues/get", """{"current_index":null,"items":0,"active":false,"display_name":"Queue"}""")
        val q = ma.client().getQueue("q")!!
        assertTrue(q.items.isEmpty())
        assertNull(q.playlistName)
        assertTrue(ma.commands.none { it.first == "player_queues/items" })
    }

    @Test fun wachtrij_resultNull_geeftNull() = runBlocking {
        ma.on("player_queues/get", "null")
        assertNull(ma.client().getQueue("q"))
    }

    @Test fun lange_wachtrij_vraagVenster_vanIndexMin2_totPlus50() = runBlocking {
        ma.on("player_queues/get", """{"current_index":500,"items":2000,"active":true}""")
        ma.on("player_queues/items", "[]")
        ma.client().getQueue("q")
        val args = ma.commands.first { it.first == "player_queues/items" }.second
        assertEquals(498, args.getInt("offset"))
        assertEquals(53, args.getInt("limit"))
    }

    @Test fun wachtrij_aanHetEinde_beperktVenster() = runBlocking {
        ma.on("player_queues/get", """{"current_index":9,"items":10,"active":true}""")
        ma.on("player_queues/items", "[]")
        ma.client().getQueue("q")
        val args = ma.commands.first { it.first == "player_queues/items" }.second
        assertEquals(7, args.getInt("offset"))
        assertEquals(3, args.getInt("limit"))
    }

    @Test fun wachtrij_items_krijgenAbsoluteIndexEnVelden() = runBlocking {
        ma.on("player_queues/get", """{"current_index":3,"items":6,"active":true,"shuffle_enabled":true,"repeat_mode":"all","elapsed_time":12.5,"autoplay_enabled":true,"display_name":"Mijn mix"}""")
        ma.on("player_queues/items", """[
            {"queue_item_id":"i1","name":"fallback","duration":200,"media_item":{"name":"Echo","uri":"spotify://track/1","media_type":"track",
              "artists":[{"name":"Pink Floyd","uri":"spotify://artist/9"},{"name":"Gast"}],"album":{"name":"Meddle"},"metadata":{"year":1971}}},
            {"queue_item_id":"i2","name":"Zonder media","media_item":null}]""")
        val q = ma.client().getQueue("q")!!
        assertEquals(3, q.currentIndex); assertTrue(q.shuffleEnabled); assertEquals("all", q.repeatMode)
        assertEquals("Mijn mix", q.playlistName); assertTrue(q.autoplayEnabled)
        assertEquals(1, ma.commands.first { it.first == "player_queues/items" }.second.getInt("offset"))
        val t = q.items[0]
        assertEquals(1, t.absoluteIndex); assertEquals("Echo", t.title)
        assertEquals("Pink Floyd, Gast - Meddle", t.subtitle)
        assertEquals(200, t.durationSeconds); assertEquals("spotify://artist/9", t.artistUri)
        assertEquals(1971, t.year); assertEquals("i1", t.queueItemId)
        assertEquals(2, q.items[1].absoluteIndex); assertEquals("Zonder media", q.items[1].title)
    }

    @Test fun wachtrij_displayNameWachtrij_wordtGenegeerd() = runBlocking {
        ma.on("player_queues/get", """{"current_index":0,"items":0,"display_name":"Wachtrij"}""")
        assertNull(ma.client().getQueue("q")!!.playlistName)
    }

    @Test fun wachtrij_dont_stop_the_music_oudeNaam() = runBlocking {
        ma.on("player_queues/get", """{"current_index":0,"items":0,"dont_stop_the_music_enabled":true}""")
        assertTrue(ma.client().getQueue("q")!!.autoplayEnabled)
    }

    @Test fun radio_streamMetadata_wordtOpHuidigeTrackGeplakt() = runBlocking {
        ma.on("player_queues/get", """{"current_index":0,"items":1,"active":true,
            "current_item":{"streamdetails":{"stream_metadata":{"title":"Artiest X - Titel Y","artist":"Artiest X","image_url":"https://img.example/c.jpg"}}}}""")
        ma.on("player_queues/items", """[{"queue_item_id":"r","name":"Radio","media_item":{"name":"Radio","media_type":"radio","uri":"radio://1"}}]""")
        val t = ma.client().getQueue("q")!!.items.single()
        assertEquals("Artiest X", t.streamArtist)
        assertEquals("Titel Y", t.streamTrack)
        assertEquals("https://img.example/c.jpg", t.streamImage)
    }

    @Test fun radio_streamTitleAlleen_wordtGesplitst() = runBlocking {
        ma.on("player_queues/get", """{"current_index":0,"items":1,"current_item":{"stream_title":"A - B - C"}}""")
        ma.on("player_queues/items", """[{"queue_item_id":"r","name":"R","media_item":{"name":"R"}}]""")
        val t = ma.client().getQueue("q")!!.items.single()
        assertEquals("A", t.streamArtist); assertEquals("B - C", t.streamTrack)
    }
}
