// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Reassembles a [Flow] of raw byte chunks — as delivered by a serial/UART, USB,
 * or BLE source whose reads do not align with frame boundaries — into a [Flow]
 * of decoded packets.
 *
 * Each collection drives a fresh [CobsStreamDecoder]: upstream chunks are fed to
 * it in order and every packet it completes is emitted downstream, so a frame
 * split across several chunks (or several frames in one chunk) is handled
 * transparently. The returned flow is cold and re-collectable.
 *
 * This mirrors [CobsFramedInputStream] for coroutine-based sources. Coroutines
 * is a `compileOnly` dependency of this library, so this extension is available
 * only to consumers who already depend on `kotlinx-coroutines-core`.
 *
 * @param reduced decode with COBS/R (reduced) instead of basic COBS.
 * @param skipEmpty drop empty frames instead of emitting empty arrays.
 * @param sentinel the delimiter byte (and the byte each frame avoids); defaults
 *   to the `0x00` [COBS_DELIMITER] and must match the sender's sentinel.
 * @throws CobsDecodeException if a completed frame is not valid encoded data.
 */
public fun Flow<ByteArray>.cobsFrames(
    reduced: Boolean = false,
    skipEmpty: Boolean = true,
    sentinel: Byte = COBS_DELIMITER,
): Flow<ByteArray> {
    val chunks = this
    return flow {
        val decoder = CobsStreamDecoder(
            reduced = reduced,
            skipEmpty = skipEmpty,
            sentinel = sentinel,
        )
        chunks.collect { chunk ->
            for (frame in decoder.feed(chunk)) {
                emit(frame)
            }
        }
    }
}
