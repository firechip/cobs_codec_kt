# Changelog

All notable changes to this library are documented here. This project adheres
to [Semantic Versioning](https://semver.org).

## 1.1.0

### Added

- **Configurable sentinel**: `encodeWithSentinel` / `decodeWithSentinel` on both
  `Cobs` and `Cobsr`, for framing with a delimiter other than `0x00`. The
  encoded output never contains the chosen sentinel byte, and a sentinel of `0`
  is byte-for-byte identical to the plain codecs.
- **In-place decoding**: `Cobs.decodeInPlace` (with an optional `sentinel`
  overload), which decodes basic COBS within the same buffer and returns the
  decoded length — no second array required.
- **Framing sentinel**: `CobsFraming.frame` / `unframe` and `CobsStreamDecoder`
  gained a trailing `sentinel` parameter (defaulting to the `0x00`
  `COBS_DELIMITER`), so a stream can be framed on a non-`0x00` delimiter.

All additions are backward compatible.

## 1.0.0

Initial release.

### Added

- **Basic COBS** and **COBS/R** encode/decode (`Cobs`, `Cobsr`).
- **Stream framing** for `0x00`-delimited links: `CobsFraming.frame` /
  `unframe`, and the incremental `CobsStreamDecoder` with a `maxFrameLength`
  guard.
- Size helpers `maxEncodedLength` and `encodingOverhead`.
- `CobsDecodeException` for invalid encoded input.
- Golden-vector tests plus a conformance test against
  [firechip/cobs-conformance](https://github.com/firechip/cobs-conformance),
  byte-identical to the reference.
