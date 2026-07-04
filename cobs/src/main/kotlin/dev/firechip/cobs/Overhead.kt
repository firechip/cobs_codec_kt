// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

/**
 * The largest number of source bytes that a single COBS code block can carry
 * without emitting an overhead byte.
 */
public const val COBS_MAX_BLOCK_LENGTH: Int = 254

/**
 * Returns the maximum encoding overhead, in bytes, that COBS or COBS/R can add
 * when encoding a message of [sourceLength] bytes.
 *
 * COBS adds at most one byte for every 254 bytes of input (rounded up), and at
 * least one byte for any message including the empty message. The overhead is
 * therefore a tight, data-independent bound.
 *
 * @throws IllegalArgumentException if [sourceLength] is negative.
 */
public fun encodingOverhead(sourceLength: Int): Int {
    require(sourceLength >= 0) { "sourceLength must not be negative: $sourceLength" }
    if (sourceLength == 0) return 1
    return (sourceLength + (COBS_MAX_BLOCK_LENGTH - 1)) / COBS_MAX_BLOCK_LENGTH
}

/**
 * Returns the maximum possible length, in bytes, of the COBS (or COBS/R)
 * encoding of a message of [sourceLength] bytes. Useful for pre-allocating an
 * output buffer.
 *
 * @throws IllegalArgumentException if [sourceLength] is negative.
 */
public fun maxEncodedLength(sourceLength: Int): Int =
    sourceLength + encodingOverhead(sourceLength)
