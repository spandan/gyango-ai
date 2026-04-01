package ai.pocket.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.min

object AudioFloatDecoder {

    private const val TARGET_RATE = 16_000
    private const val MAX_DURATION_SEC = 180

    suspend fun decodeUriToMono16kFloat(context: Context, uri: Uri): FloatArray = withContext(Dispatchers.IO) {
        val pcm = decodeToPcmAndRate(context, uri)
        
        val mono = if (pcm.channels == 1) {
            pcm.interleaved
        } else {
            stereoToMono(pcm.interleaved, pcm.channels)
        }
        
        val maxSamples = pcm.sampleRate * MAX_DURATION_SEC
        val clipped = if (mono.size > maxSamples) mono.copyOf(maxSamples) else mono
        val floats = shortsToFloats(clipped)
        val resampled = resampleLinear(floats, pcm.sampleRate, TARGET_RATE)
        
        resampled
    }

    private data class PcmData(val interleaved: ShortArray, val channels: Int, val sampleRate: Int)

    private fun decodeToPcmAndRate(context: Context, uri: Uri): PcmData {
        val resolver = context.contentResolver
        val stream = try { resolver.openInputStream(uri) } catch (e: Exception) { null } ?: error("Could not open audio stream for $uri")
        stream.use { input ->
            if (input.markSupported()) {
                input.mark(12)
                val hdr = ByteArray(12)
                val n = readFullyOrPartial(input, hdr)
                input.reset()
                val isWav = n >= 12 &&
                    hdr[0] == 'R'.code.toByte() &&
                    hdr[1] == 'I'.code.toByte() &&
                    hdr[2] == 'F'.code.toByte() &&
                    hdr[3] == 'F'.code.toByte() &&
                    hdr[8] == 'W'.code.toByte() &&
                    hdr[9] == 'A'.code.toByte() &&
                    hdr[10] == 'V'.code.toByte() &&
                    hdr[11] == 'E'.code.toByte()
                if (isWav) {
                    val (pcm, rate, ch) = parseWavFull(resolver.openInputStream(uri) ?: error("Could not reopen WAV"))
                    return PcmData(pcm, ch, rate)
                }
            }
        }
        val (s, r) = decodeWithMediaCodec(context, uri)
        return PcmData(s, 1, r)
    }

    private fun readFullyOrPartial(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r <= 0) break
            off += r
        }
        return off
    }

    private fun parseWavFull(input: InputStream): Triple<ShortArray, Int, Int> {
        DataInputStream(input).use { din ->
            val riff = ByteArray(4)
            din.readFully(riff)
            if (String(riff) != "RIFF") error("Not a RIFF file")
            din.readInt()
            val wave = ByteArray(4)
            din.readFully(wave)
            if (String(wave) != "WAVE") error("Not a WAVE file")
            var fmtRate = 0
            var channels = 1
            var bits = 0
            var audioSize = 0
            var dataRead = false
            while (true) {
                val id = ByteArray(4)
                val readId = din.read(id)
                if (readId != 4) break
                val chunkId = String(id)
                val size = readLEInt(din)
                when (chunkId) {
                    "fmt " -> {
                        val fmtBuf = ByteArray(size)
                        din.readFully(fmtBuf)
                        val fmt = ByteBuffer.wrap(fmtBuf).order(ByteOrder.LITTLE_ENDIAN)
                        val audioFormat = fmt.short.toInt() and 0xffff
                        if (audioFormat != 1) error("Only PCM WAV supported")
                        channels = fmt.short.toInt() and 0xffff
                        fmtRate = fmt.int
                        bits = (fmtBuf.size - 14).let { if (it >= 2) ByteBuffer.wrap(fmtBuf, 14, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff else 16 }
                        // Bits per sample is at offset 14 in fmt chunk
                    }
                    "data" -> {
                        audioSize = size
                        dataRead = true
                        break
                    }
                    else -> {
                        var skip = size
                        while (skip > 0) {
                            val s = din.skip(skip.toLong())
                            if (s <= 0) { din.read(); skip-- } else { skip -= s.toInt() }
                        }
                    }
                }
            }
            if (!dataRead || audioSize <= 0) error("WAV missing data")
            val bytes = ByteArray(audioSize)
            din.readFully(bytes)
            val samples = audioSize / 2
            val shorts = ShortArray(samples)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return Triple(shorts, fmtRate, channels)
        }
    }

    private fun readLEInt(din: DataInputStream): Int {
        return din.readUnsignedByte() or (din.readUnsignedByte() shl 8) or
            (din.readUnsignedByte() shl 16) or (din.readUnsignedByte() shl 24)
    }

    private fun stereoToMono(interleaved: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return interleaved
        val frames = interleaved.size / channels
        val out = ShortArray(frames)
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) {
                sum += interleaved[i * channels + c].toInt()
            }
            out[i] = (sum / channels).toShort()
        }
        return out
    }

    private fun decodeWithMediaCodec(context: Context, uri: Uri): Pair<ShortArray, Int> {
        val extractor = MediaExtractor()
        try { extractor.setDataSource(context, uri, null) } catch (e: Exception) { error("Extractor error: ${e.message}") }
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                track = i; format = f; break
            }
        }
        if (track < 0 || format == null) { extractor.release(); error("No audio track in $uri") }
        extractor.selectTrack(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("No MIME")

        val decoder = createAudioDecoder(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        var outRate = try { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { 44100 }
        var outCh = try { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Exception) { 1 }
        var outputPcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        val pcmChunks = ArrayList<ShortArray>()
        val info = MediaCodec.BufferInfo()
        var inputEos = false
        var outputEos = false
        try {
            while (!outputEos) {
                if (!inputEos) {
                    val inIx = decoder.dequeueInputBuffer(10_000)
                    if (inIx >= 0) {
                        val buf = decoder.getInputBuffer(inIx)!!
                        val sz = extractor.readSampleData(buf, 0)
                        if (sz < 0) {
                            decoder.queueInputBuffer(inIx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            decoder.queueInputBuffer(inIx, 0, sz, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIx = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIx >= 0) {
                    if (info.size > 0) {
                        val outBuf = decoder.getOutputBuffer(outIx)!!
                        val chunk = if (outputPcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            floatBufferRegionToShorts(outBuf, info.offset, info.size)
                        } else {
                            val dup = ByteArray(info.size)
                            outBuf.position(info.offset); outBuf.get(dup)
                            pcmBytesToShorts(dup)
                        }
                        if (chunk.isNotEmpty()) {
                            pcmChunks.add(chunk)
                        }
                    }
                    decoder.releaseOutputBuffer(outIx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEos = true
                } else if (outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFormat = decoder.outputFormat
                    outRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    outCh = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outputPcmEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                }
            }
        } finally {
            try { decoder.stop() } catch (_: Exception) { }
            decoder.release()
            extractor.release()
        }

        val totalShorts = pcmChunks.sumOf { it.size }
        val merged = ShortArray(totalShorts)
        var pos = 0
        for (chunk in pcmChunks) {
            chunk.copyInto(merged, pos); pos += chunk.size
        }
        return (if (outCh <= 1) merged else stereoToMono(merged, outCh)) to outRate
    }

    private fun createAudioDecoder(mime: String): MediaCodec {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        var bestName: String? = null
        var bestPrio = 3
        for (info in list.codecInfos) {
            if (info.isEncoder || info.supportedTypes.none { it.equals(mime, ignoreCase = true) }) continue
            val lower = info.name.lowercase(Locale.US)
            val prio = when {
                lower.contains("c2.android") -> 0
                lower.contains("omx.google") -> 1
                else -> 2
            }
            if (prio < bestPrio) { bestPrio = prio; bestName = info.name }
        }
        return if (bestName != null) MediaCodec.createByCodecName(bestName) else MediaCodec.createDecoderByType(mime)
    }

    private fun floatBufferRegionToShorts(buffer: ByteBuffer, offset: Int, size: Int): ShortArray {
        buffer.position(offset).limit(offset + size)
        val fb = buffer.asFloatBuffer()
        val s = ShortArray(fb.remaining())
        for (i in s.indices) s[i] = (fb.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        return s
    }

    private fun pcmBytesToShorts(bytes: ByteArray): ShortArray {
        val s = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s)
        return s
    }

    private fun shortsToFloats(s: ShortArray): FloatArray {
        val f = FloatArray(s.size)
        for (i in s.indices) f[i] = s[i] / 32768f
        return f
    }

    private fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate || srcRate <= 0) return input
        val outLen = ((input.size.toLong() * dstRate) / srcRate).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i * srcRate.toDouble() / dstRate
            val i0 = srcPos.toInt().coerceIn(0, input.size - 1)
            val i1 = min(i0 + 1, input.size - 1)
            val t = (srcPos - i0).toFloat()
            out[i] = input[i0] * (1 - t) + input[i1] * t
        }
        return out
    }
}
