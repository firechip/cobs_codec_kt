// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Verifies this library against the shared conformance vectors from
 * https://github.com/firechip/cobs-conformance
 *
 * Each check is skipped unless the corresponding environment variable points to a
 * downloaded JSONL file (CI sets these; local `./gradlew test` skips them):
 *  - `COBS_CONFORMANCE_VECTORS`  -> `vectors.jsonl`  {decoded, cobs, cobsr}
 *  - `COBS_CONFORMANCE_SENTINEL` -> `sentinel.jsonl` {decoded, sentinel, cobs, cobsr}
 *  - `COBS_CONFORMANCE_ERRORS`   -> `errors.jsonl`   {encoded, cobs, cobsr}
 */
class ConformanceTest {

    private fun hex(s: String): ByteArray {
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            out[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }

    private fun toHex(b: ByteArray): String =
        b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    @Test
    fun conformsToSharedVectors() {
        val path = System.getenv("COBS_CONFORMANCE_VECTORS")
        assumeTrue("set COBS_CONFORMANCE_VECTORS to run", !path.isNullOrBlank())

        val regex = Regex(
            "\"decoded\":\"([0-9a-f]*)\",\"cobs\":\"([0-9a-f]*)\"," +
                "\"cobsr\":\"([0-9a-f]*)\"",
        )
        var count = 0
        File(path!!).forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val match = regex.find(line) ?: error("malformed vector line: $line")
            val (d, c, cr) = match.destructured
            val data = hex(d)
            assertArrayEquals("cobs encode $d", hex(c), Cobs.encode(data))
            assertArrayEquals("cobsr encode $d", hex(cr), Cobsr.encode(data))
            assertArrayEquals("cobs decode $c", data, Cobs.decode(hex(c)))
            assertArrayEquals("cobsr decode $cr", data, Cobsr.decode(hex(cr)))
            count++
        }
        assertTrue("no vectors checked", count > 0)
    }

    @Test
    fun conformsToSentinelVectors() {
        val path = System.getenv("COBS_CONFORMANCE_SENTINEL")
        assumeTrue("set COBS_CONFORMANCE_SENTINEL to run", !path.isNullOrBlank())

        val regex = Regex(
            "\"decoded\":\"([0-9a-f]*)\",\"sentinel\":\"([0-9a-f]*)\"," +
                "\"cobs\":\"([0-9a-f]*)\",\"cobsr\":\"([0-9a-f]*)\"",
        )
        var count = 0
        File(path!!).forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val match = regex.find(line) ?: error("malformed sentinel line: $line")
            val (d, s, c, cr) = match.destructured
            val data = hex(d)
            val sentinel = hex(s)[0]
            val cobs = hex(c)
            val cobsr = hex(cr)
            assertArrayEquals("cobs sentinel $s encode $d", cobs, Cobs.encodeWithSentinel(data, sentinel))
            assertArrayEquals("cobsr sentinel $s encode $d", cobsr, Cobsr.encodeWithSentinel(data, sentinel))
            assertArrayEquals("cobs sentinel $s decode $c", data, Cobs.decodeWithSentinel(cobs, sentinel))
            assertArrayEquals("cobsr sentinel $s decode $cr", data, Cobsr.decodeWithSentinel(cobsr, sentinel))
            assertFalse("sentinel $s must not appear in cobs $c", cobs.contains(sentinel))
            assertFalse("sentinel $s must not appear in cobsr $cr", cobsr.contains(sentinel))
            count++
        }
        assertTrue("no sentinel vectors checked", count > 0)
    }

    @Test
    fun conformsToErrorVectors() {
        val path = System.getenv("COBS_CONFORMANCE_ERRORS")
        assumeTrue("set COBS_CONFORMANCE_ERRORS to run", !path.isNullOrBlank())

        val regex = Regex(
            "\"encoded\":\"([0-9a-f]*)\",\"cobs\":(null|\"[0-9a-f]*\")," +
                "\"cobsr\":(null|\"[0-9a-f]*\")",
        )
        var count = 0
        File(path!!).forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val match = regex.find(line) ?: error("malformed error line: $line")
            val (e, c, cr) = match.destructured
            val encoded = hex(e)
            checkDecode("cobs decode $e", c, encoded) { Cobs.decode(it) }
            checkDecode("cobsr decode $e", cr, encoded) { Cobsr.decode(it) }
            count++
        }
        assertTrue("no error vectors checked", count > 0)
    }

    /**
     * Asserts that [decode] applied to [encoded] either fails (when [field] is the
     * JSON literal `null`) or produces the hex payload quoted in [field].
     */
    private fun checkDecode(
        label: String,
        field: String,
        encoded: ByteArray,
        decode: (ByteArray) -> ByteArray,
    ) {
        if (field == "null") {
            try {
                decode(encoded)
                fail("$label: expected decode to fail but it succeeded")
            } catch (_: CobsDecodeException) {
                // expected: this input MUST fail to decode
            }
        } else {
            val expected = hex(field.substring(1, field.length - 1))
            assertArrayEquals(label, expected, decode(encoded))
        }
    }
}
