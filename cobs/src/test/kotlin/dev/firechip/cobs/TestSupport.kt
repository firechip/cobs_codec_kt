// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

/** Builds a [ByteArray] from int byte values, e.g. `bytes(0x01, 0xFF)`. */
internal fun bytes(vararg v: Int): ByteArray = ByteArray(v.size) { v[it].toByte() }

/** ASCII code units of [s]. */
internal fun ascii(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)

/** `[start, end)` as a byte array. */
internal fun range(start: Int, end: Int): ByteArray =
    ByteArray(end - start) { (start + it).toByte() }

/** Concatenates byte arrays. */
internal fun cat(vararg parts: ByteArray): ByteArray {
    val total = parts.sumOf { it.size }
    val out = ByteArray(total)
    var pos = 0
    for (p in parts) {
        p.copyInto(out, pos)
        pos += p.size
    }
    return out
}

/**
 * Deterministic stream of non-zero bytes, matching the reference test suites'
 * `infinite_non_zero_generator`, truncated to [length] bytes.
 */
internal fun nonZeroBytes(length: Int): ByteArray {
    val out = ByteArray(length)
    var count = 0
    outer@ while (true) {
        for (i in 1 until 50) {
            var j = 1
            while (j < 256) {
                if (count == length) break@outer
                out[count++] = j.toByte()
                j += i
            }
        }
    }
    return out
}

/**
 * The naive block encoding of a zero-free run: split into 254-byte blocks, each
 * prefixed with `length + 1`.
 */
internal fun simpleEncodeNonZeros(input: ByteArray): ByteArray {
    val out = ArrayList<Byte>()
    var i = 0
    while (i < input.size) {
        val end = minOf(i + 254, input.size)
        out.add((end - i + 1).toByte())
        for (k in i until end) out.add(input[k])
        i += 254
    }
    return out.toByteArray()
}
