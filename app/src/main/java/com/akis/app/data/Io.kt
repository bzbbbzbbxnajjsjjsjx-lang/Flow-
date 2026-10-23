package com.akis.app.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val CHUNK = 8 * 1024

/** Reads the stream fully, or returns null as soon as it grows past [limit] bytes. */
fun InputStream.readUpTo(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(CHUNK)
    var total = 0
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
