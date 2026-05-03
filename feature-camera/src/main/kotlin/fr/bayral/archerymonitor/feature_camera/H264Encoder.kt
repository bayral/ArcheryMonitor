package fr.bayral.archerymonitor.feature_camera

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import fr.bayral.archerymonitor.core.interfaces.IBufferManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * High-performance H.264 Video Encoder using [MediaCodec].
 *
 * This class captures raw YUV frames from the camera, rotates and mirrors them
 * using manual pixel manipulation (optimized for H.264 input requirements),
 * and feeds the resulting bitstream to the [IBufferManager].
 *
 * @property bufferManager The manager used to store encoded packets.
 */
class H264Encoder @Inject constructor(
    private val bufferManager: IBufferManager,
) {
    /** The Android hardware codec instance. */
    private var mediaCodec: MediaCodec? = null

    /** Job for the asynchronous output buffer processing loop. */
    private var encoderJob: Job? = null

    /** Coroutine scope for the processing loop. */
    private val scope = CoroutineScope(Dispatchers.Default)

    /** Synchronization lock for codec operations. */
    private val codecLock = Any()

    /** Public flag indicating if the encoder is currently running. */
    var isEncoding = false
        private set

    /** Internal input stride required by the codec (often equals width). */
    private var inputStride = 0

    /** Internal slice height required by the codec (often equals height). */
    private var inputSliceHeight = 0

    /**
     * Prepares the encoder with specific resolution.
     *
     * @param width The target output width.
     * @param height The target output height.
     */
    fun prepare(width: Int, height: Int) {
        synchronized(codecLock) {
            stop()
            try {
                val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                try {
                    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)

                    // Choose a supported color format (prefer YUV420Flexible)
                    val capabilities = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    val supportedColorFormats = capabilities?.colorFormats ?: intArrayOf()
                    val colorFormat = if (MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible in supportedColorFormats) {
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    } else {
                        supportedColorFormats.firstOrNull() ?: MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    }
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)

                    format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000)
                    format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

                    val videoCapabilities = capabilities?.videoCapabilities
                    if ((videoCapabilities != null) && !videoCapabilities.isSizeSupported(width, height)) {
                        throw IllegalArgumentException("Size $width x $height is not supported")
                    }

                    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

                    val inputFormat = codec.inputFormat
                    inputStride = if (inputFormat.containsKey(MediaFormat.KEY_STRIDE)) inputFormat.getInteger(MediaFormat.KEY_STRIDE) else width
                    inputSliceHeight = if (inputFormat.containsKey(MediaFormat.KEY_SLICE_HEIGHT)) inputFormat.getInteger(MediaFormat.KEY_SLICE_HEIGHT) else height

                    mediaCodec = codec
                    Log.d("H264Encoder", "Encoder prepared: $width x $height (Stride: $inputStride)")
                } catch (e: Exception) {
                    codec.release()
                    throw e
                }
            } catch (e: Exception) {
                Log.e("H264Encoder", "Failed to prepare encoder: ${e.message}")
                mediaCodec = null
            }
        }
    }

    /**
     * Starts the encoding session and the output polling loop.
     */
    fun start() {
        synchronized(codecLock) {
            try {
                mediaCodec?.start()
                isEncoding = true
            } catch (e: Exception) {
                Log.e("H264Encoder", "Failed to start encoder: ${e.message}")
                return
            }
        }
        encoderJob = scope.launch {
            val info = MediaCodec.BufferInfo()
            while (isActive) {
                val codec = synchronized(codecLock) { mediaCodec } ?: break
                try {
                    val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
                    if (outputBufferIndex >= 0) {
                        codec.getOutputBuffer(outputBufferIndex)?.let { outputBuffer ->
                            bufferManager.addPacket(outputBuffer, info)
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                    }
                } catch (_: Exception) { break }
            }
        }
    }

    /**
     * Encodes a single raw camera [Image].
     *
     * @param image The raw input frame.
     * @param timestampUs Presentation timestamp in microseconds.
     * @param isMirrored Whether to apply a horizontal mirror flip.
     * @param rotation Rotation in degrees (0, 90, 180, 270).
     */
    fun encodeImage(image: Image, timestampUs: Long, isMirrored: Boolean, rotation: Int) {
        synchronized(codecLock) {
            val codec = mediaCodec ?: return
            try {
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        copyAndRotateYUV(image, inputBuffer, isMirrored, rotation)
                        codec.queueInputBuffer(inputBufferIndex, 0, inputBuffer.capacity(), timestampUs, 0)
                    }
                }
            } catch (e: Exception) {
                Log.e("H264Encoder", "Input queuing failed", e)
            }
        }
    }

    /**
     * Internal pixel manipulation to rotate and mirror YUV planes to match codec layout.
     *
     * @param image Source camera frame.
     * @param dst Destination [ByteBuffer] (MediaCodec input buffer).
     */
    private fun copyAndRotateYUV(image: Image, dst: ByteBuffer, isMirrored: Boolean, rotation: Int) {
        dst.clear()
        val srcW = image.width
        val srcH = image.height
        val planes = image.planes

        val isPortraitOutput = (rotation == 90) || (rotation == 270)
        val dstW = if (isPortraitOutput) srcH else srcW
        val dstH = if (isPortraitOutput) srcW else srcH
        val stride = if (inputStride > 0) inputStride else dstW

        val yBuf = planes[0].buffer
        val uBuf = planes[1].buffer
        val vBuf = planes[2].buffer

        val yRowStride = planes[0].rowStride
        val uvRowStride = planes[1].rowStride
        val uvPixelStride = planes[1].pixelStride

        // 1. Rotate & Flip Y Plane
        for (y in 0 until srcH) {
            for (x in 0 until srcW) {
                var finalX: Int
                var finalY: Int

                when (rotation) {
                    90 -> {
                        finalX = srcH - 1 - y
                        finalY = x
                    }
                    180 -> {
                        finalX = srcW - 1 - x
                        finalY = srcH - 1 - y
                    }
                    270 -> {
                        finalX = y
                        finalY = srcW - 1 - x
                    }
                    else -> {
                        finalX = x
                        finalY = y
                    }
                }

                if (isMirrored) {
                    val currentW = if (isPortraitOutput) srcH else srcW
                    finalX = currentW - 1 - finalX
                }

                if ((finalY * stride + finalX) < dst.capacity()) {
                    dst.put(finalY * stride + finalX, yBuf[y * yRowStride + x])
                }
            }
        }

        // 2. Rotate & Flip U/V Planes (Interleaved NV12 format)
        val uvOffset = stride * (if (inputSliceHeight > 0) inputSliceHeight else dstH)
        for (y in 0 until srcH / 2) {
            for (x in 0 until srcW / 2) {
                var finalX: Int
                var finalY: Int

                when (rotation) {
                    90 -> {
                        finalX = srcH / 2 - 1 - y
                        finalY = x
                    }
                    180 -> {
                        finalX = srcW / 2 - 1 - x
                        finalY = srcH / 2 - 1 - y
                    }
                    270 -> {
                        finalX = y
                        finalY = srcW / 2 - 1 - x
                    }
                    else -> {
                        finalX = x
                        finalY = y
                    }
                }

                if (isMirrored) {
                    val currentW = if (isPortraitOutput) srcH / 2 else srcW / 2
                    finalX = currentW - 1 - finalX
                }

                val uValue = uBuf[y * uvRowStride + x * uvPixelStride]
                val vValue = vBuf[y * uvRowStride + x * uvPixelStride]

                val pos = uvOffset + (finalY * stride) + (finalX * 2)
                if (pos + 1 < dst.capacity()) {
                    dst.put(pos, uValue)
                    dst.put(pos + 1, vValue)
                }
            }
        }
    }

    /**
     * Cancels the loop, stops the codec, and releases hardware resources.
     */
    fun stop() {
        encoderJob?.cancel()
        encoderJob = null
        synchronized(codecLock) {
            isEncoding = false
            mediaCodec?.let {
                try {
                    it.stop()
                } catch (e: Exception) {
                    Log.e("H264Encoder", "Error stopping codec: ${e.message}")
                } finally {
                    try {
                        it.release()
                    } catch (e: Exception) {
                        Log.e("H264Encoder", "Error releasing codec: ${e.message}")
                    }
                }
            }
            mediaCodec = null
        }
    }
}
