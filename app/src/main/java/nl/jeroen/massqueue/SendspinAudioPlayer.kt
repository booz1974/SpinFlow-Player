package nl.jeroen.massqueue

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.sendspin.protocol.AudioBuffer
import com.sendspin.protocol.AudioPlayer
import com.sendspin.protocol.ClockSync
import com.sendspin.protocol.PcmDriftCorrector
import com.sendspin.protocol.StreamFormat

/**
 * Speelt de Sendspin-stream af via een [AudioTrack]. PCM gaat er direct in; FLAC
 * wordt per chunk eerst door een [ChunkDecoder] (MediaCodec) naar PCM omgezet.
 *
 * De bibliotheek zet binnenkomende chunks op servertijd in de [AudioBuffer]; deze speler
 * haalt ze eruit op het moment dat ze (na alles wat al in de AudioTrack zit) precies op
 * hun geplande tijd uit de speaker komen. Kleine afwijkingen worden weggewerkt met de
 * [PcmDriftCorrector] (onhoorbaar ±0,2% sneller/trager), grote door chunks over te slaan.
 *
 * Alle methodes worden door de bibliotheek vanaf één coroutine-thread aangeroepen; het
 * afspelen zelf draait op een eigen thread met audio-prioriteit.
 */
class SendspinAudioPlayer(
    private val buffer: AudioBuffer,
    private val clockSync: ClockSync
) : AudioPlayer {

    private val lock = Object()

    @Volatile private var format: StreamFormat? = null
    @Volatile private var thread: Thread? = null
    @Volatile private var running = false
    /** Verhoogd bij flush/transition: de afspeelthread bouwt dan een schone AudioTrack op. */
    @Volatile private var generation = 0

    @Volatile private var serverGain = 1f
    /** Audio focus: 1 = normaal, 0.2 = geduckt, 0 = tijdelijk stil (bijv. telefoongesprek). */
    @Volatile private var focusGain = 1f
    @Volatile private var track: AudioTrack? = null

    @Volatile private var dropped = 0L

    override val isPlaying: Boolean get() = running
    override val droppedDecodeFrames: Long get() = dropped

    override fun configure(format: StreamFormat) {
        this.format = format
        generation++
    }

    override fun start() {
        synchronized(lock) {
            if (running) return
            running = true
            thread = Thread(::playLoop, "sendspin-audio").also { it.start() }
        }
    }

    override fun flush() {
        buffer.flush()
        generation++
    }

    override fun transition(format: StreamFormat) {
        // MA stuurt bij een nummerwissel niet altijd stream/clear: oude chunks weg.
        buffer.flush()
        if (format != this.format) {
            this.format = format
        }
        generation++
    }

    override fun stop() {
        val t: Thread?
        synchronized(lock) {
            running = false
            t = thread
            thread = null
        }
        t?.interrupt()
        t?.join(500)
    }

    override fun setVolume(gain: Float) {
        serverGain = gain.coerceIn(0f, 1f)
        applyGain()
    }

    /** Audio focus vanuit de service. */
    fun setFocusGain(gain: Float) {
        focusGain = gain.coerceIn(0f, 1f)
        applyGain()
    }

    private fun applyGain() {
        track?.setVolume(serverGain * focusGain)
    }

    // ---- Afspeelthread -----------------------------------------------------------

    private fun playLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var myGeneration = -1
        var out: Output? = null
        try {
            while (running) {
                if (myGeneration != generation) {
                    myGeneration = generation
                    out?.release()
                    out = format?.let { createOutput(it) }
                    track = out?.track
                    applyGain()
                }
                val o = out
                if (o == null) {
                    Thread.sleep(IDLE_SLEEP_MS)
                    continue
                }

                val now = ClockSync.localMicros()
                // Moment waarop het eerstvolgende frame dat we schrijven hoorbaar wordt. Staat stil
                // zolang we niets schrijven, daarom kijken we iets vooruit (EARLY_MICROS).
                val playAt = o.nextFramePlayMicros(now)
                val chunk = buffer.poll(playAt + EARLY_MICROS)
                if (chunk == null) {
                    val wait = buffer.nextChunkDelayMicros(playAt + EARLY_MICROS)
                    if (wait == null && o.framesWritten > 0 && o.queuedMicros() <= 0) buffer.signalUnderrun()
                    val sleepMs = ((wait ?: (IDLE_SLEEP_MS * 1000)) / 1000).coerceIn(1, IDLE_SLEEP_MS)
                    Thread.sleep(sleepMs)
                    continue
                }

                o.stats.maybeLog(o)
                // Positief: we lopen achter (chunk had eerder moeten klinken); negatief: we zijn te vroeg
                var drift = playAt - scheduledMicros(chunk.serverTimestampMicros, now)
                if (drift > HARD_DROP_MICROS) {
                    dropped++
                    o.stats.dropped++
                    o.corrector.reset()
                    continue
                }
                // Een decoder kan uitvoer van een eerdere chunk teruggeven: plannen op diens eigen tijd
                val t0 = System.nanoTime()
                val block = o.decoder.decode(chunk.data, chunk.serverTimestampMicros)
                o.stats.decoded((System.nanoTime() - t0) / 1000, block == null)
                if (block == null) continue
                if (block.serverMicros != chunk.serverTimestampMicros) {
                    drift = playAt - scheduledMicros(block.serverMicros, now)
                    if (drift > HARD_DROP_MICROS) {
                        dropped++
                        o.stats.dropped++
                        o.corrector.reset()
                        continue
                    }
                }
                o.stats.drift(drift)
                if (drift < -SILENCE_GAP_MICROS) {
                    // Gat in de tijdlijn (bijv. begin van de stream): opvullen met stilte
                    o.stats.silenceMicros += -drift
                    o.writeSilence(-drift)
                    drift = 0
                }

                val pcm = block.pcm
                val blockMicros = pcm.size.toLong() / o.channels * 1_000_000L / o.sampleRate
                val corrected = o.corrector.correct(pcm, drift, blockMicros)
                o.write(corrected)
            }
        } catch (_: InterruptedException) {
            // stop()
        } catch (e: Exception) {
            Log.e(TAG, "Afspeelthread gestopt", e)
        } finally {
            track = null
            out?.release()
            running = false
        }
    }

    private fun scheduledMicros(serverMicros: Long, now: Long): Long =
        clockSync.toLocalMicros(serverMicros, now) - buffer.staticDelayMicros

    private fun createOutput(format: StreamFormat): Output? {
        Log.d(AUDIO_LOG_TAG, "Afspelen: ${format.displayLabel()} (codec_header ${if (format.codecHeader.isNullOrBlank()) "nee" else "ja"})")
        val decoder = ChunkDecoder.create(format) ?: run {
            Log.w(TAG, "Niet-ondersteund formaat: $format")
            return null
        }
        val channelMask = if (format.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minSize = AudioTrack.getMinBufferSize(format.sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(format.sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minSize * 4)
            .build()
        t.play()
        return Output(t, decoder, format.sampleRate, format.channels)
    }

    private class Output(
        val track: AudioTrack,
        val decoder: ChunkDecoder,
        val sampleRate: Int,
        val channels: Int
    ) {
        val corrector = PcmDriftCorrector(channels)
        val stats = PlaybackStats()
        var framesWritten = 0L
        private val ts = AudioTimestamp()

        fun queuedMicros(): Long {
            val played = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            return (framesWritten - played).coerceAtLeast(0) * 1_000_000L / sampleRate
        }

        /** Lokale tijd (µs) waarop het eerstvolgende te schrijven frame hoorbaar wordt. */
        fun nextFramePlayMicros(now: Long): Long {
            if (framesWritten > 0 && track.getTimestamp(ts) && ts.framePosition > 0) {
                val t = ts.nanoTime / 1000 + (framesWritten - ts.framePosition) * 1_000_000L / sampleRate
                // Na een onderloop kan de timestamp verouderd zijn; nooit in het verleden plannen
                if (t >= now) return t
            }
            return now + queuedMicros() + START_LATENCY_MICROS
        }

        fun write(pcm: ShortArray) {
            val n = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            if (n > 0) framesWritten += n / channels
        }

        fun writeSilence(micros: Long) {
            val frames = (micros * sampleRate / 1_000_000L).toInt()
            if (frames > 0) write(ShortArray(frames * channels))
        }

        fun release() {
            try {
                track.pause()
                track.flush()
                track.release()
            } catch (_: Exception) {
            }
            decoder.release()
        }
    }

    /** Elke 5 s één logregel over hoe het afspelen loopt (alleen debug-builds). */
    private class PlaybackStats {
        var dropped = 0
        var silenceMicros = 0L
        private var chunks = 0
        private var empty = 0
        private var decodeMaxMicros = 0L
        private var decodeTotalMicros = 0L
        private var driftMin = Long.MAX_VALUE
        private var driftMax = Long.MIN_VALUE
        private var lastLog = System.nanoTime()
        private var lastUnderruns = 0

        fun decoded(micros: Long, wasEmpty: Boolean) {
            chunks++
            if (wasEmpty) empty++
            decodeTotalMicros += micros
            if (micros > decodeMaxMicros) decodeMaxMicros = micros
        }

        fun drift(micros: Long) {
            if (micros < driftMin) driftMin = micros
            if (micros > driftMax) driftMax = micros
        }

        fun maybeLog(o: Output) {
            if (!BuildConfig.DEBUG) return
            val nowNs = System.nanoTime()
            if (nowNs - lastLog < 5_000_000_000L) return
            lastLog = nowNs
            val underruns = runCatching { o.track.underrunCount }.getOrDefault(0)
            Log.d(
                AUDIO_LOG_TAG,
                "stats 5s: chunks=$chunks leeg=$empty gedropt=$dropped stilte=${silenceMicros / 1000}ms " +
                    "decode gem=${if (chunks > 0) decodeTotalMicros / chunks / 1000.0 else 0.0}ms max=${decodeMaxMicros / 1000}ms " +
                    "drift=${if (driftMin == Long.MAX_VALUE) "-" else "${driftMin / 1000}..${driftMax / 1000}ms"} " +
                    "track-onderloop=+${underruns - lastUnderruns} wachtrij=${o.queuedMicros() / 1000}ms"
            )
            lastUnderruns = underruns
            dropped = 0; silenceMicros = 0; chunks = 0; empty = 0
            decodeMaxMicros = 0; decodeTotalMicros = 0
            driftMin = Long.MAX_VALUE; driftMax = Long.MIN_VALUE
        }
    }

    companion object {
        private const val TAG = "SendspinAudio"
        private const val IDLE_SLEEP_MS = 10L
        /** Meer dan zoveel achter: chunk overslaan in plaats van langzaam inhalen. */
        private const val HARD_DROP_MICROS = 80_000L
        /** Zo ver vooruit mogen chunks opgehaald worden; kleine voorsprong lost de corrector op. */
        private const val EARLY_MICROS = 30_000L
        /** Meer dan zoveel te vroeg: eerst stilte schrijven. */
        private const val SILENCE_GAP_MICROS = 15_000L
        /** Geschatte tijd tussen eerste write en hoorbaar geluid als er nog geen timestamp is. */
        private const val START_LATENCY_MICROS = 40_000L
    }
}
