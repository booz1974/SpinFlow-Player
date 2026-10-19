package nl.jeroen.massqueue

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Hoes-URLs afleiden uit realistische Music Assistant-JSON. De (private) resolveImageUrl wordt
 * getest via de publieke API: wachtrij-items, favoriete playlists/radios en zoekresultaten,
 * tegen een MockWebServer.
 *
 * LET OP: org.json:json (JVM) gedraagt zich anders dan het org.json van Android bij JSON-null:
 * optString geeft hier "" i.p.v. "null". De image-null-tests bewijzen dus alleen dat het
 * resultaat goed is; ze zouden de oorspronkelijke bug op de JVM niet laten falen.
 */
class MaImageUrlTest {
    private lateinit var ma: FakeMaServer
    private val base get() = ma.baseUrl

    @Before fun setUp() { ma = FakeMaServer() }
    @After fun tearDown() { ma.close() }

    /** Zet een wachtrij-item klaar en geeft de afgeleide imagePath van dat item. */
    private fun imageOfQueueItem(itemJson: String): String? = runBlocking {
        ma.on("player_queues/get", """{"current_index":0,"items":1,"active":true}""")
        ma.on("player_queues/items", "[$itemJson]")
        val q = ma.client().getQueue("q1")
        assertNotNull(q)
        assertEquals(1, q!!.items.size)
        q.items[0].imagePath
    }

    // ---------- Spotify ----------

    @Test fun spotify_directeHttpsUrl_inMetadataImages_blijftOngewijzigd() {
        val url = "https://i.scdn.co/image/ab67616d0000b273abcdef"
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","media_item":{"name":"x","uri":"spotify://track/1",
            "metadata":{"images":[{"type":"thumb","path":"$url","provider":"spotify","remotely_accessible":true}]}}}""")
        assertEquals(url, r)
    }

    @Test fun spotify_directeHttpsUrl_inImageObject_blijftOngewijzigd() {
        val url = "https://i.scdn.co/image/ab67616d0000b273abcdef"
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":{"type":"thumb","path":"$url","provider":"spotify","remotely_accessible":true},"media_item":{"name":"x"}}""")
        assertEquals(url, r)
    }

    /** Was een bug: de isInternal-test contains("8095") raakte ook externe URLs met die cijfers in de hash. */
    @Test fun spotify_urlMetCijfers8095_wordtNietHerschreven() {
        val url = "https://i.scdn.co/image/ab67616d0000b2738095c0ffee"
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":{"path":"$url","provider":"spotify","remotely_accessible":true}}""")
        assertEquals(url, r)
    }

    /** Was een bug (zelfde plek): .local als substring, bv. in een bestandsnaam op een CDN. */
    @Test fun externeUrlMetDotLocalInPad_wordtNietHerschreven() {
        val url = "https://cdn.example.com/art/cover.local.jpg"
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":{"path":"$url","remotely_accessible":true}}""")
        assertEquals(url, r)
    }

    @Test fun maServerUrlOpPoort8095_wordtWelHerschreven() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":{"path":"http://192.168.1.100:8095/imageproxy/abc?size=256","remotely_accessible":true}}""")
        assertEquals("$base/imageproxy/abc?size=256", r)
    }

    /** Eén afwijkend element in images mag de hele wachtrij niet laten mislukken. */
    @Test fun imagesMetNullEnZonderPad_neemtEersteBruikbare() {
        val url = "https://i.scdn.co/image/goed"
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","media_item":{"name":"x",
            "metadata":{"images":[null,{"type":"thumb"},{"path":"$url","remotely_accessible":true}]}}}""")
        assertEquals(url, r)
    }

    // ---------- Lokale bestanden ----------

    private fun localImage(extra: String = "") =
        """{"type":"thumb","path":"DJ/Pink Floyd/Meddle/4 - San Tropez.mp3","provider":"filesystem_local--abc","remotely_accessible":false$extra}"""

    private val withProxy = ",\"proxy_id\":\"PX123\""

    @Test fun lokaalBestand_metProxyId_loopViaImageproxy() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"San Tropez","image":${localImage(withProxy)},"media_item":{"name":"San Tropez"}}""")
        assertEquals("$base/imageproxy/PX123?size=256", r)
    }

    @Test fun lokaalBestand_inMetadataImages_metProxyId() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","media_item":{"name":"x","metadata":{"images":[${localImage(withProxy)}]}}}""")
        assertEquals("$base/imageproxy/PX123?size=256", r)
    }

    @Test fun lokaalBestand_zonderProxyId_gebruiktPathEnProviderQuery() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":${localImage()},"media_item":{"name":"x"}}""")
        assertNotNull(r)
        assertTrue(r!!, r.startsWith("$base/imageproxy?path="))
        assertTrue(r, r.contains("San+Tropez.mp3") || r.contains("San%20Tropez.mp3"))
        assertTrue(r, r.contains("provider=filesystem_local--abc"))
        assertTrue(r, r.endsWith("&size=256"))
    }

    /** Het mp3-pad zelf mag nooit als plaatje-URL eindigen. */
    @Test fun lokaalBestand_geeftNooitRuwMp3PadTerug() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":${localImage(withProxy)}}""")
        assertFalse(r!!.contains(".mp3"))
    }

    /** BUG (MassApiClient.kt:379): provider null zonder proxy_id geeft op Android provider=null in de URL (optString). Op de JVM niet te reproduceren. */
    @Test fun lokaalBestand_providerNull_komtNietAlsTekstNullInUrl() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":{"path":"a/b.mp3","provider":null,"remotely_accessible":false}}""")
        assertFalse(r!!, r.contains("provider=null"))
    }

    // ---------- null / ontbrekende velden ----------

    @Test fun imageNull_metHoesInMetadataImages() {
        val url = "https://i.scdn.co/image/zzz"
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":null,"media_item":{"name":"x","image":null,
            "metadata":{"images":[{"path":"$url","remotely_accessible":true}]}}}""")
        assertEquals(url, r)
    }

    @Test fun imageNull_valtTerugOpAlbumHoes() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":null,"media_item":{"name":"x","image":null,"metadata":{"images":null},
            "album":{"name":"Meddle","image":{"path":"DJ/Pink Floyd/Meddle/1.mp3","provider":"fs","remotely_accessible":false,"proxy_id":"ALB1"}}}}""")
        assertEquals("$base/imageproxy/ALB1?size=256", r)
    }

    @Test fun imageNull_albumHoesInAlbumMetadataImages() {
        val url = "https://i.scdn.co/image/albumcover"
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":null,"media_item":{"name":"x",
            "album":{"name":"A","metadata":{"images":[{"path":"$url","remotely_accessible":true}]}}}}""")
        assertEquals(url, r)
    }

    @Test fun metadataImagesNull_geeftNull() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","media_item":{"name":"x","metadata":{"images":null}}}""")
        assertNull(r)
    }

    @Test fun legeImagesLijst_geeftNull() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","media_item":{"name":"x","metadata":{"images":[]}}}""")
        assertNull(r)
    }

    @Test fun imageStringNull_alsTekst_wordtGeenUrl() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":"null","media_item":{"name":"x"}}""")
        assertNull(r)
    }

    @Test fun imageObjectZonderPath_valtTerugOpMetadata() {
        val url = "https://i.scdn.co/image/fallback"
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":{"path":null,"provider":"x"},"media_item":{"name":"x",
            "metadata":{"images":[{"path":"$url","remotely_accessible":true}]}}}""")
        assertEquals(url, r)
    }

    @Test fun imageObjectZonderPath_zelfdeDictMetMetadata() {
        val url = "https://i.scdn.co/image/inline"
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":{"path":null},
            "metadata":{"images":[{"path":"$url","remotely_accessible":true}]}}""")
        assertEquals(url, r)
    }

    /** Bandcamp-zoekresultaat zonder images en met album null: geen hoes. */
    @Test fun bandcampZoekresultaat_zonderHoes_geeftNull() = runBlocking {
        ma.on("music/search", """{"tracks":[{"uri":"bandcamp://track/1","name":"Song","provider":"bandcamp--x","album":null,
            "artists":[{"name":"Band","uri":"bandcamp://artist/1"}],"metadata":{"images":null},"image":null}],
            "artists":[],"albums":[],"playlists":[]}""")
        val res = ma.client().search("song")
        assertEquals(1, res.tracks.size)
        assertNull(res.tracks[0].imagePath)
        assertEquals(setOf("bandcamp"), res.tracks[0].providers)
        assertEquals("Band", res.tracks[0].subtitle)
    }

    // ---------- Radio en playlists ----------

    @Test fun radio_metLogoInMetadataImages_remote() = runBlocking {
        ma.on("music/radios/library_items", """[{"uri":"radioplayer://radio/1","name":"Radio X","favorite":true,
            "metadata":{"images":[{"type":"thumb","path":"https://cdn.radio.example/logo.png","provider":"tunein","remotely_accessible":true}]}}]""")
        val radios = ma.client().getFavoriteRadios()
        assertEquals(1, radios.size)
        assertEquals("https://cdn.radio.example/logo.png", radios[0].imagePath)
    }

    @Test fun radio_logoAlleenOpServer_loopViaProxy() = runBlocking {
        ma.on("music/radios/library_items", """[{"uri":"radio://1","name":"Radio Y",
            "metadata":{"images":[{"type":"thumb","path":"logo.png","provider":"builtin","remotely_accessible":false,"proxy_id":"RPX"}]}}]""")
        val radios = ma.client().getFavoriteRadios()
        assertEquals("$base/imageproxy/RPX?size=256", radios.single().imagePath)
    }

    @Test fun radio_logoAlsLosseStringVeld() = runBlocking {
        ma.on("music/radios/library_items", """[{"uri":"radio://1","name":"Radio Z","metadata":{"logo":"https://x.example/l.png"}}]""")
        assertEquals("https://x.example/l.png", ma.client().getFavoriteRadios().single().imagePath)
    }

    @Test fun radio_zonderLogo_nullEnGeenCrash() = runBlocking {
        ma.on("music/radios/library_items", """[{"uri":"radio://1","name":"Kaal","image":null,"metadata":{}}]""")
        assertNull(ma.client().getFavoriteRadios().single().imagePath)
    }

    @Test fun playlist_metadataLeegObject_valtTerugOpCollage() = runBlocking {
        ma.on("music/playlists/library_items", """[{"uri":"library://playlist/7","name":"Mix","track_count":2,"image":null,"metadata":{}}]""")
        ma.on("music/playlists/playlist_tracks", """[
            {"name":"a","image":null,"album":{"name":"A1","image":{"path":"x/1.mp3","provider":"fs","remotely_accessible":false,"proxy_id":"C1"}}},
            {"name":"b","metadata":{"images":[{"path":"https://i.scdn.co/image/c2","remotely_accessible":true}]}},
            {"name":"c","album":null,"metadata":{}},
            {"name":"d","image":{"path":"x/1.mp3","provider":"fs","remotely_accessible":false,"proxy_id":"C1"}}]""")
        val pl = ma.client().getFavoritePlaylists().single()
        assertNull(pl.imagePath)
        assertEquals(listOf("$base/imageproxy/C1?size=256", "https://i.scdn.co/image/c2"), pl.collage)
    }

    @Test fun playlist_metEigenHoes_gebruiktDieEnHaaltGeenTracks() = runBlocking {
        ma.on("music/playlists/library_items", """[{"uri":"library://playlist/7","name":"Mix","metadata":{"images":[{"path":"/data/playlist_metadata_images/7.jpg","provider":"builtin","remotely_accessible":false,"proxy_id":"PL7"}]}}]""")
        val pl = ma.client().getFavoritePlaylists().single()
        assertEquals("$base/imageproxy/PL7?size=256", pl.imagePath)
        assertTrue(ma.commands.none { it.first == "music/playlists/playlist_tracks" })
    }

    // ---------- Herschrijven naar serveradres ----------

    @Test fun relatiefPadMetSlash_krijgtServeradres() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":{"path":"/imageproxy/abc?size=256","remotely_accessible":true}}""")
        assertEquals("$base/imageproxy/abc?size=256", r)
    }

    @Test fun absoluteLokaleUrl_wordtHerschrevenNaarHuidigeServer() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":{"path":"http://192.168.1.5:8095/imageproxy/abc?size=256","remotely_accessible":true}}""")
        assertEquals("$base/imageproxy/abc?size=256", r)
    }

    @Test fun tailscaleUrl_wordtHerschreven() {
        val r = imageOfQueueItem("""{"queue_item_id":"a","name":"x","image":{"path":"https://ha.tail1234.ts.net/imageproxy/abc?size=256","remotely_accessible":true}}""")
        assertEquals("$base/imageproxy/abc?size=256", r)
    }

    @Test fun largeImageUrl_proxyUrl_naar1024_en2560_blijft() {
        assertEquals("http://h/imageproxy/PX?size=1024", largeImageUrl("http://h/imageproxy/PX?size=256"))
        assertEquals("http://h/imageproxy/PX?size=2560", largeImageUrl("http://h/imageproxy/PX?size=2560"))
        assertEquals("http://h/i?a=1&size=1024&b=2", largeImageUrl("http://h/i?a=1&size=256&b=2"))
        assertEquals("http://h/i?xsize=256", largeImageUrl("http://h/i?xsize=256"))
    }
}
