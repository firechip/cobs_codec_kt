# cobs_codec_kt

[![CI](https://github.com/firechip/cobs_codec_kt/actions/workflows/ci.yml/badge.svg)](https://github.com/firechip/cobs_codec_kt/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/firechip/cobs_codec_kt?sort=semver)](https://github.com/firechip/cobs_codec_kt/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Pure-Kotlin **Consistent Overhead Byte Stuffing (COBS)** and **COBS/R** for
Android, distributed as an `.aar`. It is the Kotlin sibling of the Dart
[`cobs_codec`](https://pub.dev/packages/cobs_codec) package and produces
byte-identical output (validated by 20,000-vector differential testing against
the original reference implementation).

COBS encodes an arbitrary `ByteArray` into one that contains no zero (`0x00`)
bytes, at a small, predictable cost (at most one extra byte per 254 bytes, plus
one). That lets a single `0x00` reliably delimit packets on a byte stream such
as a serial/UART, USB, or BLE link.

## Features

- **Basic COBS** and **COBS/R (Reduced)** encode/decode (`Cobs`, `Cobsr`).
- **Stream framing** for `0x00`-delimited links: `CobsFraming.frame` /
  `unframe`, and the incremental `CobsStreamDecoder` (reassembles packets across
  arbitrary chunk boundaries, with a `maxFrameLength` guard).
- **Zero dependencies**, pure Kotlin, no Android framework APIs in the logic.
  `minSdk 21`, `compileSdk 35`.

## Install

The `.aar` is attached to each
[GitHub release](https://github.com/firechip/cobs_codec_kt/releases). Download
`cobs_codec_kt-<version>.aar` into your app's `libs/` directory and add:

```kotlin
dependencies {
    implementation(files("libs/cobs_codec_kt-1.0.0.aar"))
}
```

## Usage

```kotlin
import dev.firechip.cobs.Cobs
import dev.firechip.cobs.Cobsr
import dev.firechip.cobs.CobsFraming
import dev.firechip.cobs.CobsStreamDecoder

// Encode / decode a single packet.
val encoded = Cobs.encode(byteArrayOf(0x11, 0x00, 0x22)) // [0x02,0x11,0x02,0x22]
val decoded = Cobs.decode(encoded)                       // [0x11,0x00,0x22]

// COBS/R often avoids the trailing overhead byte for small messages.
Cobsr.encode("12345".toByteArray())                      // "51234" bytes

// Frame a packet for a delimited link, then split a buffer back into packets.
val frame = CobsFraming.frame(byteArrayOf(0x11, 0x00, 0x22)) // ... trailing 0x00
val packets = CobsFraming.unframe(frame)

// Decode a live serial stream whose chunks do not align with frame boundaries.
val rx = CobsStreamDecoder(maxFrameLength = 4096)
serialPort.onBytes { chunk -> rx.feed(chunk).forEach(::handlePacket) }
```

Invalid encoded input throws `CobsDecodeException`.

## Build

Requires JDK 17 and the Android SDK (`compileSdk 35`).

```console
./gradlew :cobs:assembleRelease   # -> cobs/build/outputs/aar/cobs-release.aar
./gradlew :cobs:testDebugUnitTest # unit tests (golden vectors)
```

Pushing a `v*` tag builds the `.aar` in CI and attaches it to a GitHub release.

## License

MIT (c) 2026 Alexander Salas Bastidas ([Firechip](https://firechip.dev)). See
[LICENSE](LICENSE).
