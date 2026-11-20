package com.launcher.androidmediabar.data

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import com.launcher.androidmediabar.R

/**
 * Imports a PlayStation 3 theme (.p3t): a single CXML file holding the theme info, the
 * wallpapers (`bgimagetable`), the icons (`icontable`, GIM pictures keyed by fixed system IDs
 * such as `icon_game`) and more.
 */
object Ps3ThemeImporter {

    class Theme(
        val name: String,
        val author: String?,
        val version: String?,
        val background: ByteArray,
        /**
         * System icon ID (e.g. `icon_music`) -> GIM data. Kept encoded so that only one icon at a
         * time is ever decoded in memory (see [GimDecoder]).
         */
        val icons: Map<String, ByteArray>,
    )

    sealed interface Result {
        class Success(val theme: Theme) : Result
        data class Failure(@param:StringRes val messageRes: Int) : Result
    }

    fun import(resolver: ContentResolver, uri: Uri): Result {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: ""
        val bytes = read(resolver, uri, MAX_P3T_SIZE + 1)
        if (bytes.size > MAX_P3T_SIZE) return Result.Failure(R.string.ps3_err_bad_p3t)
        return parseP3t(bytes, name.substringBeforeLast('.').ifBlank { "P3T" })
    }

    internal fun parseP3t(bytes: ByteArray, fileName: String): Result {
        if (!P3tReader.isP3t(bytes)) return Result.Failure(R.string.ps3_err_bad_p3t)
        val reader = runCatching { P3tReader(bytes) }.getOrNull()

        val info = reader?.root?.child("infotable")?.child("info")
        val background = reader?.let(::declaredBackground) ?: scannedBackground(bytes)
            ?: return Result.Failure(R.string.ps3_err_no_images)

        // Bounded so that a crafted file cannot exhaust memory: real themes have ~60 small icons.
        var totalBytes = 0L
        val icons = buildMap {
            for (icon in reader?.root?.child("icontable")?.children.orEmpty()) {
                if (size >= MAX_ICONS) break
                val id = (icon.attrs["id"] as? P3tReader.Attr.Id)?.value?.takeIf(ICON_ID::matches) ?: continue
                val data = reader?.file(icon.attrs["src"])?.takeIf(GimDecoder::isGim) ?: continue
                totalBytes += data.size
                if (totalBytes > MAX_ICON_BYTES) break
                put(id, data)
            }
        }

        return Result.Success(
            Theme(
                name = info?.string("name") ?: fileName,
                author = info?.string("author"),
                version = info?.string("version")?.removePrefix("version "),
                background = background,
                icons = icons,
            ),
        )
    }

    /** The HD wallpaper declared in `bgimagetable` (SD as fallback). */
    private fun declaredBackground(reader: P3tReader): ByteArray? {
        val images = reader.root.child("bgimagetable")?.children.orEmpty()
        return images.firstNotNullOfOrNull { reader.file(it.attrs["hd"]) }
            ?: images.firstNotNullOfOrNull { reader.file(it.attrs["sd"]) ?: reader.file(it.attrs["src"]) }
    }

    /** Fallback for unusual files: the largest JPEG/PNG stored anywhere in the theme. */
    private fun scannedBackground(bytes: ByteArray): ByteArray? {
        val tableOffset = beInt(bytes, FILE_TABLE_OFFSET)
        val tableSize = beInt(bytes, FILE_TABLE_OFFSET + 4)
        val (from, to) = if (tableOffset >= CXML_HEADER_SIZE && tableSize > 0 &&
            tableOffset.toLong() + tableSize <= bytes.size
        ) {
            tableOffset to tableOffset + tableSize
        } else {
            CXML_HEADER_SIZE to bytes.size
        }
        val best = findImages(bytes, from, to)
            .mapNotNull { range ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, range.first, range.last - range.first + 1, opts)
                if (opts.outWidth >= MIN_BG_WIDTH && opts.outHeight > 0) range to opts.outWidth.toLong() * opts.outHeight else null
            }
            .maxByOrNull { it.second }
            ?.first
            ?: return null
        return bytes.copyOfRange(best.first, best.last + 1)
    }

    /** Byte ranges of the PNG/JPEG streams stored in [from]..[to]. GIM/RAF assets are skipped. */
    internal fun findImages(b: ByteArray, from: Int, to: Int): List<IntRange> = buildList {
        var i = from
        while (i < to - 8) {
            val end = when {
                isPng(b, i) -> pngEnd(b, i, to)
                b[i] == 0xFF.toByte() && b[i + 1] == 0xD8.toByte() && b[i + 2] == 0xFF.toByte() -> jpegEnd(b, i, to)
                else -> null
            }
            if (end != null) {
                add(i until end)
                i = end // Skips thumbnails embedded inside this image.
            } else {
                i++
            }
        }
    }

    private fun isPng(b: ByteArray, i: Int) = PNG_SIGNATURE.indices.all { b[i + it] == PNG_SIGNATURE[it] }

    private fun pngEnd(b: ByteArray, start: Int, limit: Int): Int? {
        var p = start + PNG_SIGNATURE.size
        while (p + 12 <= limit) {
            val length = beInt(b, p)
            if (length < 0) return null
            val type = String(b, p + 4, 4, Charsets.US_ASCII)
            val next = p.toLong() + 12 + length
            if (next > limit) return null
            p = next.toInt()
            if (type == "IEND") return p
        }
        return null
    }

    /** Walks the JPEG markers (including progressive scans) up to EOI. */
    private fun jpegEnd(b: ByteArray, start: Int, limit: Int): Int? {
        var p = start + 2
        while (p + 1 < limit) {
            if (b[p] != 0xFF.toByte()) return null
            val marker = b[p + 1].toInt() and 0xFF
            when {
                marker == 0xFF -> p++ // Fill byte.
                marker == 0xD9 -> return p + 2
                marker == 0x01 || marker in 0xD0..0xD7 -> p += 2
                else -> {
                    if (p + 3 >= limit) return null
                    val length = ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)
                    if (length < 2) return null
                    p += 2 + length
                    if (marker == 0xDA) {
                        // Entropy-coded data: FF is always followed by 00 (stuffing) or RSTn there.
                        while (p + 1 < limit) {
                            if (b[p] == 0xFF.toByte()) {
                                val n = b[p + 1].toInt() and 0xFF
                                if (n != 0x00 && n !in 0xD0..0xD7) break
                            }
                            p++
                        }
                    }
                }
            }
        }
        return null
    }

    private fun beInt(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    /** Reads at most [max] bytes. */
    private fun read(resolver: ContentResolver, uri: Uri, max: Int): ByteArray = resolver.openInputStream(uri)?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (out.size() < max) {
            val n = input.read(buffer, 0, minOf(buffer.size, max - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    } ?: ByteArray(0)

    private const val CXML_HEADER_SIZE = 0x38
    private const val FILE_TABLE_OFFSET = 0x30
    private const val MAX_P3T_SIZE = 64 * 1024 * 1024
    private const val MAX_ICONS = 128
    private const val MAX_ICON_BYTES = 32L * 1024 * 1024

    /** IDs become file names: only the plain system naming is accepted. */
    val ICON_ID = Regex("[a-z0-9_]{1,64}")
    private const val MIN_BG_WIDTH = 640
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
}
