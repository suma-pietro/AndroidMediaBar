package com.launcher.androidmediabar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs against the real themes dropped in app/sampledata (git-ignored, so skipped when absent).
 * Bitmap decoding needs a device: here we check that the embedded JPEG/PNG streams are found intact.
 */
class Ps3ThemeImporterTest {

    private val themes = File("sampledata").listFiles { f -> f.extension.equals("p3t", true) }.orEmpty()

    @Test
    fun findsCompleteImagesInSampleThemes() {
        assumeTrue("No .p3t in app/sampledata", themes.isNotEmpty())
        for (theme in themes) {
            val bytes = theme.readBytes()
            assertEquals("P3TF", String(bytes, 0, 4, Charsets.US_ASCII))

            val images = Ps3ThemeImporter.findImages(bytes, 0x40, bytes.size)
            println("${theme.name}: ${images.map { "${it.first}..${it.last} (${jpegSize(bytes, it) ?: "png"})" }}")
            assertTrue("${theme.name}: no images", images.isNotEmpty())
            for (range in images) {
                val isJpeg = bytes[range.first] == 0xFF.toByte()
                if (isJpeg) {
                    // Must end exactly on EOI, not on an embedded thumbnail.
                    assertEquals(0xFF.toByte(), bytes[range.last - 1])
                    assertEquals(0xD9.toByte(), bytes[range.last])
                    assertTrue("${theme.name}: JPEG without frame", jpegSize(bytes, range) != null)
                }
            }
        }
    }

    @Test
    fun sampleThemesContainAWallpaperSizedPicture() {
        assumeTrue("No .p3t in app/sampledata", themes.isNotEmpty())
        for (theme in themes) {
            val bytes = theme.readBytes()
            val sizes = Ps3ThemeImporter.findImages(bytes, 0x40, bytes.size).mapNotNull { jpegSize(bytes, it) }
            assertTrue("${theme.name}: sizes=$sizes", sizes.any { (w, _) -> w >= 1280 })
        }
    }

    /** Width/height of the main frame (SOF0..SOF2), walking markers so thumbnails are skipped. */
    private fun jpegSize(b: ByteArray, r: IntRange): Pair<Int, Int>? {
        if (b[r.first] != 0xFF.toByte()) return null
        var p = r.first + 2
        while (p + 8 < r.last) {
            val marker = b[p + 1].toInt() and 0xFF
            val length = ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)
            if (marker in 0xC0..0xC2) {
                val h = ((b[p + 5].toInt() and 0xFF) shl 8) or (b[p + 6].toInt() and 0xFF)
                val w = ((b[p + 7].toInt() and 0xFF) shl 8) or (b[p + 8].toInt() and 0xFF)
                return w to h
            }
            p += 2 + length
        }
        return null
    }
}
