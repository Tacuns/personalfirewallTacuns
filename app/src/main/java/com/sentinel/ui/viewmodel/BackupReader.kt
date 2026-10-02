package com.sentinel.ui.viewmodel

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads a backup file as UTF-8 text, refusing anything larger than [maxBytes].
 *
 * The bytes are decoded once at the end. Decoding each 8 KB chunk on its own broke any
 * character that crossed a chunk edge (Tamil, Hindi, Arabic and other non-Latin text
 * came back with "�" in it).
 */
object BackupReader {

    /** The file's text, or null when it is larger than [maxBytes]. */
    fun readCapped(input: InputStream, maxBytes: Int): String? {
        val buf = ByteArray(8 * 1024)
        val out = ByteArrayOutputStream()
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            if (out.size() + n > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
