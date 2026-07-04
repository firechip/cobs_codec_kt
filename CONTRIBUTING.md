# Contributing to cobs_codec_kt

Thanks for your interest in improving `cobs_codec_kt`!

## Getting started

You need **JDK 17** and the **Android SDK** (`compileSdk 35`, `build-tools
35.0.0`). The Gradle wrapper pins the Gradle version, so no separate Gradle
install is needed.

```console
git clone https://github.com/firechip/cobs_codec_kt.git
cd cobs_codec_kt
./gradlew :cobs:assembleRelease
```

## Development workflow

Before opening a pull request, make sure the following pass (this is the same
bar CI enforces):

```console
./gradlew :cobs:testDebugUnitTest   # unit tests (golden vectors)
./gradlew :cobs:assembleRelease     # builds the .aar
```

## Correctness bar

COBS and COBS/R are exact, well-specified algorithms, so correctness is
non-negotiable:

- Any change to `Cobs.kt` or `Cobsr.kt` must keep the golden vectors in
  `cobs/src/test/kotlin` passing. Those vectors are ported from the reference
  implementations and must **not** be changed to make new code pass.
- The implementation is validated by differential testing against the original
  Python reference and is byte-identical to the Dart `cobs_codec` package. If you
  change the algorithms, re-run a byte-for-byte comparison against the reference.
- New behaviour needs new tests.

## Commits and pull requests

- Write clear, imperative commit messages.
- Commits and tags in this repository are **SSH-signed**; please sign your
  commits and include a sign-off (`git commit -s`) certifying the
  [DCO](https://developercertificate.org/).
- Keep pull requests focused.

## Releasing

Push a `v*` tag (for example `v1.0.0`); the `Release AAR` workflow builds the
`.aar` and attaches it to the corresponding GitHub release.

## License

By contributing, you agree that your contributions are licensed under the
project's [MIT License](LICENSE).
