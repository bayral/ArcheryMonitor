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

@Singleton
class CircularBufferManager @Inject constructor(
    @ApplicationContext private val context: Context
) : IBufferManager {

    private val BUFFER_SIZE = 50 * 1024 * 1024L // 50MB
    private val bufferFile = File(context.filesDir, "video_buffer.bin")
    private var mappedBuffer: MappedByteBuffer? = null
    private var fileChannel: FileChannel? = null

    private val packets = mutableListOf<PacketMeta>()

    init {
        setupBuffer()
    }

    private fun setupBuffer() {
        val raf = RandomAccessFile(bufferFile, "rw")
        raf.setLength(BUFFER_SIZE)
        fileChannel = raf.channel
        mappedBuffer = fileChannel?.map(FileChannel.MapMode.READ_WRITE, 0, BUFFER_SIZE)
    }

    override fun addPacket(data: ByteBuffer, info: MediaCodec.BufferInfo) {
        val buffer = mappedBuffer ?: return

        synchronized(this) {
            if (buffer.position() + info.size > BUFFER_SIZE) {
                Log.d("CircularBufferManager", "Buffer wrap around! Clearing ${packets.size} packets.")
                buffer.position(0)
                packets.clear()
            }

            val offset = buffer.position()
            data.position(info.offset)
            data.limit(info.offset + info.size)
            buffer.put(data)

            packets.add(PacketMeta(
                offset = offset,
                size = info.size,
                presentationTimeUs = info.presentationTimeUs,
                flags = info.flags
            ))
            
            if (packets.size % 100 == 0) {
                Log.d("CircularBufferManager", "Status: ${packets.size} packets stored. Latest PTS: ${info.presentationTimeUs}")
            }
        }
    }

    override fun getPacketsForRange(startTimeUs: Long, endTimeUs: Long): List<EncodedPacket> {
        val result = mutableListOf<EncodedPacket>()
        val matchingMetas = synchronized(this) {
            packets.filter { it.presentationTimeUs in startTimeUs..endTimeUs }
        }

        if (matchingMetas.isEmpty()) return emptyList()

        matchingMetas.forEach { meta ->
            mappedBuffer?.let { mb ->
                synchronized(this) {
                    val currentPos = mb.position()
                    mb.position(meta.offset)
                    val slice = mb.slice()
                    slice.limit(meta.size)

                    val info = MediaCodec.BufferInfo().apply {
                        set(0, meta.size, meta.presentationTimeUs, meta.flags)
                    }

                    result.add(EncodedPacket(
                        data = slice,
                        info = info,
                        isKeyFrame = (meta.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                    ))
                    mb.position(currentPos)
                }
            }
        }
        return result
    }

    override fun getLatestTimestamp(): Long {
        return synchronized(this) {
            packets.lastOrNull()?.presentationTimeUs ?: 0L
        }
    }

    override fun release() {
        fileChannel?.close()
        if (bufferFile.exists()) {
            bufferFile.delete()
        }
    }

    private data class PacketMeta(
        val offset: Int,
        val size: Int,
        val presentationTimeUs: Long,
        val flags: Int
    )
}
