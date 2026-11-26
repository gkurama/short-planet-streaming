package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

private const val AUDIO_TAG = "StudioAudioGenerator"

object StudioAudioGenerator {

    fun hasAudioTrack(context: Context, uri: Uri): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount).any { idx ->
                val mime = extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME).orEmpty()
                mime.startsWith("audio/")
            }
        } catch (e: Exception) {
            Log.w(AUDIO_TAG, "Failed probing audio track for $uri: ${e.message}")
            false
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Extracts the compressed audio track slice `[startMs..endMs]` from [uri] into a compact `.m4a`/`.mp4`
     * file in cache so Gemini multimodal AI can transcribe actual spoken words and timestamps.
     */
    fun extractClippedAudioForTranscription(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        maxBytes: Int = 3_200_000
    ): File? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        val outFile = File(context.cacheDir, "speech_slice_${System.currentTimeMillis()}.mp4")
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) return null

            extractor.selectTrack(audioTrackIndex)
            val safeStartUs = (startMs.coerceAtLeast(0L)) * 1000L
            val cappedEndMs = endMs.coerceAtMost(startMs + 60_000L)
            val safeEndUs = (cappedEndMs.coerceAtLeast(startMs + 1000L)) * 1000L
            extractor.seekTo(safeStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxTrackIdx = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()
            var totalBytes = 0
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > safeEndUs) break
                if (sampleTimeUs >= safeStartUs) {
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = (sampleTimeUs - safeStartUs).coerceAtLeast(0L)
                    @Suppress("DEPRECATION")
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxTrackIdx, buffer, info)
                    totalBytes += sampleSize
                    if (totalBytes >= maxBytes) break
                }
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            muxer = null

            outFile.takeIf { it.exists() && it.length() > 512L }
        } catch (e: Exception) {
            Log.w(AUDIO_TAG, "Failed extracting audio slice for transcription: ${e.message}")
            try {
                outFile.delete()
            } catch (_: Exception) {
            }
            null
        } finally {
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Generates a warm, studio-safe stereo 16-bit PCM WAV track (44.1 kHz) of exact [durationMs]
     * so image-based Shorts or silent screen recordings always upload to YouTube & TikTok with valid AAC audio.
     */
    fun generateStudioTrackWav(
        context: Context,
        fileName: String,
        durationMs: Long
    ): File {
        val dir = File(context.cacheDir, "studio_audio").apply { mkdirs() }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val outFile = File(dir, safeName)
        if (outFile.exists()) outFile.delete()

        val sampleRate = 44_100
        val channels = 2
        val bitsPerSample = 16
        val safeDurationMs = durationMs.coerceIn(2_000L, 180_000L)
        val totalFrames = ((sampleRate.toLong() * safeDurationMs) / 1000L).toInt().coerceAtLeast(sampleRate)
        val dataByteCount = totalFrames * channels * (bitsPerSample / 8)

        // Warm ambient Am7 -> Fmaj7 -> Cmaj7 -> G6 chord progression
        val chords = arrayOf(
            doubleArrayOf(220.00, 261.63, 329.63, 392.00), // Am7
            doubleArrayOf(174.61, 220.00, 261.63, 329.63), // Fmaj7
            doubleArrayOf(261.63, 329.63, 392.00, 493.88), // Cmaj7
            doubleArrayOf(196.00, 246.94, 293.66, 329.63)  // G6
        )

        BufferedOutputStream(FileOutputStream(outFile), 32 * 1024).use { out ->
            out.write(buildWavHeader(dataByteCount, sampleRate, channels, bitsPerSample))

            val chunkFrames = 2048
            val buf = ByteBuffer.allocate(chunkFrames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
            val fadeFrames = (sampleRate * 0.30).toInt().coerceAtLeast(1)
            val twoPi = 2.0 * PI

            var frameOffset = 0
            while (frameOffset < totalFrames) {
                val count = minOf(chunkFrames, totalFrames - frameOffset)
                buf.clear()
                for (i in 0 until count) {
                    val f = frameOffset + i
                    val t = f.toDouble() / sampleRate.toDouble()
                    val chordIdx = ((t / 2.0).toInt()) % chords.size
                    val chord = chords[chordIdx]

                    // Soft harmonic pad + gentle lo-fi beat pulse every 0.5s
                    val beatPhase = (t % 0.5) / 0.5
                    val pulseEnv = kotlin.math.exp(-beatPhase * 7.5)
                    val bass = sin(twoPi * (chord[0] * 0.5) * t) * 0.30 * (0.45 + 0.55 * pulseEnv)

                    var padL = 0.0
                    var padR = 0.0
                    for (n in chord.indices) {
                        val freq = chord[n]
                        val detune = if (n % 2 == 0) 1.0018 else 0.9982
                        val weight = 0.16 / (1.0 + n * 0.25)
                        padL += sin(twoPi * freq * t) * weight
                        padR += sin(twoPi * freq * detune * t) * weight
                    }

                    // Subtle global envelope (fade in/out + comfortable -16 dBFS level)
                    val envIn = if (f < fadeFrames) f.toDouble() / fadeFrames else 1.0
                    val rem = totalFrames - f
                    val envOut = if (rem < fadeFrames) rem.toDouble() / fadeFrames else 1.0
                    val master = 0.42 * minOf(envIn, envOut)

                    val sampleL = ((bass + padL) * master).coerceIn(-0.95, 0.95)
                    val sampleR = ((bass + padR) * master).coerceIn(-0.95, 0.95)

                    buf.putShort((sampleL * Short.MAX_VALUE).toInt().toShort())
                    buf.putShort((sampleR * Short.MAX_VALUE).toInt().toShort())
                }
                out.write(buf.array(), 0, count * channels * 2)
                frameOffset += count
            }
        }
        return outFile
    }

    private fun buildWavHeader(
        dataByteCount: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val totalDataLen = 36 + dataByteCount
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM chunk size
        header.putShort(1) // AudioFormat = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataByteCount)
        return header.array()
    }
}
