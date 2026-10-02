package voice.core.copilot.frame

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.math.roundToInt

/**
 * Writes the precise-edit mask: an 8-bit grayscale PNG the size of the frame, white (keep)
 * everywhere except the character's box, which is black (edit). Hand-rolled so it needs no
 * Android bitmap and runs identically in unit tests.
 */
internal object MaskPng {

  private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

  fun box(width: Int, height: Int, box: MaskBox): ByteArray {
    val left = (box.x0 * width).roundToInt().coerceIn(0, width)
    val right = (box.x1 * width).roundToInt().coerceIn(0, width)
    val top = (box.y0 * height).roundToInt().coerceIn(0, height)
    val bottom = (box.y1 * height).roundToInt().coerceIn(0, height)

    val raw = ByteArrayOutputStream(height * (width + 1))
    val whiteRow = ByteArray(width + 1) { if (it == 0) 0 else WHITE }
    val boxRow = whiteRow.copyOf().also { row -> for (x in left until right) row[x + 1] = BLACK }
    for (y in 0 until height) raw.write(if (y in top until bottom) boxRow else whiteRow)

    val out = ByteArrayOutputStream()
    out.write(SIGNATURE)
    writeChunk(out, "IHDR", header(width, height))
    writeChunk(out, "IDAT", deflate(raw.toByteArray()))
    writeChunk(out, "IEND", ByteArray(0))
    return out.toByteArray()
  }

  private fun header(width: Int, height: Int): ByteArray {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).apply {
      writeInt(width)
      writeInt(height)
      writeByte(BIT_DEPTH)
      writeByte(COLOR_TYPE_GRAYSCALE)
      writeByte(0)
      writeByte(0)
      writeByte(0)
    }
    return bytes.toByteArray()
  }

  private fun deflate(data: ByteArray): ByteArray {
    val bytes = ByteArrayOutputStream()
    DeflaterOutputStream(bytes, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(data) }
    return bytes.toByteArray()
  }

  private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
    val typeBytes = type.toByteArray(Charsets.US_ASCII)
    val stream = DataOutputStream(out)
    stream.writeInt(data.size)
    stream.write(typeBytes)
    stream.write(data)
    stream.writeInt(CRC32().apply { update(typeBytes); update(data) }.value.toInt())
  }

  private const val WHITE: Byte = -1
  private const val BLACK: Byte = 0
  private const val BIT_DEPTH = 8
  private const val COLOR_TYPE_GRAYSCALE = 0
}
