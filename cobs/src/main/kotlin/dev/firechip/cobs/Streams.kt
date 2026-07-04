// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * An [OutputStream] wrapper that writes whole packets as self-delimiting COBS
 * frames, for pushing packets onto a byte stream such as a serial/UART link.
 *
 * Each [writeFrame] call COBS-encodes a packet (COBS/R when [reduced] is true,
 * otherwise basic COBS) and writes the encoded bytes followed by the [sentinel]
 * delimiter. Because the encoding never emits the sentinel, the delimiter
 * unambiguously ends the frame. This is the streaming counterpart of
 * [CobsFraming.frame].
 *
 * The class extends [FilterOutputStream], so [flush] and [close] delegate to the
 * wrapped [out] stream; closing this stream closes the underlying one.
 *
 * @param out the underlying stream that framed bytes are written to.
 * @param reduced use COBS/R (reduced) encoding instead of basic COBS.
 * @param sentinel the delimiter byte (and the byte the encoding avoids); defaults
 *   to the `0x00` [COBS_DELIMITER] and must match the reader's sentinel.
 */
public class CobsFramedOutputStream @JvmOverloads constructor(
    out: OutputStream,
    private val reduced: Boolean = false,
    private val sentinel: Byte = COBS_DELIMITER,
) : FilterOutputStream(out) {

    /**
     * Encodes [packet] and writes the resulting frame — the encoded bytes plus a
     * trailing [sentinel] delimiter — to the underlying stream.
     *
     * The bytes are written straight to the wrapped stream; call [flush] to push
     * them through a buffered stream. An empty [packet] still produces a
     * non-empty frame (a single length byte plus the delimiter).
     *
     * @throws IOException if the underlying stream fails to accept the bytes.
     */
    @Throws(IOException::class)
    public fun writeFrame(packet: ByteArray) {
        val encoded = if (reduced) {
            Cobsr.encodeWithSentinel(packet, sentinel)
        } else {
            Cobs.encodeWithSentinel(packet, sentinel)
        }
        out.write(encoded)
        out.write(sentinel.toInt())
    }
}

/**
 * An [InputStream] wrapper that reads self-delimiting COBS frames back into
 * packets, the reading counterpart of [CobsFramedOutputStream].
 *
 * [readFrame] consumes bytes up to the next [sentinel] delimiter (or end of
 * stream), decodes them (COBS/R when [reduced] is true, otherwise basic COBS)
 * and returns the recovered packet, or `null` once the stream is exhausted.
 * Empty frames — produced by a leading or consecutive delimiter — are skipped
 * when [skipEmpty] is true. [frames] exposes the same reads as a [Sequence].
 *
 * Bytes are pulled one at a time from the source, so wrap an unbuffered source
 * (a socket or file) in a [java.io.BufferedInputStream] for throughput. The
 * class extends [FilterInputStream], so [close] delegates to the wrapped
 * [source].
 *
 * @param source the underlying stream framed bytes are read from.
 * @param reduced decode with COBS/R (reduced) instead of basic COBS.
 * @param skipEmpty drop empty frames instead of returning empty arrays.
 * @param sentinel the delimiter byte (and the byte each frame avoids); defaults
 *   to the `0x00` [COBS_DELIMITER] and must match the writer's sentinel.
 */
public class CobsFramedInputStream @JvmOverloads constructor(
    source: InputStream,
    private val reduced: Boolean = false,
    private val skipEmpty: Boolean = true,
    private val sentinel: Byte = COBS_DELIMITER,
) : FilterInputStream(source) {

    /**
     * Reads and decodes the next frame, returning the recovered packet, or `null`
     * at end of stream.
     *
     * Bytes are read until the [sentinel] delimiter or end of stream; the bytes
     * before it form the frame. A frame that ends at end of stream without a
     * delimiter (a truncated tail) is still decoded and returned. Empty frames
     * are skipped while [skipEmpty] is true, otherwise each yields an empty array.
     *
     * @throws IOException if the underlying stream fails.
     * @throws CobsDecodeException if a complete frame is not valid encoded data.
     */
    @Throws(IOException::class)
    public fun readFrame(): ByteArray? {
        while (true) {
            val first = `in`.read()
            if (first == -1) return null
            if (first.toByte() == sentinel) {
                // Empty frame from a leading or consecutive delimiter.
                if (skipEmpty) continue
                return ByteArray(0)
            }
            val buffer = ByteArrayOutputStream()
            buffer.write(first)
            while (true) {
                val b = `in`.read()
                if (b == -1 || b.toByte() == sentinel) break
                buffer.write(b)
            }
            return decodeFrame(buffer.toByteArray())
        }
    }

    /**
     * Returns a [Sequence] that yields each frame from [readFrame] until the
     * stream is exhausted. Iterating the sequence drives the same underlying
     * reads, so it is single-pass.
     */
    public fun frames(): Sequence<ByteArray> = generateSequence { readFrame() }

    private fun decodeFrame(frame: ByteArray): ByteArray =
        if (reduced) {
            Cobsr.decodeWithSentinel(frame, sentinel)
        } else {
            Cobs.decodeWithSentinel(frame, sentinel)
        }
}
