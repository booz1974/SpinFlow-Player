package nl.jeroen.massqueue

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Base64
import android.util.Log
import com.sendspin.protocol.StreamFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Tag voor formaat-onderhandeling en decoderkeuze (`adb logcat -s SpinflowAudio`). */
const val AUDIO_LOG_TAG = "SpinflowAudio"

/** Instelling "Audiokwaliteit" voor de eigen Sendspin-speler. */
enum class SendspinAudioQuality(val codec: String) {
    LOSSLESS("flac"),
    ORIGINAL("pcm");

    val label: String
        get() = when (this) {
            LOSSLESS -> "Lossless (FLAC)"
            ORIGINAL -> tr("Origineel (PCM)", "Original (PCM)")
        }

    val description: String
        get() = when (this) {
            LOSSLESS -> tr("Zelfde geluid als PCM, ongeveer de helft van de data", "Same sound as PCM, about half the data")
            ORIGINAL -> tr("Onbewerkte audio, meeste data (standaard)", "Unprocessed audio, most data (default)")
        }

    /** Voorkeursvolgorde van codecs: de gekozen eerst, PCM altijd erin als terugval. */
    val codecPreference: List<String>
        get() = when (this) {
            LOSSLESS -> listOf("flac", "pcm")
            ORIGINAL -> listOf("pcm", "flac")
        }

    companion object {
        /** Onbekende sleutel (ook het vervallen DATA_SAVER/Opus) → PCM. */
        fun fromKey(key: String?): SendspinAudioQuality = entries.firstOrNull { it.name == key } ?: ORIGINAL
    }
}

/** Korte omschrijving voor de UI: codec, kHz/bitdiepte, bv. "PCM 48/16" of "FLAC 44.1/16". */
fun StreamFormat.displayLabel(): String {
    val khz = if (sampleRate % 1000 == 0) "${sampleRate / 1000}" else "%.1f".format(java.util.Locale.ROOT, sampleRate / 1000.0)
    return "${codec.uppercase()} $khz/$bitDepth"
}

/** Gedecodeerde PCM (16 bit, interleaved) met de servertijd van het eerste frame. */
class DecodedBlock(val serverMicros: Long, val pcm: ShortArray)

/**
 * Zet een binnengekomen Sendspin-chunk om naar 16-bit PCM. Eén instantie per stream;
 * [release] bij flush, formaatwissel en stop.
 */
interface ChunkDecoder {
    /** null = (nog) geen uitvoer, bv. decoder-latentie of een fout. */
    fun decode(data: ByteArray, serverMicros: Long): DecodedBlock?
    fun release() {}

    companion object {
        /** Kan dit toestel deze codec decoderen? PCM altijd. */
        fun isSupported(codec: String, sampleRate: Int = 48_000, channels: Int = 2): Boolean {
            val mime = mimeFor(codec) ?: return codec.equals("pcm", ignoreCase = true)
            val format = MediaFormat.createAudioFormat(mime, sampleRate, channels)
            return runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) != null }
                .getOrDefault(false)
        }

        /** Kiest de decoder bij [format]; null als het formaat niet af te spelen is. */
        fun create(format: StreamFormat): ChunkDecoder? {
            val codec = format.codec.lowercase()
            if (format.bitDepth != 16) {
                Log.w(AUDIO_LOG_TAG, "Niet-ondersteunde bitdiepte: $format")
                return null
            }
            return when (codec) {
                "pcm" -> PcmDecoder.also { Log.d(AUDIO_LOG_TAG, "Decoder: PCM (direct) voor ${format.displayLabel()}") }
                "flac" -> MediaCodecChunkDecoder.create(format)
                else -> {
                    Log.w(AUDIO_LOG_TAG, "Onbekende codec: $format")
                    null
                }
            }
        }

        private fun mimeFor(codec: String): String? = when (codec.lowercase()) {
            "flac" -> MediaFormat.MIMETYPE_AUDIO_FLAC
            else -> null
        }
    }
}

/** Het oude pad: chunks zijn al 16-bit little-endian PCM. */
private object PcmDecoder : ChunkDecoder {
    override fun decode(data: ByteArray, serverMicros: Long): DecodedBlock {
        val shorts = ShortArray(data.size / 2)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return DecodedBlock(serverMicros, shorts)
    }
}

/**
 * Synchrone MediaCodec-decoder voor FLAC. Elke chunk is één gecodeerd frame; de
 * servertijd gaat als presentationTimeUs mee, zodat de uitvoer zijn eigen tijdstempel houdt
 * en de bestaande planning/drift-correctie gewoon blijft werken.
 */
private class MediaCodecChunkDecoder(
    private val codec: MediaCodec,
    private val mediaFormat: MediaFormat,
    private val label: String
) : ChunkDecoder {

    private val info = MediaCodec.BufferInfo()
    private var failed = false
    private var chunksSeen = 0
    private var resets = 0

    override fun decode(data: ByteArray, serverMicros: Long): DecodedBlock? {
        if (failed) return null
        val debug = chunksSeen < DEBUG_CHUNKS
        if (debug) {
            chunksSeen++
            Log.d(AUDIO_LOG_TAG, "$label: chunk ${data.size} bytes, begin ${data.hexHead()}")
        }
        return try {
            val inIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inIndex >= 0) {
                val buf = codec.getInputBuffer(inIndex)!!
                buf.clear()
                buf.put(data)
                codec.queueInputBuffer(inIndex, 0, data.size, serverMicros, 0)
            } else {
                Log.w(AUDIO_LOG_TAG, "$label: geen invoerbuffer vrij, chunk overgeslagen")
            }
            drain(serverMicros).also { out ->
                if (debug) {
                    Log.d(AUDIO_LOG_TAG, "$label: uit ${out?.pcm?.size ?: 0} samples, pts Δ ${out?.let { it.serverMicros - serverMicros }}")
                }
            }
        } catch (e: Exception) {
            // Eén kapotte chunk mag de stream niet stilzetten: decoder herstarten en doorgaan
            if (resets < MAX_RESETS && restart()) {
                resets++
                Log.w(AUDIO_LOG_TAG, "$label: decoderfout, herstart ($resets/$MAX_RESETS)", e)
            } else {
                Log.e(AUDIO_LOG_TAG, "$label: decoderfout, stream blijft stil", e)
                failed = true
            }
            null
        }
    }

    private fun restart(): Boolean = runCatching {
        codec.reset()
        codec.configure(mediaFormat, null, null, 0)
        codec.start()
    }.isSuccess

    /**
     * Haalt alle beschikbare uitvoer op. Alleen als er nog niets is, wachten we kort: sommige
     * decoders geven de uitvoer pas een pakket later. Die uitvoer houdt zijn eigen pts,
     * dus wachten tot "onze" chunk klaar is zou elke chunk vertragen (onderloop → ruis).
     */
    private fun drain(serverMicros: Long): DecodedBlock? {
        var firstPts: Long? = null
        val parts = ArrayList<ShortArray>(2)
        var total = 0
        val deadline = System.nanoTime() + OUTPUT_WAIT_NS
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(info, if (parts.isEmpty()) OUTPUT_POLL_US else 0L)
            when {
                outIndex >= 0 -> {
                    if (info.size > 0) {
                        val buf = codec.getOutputBuffer(outIndex)!!
                        buf.position(info.offset).limit(info.offset + info.size)
                        val shorts = ShortArray(info.size / 2)
                        buf.order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts)
                        if (firstPts == null) firstPts = info.presentationTimeUs
                        parts += shorts
                        total += shorts.size
                    }
                    val pts = info.presentationTimeUs
                    codec.releaseOutputBuffer(outIndex, false)
                    if (pts >= serverMicros) break
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                    Log.d(AUDIO_LOG_TAG, "$label: uitvoerformaat ${codec.outputFormat}")
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER ->
                    if (parts.isNotEmpty() || System.nanoTime() > deadline) break
            }
        }
        val start = firstPts ?: return null
        if (parts.size == 1) return DecodedBlock(start, parts[0])
        val pcm = ShortArray(total)
        var pos = 0
        for (p in parts) {
            p.copyInto(pcm, pos)
            pos += p.size
        }
        return DecodedBlock(start, pcm)
    }

    override fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        Log.d(AUDIO_LOG_TAG, "$label: decoder vrijgegeven")
    }

    companion object {
        private const val INPUT_TIMEOUT_US = 10_000L
        /** Zoveel eerste chunks per decoder loggen (grootte + begin), voor diagnose. */
        private const val DEBUG_CHUNKS = 4
        private const val MAX_RESETS = 5
        private const val OUTPUT_POLL_US = 2_000L
        /** Langer wachten op uitvoer heeft geen zin: dan schuift die door naar de volgende chunk. */
        private const val OUTPUT_WAIT_NS = 20_000_000L

        fun create(format: StreamFormat): ChunkDecoder? {
            val codecName = format.codec.lowercase()
            val mime = MediaFormat.MIMETYPE_AUDIO_FLAC
            val header = format.codecHeader?.takeIf { it.isNotBlank() }?.let {
                runCatching { Base64.decode(it, Base64.DEFAULT) }
                    .onFailure { e -> Log.w(AUDIO_LOG_TAG, "codec_header niet te decoderen", e) }
                    .getOrNull()
            }
            val mf = MediaFormat.createAudioFormat(mime, format.sampleRate, format.channels)
            mf.setByteBuffer("csd-0", ByteBuffer.wrap(flacCsd(header, format)))
            val source = when {
                header == null -> "zelf opgebouwd (geen codec_header)"
                else -> "codec_header van server (${header.size} bytes)"
            }
            return try {
                val codec = MediaCodec.createDecoderByType(mime)
                codec.configure(mf, null, null, 0)
                codec.start()
                val label = "${codecName.uppercase()}-decoder ${codec.name}"
                Log.d(AUDIO_LOG_TAG, "Decoder: $label voor ${format.displayLabel()}, CSD $source")
                MediaCodecChunkDecoder(codec, mf, label)
            } catch (e: Exception) {
                Log.e(AUDIO_LOG_TAG, "Kan geen $mime-decoder maken voor $format", e)
                null
            }
        }

        /** csd-0 voor FLAC: "fLaC" + STREAMINFO-blok. */
        private fun flacCsd(header: ByteArray?, format: StreamFormat): ByteArray {
            if (header != null) {
                if (header.startsWithAscii("fLaC")) return header
                // Alleen de metadata-blokken (zonder magic) of de kale 34-byte STREAMINFO
                if (header.size == STREAMINFO_SIZE) return "fLaC".toByteArray() + byteArrayOf(0x80.toByte(), 0, 0, 34) + header
                return "fLaC".toByteArray() + header
            }
            return "fLaC".toByteArray() + byteArrayOf(0x80.toByte(), 0, 0, 34) + streamInfo(format)
        }

        private const val STREAMINFO_SIZE = 34

        /** Minimale STREAMINFO: blokgroottes ruim, frame-groottes/aantal samples/MD5 onbekend (0). */
        private fun streamInfo(format: StreamFormat): ByteArray {
            val b = ByteBuffer.allocate(STREAMINFO_SIZE).order(ByteOrder.BIG_ENDIAN)
            b.putShort(16)                 // min. blokgrootte
            b.putShort(0xFFFF.toShort())   // max. blokgrootte
            b.put(ByteArray(6))            // min./max. framegrootte onbekend
            // 20 bit samplerate, 3 bit kanalen-1, 5 bit bitdiepte-1, 36 bit totaal aantal samples (0)
            val packed = (format.sampleRate.toLong() shl 44) or
                ((format.channels - 1).toLong() shl 41) or
                ((format.bitDepth - 1).toLong() shl 36)
            b.putLong(packed)
            b.put(ByteArray(16))           // MD5 onbekend
            return b.array()
        }

        private fun ByteArray.hexHead(): String =
            take(16).joinToString(" ") { "%02x".format(it) }

        private fun ByteArray.startsWithAscii(prefix: String): Boolean =
            size >= prefix.length && prefix.indices.all { this[it] == prefix[it].code.toByte() }
    }
}
