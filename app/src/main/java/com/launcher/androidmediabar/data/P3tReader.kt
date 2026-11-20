package com.launcher.androidmediabar.data

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Reader for the PS3 theme container: a big-endian CXML document ("P3TF").
 *
 * Header: magic, version, then (offset, size) pairs for the tree, ID, string, int-array,
 * float-array and file tables. Each element is 7 int32 (name, attribute count, parent, prev,
 * next, first child, last child) followed by 16-byte attributes (name, type, 2 × int32).
 * Embedded files are often zlib-compressed.
 */
internal class P3tReader(private val b: ByteArray) {

    sealed interface Attr {
        data class Int(val value: kotlin.Int) : Attr
        data class Float(val value: kotlin.Float) : Attr
        data class Str(val value: String) : Attr
        data class Id(val value: String) : Attr
        class File(val offset: kotlin.Int, val size: kotlin.Int) : Attr
    }

    class Element(val name: String, val attrs: Map<String, Attr>, val children: List<Element>) {
        fun child(name: String): Element? = children.firstOrNull { it.name == name }
        fun string(name: String): String? = (attrs[name] as? Attr.Str)?.value?.takeIf { it.isNotBlank() }
    }

    private val treeOffset = header(0)
    private val treeSize = header(1)
    private val idOffset = header(2)
    private val idSize = header(3)
    private val stringOffset = header(4)
    private val stringSize = header(5)
    private val fileOffset = header(10)
    private val fileSize = header(11)
    private var budget = MAX_ELEMENTS

    init {
        require(isP3t(b)) { "Not a P3T file" }
        for ((offset, size) in listOf(treeOffset to treeSize, idOffset to idSize, stringOffset to stringSize, fileOffset to fileSize)) {
            require(offset >= 0 && size >= 0 && offset.toLong() + size <= b.size) { "Corrupted P3T table" }
        }
    }

    val root: Element = element(0, depth = 0)

    /** Bytes of an embedded file, inflated when stored compressed. */
    fun file(attr: Attr?): ByteArray? {
        if (attr !is Attr.File || attr.size <= 0 || attr.offset < 0) return null
        if (attr.offset.toLong() + attr.size > fileSize) return null
        val start = fileOffset + attr.offset
        val raw = b.copyOfRange(start, start + attr.size)
        return if (isZlib(raw)) inflate(raw) ?: raw else raw
    }

    private fun element(offset: Int, depth: Int): Element {
        require(depth < MAX_DEPTH && budget-- > 0 && offset >= 0 && offset + ELEMENT_SIZE <= treeSize) { "Corrupted P3T tree" }
        val p = treeOffset + offset
        val attrCount = int(p + 4)
        require(attrCount in 0..MAX_ATTRS && offset + ELEMENT_SIZE + attrCount * ATTR_SIZE <= treeSize) { "Corrupted P3T element" }

        val attrs = LinkedHashMap<String, Attr>()
        for (i in 0 until attrCount) {
            val a = p + ELEMENT_SIZE + i * ATTR_SIZE
            val name = cString(stringOffset, stringSize, int(a)) ?: continue
            val v1 = int(a + 8)
            val v2 = int(a + 12)
            when (int(a + 4)) {
                TYPE_INT -> Attr.Int(v1)
                TYPE_FLOAT -> Attr.Float(java.lang.Float.intBitsToFloat(v1))
                TYPE_STRING -> string(v1, v2)?.let(Attr::Str)
                TYPE_FILE -> Attr.File(v1, v2)
                // ID table entries are an int32 (owner element) followed by a C string.
                TYPE_ID, TYPE_ID_REF -> cString(idOffset, idSize, v1 + 4)?.let(Attr::Id)
                else -> null
            }?.let { attrs[name] = it }
        }

        val children = buildList {
            var child = int(p + 20)
            while (child != -1) {
                add(element(child, depth + 1))
                child = int(treeOffset + child + 16)
            }
        }
        return Element(cString(stringOffset, stringSize, int(p)).orEmpty(), attrs, children)
    }

    private fun string(offset: Int, length: Int): String? {
        if (offset < 0 || length < 0 || offset.toLong() + length > stringSize) return null
        return String(b, stringOffset + offset, length, Charsets.UTF_8).trimEnd('\u0000')
    }

    private fun cString(tableOffset: Int, tableSize: Int, offset: Int): String? {
        if (offset < 0 || offset >= tableSize) return null
        val start = tableOffset + offset
        var end = start
        val limit = tableOffset + tableSize
        while (end < limit && b[end] != 0.toByte()) end++
        return String(b, start, end - start, Charsets.UTF_8)
    }

    private fun header(index: Int) = int(8 + index * 4)

    private fun int(at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    companion object {
        private const val HEADER_SIZE = 0x40
        private const val ELEMENT_SIZE = 28
        private const val ATTR_SIZE = 16
        private const val MAX_ELEMENTS = 20_000
        private const val MAX_DEPTH = 32
        private const val MAX_ATTRS = 256
        private const val MAX_INFLATED = 16 * 1024 * 1024

        private const val TYPE_INT = 1
        private const val TYPE_FLOAT = 2
        private const val TYPE_STRING = 3
        private const val TYPE_FILE = 6
        private const val TYPE_ID = 7
        private const val TYPE_ID_REF = 8

        fun isP3t(b: ByteArray) = b.size >= HEADER_SIZE && String(b, 0, 4, Charsets.US_ASCII) == "P3TF"

        private fun isZlib(d: ByteArray) =
            d.size > 2 && (d[0].toInt() and 0x0F) == 8 && (((d[0].toInt() and 0xFF) shl 8) or (d[1].toInt() and 0xFF)) % 31 == 0

        private fun inflate(data: ByteArray): ByteArray? {
            val inflater = Inflater()
            return try {
                inflater.setInput(data)
                val out = ByteArrayOutputStream(data.size * 2)
                val buffer = ByteArray(64 * 1024)
                while (!inflater.finished()) {
                    val n = inflater.inflate(buffer)
                    if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_INFLATED) return null
                }
                out.toByteArray()
            } catch (_: DataFormatException) {
                null
            } finally {
                inflater.end()
            }
        }
    }
}
