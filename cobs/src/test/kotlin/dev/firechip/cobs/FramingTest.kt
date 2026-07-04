// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FramingTest {

    @Test
    fun frameAppendsDelimiterAndRoundTrips() {
        val packet = bytes(0x11, 0x00, 0x22)
        val frame = CobsFraming.frame(packet)
        assertEquals(0, frame.last().toInt())
        assertArrayEquals(bytes(0x02, 0x11, 0x02, 0x22, 0x00), frame)
        assertArrayEquals(packet, CobsFraming.unframe(frame).single())
    }

    @Test
    fun unframeSplitsMultipleFramesAndSkipsEmpty() {
        val wire = cat(
            bytes(0x00),
            CobsFraming.frame(bytes(0x42)),
            bytes(0x00, 0x00),
        )
        val packets = CobsFraming.unframe(wire)
        assertEquals(1, packets.size)
        assertArrayEquals(bytes(0x42), packets[0])

        val withEmpty = CobsFraming.unframe(wire, skipEmpty = false)
        assertEquals(4, withEmpty.size)
    }

    @Test
    fun cobsrFraming() {
        val frame = CobsFraming.frame(ascii("12345"), reduced = true)
        assertArrayEquals(cat(bytes(0x35), ascii("1234"), bytes(0x00)), frame)
        assertArrayEquals(ascii("12345"), CobsFraming.unframe(frame, reduced = true).single())
    }

    @Test
    fun streamDecoderReassemblesAcrossChunks() {
        val wire = cat(
            CobsFraming.frame(bytes(0x01, 0x02)),
            CobsFraming.frame(bytes(0x00, 0xFF)),
            CobsFraming.frame(bytes(0x2A)),
        )
        val decoder = CobsStreamDecoder()
        val got = ArrayList<ByteArray>()
        // Feed in misaligned chunks.
        val cuts = intArrayOf(0, 1, 6, 7, wire.size)
        for (k in 0 until cuts.size - 1) {
            got.addAll(decoder.feed(wire.copyOfRange(cuts[k], cuts[k + 1])))
        }
        assertEquals(3, got.size)
        assertArrayEquals(bytes(0x01, 0x02), got[0])
        assertArrayEquals(bytes(0x00, 0xFF), got[1])
        assertArrayEquals(bytes(0x2A), got[2])
    }

    @Test
    fun streamDecoderByteAtATime() {
        val wire = cat(
            CobsFraming.frame(bytes(0xAA, 0x00, 0xBB)),
            CobsFraming.frame(bytes(0x01)),
        )
        val decoder = CobsStreamDecoder()
        val got = ArrayList<ByteArray>()
        for (b in wire) got.addAll(decoder.feed(byteArrayOf(b)))
        assertEquals(2, got.size)
        assertArrayEquals(bytes(0xAA, 0x00, 0xBB), got[0])
        assertArrayEquals(bytes(0x01), got[1])
    }

    @Test
    fun streamDecoderMaxFrameLengthGuard() {
        val errors = ArrayList<CobsDecodeException>()
        val decoder = CobsStreamDecoder(
            maxFrameLength = 100,
            onInvalidFrame = { e, _ -> errors.add(e) },
        )
        val got = ArrayList<ByteArray>()
        got.addAll(decoder.feed(ByteArray(50) { 1 }))
        got.addAll(decoder.feed(ByteArray(60) { 1 })) // 110 buffered -> discard
        got.addAll(decoder.feed(CobsFraming.frame(bytes(0x22))))
        assertEquals(1, errors.size)
        assertEquals(1, got.size)
        assertArrayEquals(bytes(0x22), got[0])
    }

    @Test
    fun streamDecoderToleratesReusedBuffer() {
        // A caller that reuses one backing array across feeds must not corrupt
        // buffered partial-frame bytes.
        val backing = ByteArray(4)
        val decoder = CobsStreamDecoder()
        val got = ArrayList<ByteArray>()

        backing[0] = 0x04; backing[1] = 0x11
        got.addAll(decoder.feed(backing.copyOfRange(0, 2))) // partial, no delimiter
        backing[0] = 0x22; backing[1] = 0x33; backing[2] = 0x00
        got.addAll(decoder.feed(backing.copyOfRange(0, 3))) // completes the frame

        assertEquals(1, got.size)
        assertArrayEquals(bytes(0x11, 0x22, 0x33), got[0])
    }

    @Test
    fun streamDecoderOnInvalidFrameContinues() {
        val errors = ArrayList<CobsDecodeException>()
        val decoder = CobsStreamDecoder(onInvalidFrame = { e, _ -> errors.add(e) })
        val wire = cat(
            CobsFraming.frame(bytes(0x11)),
            bytes(0x05, 0x01, 0x00), // invalid frame: length code past end
            CobsFraming.frame(bytes(0x22)),
        )
        val got = decoder.feed(wire)
        assertEquals(2, got.size)
        assertArrayEquals(bytes(0x11), got[0])
        assertArrayEquals(bytes(0x22), got[1])
        assertEquals(1, errors.size)
        assertEquals("length code points past end of input", errors[0].message)
    }
}
