package voice.core.companion.setup

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dev.zacsweers.metro.Inject
import java.io.File
import java.nio.ByteBuffer

/** How one audio file is cut into parts for transcription. */
data class ChunkPlan(
  val durationMs: Long,
  /** Whether the file can be cut at all; when not, it goes to Deepgram whole as a single part. */
  val splittable: Boolean,
  val parts: List<AudioPart>,
)

data class AudioPart(
  val index: Int,
  val startMs: Long,
  val endMs: Long,
)

/** A part written to disk, ready to upload. [startMs] is where its first sample really sits in the file. */
data class WrittenPart(
  val file: File,
  val mimeType: String,
  val startMs: Long,
)

/**
 * Cuts an audio file into parts without re-encoding: MP3 frames are copied as they are, and AAC frames (m4b, m4a)
 * get an ADTS header each so the part plays on its own. Other codecs are not cut.
 */
@Inject
class AudioChunker(private val context: Context) {

  fun plan(uri: Uri, partMs: Long = PART_MS): ChunkPlan = withExtractor(uri) { extractor, format ->
    val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0L
    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
    val splittable = mime == MIME_AAC || mime == MIME_MP3
    extractor.release()
    ChunkPlan(
      durationMs = durationMs,
      splittable = splittable,
      parts = if (splittable && durationMs > 0) partsOf(durationMs, partMs) else listOf(AudioPart(0, 0, durationMs)),
    )
  }

  /** Copies [part] of [uri] into [out]. A plan that isn't splittable copies the whole file. */
  fun write(uri: Uri, plan: ChunkPlan, part: AudioPart, out: File): WrittenPart {
    if (!plan.splittable) {
      context.contentResolver.openInputStream(uri).use { input ->
        checkNotNull(input) { "Could not open $uri" }
        out.outputStream().use { input.copyTo(it) }
      }
      return WrittenPart(out, context.contentResolver.getType(uri) ?: "audio/*", startMs = 0)
    }
    return withExtractor(uri) { extractor, format ->
      try {
        copySamples(extractor, format, part, out)
      } finally {
        extractor.release()
      }
    }
  }

  private fun copySamples(
    extractor: MediaExtractor,
    format: MediaFormat,
    part: AudioPart,
    out: File,
  ): WrittenPart {
    val aac = format.getString(MediaFormat.KEY_MIME) == MIME_AAC
    val adts = if (aac) AdtsConfig.of(format) else null
    extractor.seekTo(part.startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    val buffer = ByteBuffer.allocate(SAMPLE_BUFFER_BYTES)
    var firstSampleUs = -1L
    out.outputStream().buffered().use { stream ->
      while (true) {
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        val timeUs = extractor.sampleTime
        if (timeUs >= part.endMs * 1000) break
        if (firstSampleUs < 0) firstSampleUs = timeUs
        if (adts != null) stream.write(adts.header(size))
        stream.write(buffer.array(), buffer.arrayOffset(), size)
        buffer.clear()
        extractor.advance()
      }
    }
    return WrittenPart(
      file = out,
      mimeType = if (aac) "audio/aac" else "audio/mpeg",
      startMs = if (firstSampleUs < 0) part.startMs else firstSampleUs / 1000,
    )
  }

  private fun <T> withExtractor(uri: Uri, block: (MediaExtractor, MediaFormat) -> T): T {
    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(context, uri, null)
      val track = (0 until extractor.trackCount).firstOrNull { index ->
        extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
      } ?: error("No audio track in $uri")
      extractor.selectTrack(track)
      return block(extractor, extractor.getTrackFormat(track))
    } catch (e: Exception) {
      extractor.release()
      throw e
    }
  }

  companion object {
    /** Twenty minutes: small enough to retry cheaply, big enough that a long book is a few dozen requests. */
    const val PART_MS = 20 * 60 * 1000L
    private const val MIME_AAC = "audio/mp4a-latm"
    private const val MIME_MP3 = "audio/mpeg"
    private const val SAMPLE_BUFFER_BYTES = 1 shl 20

    fun partsOf(durationMs: Long, partMs: Long): List<AudioPart> {
      val count = ((durationMs + partMs - 1) / partMs).toInt().coerceAtLeast(1)
      return List(count) { index ->
        AudioPart(index = index, startMs = index * partMs, endMs = minOf(durationMs, (index + 1) * partMs))
      }
    }
  }
}

/** What an ADTS header needs to know about an AAC stream. */
internal data class AdtsConfig(
  val profile: Int,
  val frequencyIndex: Int,
  val channelConfig: Int,
) {

  fun header(payloadSize: Int): ByteArray {
    val frameLength = payloadSize + HEADER_SIZE
    return byteArrayOf(
      0xFF.toByte(),
      0xF1.toByte(),
      (((profile - 1) shl 6) or (frequencyIndex shl 2) or (channelConfig shr 2)).toByte(),
      (((channelConfig and 3) shl 6) or (frameLength shr 11)).toByte(),
      ((frameLength and 0x7FF) shr 3).toByte(),
      (((frameLength and 7) shl 5) or 0x1F).toByte(),
      0xFC.toByte(),
    )
  }

  companion object {
    const val HEADER_SIZE = 7
    private val SAMPLE_RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)

    fun of(format: MediaFormat): AdtsConfig {
      val csd = if (format.containsKey("csd-0")) format.getByteBuffer("csd-0") else null
      if (csd != null && csd.remaining() >= 2) {
        return fromAudioSpecificConfig(csd.get(csd.position()).toInt() and 0xFF, csd.get(csd.position() + 1).toInt() and 0xFF)
      }
      val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      return AdtsConfig(
        profile = AAC_LC,
        frequencyIndex = SAMPLE_RATES.indexOf(rate).takeIf { it >= 0 } ?: 4,
        channelConfig = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
      )
    }

    /**
     * Reads the first two bytes of an AudioSpecificConfig. HE-AAC streams (object types 5 and 29) are sent as AAC-LC
     * at their core rate; decoders find the SBR data on their own.
     */
    fun fromAudioSpecificConfig(first: Int, second: Int): AdtsConfig {
      val objectType = first shr 3
      val frequencyIndex = ((first and 0x07) shl 1) or (second shr 7)
      val channelConfig = (second shr 3) and 0x0F
      val profile = if (objectType in 1..4) objectType else AAC_LC
      return AdtsConfig(profile = profile, frequencyIndex = frequencyIndex, channelConfig = channelConfig)
    }

    private const val AAC_LC = 2
  }
}
