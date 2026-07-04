// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CobsTest {

    // Golden vectors ported from the reference COBS suite: (decoded, encoded).
    private val predefined: List<Pair<ByteArray, ByteArray>> = listOf(
        ascii("") to bytes(0x01),
        ascii("1") to bytes(0x02, 0x31),
        ascii("12345") to cat(bytes(0x06), ascii("12345")),
        cat(ascii("12345"), bytes(0), ascii("6789")) to
            cat(bytes(0x06), ascii("12345"), bytes(0x05), ascii("6789")),
        cat(bytes(0), ascii("12345"), bytes(0), ascii("6789")) to
            cat(bytes(0x01, 0x06), ascii("12345"), bytes(0x05), ascii("6789")),
        cat(ascii("12345"), bytes(0), ascii("6789"), bytes(0)) to
            cat(bytes(0x06), ascii("12345"), bytes(0x05), ascii("6789"), bytes(0x01)),
        bytes(0) to bytes(0x01, 0x01),
        bytes(0, 0) to bytes(0x01, 0x01, 0x01),
        bytes(0, 0, 0) to bytes(0x01, 0x01, 0x01, 0x01),
        range(1, 254) to cat(bytes(0xFE), range(1, 254)),
        range(1, 255) to cat(bytes(0xFF), range(1, 255)),
        range(1, 256) to cat(bytes(0xFF), range(1, 255), bytes(0x02, 0xFF)),
        range(0, 256) to cat(bytes(0x01, 0xFF), range(1, 255), bytes(0x02, 0xFF)),
    )

    @Test
    fun encodeMatchesGoldenVectors() {
        for ((decoded, encoded) in predefined) {
            assertArrayEquals(encoded, Cobs.encode(decoded))
        }
    }

    @Test
    fun decodeMatchesGoldenVectors() {
        for ((decoded, encoded) in predefined) {
            assertArrayEquals(decoded, Cobs.decode(encoded))
        }
    }

    @Test
    fun decodeRejectsInvalidInput() {
        val bad = listOf(
            bytes(0x00),
            cat(bytes(0x05), ascii("123")),
            cat(bytes(0x05), ascii("1234"), bytes(0x00)),
            cat(bytes(0x05), ascii("12"), bytes(0x00), ascii("4")),
        )
        for (input in bad) {
            try {
                Cobs.decode(input)
                fail("expected CobsDecodeException for ${input.toList()}")
            } catch (_: CobsDecodeException) {
                // expected
            }
        }
    }

    @Test
    fun allZeroMessages() {
        for (len in 0 until 520) {
            val data = ByteArray(len)
            val encoded = Cobs.encode(data)
            assertArrayEquals(ByteArray(len + 1) { 1 }, encoded)
            assertArrayEquals(data, Cobs.decode(encoded))
        }
    }

    @Test
    fun nonZeroMessages() {
        for (len in 1 until 1000) {
            val data = nonZeroBytes(len)
            assertArrayEquals("len=$len", simpleEncodeNonZeros(data), Cobs.encode(data))
            assertArrayEquals("len=$len", data, Cobs.decode(Cobs.encode(data)))
        }
    }

    @Test
    fun nonZeroMessagesWithTrailingZero() {
        for (len in 1 until 1000) {
            val nz = nonZeroBytes(len)
            val data = cat(nz, bytes(0))
            val tail = if (nz.size % 254 == 0) bytes(0x01, 0x01) else bytes(0x01)
            assertArrayEquals("len=$len", cat(simpleEncodeNonZeros(nz), tail), Cobs.encode(data))
            assertArrayEquals("len=$len", data, Cobs.decode(Cobs.encode(data)))
        }
    }

    @Test
    fun randomRoundTripAndZeroFree() {
        val rng = Random(0xC0B5)
        repeat(5000) {
            val len = rng.nextInt(0, 2001)
            val data = ByteArray(len) { rng.nextInt(0, 256).toByte() }
            val encoded = Cobs.encode(data)
            assertTrue(encoded.none { it.toInt() == 0 })
            assertTrue(encoded.size <= maxEncodedLength(len))
            assertArrayEquals(data, Cobs.decode(encoded))
        }
    }

    @Test
    fun sizeHelpers() {
        assertEquals(1, encodingOverhead(0))
        assertEquals(1, encodingOverhead(254))
        assertEquals(2, encodingOverhead(255))
        assertEquals(1, maxEncodedLength(0))
        assertEquals(255, maxEncodedLength(254))
        assertEquals(257, maxEncodedLength(255))
        assertEquals(257, Cobs.maxEncodedLength(255))
        assertEquals(2, Cobs.encodingOverhead(255))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeLengthThrows() {
        encodingOverhead(-1)
    }
}
