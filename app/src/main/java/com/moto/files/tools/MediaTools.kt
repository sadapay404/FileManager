package com.moto.files.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Photo JPEG compression + video re-encode at a scaled bitrate. */
object MediaTools {

    suspend fun shrinkPhoto(src: File, dest: File, quality: Int = 70, maxDim: Int = 1920): File? =
        withContext(Dispatchers.IO) {
            try {
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(src.absolutePath, b)
                val largest = maxOf(b.outWidth, b.outHeight)
                var sample = 1
                while (largest / (sample * 2) >= maxDim) sample *= 2
                val bmp = BitmapFactory.decodeFile(
                    src.absolutePath,
                    BitmapFactory.Options().apply {
                        if (largest > maxDim) inSampleSize = sample
                    }
                ) ?: return@withContext null
                dest.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), it) }
                bmp.recycle()
                dest
            } catch (e: Exception) { null }
        }

    /**
     * Re-encode the video track at [scale] × its original bitrate (min 400 kbps).
     * Extractor → decoder → encoder surface → encoder → muxer, using the
     * dequeueInputBuffer/dequeueOutputBuffer API. Audio is not carried over.
     */
    suspend fun shrinkVideo(src: File, dest: File, scale: Float = 0.5f): File? =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            var decoder: MediaCodec? = null
            var encoder: MediaCodec? = null
            var muxer: MediaMuxer? = null
            try {
                extractor.setDataSource(src.absolutePath)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                        ?.startsWith("video/") == true
                } ?: return@withContext null
                extractor.selectTrack(track)

                val input = extractor.getTrackFormat(track)
                val mime = input.getString(MediaFormat.KEY_MIME)!!
                val w = input.getInteger(MediaFormat.KEY_WIDTH)
                val h = input.getInteger(MediaFormat.KEY_HEIGHT)
                val bitrate = if (input.containsKey(MediaFormat.KEY_BIT_RATE))
                    input.getInteger(MediaFormat.KEY_BIT_RATE) else 4_000_000
                val fps = if (input.containsKey(MediaFormat.KEY_FRAME_RATE))
                    input.getInteger(MediaFormat.KEY_FRAME_RATE) else 30
                val iframe = if (input.containsKey(MediaFormat.KEY_I_FRAME_INTERVAL))
                    input.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL) else 1

                encoder = MediaCodec.createEncoderByType(mime).apply {
                    configure(
                        MediaFormat.createVideoFormat(mime, w, h).apply {
                            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                            setInteger(MediaFormat.KEY_BIT_RATE,
                                (bitrate * scale).toInt().coerceAtLeast(400_000))
                            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iframe)
                        },
                        null, null, MediaCodec.CONFIGURE_FLAG_ENCODE
                    )
                    start()
                }
                val surface = encoder.createInputSurface()
                decoder = MediaCodec.createDecoderByType(mime).apply {
                    configure(input, surface, null, 0)
                    start()
                }
                muxer = MediaMuxer(dest.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                var muxerTrack = -1
                var muxerOn = false
                var inputEos = false
                var outputEos = false
                val info = MediaCodec.BufferInfo()
                val timeoutUs = 10_000L

                while (!outputEos) {
                    if (!inputEos) {
                        val idx = decoder.dequeueInputBuffer(timeoutUs)
                        if (idx >= 0) {
                            val buf = decoder.getInputBuffer(idx)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(idx, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                decoder.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val o = decoder.dequeueOutputBuffer(info, timeoutUs)
                    if (o >= 0) {
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoder.signalEndOfInputStream()
                        }
                        decoder.releaseOutputBuffer(o, info.size > 0)
                    }
                    val e = encoder.dequeueOutputBuffer(info, timeoutUs)
                    when {
                        e == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            muxerTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerOn = true
                        }
                        e >= 0 -> {
                            if (muxerOn && info.size > 0 &&
                                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                muxer.writeSampleData(muxerTrack, encoder.getOutputBuffer(e)!!, info)
                            }
                            encoder.releaseOutputBuffer(e, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEos = true
                        }
                    }
                }
                dest.takeIf { it.exists() && it.length() > 0 }
            } catch (e: Exception) {
                null
            } finally {
                try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
                try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
                try { muxer?.stop() } catch (_: Exception) {}
                try { muxer?.release() } catch (_: Exception) {}
                extractor.release()
            }
        }

    fun durationMs(f: File): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) { 0L } finally { r.release() }
    }
}
