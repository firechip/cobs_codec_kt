// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reassembles flows of raw chunks into decoded frames via [cobsFrames]. */
class FlowTest {

    @Test
    fun reassemblesMisalignedChunks() = runTest {
        val wire = cat(
            CobsFraming.frame(bytes(0x01, 0x02)),
            CobsFraming.frame(bytes(0x00, 0xFF)),
            CobsFraming.frame(bytes(0x2A)),
        )
        // Cut the wire at boundaries that fall in the middle of frames.
        val cuts = intArrayOf(0, 1, 6, 7, wire.size)
        val chunks = (0 until cuts.size - 1).map { wire.copyOfRange(cuts[it], cuts[it + 1]) }

        val frames = flowOf(*chunks.toTypedArray()).cobsFrames().toList()
        assertEquals(3, frames.size)
        assertArrayEquals(bytes(0x01, 0x02), frames[0])
        assertArrayEquals(bytes(0x00, 0xFF), frames[1])
        assertArrayEquals(bytes(0x2A), frames[2])
    }

    @Test
    fun reducedAndSentinelByteAtATime() = runTest {
        val s = 0xAA.toByte()
        val packets = listOf(ascii("12345"), bytes(0x11, 0x00, 0x22), bytes(0x2A))
        val wire = cat(
            *Array(packets.size) {
                CobsFraming.frame(packets[it], reduced = true, sentinel = s)
            },
        )
        // One byte per chunk is the most misaligned drip possible.
        val chunks = wire.map { byteArrayOf(it) }

        val frames = chunks.asFlow().cobsFrames(reduced = true, sentinel = s).toList()
        assertEquals(packets.size, frames.size)
        for (k in packets.indices) assertArrayEquals(packets[k], frames[k])
    }

    @Test
    fun skipEmptyFalseKeepsEmptyFrames() = runBlocking {
        val wire = cat(bytes(0x00), CobsFraming.frame(bytes(0x42)), bytes(0x00, 0x00))

        val frames = flowOf(wire).cobsFrames(skipEmpty = false).toList()
        assertEquals(4, frames.size)
        assertTrue(frames[0].isEmpty())
        assertArrayEquals(bytes(0x42), frames[1])
        assertTrue(frames[2].isEmpty())
        assertTrue(frames[3].isEmpty())
    }
}
