// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Verifies this library against the shared conformance vectors from
 * https://github.com/firechip/cobs-conformance
 *
 * Skipped unless the `COBS_CONFORMANCE_VECTORS` environment variable points to a
 * downloaded `vectors.jsonl` (CI sets this; local `./gradlew test` skips it).
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
}
