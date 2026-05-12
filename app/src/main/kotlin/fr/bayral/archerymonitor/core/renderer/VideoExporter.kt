package fr.bayral.archerymonitor.core.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Exporter responsible for converting a sequence of Bitmaps from VisualCache into an MP4 video.
 */
class VideoExporter {

    private val TAG = "VideoExporter"

    suspend fun export(
        bitmaps: List<Bitmap>,
        outputFile: File,
        width: Int,
        height: Int,
        frameRate: Int = 30
    ): Boolean = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) return@withContext false

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 4000000)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1000000L / frameRate

            for ((index, bitmap) in bitmaps.withIndex()) {
                // Render bitmap to surface
                val canvas = inputSurface.lockCanvas(null)
                canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), null)
                inputSurface.unlockCanvasAndPost(canvas)

                // Drain encoder
                while (true) {
                    val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, 0)
                    if (encoderStatus == MediaCodec.dequeueOutputBuffer(bufferInfo, 0)) {
                        // Nothing to do
                    }
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxerStarted) throw RuntimeException("Format changed twice")
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (encoderStatus >= 0) {
                        val encodedData = encoder.getOutputBuffer(encoderStatus) ?: throw RuntimeException("Buffer was null")
                        
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size != 0) {
                            if (!muxerStarted) throw RuntimeException("Muxer not started")
                            bufferInfo.presentationTimeUs = index * frameDurationUs
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }

                        encoder.releaseOutputBuffer(encoderStatus, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }

            encoder.signalEndOfInputStream()
            // Final drain
            var done = false
            while (!done) {
                val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    done = true
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Already handled
                } else if (encoderStatus >= 0) {
                    val encodedData = encoder.getOutputBuffer(encoderStatus)!!
                    if (bufferInfo.size != 0) {
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(encoderStatus, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            false
        } finally {
            encoder?.stop()
            encoder?.release()
            muxer?.stop()
            muxer?.release()
        }
    }
}
