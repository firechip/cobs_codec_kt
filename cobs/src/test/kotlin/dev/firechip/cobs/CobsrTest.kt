// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CobsrTest {

    // Golden vectors ported from the reference COBS/R suite: (decoded, encoded).
    private val predefined: List<Pair<ByteArray, ByteArray>> = listOf(
        ascii("") to bytes(0x01),
        bytes(0x01) to bytes(0x02, 0x01),
        bytes(0x02) to bytes(0x02),
        bytes(0x03) to bytes(0x03),
        bytes(0x7E) to bytes(0x7E),
        bytes(0x7F) to bytes(0x7F),
        bytes(0x80) to bytes(0x80),
        bytes(0xD5) to bytes(0xD5),
        bytes(0xFE) to bytes(0xFE),
        bytes(0xFF) to bytes(0xFF),
        cat(ascii("a"), bytes(0x02)) to cat(bytes(0x03), ascii("a"), bytes(0x02)),
        cat(ascii("a"), bytes(0x03)) to cat(bytes(0x03), ascii("a")),
        cat(ascii("a"), bytes(0xFF)) to cat(bytes(0xFF), ascii("a")),
        bytes(0x05, 0x04, 0x03, 0x02, 0x01) to bytes(0x06, 0x05, 0x04, 0x03, 0x02, 0x01),
        ascii("12345") to cat(bytes(0x35), ascii("1234")),
        cat(ascii("12345"), bytes(0), bytes(0x04, 0x03, 0x02, 0x01)) to
            cat(bytes(0x06), ascii("12345"), bytes(0x05, 0x04, 0x03, 0x02, 0x01)),
        cat(ascii("12345"), bytes(0), ascii("6789")) to
            cat(bytes(0x06), ascii("12345"), ascii("9678")),
        cat(bytes(0), ascii("12345"), bytes(0), ascii("6789")) to
            cat(bytes(0x01, 0x06), ascii("12345"), ascii("9678")),
        cat(ascii("12345"), bytes(0), ascii("6789"), bytes(0)) to
            cat(bytes(0x06), ascii("12345"), bytes(0x05), ascii("6789"), bytes(0x01)),
        bytes(0) to bytes(0x01, 0x01),
        bytes(0, 0) to bytes(0x01, 0x01, 0x01),
        bytes(0, 0, 0) to bytes(0x01, 0x01, 0x01, 0x01),
        range(1, 254) to cat(bytes(0xFE), range(1, 254)),
        range(1, 255) to cat(bytes(0xFF), range(1, 255)),
        range(1, 256) to cat(bytes(0xFF), range(1, 255), bytes(0xFF)),
        range(0, 256) to cat(bytes(0x01, 0xFF), range(1, 255), bytes(0xFF)),
        range(2, 256) to cat(bytes(0xFF), range(2, 255)),
    )

    @Test
    fun encodeMatchesGoldenVectors() {
        for ((decoded, encoded) in predefined) {
            assertArrayEquals(encoded, Cobsr.encode(decoded))
        }
    }

    @Test
    fun decodeMatchesGoldenVectors() {
        for ((decoded, encoded) in predefined) {
            assertArrayEquals(decoded, Cobsr.decode(encoded))
        }
    }

    @Test
    fun decodeRejectsInvalidInput() {
        val bad = listOf(
            bytes(0x00),
            cat(bytes(0x05), ascii("1234"), bytes(0x00)),
            cat(bytes(0x05), ascii("12"), bytes(0x00), ascii("4")),
        )
        for (input in bad) {
            try {
                Cobsr.decode(input)
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
            val encoded = Cobsr.encode(data)
            assertArrayEquals(ByteArray(len + 1) { 1 }, encoded)
            assertArrayEquals(data, Cobsr.decode(encoded))
        }
    }

    @Test
    fun randomRoundTripNeverLargerThanCobs() {
        val rng = Random(0x600B5)
        repeat(5000) {
            val len = rng.nextInt(0, 2001)
            val data = ByteArray(len) { rng.nextInt(0, 256).toByte() }
            val encoded = Cobsr.encode(data)
            assertTrue(encoded.none { it.toInt() == 0 })
            assertTrue(encoded.size <= Cobs.encode(data).size)
            assertArrayEquals(data, Cobsr.decode(encoded))
        }
    }
}
