// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trips packets through the [CobsFramedOutputStream] /
 * [CobsFramedInputStream] adapters over in-memory byte streams, covering the
 * sentinel, reduced, and empty-frame options.
 */
class StreamsTest {

    private fun roundTrip(
        packets: List<ByteArray>,
        reduced: Boolean = false,
        sentinel: Byte = COBS_DELIMITER,
    ): List<ByteArray> {
        val sink = ByteArrayOutputStream()
        CobsFramedOutputStream(sink, reduced = reduced, sentinel = sentinel).use { out ->
            for (p in packets) out.writeFrame(p)
        }
        val input = CobsFramedInputStream(
            ByteArrayInputStream(sink.toByteArray()),
            reduced = reduced,
            sentinel = sentinel,
        )
        return input.frames().toList()
    }

    @Test
    fun writeThenReadRoundTrips() {
        val packets = listOf(
            bytes(0x11, 0x00, 0x22),
            bytes(0x01),
            ByteArray(0), // encodes to a one-byte frame, so it survives skipEmpty
            range(1, 40),
            nonZeroBytes(600),
        )
        val got = roundTrip(packets)
        assertEquals(packets.size, got.size)
        for (k in packets.indices) assertArrayEquals(packets[k], got[k])
    }

    @Test
    fun readFrameReturnsNullAtEndOfStream() {
        val sink = ByteArrayOutputStream()
        CobsFramedOutputStream(sink).writeFrame(bytes(0x42))
        val input = CobsFramedInputStream(ByteArrayInputStream(sink.toByteArray()))
        assertArrayEquals(bytes(0x42), input.readFrame())
        assertNull(input.readFrame())
        assertNull(input.readFrame())
    }

    @Test
    fun sentinelAndReducedRoundTrip() {
        val s = 0xAA.toByte()
        val packets = listOf(ascii("12345"), bytes(0x11, 0x00, 0x22), range(0, 16))

        // The written wire must avoid the sentinel except as the trailing delimiters.
        val sink = ByteArrayOutputStream()
        val out = CobsFramedOutputStream(sink, reduced = true, sentinel = s)
        for (p in packets) out.writeFrame(p)
        out.flush()
        val wire = sink.toByteArray()
        assertEquals(packets.size, wire.count { it == s })

        val got = roundTrip(packets, reduced = true, sentinel = s)
        assertEquals(packets.size, got.size)
        for (k in packets.indices) assertArrayEquals(packets[k], got[k])
    }

    @Test
    fun skipEmptyControlsEmptyFrames() {
        // Leading, then a real frame, then two trailing delimiters -> three empties.
        val wire = cat(bytes(0x00), CobsFraming.frame(bytes(0x42)), bytes(0x00, 0x00))

        val skipped = CobsFramedInputStream(ByteArrayInputStream(wire)).frames().toList()
        assertEquals(1, skipped.size)
        assertArrayEquals(bytes(0x42), skipped[0])

        val kept = CobsFramedInputStream(ByteArrayInputStream(wire), skipEmpty = false)
            .frames().toList()
        assertEquals(4, kept.size)
        assertTrue(kept[0].isEmpty())
        assertArrayEquals(bytes(0x42), kept[1])
        assertTrue(kept[2].isEmpty())
        assertTrue(kept[3].isEmpty())
    }

    @Test
    fun truncatedTrailingFrameIsDecoded() {
        // A frame with no trailing delimiter (a truncated tail) is still decoded.
        val wire = Cobs.encode(bytes(0x07, 0x08, 0x09))
        val input = CobsFramedInputStream(ByteArrayInputStream(wire))
        assertArrayEquals(bytes(0x07, 0x08, 0x09), input.readFrame())
        assertNull(input.readFrame())
    }
}
