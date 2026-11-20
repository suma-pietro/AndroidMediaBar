package com.launcher.androidmediabar.data

import com.launcher.androidmediabar.ui.xmb.Ps3IconMap
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class P3tReaderTest {

    /** Real themes dropped in app/sampledata (git-ignored): those tests are skipped when absent. */
    private val themes = File("sampledata").listFiles { f -> f.extension.equals("p3t", true) }.orEmpty()

    @Test
    fun decodesGimRgba8888WithRowPadding() {
        // 3x2 RGBA pixels: 12 bytes per row, padded to the 16-byte pitch.
        val rows = listOf(
            byteArrayOf(1, 2, 3, -1, 4, 5, 6, -128, 7, 8, 9, 0),
            byteArrayOf(10, 11, 12, -1, 13, 14, 15, -1, 16, 17, 18, -1),
        )
        val decoded = GimDecoder.decode(gim(width = 3, height = 2, rows = rows))
        assertNotNull(decoded)
        val image = decoded!!
        assertEquals(3, image.width)
        assertEquals(2, image.height)
        assertArrayEquals(
            intArrayOf(
                0xFF010203.toInt(), 0x80040506.toInt(), 0x00070809,
                0xFF0A0B0C.toInt(), 0xFF0D0E0F.toInt(), 0xFF101112.toInt(),
            ),
            image.pixels,
        )
    }

    @Test
    fun rejectsNonGimData() {
        assertFalse(GimDecoder.isGim(ByteArray(64)))
        assertEquals(null, GimDecoder.decode(ByteArray(64)))
    }

    @Test
    fun readsSampleThemes() {
        assumeTrue("No .p3t in app/sampledata", themes.isNotEmpty())
        for (theme in themes) {
            val bytes = theme.readBytes()
            val reader = P3tReader(bytes)
            val icons = reader.root.child("icontable")?.children.orEmpty()

            // Every icon of the table must decode to a non-empty picture.
            for (icon in icons) {
                val id = (icon.attrs["id"] as? P3tReader.Attr.Id)?.value
                val data = reader.file(icon.attrs["src"])
                val image = data?.let(GimDecoder::decode)
                assertTrue("${theme.name}: icon $id does not decode", image != null && image.width > 0 && image.height > 0)
            }

            val result = Ps3ThemeImporter.parseP3t(bytes, theme.nameWithoutExtension)
            assertTrue("${theme.name}: import failed", result is Ps3ThemeImporter.Result.Success)
            val parsed = (result as Ps3ThemeImporter.Result.Success).theme
            assertTrue(parsed.name.isNotBlank())
            assertTrue(parsed.background.isNotEmpty())
            assertEquals(icons.size, parsed.icons.size)
            assertTrue(parsed.icons.values.all { GimDecoder.decode(it) != null })
            println("${theme.name}: \"${parsed.name}\" by ${parsed.author}, ${parsed.icons.size} icons, " +
                "missing mapped icons: ${Ps3IconMap.allIds - parsed.icons.keys}")
        }
    }

    /** Minimal big-endian PS3 GIM: root > picture > image block, RGBA8888, 16-byte pitch. */
    private fun gim(width: Int, height: Int, rows: List<ByteArray>): ByteArray {
        val pitch = (width * 4 + 15) / 16 * 16
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v shr 8); out.write(v) }
        fun u32(v: Int) { u16(v ushr 16); u16(v and 0xFFFF) }
        fun block(id: Int, size: Int, next: Int) { u16(id); u16(0); u32(size); u32(next); u32(0x10) }

        out.write(".GIM1.00".toByteArray()); out.write(0); out.write("PSP".toByteArray()); u32(0)
        val imageSize = 0x10 + 0x40 + pitch * height
        block(0x02, imageSize + 0x20, 0x10)
        block(0x03, imageSize + 0x10, 0x10)
        block(0x04, imageSize, imageSize)
        // Image header: size, ref, format 3 (RGBA8888), order 0, width, height, bpp, pitch align...
        u16(0x30); u16(0); u16(3); u16(0); u16(width); u16(height); u16(32); u16(16)
        repeat(6) { u16(0) }
        u32(0x40) // pixels start, relative to this header
        repeat(8) { u32(0) }
        for (row in rows) {
            out.write(row)
            repeat(pitch - row.size) { out.write(0xEE) }
        }
        return out.toByteArray()
    }
}
