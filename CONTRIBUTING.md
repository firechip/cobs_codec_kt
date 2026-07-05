# Contributing to cobs_codec (Kotlin/Android)

Thanks for your interest in improving `cobs_codec` (repository `cobs_codec_kt`)!

## Getting started

You need **JDK 17 or newer** (CI builds on JDK 25) and the **Android SDK** (`compileSdk 35`, `build-tools 36.0.0`). The Gradle wrapper pins the Gradle version, so no separate Gradle
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

## Git workflow: Trunk-Based Development with tbdflow

This project uses [Trunk-Based Development](https://trunkbaseddevelopment.com/):
small, frequent changes integrated into `main` (the trunk) rather than
long-lived branches. We use the [`tbdflow`](https://github.com/cladam/tbdflow)
CLI (`cargo install tbdflow`) so the safe path is the easy path.

`tbdflow commit` pulls `main`, creates a Conventional Commit, and pushes:

```console
tbdflow commit --type fix --scope framing -m "guard against oversized frames"
```

For a change that needs review, use a short-lived branch and merge it back
quickly: `tbdflow branch --type feat --name my-change`, then `tbdflow complete`.
Other helpers: `tbdflow sync`, `tbdflow radar`, `tbdflow changelog`,
`tbdflow undo`.

Two committed files drive this workflow:

- **`.tbdflow.yml`** -- workflow + commit-message lint rules (trunk branch,
  allowed Conventional Commit types, lowercase scope/subject, 72-char subject).
- **`.dod.yml`** -- the Definition of Done checklist shown before each commit
  (unit tests, `.aar` build, conformance). Bypass for a trivial change with
  `--no-verify`.

Commits and tags are **SSH-signed** (`tbdflow commit` respects the repo's
signing config); include a sign-off (`-s`) certifying the
[DCO](https://developercertificate.org/). Keep pull requests focused.

## Conventional Commits

Every commit message follows
[Conventional Commits](https://www.conventionalcommits.org):
`type(scope): short imperative subject`. Allowed **types**: `build`, `chore`,
`ci`, `docs`, `feat`, `fix`, `perf`, `refactor`, `revert`, `style`, `test`. The
subject is lowercase, imperative, and has no trailing period; breaking changes
use `!` (`feat!:`) or a `BREAKING CHANGE:` footer.

This is enforced locally by `tbdflow commit` and in CI by the **Commit lint**
workflow (`.github/workflows/commit-lint.yml`), which checks every commit in a
pull request.

## Releasing

Publishing is automated from a signed `v*` tag; still give the **GitHub Release
a proper description — don't skip it.** The full checklist:

1. Bump `version` in [`cobs/build.gradle.kts`](cobs/build.gradle.kts) and add a
   `## X.Y.Z` section to [`CHANGELOG.md`](CHANGELOG.md).
2. Commit (`chore: release X.Y.Z`) and tag it **signed**:
   `git tag -s vX.Y.Z -m "cobs_codec X.Y.Z"`; push `main` and the tag.
3. The tag triggers two workflows:
   [`publish-maven.yml`](.github/workflows/publish-maven.yml) publishes
   `dev.firechip:cobs_codec` to **GitHub Packages** (Maven), and
   [`release-aar.yml`](.github/workflows/release-aar.yml) builds the `.aar` and
   attaches it to the **GitHub Release** it creates for the tag.
4. Edit that release's description to match the other members: the
   `CHANGELOG.md` highlights, the `dev.firechip:cobs_codec:X.Y.Z` Maven
   coordinate, and the attached `.aar`.

## License

By contributing, you agree that your contributions are licensed under the
project's [MIT License](LICENSE).
