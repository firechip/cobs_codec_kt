// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

/** The byte value used to delimit COBS-encoded frames on the wire. */
public const val COBS_DELIMITER: Byte = 0

/**
 * Packet framing helpers built on top of COBS.
 *
 * Because COBS-encoded data never contains a zero byte, a single `0x00` byte
 * can safely delimit encoded packets on a byte stream such as a serial/UART
 * link.
 */
public object CobsFraming {

    /**
     * Encodes [packet] (with COBS/R when [reduced] is true, otherwise basic
     * COBS) and appends the delimiter, producing a self-delimiting frame.
     *
     * [sentinel] selects the delimiter byte (and the byte the encoding avoids);
     * it defaults to the `0x00` [COBS_DELIMITER]. The packet is encoded with the
     * same [sentinel], so the output never contains it before the trailing
     * delimiter.
     */
    @JvmStatic
    @JvmOverloads
    public fun frame(
        packet: ByteArray,
        reduced: Boolean = false,
        sentinel: Byte = COBS_DELIMITER,
    ): ByteArray {
        val encoded = if (reduced) {
            Cobsr.encodeWithSentinel(packet, sentinel)
        } else {
            Cobs.encodeWithSentinel(packet, sentinel)
        }
        val framed = encoded.copyOf(encoded.size + 1)
        framed[encoded.size] = sentinel
        return framed
    }

    /**
     * Splits [data] on the delimiter and decodes each frame, returning the
     * recovered packets. Trailing bytes after the final delimiter are ignored.
     * Empty frames are skipped when [skipEmpty] is true.
     *
     * [sentinel] selects the delimiter byte (and the byte each frame was encoded
     * to avoid); it defaults to the `0x00` [COBS_DELIMITER] and must match the
     * sentinel used to frame.
     *
     * @throws CobsDecodeException if a complete frame is not valid encoded data.
     */
    @JvmStatic
    @JvmOverloads
    public fun unframe(
        data: ByteArray,
        reduced: Boolean = false,
        skipEmpty: Boolean = true,
        sentinel: Byte = COBS_DELIMITER,
    ): List<ByteArray> {
        val frames = ArrayList<ByteArray>()
        var start = 0
        for (i in data.indices) {
            if (data[i] != sentinel) continue
            if (i == start) {
                if (!skipEmpty) frames.add(ByteArray(0))
            } else {
                val slice = data.copyOfRange(start, i)
                frames.add(
                    if (reduced) {
                        Cobsr.decodeWithSentinel(slice, sentinel)
                    } else {
                        Cobs.decodeWithSentinel(slice, sentinel)
                    },
                )
            }
            start = i + 1
        }
        return frames
    }
}

/**
 * A stateful, incremental decoder for a stream of delimiter-framed data, for
 * reading COBS packets from a serial/UART link where bytes arrive in arbitrarily
 * sized chunks that do not align with frame boundaries.
 *
 * Feed raw bytes with [feed]; it returns any packets completed by a delimiter,
 * buffering the rest until a later call. [maxFrameLength] (when greater than 0)
 * bounds how many bytes are buffered for a single unterminated frame, guarding
 * against unbounded memory use on a noisy link that never sends the delimiter.
 * A frame that fails to decode is passed to [onInvalidFrame] if provided
 * (decoding then continues), otherwise the [CobsDecodeException] is thrown.
 *
 * [sentinel] selects the delimiter byte (and the byte each frame was encoded to
 * avoid); it defaults to the `0x00` [COBS_DELIMITER] and must match the sentinel
 * used to frame the stream.
 */
public class CobsStreamDecoder @JvmOverloads constructor(
    private val reduced: Boolean = false,
    private val skipEmpty: Boolean = true,
    private val maxFrameLength: Int = 0,
    private val onInvalidFrame: ((CobsDecodeException, ByteArray) -> Unit)? = null,
    private val sentinel: Byte = COBS_DELIMITER,
) {
    // Concatenation allocates a fresh array, so buffered bytes never alias a
    // caller-supplied chunk that may be reused for the next read.
    private var buffer: ByteArray = ByteArray(0)

    /**
     * Feeds a chunk of raw bytes and returns the packets completed within it.
     */
    public fun feed(chunk: ByteArray): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var start = 0
        for (i in chunk.indices) {
            if (chunk[i] != sentinel) continue
            val frame = buffer + chunk.copyOfRange(start, i)
            buffer = ByteArray(0)
            start = i + 1
            if (frame.isEmpty()) {
                if (!skipEmpty) out.add(ByteArray(0))
                continue
            }
            try {
                out.add(
                    if (reduced) {
                        Cobsr.decodeWithSentinel(frame, sentinel)
                    } else {
                        Cobs.decodeWithSentinel(frame, sentinel)
                    },
                )
            } catch (e: CobsDecodeException) {
                val handler = onInvalidFrame ?: throw e
                handler(e, frame)
            }
        }
        if (start < chunk.size) {
            buffer += chunk.copyOfRange(start, chunk.size)
            if (maxFrameLength > 0 && buffer.size > maxFrameLength) {
                val partial = buffer
                buffer = ByteArray(0)
                val e = CobsDecodeException(
                    "unterminated frame exceeds maxFrameLength ($maxFrameLength bytes)",
                )
                val handler = onInvalidFrame ?: throw e
                handler(e, partial)
            }
        }
        return out
    }

    /** Discards any buffered partial frame. */
    public fun reset() {
        buffer = ByteArray(0)
    }
}
