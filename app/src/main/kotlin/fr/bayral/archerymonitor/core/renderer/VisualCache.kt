package fr.bayral.archerymonitor.core.renderer

import android.graphics.Bitmap
import java.util.ArrayDeque

/**
 * VisualCache stores a fixed-size circular buffer of final composed Bitmaps.
 * It uses a pool of reusable bitmaps to prevent memory pressure from constant allocations.
 */
class VisualCache(private val maxCapacity: Int = 300) {

    private val cache = ArrayDeque<Bitmap>(maxCapacity)
    private val pool = ArrayDeque<Bitmap>(maxCapacity)

    /**
     * Obtains a bitmap from the pool if available, otherwise creates a new one.
     */
    @Synchronized
    fun getReusableBitmap(width: Int, height: Int): Bitmap {
        val pooled = pool.poll()
        if (pooled != null && pooled.width == width && pooled.height == height) {
            return pooled
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    @Synchronized
    fun addFrame(bitmap: Bitmap) {
        if (cache.size >= maxCapacity) {
            val oldest = cache.removeFirst()
            pool.add(oldest) // Move to pool for reuse
        }
        cache.addLast(bitmap)
    }

    @Synchronized
    fun getFrameAt(index: Int): Bitmap? {
        return cache.elementAtOrNull(index)
    }

    @Synchronized
    fun clear() {
        while (cache.isNotEmpty()) {
            pool.add(cache.removeFirst())
        }
    }

    val size: Int
        @Synchronized get() = cache.size
}
