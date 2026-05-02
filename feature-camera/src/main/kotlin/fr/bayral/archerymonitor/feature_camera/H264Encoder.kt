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

class H264Encoder @Inject constructor(
    private val bufferManager: IBufferManager,
) {
    private var mediaCodec: MediaCodec? = null
    private var encoderJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val codecLock = Any()
    private var inputStride = 0
    private var inputSliceHeight = 0

    fun prepare(width: Int, height: Int) {
        synchronized(codecLock) {
            stop()
            try {
                val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                try {
                    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)

                    // Choose a supported color format
                    val capabilities = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    val supportedColorFormats = capabilities?.colorFormats ?: intArrayOf()
                    val colorFormat = when {
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible in supportedColorFormats ->
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar in supportedColorFormats ->
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar in supportedColorFormats ->
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                        else -> supportedColorFormats.firstOrNull() ?: MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    }
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)

                    // Add essential keys for H264 encoding
                    format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000)
                    format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

                    // Validate if the device supports the requested dimensions
                    val videoCapabilities = capabilities?.videoCapabilities
                    if ((videoCapabilities != null) && !videoCapabilities.isSizeSupported(width, height)) {
                        Log.e("H264Encoder", "Size $width x $height is not supported by this encoder")
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

    fun start() {
        synchronized(codecLock) {
            try {
                mediaCodec?.start()
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

    private fun copyAndRotateYUV(image: Image, dst: ByteBuffer, isMirrored: Boolean, rotation: Int) {
        dst.clear()
        val srcW = image.width
        val srcH = image.height
        val planes = image.planes

        // Output dimensions depend on rotation
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

        // 1. Rotate & Flip Y
        for (y in 0 until srcH) {
            for (x in 0 until srcW) {
                var finalX: Int
                var finalY: Int

                when (rotation) {
                    90 -> { // Typical front camera
                        finalX = srcH - 1 - y
                        finalY = x
                    }
                    180 -> { // Upside down landscape
                        finalX = srcW - 1 - x
                        finalY = srcH - 1 - y
                    }
                    270 -> { // Typical back camera
                        finalX = y
                        finalY = srcW - 1 - x
                    }
                    else -> { // 0 - Landscape
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

        // 2. Rotate & Flip U/V (NV12)
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

    fun stop() {
        encoderJob?.cancel()
        encoderJob = null
        synchronized(codecLock) {
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
