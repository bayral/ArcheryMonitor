package fr.bayral.archerymonitor.feature_camera

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import fr.bayral.archerymonitor.core.interfaces.IBufferManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import javax.inject.Inject

class H264Encoder @Inject constructor(
    private val bufferManager: IBufferManager
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
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                format.setInteger(MediaFormat.KEY_BIT_RATE, 5000000)
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                format.setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)

                mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                
                val inputFormat = mediaCodec?.inputFormat
                inputStride = inputFormat?.getInteger(MediaFormat.KEY_STRIDE) ?: width
                inputSliceHeight = inputFormat?.getInteger(MediaFormat.KEY_SLICE_HEIGHT) ?: height
                
                Log.d("H264Encoder", "Encoder prepared: $width x $height (Stride: $inputStride)")
            } catch (e: Exception) {
                Log.e("H264Encoder", "Failed to prepare encoder", e)
            }
        }
    }

    fun start() {
        synchronized(codecLock) {
            mediaCodec?.start()
        }
        encoderJob = scope.launch {
            val info = MediaCodec.BufferInfo()
            while (isActive) {
                val codec = synchronized(codecLock) { mediaCodec } ?: break
                try {
                    val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
                    if (outputBufferIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null) {
                            bufferManager.addPacket(outputBuffer, info)
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                    }
                } catch (e: Exception) { break }
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
        val isPortraitOutput = rotation == 90 || rotation == 270
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

                if (finalY * stride + finalX < dst.capacity()) {
                    dst.put(finalY * stride + finalX, yBuf.get(y * yRowStride + x))
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
                
                val uValue = uBuf.get(y * uvRowStride + x * uvPixelStride)
                val vValue = vBuf.get(y * uvRowStride + x * uvPixelStride)
                
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
            try {
                mediaCodec?.stop()
                mediaCodec?.release()
            } catch (e: Exception) {}
            mediaCodec = null
        }
    }
}
