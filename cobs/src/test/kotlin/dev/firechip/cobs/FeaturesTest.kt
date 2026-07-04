// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the configurable-sentinel, in-place, and sentinel-framing features.
 * Differential checks against the plain slice codecs are the strongest test, so
 * most of this is randomized.
 */
class FeaturesTest {

    private val sentinels: List<Byte> =
        listOf(0x00, 0x01, 0x7F, 0xAA.toByte(), 0xFF.toByte())

    @Test
    fun sentinelEncodingAvoidsSentinelAndRoundTrips() {
        val rng = Random(0x5E17)
        for (s in sentinels) {
            repeat(2000) {
                val len = rng.nextInt(0, 601)
                val data = ByteArray(len) { rng.nextInt(0, 256).toByte() }

                // Basic COBS.
                val enc = Cobs.encodeWithSentinel(data, s)
                assertTrue("cobs output must avoid sentinel $s", enc.none { it == s })
                assertArrayEquals(data, Cobs.decodeWithSentinel(enc, s))

                // COBS/R.
                val encr = Cobsr.encodeWithSentinel(data, s)
                assertTrue("cobsr output must avoid sentinel $s", encr.none { it == s })
                assertArrayEquals(data, Cobsr.decodeWithSentinel(encr, s))
            }
        }
    }

    @Test
    fun sentinelZeroMatchesPlainCodecs() {
        val rng = Random(0x5E170)
        repeat(2000) {
            val len = rng.nextInt(0, 601)
            val data = ByteArray(len) { rng.nextInt(0, 256).toByte() }
            assertArrayEquals(Cobs.encode(data), Cobs.encodeWithSentinel(data, 0))
            assertArrayEquals(Cobsr.encode(data), Cobsr.encodeWithSentinel(data, 0))
        }
    }

    @Test
    fun decodeInPlaceMatchesDecode() {
        val rng = Random(0x1234A17A)
        for (s in sentinels) {
            repeat(4000) {
                val len = rng.nextInt(0, 701)
                val data = ByteArray(len) { rng.nextInt(0, 256).toByte() }
                val encoded = Cobs.encodeWithSentinel(data, s)

                val expected = Cobs.decodeWithSentinel(encoded, s)
                val buf = encoded.copyOf()
                val n = Cobs.decodeInPlace(buf, s)
                assertArrayEquals(expected, buf.copyOf(n))
                assertArrayEquals(data, buf.copyOf(n))
            }
        }
    }

    @Test
    fun framingWithSentinelRoundTrips() {
        val rng = Random(0x57EA9000)
        for (s in sentinels) {
            for (reduced in listOf(false, true)) {
                repeat(300) {
                    val count = 1 + rng.nextInt(0, 7)
                    val packets = List(count) {
                        val len = rng.nextInt(0, 301)
                        ByteArray(len) { rng.nextInt(0, 256).toByte() }
                    }
                    val wire = cat(
                        *Array(count) {
                            CobsFraming.frame(packets[it], reduced = reduced, sentinel = s)
                        },
                    )
                    // A frame slice is always at least one byte, so no frame is
                    // empty even for an empty packet: every packet round-trips.
                    val whole = CobsFraming.unframe(wire, reduced = reduced, sentinel = s)
                    assertEquals(count, whole.size)
                    for (k in 0 until count) assertArrayEquals(packets[k], whole[k])

                    // Byte-at-a-time streaming must reassemble identically.
                    val decoder = CobsStreamDecoder(reduced = reduced, sentinel = s)
                    val streamed = ArrayList<ByteArray>()
                    for (b in wire) streamed.addAll(decoder.feed(byteArrayOf(b)))
                    assertEquals(count, streamed.size)
                    for (k in 0 until count) assertArrayEquals(packets[k], streamed[k])
                }
            }
        }
    }
}
