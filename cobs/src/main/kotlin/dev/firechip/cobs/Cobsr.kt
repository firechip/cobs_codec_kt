// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

/**
 * Consistent Overhead Byte Stuffing, Reduced (COBS/R).
 *
 * COBS/R (a variant devised by Craig McQueen) is identical to basic [Cobs]
 * except that, when the final data byte's value is greater than or equal to the
 * final length code, that data byte is used as the length code and dropped from
 * the tail, saving one byte. This often avoids the `+1` byte that basic COBS
 * always adds, which is valuable for small messages.
 */
public object Cobsr {

    /**
     * Encodes [input] with COBS/R, returning a zero-free [ByteArray] that is
     * never larger than the basic-COBS encoding. The empty input encodes to
     * `[0x01]`.
     */
    @JvmStatic
    public fun encode(input: ByteArray): ByteArray {
        val srcLen = input.size

        val dst = ByteArray(maxEncodedLength(srcLen))
        var codeIndex = 0
        var writeIndex = 1
        var code = 1
        var lastByte = 0

        if (srcLen != 0) {
            var readIndex = 0
            while (true) {
                val b = input[readIndex++].toInt() and 0xFF
                lastByte = b
                if (b == 0) {
                    dst[codeIndex] = code.toByte()
                    codeIndex = writeIndex++
                    code = 1
                    if (readIndex >= srcLen) break
                } else {
                    dst[writeIndex++] = b.toByte()
                    code++
                    if (readIndex >= srcLen) break
                    if (code == 0xFF) {
                        dst[codeIndex] = code.toByte()
                        codeIndex = writeIndex++
                        code = 1
                    }
                }
            }
        }

        // Reduction: if the final data byte >= the length code basic COBS would
        // write, use that byte as the length code and drop it from the tail.
        if (lastByte < code) {
            dst[codeIndex] = code.toByte()
        } else {
            dst[codeIndex] = lastByte.toByte()
            writeIndex--
        }

        return dst.copyOf(writeIndex)
    }

    /**
     * Decodes COBS/R-encoded [input], returning the original bytes. The empty
     * input decodes to an empty array.
     *
     * @throws CobsDecodeException if [input] contains a `0x00` byte.
     */
    @JvmStatic
    public fun decode(input: ByteArray): ByteArray {
        val srcLen = input.size
        if (srcLen == 0) return ByteArray(0)

        val out = ByteArray(srcLen)
        var writeIndex = 0
        var index = 0

        while (true) {
            val code = input[index].toInt() and 0xFF
            if (code == 0) {
                throw CobsDecodeException("zero byte in COBS/R input", index)
            }
            index++
            val blockEnd = index + code - 1
            val copyEnd = if (blockEnd < srcLen) blockEnd else srcLen
            while (index < copyEnd) {
                val b = input[index].toInt() and 0xFF
                if (b == 0) {
                    throw CobsDecodeException("zero byte in COBS/R input", index)
                }
                out[writeIndex++] = b.toByte()
                index++
            }
            if (blockEnd > srcLen) {
                // Reduced encoding: the length code was the final data byte.
                out[writeIndex++] = code.toByte()
                break
            } else if (blockEnd < srcLen) {
                if (code < 0xFF) out[writeIndex++] = 0
            } else {
                break
            }
        }

        return out.copyOf(writeIndex)
    }
}
