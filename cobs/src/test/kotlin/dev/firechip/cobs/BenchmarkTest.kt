// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

import java.io.File
import java.util.Locale
import kotlin.random.Random
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A lightweight JVM throughput micro-benchmark for [Cobs] and [Cobsr].
 *
 * This is DEV tooling, not a correctness test. Like [ConformanceTest], it is
 * skipped during the normal unit-test job and only runs when the `COBS_BENCH`
 * environment variable is set, so it never slows the default test/CI run:
 *
 * ```
 * COBS_BENCH=1 ./gradlew :cobs:testDebugUnitTest --tests '*BenchmarkTest*'
 * ```
 *
 * Methodology: a fixed 1 KiB pseudo-random payload (~1 byte in 8 is `0x00`, so
 * the COBS block-splitting path is exercised) is encoded/decoded in a tight
 * loop. Each operation is warmed up for [WARMUP] iterations (to let the JIT
 * compile it) and then timed over [ITERS] iterations with [System.nanoTime].
 * Throughput is reported as decimal MB/s, i.e.
 * `(payload bytes x iterations) / elapsed seconds / 1e6`. A running `sink`
 * consumes every result so the timed work cannot be optimised away.
 *
 * JVM micro-benchmarks are approximate (no forking, GC/JIT noise); treat the
 * numbers as ballpark single-threaded throughput, not JMH-grade measurements.
 */
class BenchmarkTest {

    private companion object {
        const val PAYLOAD_SIZE = 1024
        const val WARMUP = 50_000
        const val ITERS = 1_000_000
        const val SEED = 0xC0B5_C0DEL
    }

    /** A 1 KiB payload: mostly non-zero with ~1-in-8 bytes set to `0x00`. */
    private fun makePayload(size: Int): ByteArray {
        val rng = Random(SEED)
        return ByteArray(size) {
            if (rng.nextInt(8) == 0) 0.toByte() else rng.nextInt(1, 256).toByte()
        }
    }

    private fun throughputMbps(payloadBytes: Int, iters: Int, elapsedNanos: Long): Double {
        val seconds = elapsedNanos / 1_000_000_000.0
        return payloadBytes.toDouble() * iters / seconds / 1_000_000.0
    }

    @Test
    fun throughput() {
        assumeTrue(
            "set COBS_BENCH to run the throughput benchmark",
            !System.getenv("COBS_BENCH").isNullOrBlank(),
        )

        val payload = makePayload(PAYLOAD_SIZE)
        val cobsEncoded = Cobs.encode(payload)

        // The sink accumulates a byte of every output so the JIT cannot drop the
        // timed calls as dead code.
        var sink = 0L

        // --- COBS encode ---
        repeat(WARMUP) { sink += Cobs.encode(payload)[0].toLong() }
        var start = System.nanoTime()
        repeat(ITERS) { sink += Cobs.encode(payload)[0].toLong() }
        val cobsEncodeMbps = throughputMbps(PAYLOAD_SIZE, ITERS, System.nanoTime() - start)

        // --- COBS decode ---
        repeat(WARMUP) { sink += Cobs.decode(cobsEncoded)[0].toLong() }
        start = System.nanoTime()
        repeat(ITERS) { sink += Cobs.decode(cobsEncoded)[0].toLong() }
        val cobsDecodeMbps = throughputMbps(PAYLOAD_SIZE, ITERS, System.nanoTime() - start)

        // --- COBS/R encode ---
        repeat(WARMUP) { sink += Cobsr.encode(payload)[0].toLong() }
        start = System.nanoTime()
        repeat(ITERS) { sink += Cobsr.encode(payload)[0].toLong() }
        val cobsrEncodeMbps = throughputMbps(PAYLOAD_SIZE, ITERS, System.nanoTime() - start)

        val zeroCount = payload.count { it.toInt() == 0 }
        val report = buildString {
            appendLine("=== cobs_codec_kt throughput benchmark ===")
            appendLine(
                "JVM:      ${System.getProperty("java.vm.name")} " +
                    System.getProperty("java.version"),
            )
            appendLine(
                "OS/arch:  ${System.getProperty("os.name")} " +
                    "${System.getProperty("os.version")} ${System.getProperty("os.arch")}",
            )
            appendLine(
                "payload:  $PAYLOAD_SIZE bytes, $zeroCount zero (" +
                    String.format(Locale.ROOT, "%.1f", zeroCount * 100.0 / PAYLOAD_SIZE) +
                    "%), cobs-encoded ${cobsEncoded.size} bytes",
            )
            appendLine("loops:    warmup=$WARMUP  timed=$ITERS  (single-threaded)")
            appendLine("---")
            appendLine("COBS   encode: " + fmt(cobsEncodeMbps) + " MB/s")
            appendLine("COBS   decode: " + fmt(cobsDecodeMbps) + " MB/s")
            appendLine("COBS/R encode: " + fmt(cobsrEncodeMbps) + " MB/s")
            appendLine("(decimal MB = 1e6 bytes; MB/s = payload bytes x iters / elapsed s)")
            appendLine("sink=$sink")
        }
        print(report)

        System.getenv("COBS_BENCH_OUT")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).writeText(report) }
    }

    private fun fmt(mbps: Double): String = String.format(Locale.ROOT, "%,.1f", mbps)
}
