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
     * Encodes [input] with COBS/R using an arbitrary [sentinel] byte instead of
     * `0x00`, returning a [ByteArray] that never contains the [sentinel] byte.
     *
     * This runs the ordinary encode and then XORs every output byte with
     * [sentinel]. A [sentinel] of `0` is byte-for-byte identical to [encode].
     */
    @JvmStatic
    public fun encodeWithSentinel(input: ByteArray, sentinel: Byte): ByteArray {
        val out = encode(input)
        val s = sentinel.toInt() and 0xFF
        if (s != 0) {
            for (i in out.indices) {
                out[i] = (out[i].toInt() xor s).toByte()
            }
        }
        return out
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

    /**
     * Decodes COBS/R [input] that was encoded with an arbitrary [sentinel] byte
     * (see [encodeWithSentinel]), returning the original bytes.
     *
     * A fresh copy of [input] is XORed back with [sentinel] before decoding, so
     * the caller's array is never mutated. A [sentinel] of `0` is identical to
     * [decode].
     *
     * @throws CobsDecodeException if [input] contains the [sentinel] byte.
     */
    @JvmStatic
    public fun decodeWithSentinel(input: ByteArray, sentinel: Byte): ByteArray {
        val s = sentinel.toInt() and 0xFF
        if (s == 0) return decode(input)
        val src = input.copyOf()
        for (i in src.indices) {
            src[i] = (src[i].toInt() xor s).toByte()
        }
        return decode(src)
    }

    /**
     * Decodes COBS/R data in place, overwriting [buffer] with the decoded output
     * and returning its length; the decoded bytes occupy the first `len` elements
     * of [buffer].
     *
     * This needs no output buffer: a COBS/R encoding is never shorter than its
     * decoding, so the write position always trails the read position. A length
     * code that points past the end of the input is not an error here but the
     * reduced final block, whose data byte is the code value itself; appending it
     * lands on a byte that has already been read, so decoding never clobbers
     * unread input.
     *
     * @throws CobsDecodeException if [buffer] contains a `0x00` byte.
     */
    @JvmStatic
    public fun decodeInPlace(buffer: ByteArray): Int = decodeInPlace(buffer, COBS_DELIMITER)

    /**
     * Decodes COBS/R data that was encoded with an arbitrary [sentinel] byte in
     * place, overwriting [buffer] with the decoded output and returning its
     * length. A [sentinel] of `0` is identical to [decodeInPlace].
     *
     * When [sentinel] is non-zero the buffer is first XORed back to its
     * `0x00`-based form in place, then decoded in place.
     *
     * @throws CobsDecodeException if [buffer] is not valid encoded data.
     */
    @JvmStatic
    public fun decodeInPlace(buffer: ByteArray, sentinel: Byte): Int {
        val srcLen = buffer.size
        if (srcLen == 0) return 0

        val s = sentinel.toInt() and 0xFF
        if (s != 0) {
            for (i in 0 until srcLen) {
                buffer[i] = (buffer[i].toInt() xor s).toByte()
            }
        }

        var writeIndex = 0
        var index = 0

        while (true) {
            val code = buffer[index].toInt() and 0xFF
            if (code == 0) {
                throw CobsDecodeException("zero byte in COBS/R input", index)
            }
            index++
            val blockEnd = index + code - 1
            val copyEnd = if (blockEnd < srcLen) blockEnd else srcLen
            while (index < copyEnd) {
                val b = buffer[index].toInt() and 0xFF
                if (b == 0) {
                    throw CobsDecodeException("zero byte in COBS/R input", index)
                }
                // writeIndex < index throughout, so this never clobbers unread input.
                buffer[writeIndex++] = b.toByte()
                index++
            }
            if (blockEnd > srcLen) {
                // Reduced encoding: the length code was the final data byte. The
                // append overwrites a byte already consumed, so it is safe here.
                buffer[writeIndex++] = code.toByte()
                break
            } else if (blockEnd < srcLen) {
                if (code < 0xFF) buffer[writeIndex++] = 0
            } else {
                break
            }
        }

        return writeIndex
    }
}
