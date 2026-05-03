package fr.bayral.archerymonitor.feature_camera

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import fr.bayral.archerymonitor.core.interfaces.IBufferManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * H.264 Video Decoder for delayed replay playback.
 *
 * This class retrieves encoded packets from the [IBufferManager] based on a target delay,
 * feeds them into a [MediaCodec] decoder, and outputs the decoded frames to a [Surface].
 *
 * @property bufferManager Source of encoded H.264 packets.
 */
class H264Decoder @Inject constructor(
    private val bufferManager: IBufferManager
) {
    /** The Android hardware codec instance. */
    private var mediaCodec: MediaCodec? = null

    /** Job for the asynchronous decoding and playback loop. */
    private var decoderJob: Job? = null

    /** Coroutine scope for playback timing and polling. */
    private val scope = CoroutineScope(Dispatchers.Default)

    /** Internal flow for tracking the current frame's presentation timestamp. */
    private val _currentPlaybackTimestamp = MutableStateFlow(0L)

    /** [StateFlow] emitting the timestamp of the frame currently being displayed. */
    val currentPlaybackTimestamp: StateFlow<Long> = _currentPlaybackTimestamp.asStateFlow()

    /**
     * Starts the decoder and the playback loop.
     *
     * @param surface The destination surface for video output.
     * @param width Expected frame width.
     * @param height Expected frame height.
     * @param delaySeconds The replay delay in seconds.
     * @param onStarted Callback invoked once the decoder is initialized.
     */
    fun start(surface: Surface, width: Int, height: Int, delaySeconds: Float, onStarted: () -> Unit) {
        stop()
        Log.d("H264Decoder", "STARTING DECODER V3 - Delay: ${delaySeconds}s")
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(MediaFormat.KEY_ROTATION, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }

            val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            try {
                codec.configure(format, surface, null, 0)
                codec.start()
                mediaCodec = codec
                onStarted()
                Log.d("H264Decoder", "Decoder started: $width x $height")
            } catch (e: Exception) {
                codec.release()
                throw e
            }
        } catch (e: Exception) {
            Log.e("H264Decoder", "Failed to start decoder: ${e.message}")
            mediaCodec = null
            return
        }

        // Main Playback Coroutine
        decoderJob = scope.launch {
            val info = MediaCodec.BufferInfo()
            var lastQueuedPts = -1L

            while (isActive) {
                val codec = mediaCodec ?: break

                val latestPts = bufferManager.getLatestTimestamp()
                if (latestPts == 0L) {
                    delay(100)
                    continue
                }

                // Target timestamp based on configured delay
                val targetTimeUs = latestPts - (delaySeconds * 1_000_000).toLong()
                
                // Fetch candidate packets near the target time
                val packets = bufferManager.getPacketsForRange(targetTimeUs - 100_000, targetTimeUs + 100_000)

                if (packets.isNotEmpty()) {
                    // Pick the best match that hasn't been queued yet
                    val packet = packets
                        .filter { it.info.presentationTimeUs > lastQueuedPts }
                        .minByOrNull { abs(it.info.presentationTimeUs - targetTimeUs) }

                    if (packet != null) {
                        try {
                            val inputBufferIndex = codec.dequeueInputBuffer(5000)
                            if (inputBufferIndex >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                                if (inputBuffer != null) {
                                    inputBuffer.clear()
                                    inputBuffer.put(packet.data)
                                    codec.queueInputBuffer(
                                        inputBufferIndex,
                                        0,
                                        packet.info.size,
                                        packet.info.presentationTimeUs,
                                        packet.info.flags
                                    )
                                    lastQueuedPts = packet.info.presentationTimeUs
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("H264Decoder", "Queuing error: ${e.message}")
                        }
                    }
                }

                // Polling for output buffers (decoded pixels)
                try {
                    var outputBufferIndex = codec.dequeueOutputBuffer(info, 5000)
                    while (outputBufferIndex >= 0) {
                        // Render to surface and broadcast timestamp for AI sync
                        codec.releaseOutputBuffer(outputBufferIndex, true)
                        _currentPlaybackTimestamp.value = info.presentationTimeUs
                        outputBufferIndex = codec.dequeueOutputBuffer(info, 0)
                    }
                } catch (e: Exception) {
                    if (e !is IllegalStateException) {
                        Log.e("H264Decoder", "Dequeuing error: ${e.message}")
                    }
                }
                delay(10)
            }
        }
    }

    /**
     * Cancels the playback loop and releases hardware resources.
     */
    fun stop() {
        decoderJob?.cancel()
        decoderJob = null
        mediaCodec?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                Log.e("H264Decoder", "Error stopping codec: ${e.message}")
            } finally {
                try {
                    it.release()
                } catch (e: Exception) {
                    Log.e("H264Decoder", "Error releasing codec: ${e.message}")
                }
            }
        }
        mediaCodec = null
        _currentPlaybackTimestamp.value = 0L
    }
}
