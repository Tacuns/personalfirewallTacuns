package com.sentinel.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BackupReaderTest {

    /** Hands out at most [step] bytes per read, like a real content stream can. */
    private class Trickle(bytes: ByteArray, private val step: Int) : InputStream() {
        private val src = ByteArrayInputStream(bytes)
        override fun read(): Int = src.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = src.read(b, off, minOf(len, step))
    }

    @Test fun multibyteTextAcrossChunkEdgesIsKept() {
        val text = "{\"app\":\"" + "தமிழ்".repeat(700) + "\"}"   // > 8 KB of 3-byte characters
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertEquals(text, BackupReader.readCapped(ByteArrayInputStream(bytes), 2 * 1024 * 1024))
    }

    @Test fun oddSizedReadsStillDecodeCorrectly() {
        val text = "日本語 عربى हिन्दी 😀".repeat(500)
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertEquals(text, BackupReader.readCapped(Trickle(bytes, 7), 2 * 1024 * 1024))
    }

    @Test fun exactlyAtCapIsAccepted() {
        val bytes = ByteArray(1000) { 'a'.code.toByte() }
        assertEquals(1000, BackupReader.readCapped(ByteArrayInputStream(bytes), 1000)!!.length)
    }

    @Test fun overCapIsRefused() {
        val bytes = ByteArray(1001) { 'a'.code.toByte() }
        assertNull(BackupReader.readCapped(ByteArrayInputStream(bytes), 1000))
    }

    @Test fun emptyFileGivesEmptyText() =
        assertEquals("", BackupReader.readCapped(ByteArrayInputStream(ByteArray(0)), 1000))
}
