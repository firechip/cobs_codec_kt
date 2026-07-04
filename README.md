# cobs_codec

_Kotlin / Android edition — Maven artifact `dev.firechip:cobs_codec`, repository
[`cobs_codec_kt`](https://github.com/firechip/cobs_codec_kt) (the `_kt` suffix is
just the repo slug)._

[![CI](https://github.com/firechip/cobs_codec_kt/actions/workflows/ci.yml/badge.svg)](https://github.com/firechip/cobs_codec_kt/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/firechip/cobs_codec_kt?sort=semver)](https://github.com/firechip/cobs_codec_kt/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Pure-Kotlin **Consistent Overhead Byte Stuffing (COBS)** and **COBS/R** for
Android, distributed as an `.aar`. It is the Kotlin/Android member of the
Firechip COBS family (alongside the Dart
[`cobs_codec`](https://pub.dev/packages/cobs_codec) on pub.dev) and produces
byte-identical output (validated against the shared conformance vectors).

COBS encodes an arbitrary `ByteArray` into one that contains no zero (`0x00`)
bytes, at a small, predictable cost (at most one extra byte per 254 bytes, plus
one). That lets a single `0x00` reliably delimit packets on a byte stream such
as a serial/UART, USB, or BLE link.

## Features

- **Basic COBS** and **COBS/R (Reduced)** encode/decode (`Cobs`, `Cobsr`).
- **Configurable sentinel** — encode/decode (both COBS and COBS/R) against any
  delimiter byte instead of `0x00`, via `encodeWithSentinel` / `decodeWithSentinel`.
  The encoded output never contains the sentinel byte, so a non-`0x00` byte can
  delimit frames; a `sentinel` of `0` is byte-for-byte identical to the plain
  codec.
- **In-place decode** (basic COBS) — `Cobs.decodeInPlace` decodes within the same
  buffer with no second allocation, returning the decoded length. COBS never
  expands on decode, so the decoded bytes occupy the front of the buffer.
- **Stream framing** for delimiter-framed links: `CobsFraming.frame` /
  `unframe`, and the incremental `CobsStreamDecoder` (reassembles packets across
  arbitrary chunk boundaries, with a `maxFrameLength` guard). Each takes a
  `sentinel` byte (default `0x00`) so frames can be delimited by any chosen byte.
- **`java.io` stream adapters** — `CobsFramedOutputStream.writeFrame` /
  `CobsFramedInputStream.readFrame` (plus `frames()` as a `Sequence`) wrap any
  `OutputStream` / `InputStream` to write and read self-delimiting frames. They
  use only `java.io`, so they add no dependency.
- **Coroutines `Flow` hook** — `Flow<ByteArray>.cobsFrames()` reassembles a flow
  of raw chunks into a flow of decoded packets. `kotlinx-coroutines` is a
  `compileOnly` dependency, so the published artifact stays free of any runtime
  dependency; the extension is available to consumers who already use coroutines.
- **Zero dependencies**, pure Kotlin, no Android framework APIs in the logic.
  `minSdk 21`, `compileSdk 35`.

## Install

### Gradle (GitHub Packages)

The library is published to the GitHub Packages Maven registry as
`dev.firechip:cobs_codec`. Add the repository and the dependency:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/firechip/cobs_codec_kt")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
                ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("dev.firechip:cobs_codec:1.1.0")
}
```

> GitHub Packages requires authentication even for public packages. Use a GitHub
> [personal access token](https://github.com/settings/tokens) with the
> `read:packages` scope, set as `gpr.user` / `gpr.key` in
> `~/.gradle/gradle.properties` (or the `GITHUB_ACTOR` / `GITHUB_TOKEN`
> environment variables).

### Direct `.aar` download

Alternatively, the `.aar` is attached to every
[GitHub release](https://github.com/firechip/cobs_codec_kt/releases) and needs no
authentication:

```kotlin
dependencies {
    implementation(files("libs/cobs_codec-1.1.0.aar"))
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

// Configurable sentinel: encode/decode against any delimiter byte, not just 0x00.
// The output never contains the sentinel, so it can delimit frames instead of 0x00.
// A Byte literal above 0x7F needs `.toByte()`, e.g. 0xAA.toByte().
val s = 0xAA.toByte()
val stuffed = Cobs.encodeWithSentinel(byteArrayOf(0x11, 0x00, 0x22), s) // [0xA8,0xBB,0xA8,0x88], no 0xAA
Cobs.decodeWithSentinel(stuffed, s)                                     // [0x11,0x00,0x22]
Cobsr.encodeWithSentinel(byteArrayOf(0x11, 0x00, 0x22), s)             // [0xA8,0xBB,0x88], COBS/R variant

// In-place decode (basic COBS only): no second allocation; returns the decoded length.
val buf = Cobs.encode(byteArrayOf(0x11, 0x00, 0x22)) // [0x02,0x11,0x02,0x22]
val n = Cobs.decodeInPlace(buf)                      // n == 3; buf.copyOf(n) == [0x11,0x00,0x22]
// Cobs.decodeInPlace(buf, s) does the same for sentinel-encoded data.

// Sentinel-aware framing: frame/unframe and the stream decoder all take a sentinel.
val framed = CobsFraming.frame(byteArrayOf(0x11, 0x00, 0x22), sentinel = s) // ...trailing 0xAA
CobsFraming.unframe(framed, sentinel = s)                                   // [[0x11,0x00,0x22]]
val rxAA = CobsStreamDecoder(maxFrameLength = 4096, sentinel = s)
```

### `java.io` stream adapters

Wrap any `OutputStream` / `InputStream` to write and read self-delimiting frames
(dependency-free — `java.io` only). `readFrame()` returns `null` at end of stream;
`frames()` exposes the same reads as a `Sequence`.

```kotlin
import dev.firechip.cobs.CobsFramedOutputStream
import dev.firechip.cobs.CobsFramedInputStream

CobsFramedOutputStream(socket.outputStream).use { out ->
    out.writeFrame(byteArrayOf(0x11, 0x00, 0x22)) // encoded frame + 0x00 delimiter
}

val input = CobsFramedInputStream(socket.inputStream) // reduced / sentinel optional
for (packet in input.frames()) handlePacket(packet)
```

### Coroutines `Flow`

`Flow<ByteArray>.cobsFrames()` reassembles a flow of raw chunks (however
misaligned) into a flow of decoded packets. `kotlinx-coroutines` is a
`compileOnly` dependency, so it adds nothing to the published artifact; add it to
your own build to use this extension.

```kotlin
import dev.firechip.cobs.cobsFrames

serialBytes // Flow<ByteArray> of raw reads
    .cobsFrames() // reduced / skipEmpty / sentinel optional
    .collect { packet -> handlePacket(packet) }
```

Invalid encoded input throws `CobsDecodeException`.

## Benchmarks

Single-threaded JVM throughput on a 1 KiB payload (JDK 25, AMD Ryzen 7 3800XT
under WSL2) — ballpark micro-benchmark numbers:

| Operation | Throughput |
| --------- | ---------- |
| `Cobs.encode` | ~580 MB/s |
| `Cobs.decode` | ~850 MB/s |
| `Cobsr.encode` | ~600 MB/s |

Run with `COBS_BENCH=1 ./gradlew :cobs:testDebugUnitTest --tests '*BenchmarkTest*' --rerun-tasks`.

## Build

Requires JDK 17 or newer (CI builds on JDK 25) and the Android SDK
(`compileSdk 35`).

```console
./gradlew :cobs:assembleRelease   # -> cobs/build/outputs/aar/cobs-release.aar
./gradlew :cobs:testDebugUnitTest # unit tests (golden vectors)
```

Pushing a `v*` tag builds the `.aar` in CI and attaches it to a GitHub release.

## License

MIT (c) 2026 Alexander Salas Bastidas ([Firechip](https://firechip.dev)). See
[LICENSE](LICENSE).
