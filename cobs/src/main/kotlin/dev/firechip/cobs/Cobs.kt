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

    /** See the top-level [encodingOverhead]. */
    @JvmStatic
    public fun encodingOverhead(sourceLength: Int): Int =
        dev.firechip.cobs.encodingOverhead(sourceLength)

    /** See the top-level [maxEncodedLength]. */
    @JvmStatic
    public fun maxEncodedLength(sourceLength: Int): Int =
        dev.firechip.cobs.maxEncodedLength(sourceLength)
}
