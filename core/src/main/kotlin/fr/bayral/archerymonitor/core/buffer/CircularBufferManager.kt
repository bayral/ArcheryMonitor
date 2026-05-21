package fr.bayral.archerymonitor.core.buffer

import android.content.Context
import android.media.MediaCodec
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.bayral.archerymonitor.core.interfaces.EncodedPacket
import fr.bayral.archerymonitor.core.interfaces.IBufferManager
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance circular buffer implementation using Memory-Mapped Files (mmap).
 *
 * This class stores encoded H.264 packets on disk while maintaining them in virtual memory
 * for zero-latency random access. It supports up to 30s of HD video without OOM issues.
 *
 * @property context The application context to access the internal storage directory.
 */
@Singleton
class CircularBufferManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : IBufferManager {

    /** Fixed size of the memory-mapped file (50MB). */
    private val bufferSize = 50 * 1024 * 1024L 

    /** File reference for the circular buffer on internal storage. */
    private val bufferFile = File(context.filesDir, "video_buffer.bin")

    /** The mapped byte buffer allowing direct memory access to the file. */
    private var mappedBuffer: MappedByteBuffer? = null

    /** File channel used for the mapping operation. */
    private var fileChannel: FileChannel? = null

    /** Metadata list for every packet currently stored in the buffer. */
    private val packets = mutableListOf<PacketMeta>()

    init {
        setupBuffer()
    }

    /**
     * Initializes the random access file and creates the memory mapping.
     */
    private fun setupBuffer() {
        val raf = RandomAccessFile(bufferFile, "rw")
        raf.setLength(bufferSize)
        fileChannel = raf.channel
        mappedBuffer = fileChannel?.map(FileChannel.MapMode.READ_WRITE, 0, bufferSize)
    }

    /**
     * Adds an encoded packet to the buffer.
     * Handles buffer wrap-around by clearing the metadata list when space is exhausted.
     *
     * @param data The encoded [ByteBuffer] from the encoder.
     * @param info Metadata about the buffer size, offset, and timestamp.
     */
    override fun addPacket(data: ByteBuffer, info: MediaCodec.BufferInfo) {
        val buffer = mappedBuffer ?: return

        synchronized(this) {
            // Check if we need to wrap around to the beginning of the file
            if ((buffer.position() + info.size) > bufferSize) {
                Log.d("CircularBufferManager", "Buffer wrap around! Resetting position to 0.")
                buffer.position(0)
            }

            val offset = buffer.position()
            val endOffset = offset + info.size

            // Discard any packets that overlap with the written range [offset, endOffset]
            packets.removeAll { packet ->
                val packetStart = packet.offset
                val packetEnd = packet.offset + packet.size
                maxOf(offset, packetStart) < minOf(endOffset, packetEnd)
            }

            data.position(info.offset)
            data.limit(info.offset + info.size)
            
            // Direct memory copy to mmap space
            buffer.put(data)

            packets.add(
                PacketMeta(
                    offset = offset,
                    size = info.size,
                    presentationTimeUs = info.presentationTimeUs,
                    flags = info.flags,
                ),
            )

            if ((packets.size % 100) == 0) {
                Log.d("CircularBufferManager", "Status: ${packets.size} packets stored. Latest PTS: ${info.presentationTimeUs}")
            }
        }
    }

    /**
     * Retrieves a list of packets within the requested time range.
     *
     * @param startTimeUs The start timestamp (inclusive) in microseconds.
     * @param endTimeUs The end timestamp (inclusive) in microseconds.
     * @return List of packets ready for decoding.
     */
    override fun getPacketsForRange(startTimeUs: Long, endTimeUs: Long): List<EncodedPacket> {
        val result = mutableListOf<EncodedPacket>()
        val matchingMetas = synchronized(this) {
            packets.filter { (it.presentationTimeUs in startTimeUs..endTimeUs) }
        }

        if (matchingMetas.isEmpty()) return emptyList()

        matchingMetas.forEach { meta ->
            mappedBuffer?.let { mb ->
                synchronized(this) {
                    val currentPos = mb.position()
                    
                    // Slice the buffer to provide a clean view of this specific packet
                    mb.position(meta.offset)
                    val slice = mb.slice()
                    slice.limit(meta.size)

                    val info = MediaCodec.BufferInfo().apply {
                        set(0, meta.size, meta.presentationTimeUs, meta.flags)
                    }

                    result.add(
                        EncodedPacket(
                            data = slice,
                            info = info,
                            isKeyFrame = (meta.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0,
                        ),
                    )
                    
                    // Restore original buffer position for future writes
                    mb.position(currentPos)
                }
            }
        }
        return result
    }

    /**
     * Returns the timestamp of the very last packet added to the buffer.
     */
    override fun getLatestTimestamp(): Long {
        return synchronized(this) {
            packets.lastOrNull()?.presentationTimeUs ?: 0L
        }
    }

    /**
     * Closes the file channel and deletes the buffer file.
     */
    override fun release() {
        fileChannel?.close()
        if (bufferFile.exists()) {
            bufferFile.delete()
        }
    }

    /**
     * Internal metadata for a single packet in the circular buffer.
     */
    private data class PacketMeta(
        /** Byte offset in the mapped file. */
        val offset: Int,
        /** Size of the packet in bytes. */
        val size: Int,
        /** Presentation timestamp from the encoder. */
        val presentationTimeUs: Long,
        /** MediaCodec flags (e.g. keyframe). */
        val flags: Int
    )
}
