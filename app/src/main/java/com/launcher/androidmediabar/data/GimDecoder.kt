package com.launcher.androidmediabar.data

/**
 * Decoder for GIM, the Sony picture format used by PS3/PSP theme icons.
 *
 * Layout: 16-byte signature (".GIM1.00\0PSP" big-endian on PS3, "MIG.00.1PSP" little-endian on
 * PSP), then nested blocks `{id u16, pad u16, size u32, next u32, data u32}`: 0x02 root,
 * 0x03 picture, 0x04 image, 0x05 palette. Image/palette data starts with a header holding format,
 * pixel order (0 linear, 1 PSP-swizzled), width, height, bpp and pitch alignment; pixels start at
 * header + u32@0x1C.
 */
object GimDecoder {

    class Image(val width: Int, val height: Int, /** ARGB, row-major. */ val pixels: IntArray)

    private class Plane(val format: Int, val order: Int, val width: Int, val height: Int, val pitch: Int, val start: Int)

    fun isGim(b: ByteArray): Boolean = b.size > 16 && (
        String(b, 0, 8, Charsets.US_ASCII) == ".GIM1.00" || String(b, 0, 8, Charsets.US_ASCII) == "MIG.00.1"
        )

    fun decode(b: ByteArray): Image? = runCatching { decodeOrThrow(b) }.getOrNull()

    private fun decodeOrThrow(b: ByteArray): Image? {
        if (!isGim(b)) return null
        val r = Reader(b, bigEndian = b[0] == '.'.code.toByte())

        var image: Plane? = null
        var palette: Plane? = null
        var pos = 0x10
        var guard = 0
        while (pos + 16 <= b.size && guard++ < 64) {
            val id = r.u16(pos)
            val next = r.u32(pos + 8)
            val data = r.u32(pos + 12)
            when (id) {
                BLOCK_IMAGE -> image = plane(r, pos + data)
                BLOCK_PALETTE -> palette = plane(r, pos + data)
            }
            if (next <= 0) break
            pos += next
        }
        val img = image ?: return null
        if (img.width !in 1..MAX_SIDE || img.height !in 1..MAX_SIDE) return null

        val raw = if (img.order == ORDER_SWIZZLED) unswizzle(b, img) else b.copyOfRange(img.start, img.start + img.pitch * img.height)
        val bpp = bitsPerPixel(img.format) ?: return null
        val colors = when (img.format) {
            FORMAT_INDEX4, FORMAT_INDEX8 -> paletteColors(b, r, palette ?: return null) ?: return null
            else -> null
        }

        val out = IntArray(img.width * img.height)
        for (y in 0 until img.height) {
            val row = y * img.pitch
            for (x in 0 until img.width) {
                out[y * img.width + x] = when (img.format) {
                    FORMAT_INDEX4 -> {
                        val v = raw[row + x / 2].toInt() and 0xFF
                        colors!!.getOrElse(if (x % 2 == 0) v and 0x0F else v shr 4) { 0 }
                    }
                    FORMAT_INDEX8 -> colors!!.getOrElse(raw[row + x].toInt() and 0xFF) { 0 }
                    else -> {
                        val at = row + x * bpp / 8
                        color(img.format, if (bpp == 32) r.pixel32(raw, at) else r.pixel16(raw, at))
                    }
                }
            }
        }
        return Image(img.width, img.height, out)
    }

    private fun plane(r: Reader, h: Int): Plane {
        val format = r.u16(h + 4)
        val width = r.u16(h + 8)
        val height = r.u16(h + 10)
        val bpp = bitsPerPixel(format) ?: r.u16(h + 12)
        val pitchAlign = r.u16(h + 14).coerceAtLeast(1)
        val pitch = align((width * bpp + 7) / 8, pitchAlign)
        return Plane(format, r.u16(h + 6), width, height, pitch, h + r.u32(h + 0x1C))
    }

    private fun paletteColors(b: ByteArray, r: Reader, p: Plane): IntArray? {
        val bpp = bitsPerPixel(p.format)?.takeIf { p.format <= FORMAT_8888 } ?: return null
        val count = p.width * p.height
        if (p.start + count * bpp / 8 > b.size) return null
        return IntArray(count) { i ->
            val at = p.start + i * bpp / 8
            color(p.format, if (bpp == 32) r.pixel32(b, at) else r.pixel16(b, at))
        }
    }

    /** PSP swizzle: the image is stored as 16-byte × 8-row tiles. */
    private fun unswizzle(b: ByteArray, p: Plane): ByteArray {
        val height = align(p.height, 8)
        val out = ByteArray(p.pitch * height)
        val tilesPerRow = p.pitch / 16
        var src = p.start
        for (tile in 0 until tilesPerRow * (height / 8)) {
            val tx = tile % tilesPerRow
            val ty = tile / tilesPerRow
            for (line in 0 until 8) {
                System.arraycopy(b, src, out, (ty * 8 + line) * p.pitch + tx * 16, 16)
                src += 16
            }
        }
        return out
    }

    /** [v] holds the pixel with its first byte in the lowest bits (R first, A last). */
    private fun color(format: Int, v: Int): Int = when (format) {
        FORMAT_8888 -> {
            val r = v and 0xFF
            val g = (v shr 8) and 0xFF
            val bl = (v shr 16) and 0xFF
            val a = (v ushr 24) and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or bl
        }
        FORMAT_5650 -> argb(0xFF, expand(v and 0x1F, 5), expand((v shr 5) and 0x3F, 6), expand((v shr 11) and 0x1F, 5))
        FORMAT_5551 -> argb(
            if ((v shr 15) and 1 == 1) 0xFF else 0,
            expand(v and 0x1F, 5), expand((v shr 5) and 0x1F, 5), expand((v shr 10) and 0x1F, 5),
        )
        FORMAT_4444 -> argb(
            expand((v shr 12) and 0xF, 4),
            expand(v and 0xF, 4), expand((v shr 4) and 0xF, 4), expand((v shr 8) and 0xF, 4),
        )
        else -> 0
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun expand(v: Int, bits: Int) = (v shl (8 - bits)) or (v shr (2 * bits - 8).coerceAtLeast(0))
    private fun align(v: Int, a: Int) = (v + a - 1) / a * a

    private fun bitsPerPixel(format: Int): Int? = when (format) {
        FORMAT_5650, FORMAT_5551, FORMAT_4444 -> 16
        FORMAT_8888 -> 32
        FORMAT_INDEX4 -> 4
        FORMAT_INDEX8 -> 8
        else -> null
    }

    private class Reader(private val b: ByteArray, private val bigEndian: Boolean) {
        fun u16(at: Int): Int {
            val x = b[at].toInt() and 0xFF
            val y = b[at + 1].toInt() and 0xFF
            return if (bigEndian) (x shl 8) or y else (y shl 8) or x
        }

        fun u32(at: Int): Int = if (bigEndian) (u16(at) shl 16) or u16(at + 2) else (u16(at + 2) shl 16) or u16(at)

        /** Pixels are byte-ordered R,G,B,A in memory on both platforms. */
        fun pixel32(d: ByteArray, at: Int): Int =
            (d[at].toInt() and 0xFF) or ((d[at + 1].toInt() and 0xFF) shl 8) or
                ((d[at + 2].toInt() and 0xFF) shl 16) or ((d[at + 3].toInt() and 0xFF) shl 24)

        /** 16-bit pixels follow the file endianness. */
        fun pixel16(d: ByteArray, at: Int): Int {
            val x = d[at].toInt() and 0xFF
            val y = d[at + 1].toInt() and 0xFF
            return if (bigEndian) (x shl 8) or y else (y shl 8) or x
        }
    }

    private const val BLOCK_IMAGE = 0x04
    private const val BLOCK_PALETTE = 0x05
    private const val ORDER_SWIZZLED = 1
    private const val FORMAT_5650 = 0
    private const val FORMAT_5551 = 1
    private const val FORMAT_4444 = 2
    private const val FORMAT_8888 = 3
    private const val FORMAT_INDEX4 = 4
    private const val FORMAT_INDEX8 = 5
    private const val MAX_SIDE = 4096
}
