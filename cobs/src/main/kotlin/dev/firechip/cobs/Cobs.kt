// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

/**
 * Basic Consistent Overhead Byte Stuffing (COBS).
 *
 * COBS encodes an arbitrary [ByteArray] into one that contains no zero
 * (`0x00`) bytes, at a small and predictable cost (at most one extra byte per
 * 254 bytes, plus one). That lets a single `0x00` reliably delimit packets on a
 * byte stream. See Cheshire & Baker, "Consistent Overhead Byte Stuffing",
 * IEEE/ACM Transactions on Networking, Vol. 7, No. 2, April 1999.
 */
public object Cobs {

    /**
     * Encodes [input] with basic COBS, returning a zero-free [ByteArray].
     *
     * Encoding never fails: any sequence of bytes is encodable. The empty input
     * encodes to `[0x01]`.
     */
    @JvmStatic
    public fun encode(input: ByteArray): ByteArray {
        val srcLen = input.size
        if (srcLen == 0) return byteArrayOf(0x01)

        // Worst-case output size; the actual output is a prefix of this buffer.
        val dst = ByteArray(maxEncodedLength(srcLen))
        var codeIndex = 0
        var writeIndex = 1
        var code = 1
        var readIndex = 0

        while (true) {
            val b = input[readIndex++].toInt() and 0xFF
            if (b == 0) {
                dst[codeIndex] = code.toByte()
                codeIndex = writeIndex++
                code = 1
                if (readIndex >= srcLen) break
            } else {
                dst[writeIndex++] = b.toByte()
                code++
                // Terminate before the 0xFF split so a chunk of exactly 254
                // non-zero bytes does not emit a spurious trailing block.
                if (readIndex >= srcLen) break
                if (code == 0xFF) {
                    dst[codeIndex] = code.toByte()
                    codeIndex = writeIndex++
                    code = 1
                }
            }
        }
        dst[codeIndex] = code.toByte()

        return dst.copyOf(writeIndex)
    }

    /**
     * Encodes [input] with basic COBS using an arbitrary [sentinel] byte instead
     * of `0x00`, returning a [ByteArray] that never contains the [sentinel] byte.
     *
     * This runs the ordinary encode and then XORs every output byte with
     * [sentinel], so `sentinel` (rather than `0x00`) may be used to delimit
     * packets. Choosing a sentinel that rarely occurs in the payload minimises
     * overhead. A [sentinel] of `0` is byte-for-byte identical to [encode].
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
     * Decodes basic-COBS-encoded [input], returning the original bytes.
     *
     * The empty input decodes to an empty array. Input should be a single
     * encoded packet with no surrounding `0x00` delimiter bytes.
     *
     * @throws CobsDecodeException if [input] contains a `0x00` byte or a length
     *   code points past the end of the input.
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
                throw CobsDecodeException("zero byte in COBS input", index)
            }
            index++
            val blockEnd = index + code - 1
            val copyEnd = if (blockEnd < srcLen) blockEnd else srcLen
            while (index < copyEnd) {
                val b = input[index].toInt() and 0xFF
                if (b == 0) {
                    throw CobsDecodeException("zero byte in COBS input", index)
                }
                out[writeIndex++] = b.toByte()
                index++
            }
            if (blockEnd > srcLen) {
                throw CobsDecodeException(
                    "length code points past end of input",
                    blockEnd - code,
                )
            }
            if (blockEnd < srcLen) {
                if (code < 0xFF) out[writeIndex++] = 0
            } else {
                break
            }
        }

        return out.copyOf(writeIndex)
    }

    /**
     * Decodes basic-COBS [input] that was encoded with an arbitrary [sentinel]
     * byte (see [encodeWithSentinel]), returning the original bytes.
     *
     * A fresh copy of [input] is XORed back with [sentinel] before decoding, so
     * the caller's array is never mutated. A [sentinel] of `0` is identical to
     * [decode].
     *
     * @throws CobsDecodeException if [input] contains the [sentinel] byte or a
     *   length code points past the end of the input.
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
     * Decodes basic-COBS data in place, overwriting [buffer] with the decoded
     * output and returning its length; the decoded bytes occupy the first `len`
     * elements of [buffer].
     *
     * This needs no output buffer: COBS decoding never expands, so the write
     * position always trails the read position.
     *
     * @throws CobsDecodeException if [buffer] contains a `0x00` byte or a length
     *   code points past the end of the input.
     */
    @JvmStatic
    public fun decodeInPlace(buffer: ByteArray): Int = decodeInPlace(buffer, COBS_DELIMITER)

    /**
     * Decodes basic-COBS data that was encoded with an arbitrary [sentinel] byte
     * in place, overwriting [buffer] with the decoded output and returning its
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
                throw CobsDecodeException("zero byte in COBS input", index)
            }
            index++
            val blockEnd = index + code - 1
            val copyEnd = if (blockEnd < srcLen) blockEnd else srcLen
            while (index < copyEnd) {
                val b = buffer[index].toInt() and 0xFF
                if (b == 0) {
                    throw CobsDecodeException("zero byte in COBS input", index)
                }
                // writeIndex < index throughout, so this never clobbers unread input.
                buffer[writeIndex++] = b.toByte()
                index++
            }
            if (blockEnd > srcLen) {
                throw CobsDecodeException(
                    "length code points past end of input",
                    blockEnd - code,
                )
            }
            if (blockEnd < srcLen) {
                if (code < 0xFF) buffer[writeIndex++] = 0
            } else {
                break
            }
        }

        return writeIndex
    }

    /** See the top-level [encodingOverhead]. */
    @JvmStatic
    public fun encodingOverhead(sourceLength: Int): Int =
        dev.firechip.cobs.encodingOverhead(sourceLength)

    /** See the top-level [maxEncodedLength]. */
    @JvmStatic
    public fun maxEncodedLength(sourceLength: Int): Int =
        dev.firechip.cobs.maxEncodedLength(sourceLength)
}
