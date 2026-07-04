// Copyright (c) 2026 Alexander Salas Bastidas <ajsb85@firechip.dev>
// SPDX-License-Identifier: MIT

package dev.firechip.cobs

/**
 * Thrown when decoding fails because the input is not a valid COBS (or COBS/R)
 * encoded byte sequence.
 *
 * A valid COBS stream never contains a zero byte, and every length code must
 * point to a valid position within the input. Decoding throws this when either
 * invariant is violated: a zero (`0x00`) byte appears in the input, or a length
 * code claims more bytes than remain (basic COBS only; COBS/R interprets that
 * same situation as its reduced final block).
 *
 * [offset] is the index of the offending byte within the encoded input, or
 * `-1` when unknown.
 */
public class CobsDecodeException @JvmOverloads constructor(
    message: String,
    public val offset: Int = -1,
) : IllegalArgumentException(message)
