package com.akis.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.akis.app.domain.FeedRules
import com.akis.app.domain.Limits
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Pictures for tiles and the reader, with hard bounds: https only, 2 MB per download, a pixel cap,
 * three downloads at once, a memory cache of 1/16 of the heap and a disk cache trimmed to
 * [Limits.IMAGE_CACHE_BYTES]. Blocking work: call [load] from an IO dispatcher.
 */
@Suppress("TooManyFunctions")
object ImageStore {
    private const val MAX_PIXELS = 25_000_000L
    private const val MAX_SIDE = 8_000
    private const val MAX_PARALLEL = 3
    private const val HEAP_DIVISOR = 16
    private const val MAX_CACHE_BYTES = 24 * 1024 * 1024
    private const val MAX_DECODE_PIXELS = 1_500_000L
    private const val NAME_LENGTH = 32
    private var dir: File? = null
    private val gate = Semaphore(MAX_PARALLEL)
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val memory = object : LruCache<String, Bitmap>(minOf(Runtime.getRuntime().maxMemory() / HEAP_DIVISOR, MAX_CACHE_BYTES.toLong()).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun setup(cacheDir: File) {
        dir = File(cacheDir, "img").apply { mkdirs() }
    }

    /** A picture already in memory, for a first frame without flicker. */
    fun peek(url: String): Bitmap? = memory.get(url)

    suspend fun load(url: String, targetPx: Int): Bitmap? {
        val cached = peek(url)
        return when {
            cached != null -> cached
            url in failed -> null
            else -> gate.withPermit {
                val file = fileFor(url)
                val bitmap = file?.let { fromFile(it, targetPx) ?: download(url, it, targetPx) }
                if (bitmap == null) failed.add(url) else memory.put(url, bitmap)
                bitmap
            }
        }
    }

    private fun fileFor(url: String): File? = dir?.let { File(it, FeedRules.feedId(url).take(NAME_LENGTH) + "_" + url.hashCode().toUInt()) }

    private fun fromFile(file: File, targetPx: Int): Bitmap? {
        if (!file.exists()) return null
        val bitmap = try {
            decode(file.readBytes(), targetPx)
        } catch (_: IOException) {
            null
        }
        if (bitmap == null) file.delete() else file.setLastModified(System.currentTimeMillis())
        return bitmap
    }

    private fun download(url: String, file: File, targetPx: Int): Bitmap? {
        val bytes = (FeedFetcher.fetch(url, "", "", FeedFetcher.ACCEPT_IMAGE) as? HttpResult.Ok)?.body
        val bitmap = bytes?.let { decode(it, targetPx) }
        if (bytes != null && bitmap != null) {
            try {
                file.writeBytes(bytes)
            } catch (_: IOException) {
                // The picture still shows; it just will not be cached.
            }
        }
        return bitmap
    }

    /** Reads the size first, refuses huge pictures, and decodes at the smallest scale that still fills [targetPx]. */
    private fun decode(bytes: ByteArray, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (!fits(w, h)) return null
        var sample = 1
        while (minOf(w, h) / (sample * 2) >= targetPx) sample *= 2
        // However big the source is, one decoded picture stays near 6 MB.
        while ((w / sample).toLong() * (h / sample) > MAX_DECODE_PIXELS) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun fits(w: Int, h: Int): Boolean = w in 1..MAX_SIDE && h in 1..MAX_SIDE && w.toLong() * h <= MAX_PIXELS

    fun sizeBytes(): Long = dir?.listFiles().orEmpty().sumOf { it.length() }

    /** Deletes the oldest files until the cache fits its limit. */
    fun trim() {
        var total = sizeBytes()
        dir?.listFiles().orEmpty().sortedBy { it.lastModified() }.forEach { f ->
            if (total > Limits.IMAGE_CACHE_BYTES) {
                total -= f.length()
                f.delete()
            }
        }
    }

    /** The app is hidden or the system is short of memory: drop decoded pictures; they reload from the disk cache. */
    fun releaseMemory() = memory.evictAll()

    fun clear() {
        dir?.listFiles().orEmpty().forEach { it.delete() }
        memory.evictAll()
        failed.clear()
    }
}
