# Ravel

[![Tests](https://github.com/andret2344/ravel-android/actions/workflows/tests.yml/badge.svg)](https://github.com/andret2344/ravel-android/actions/workflows/tests.yml)
[![Release](https://img.shields.io/github/v/release/andret2344/ravel-android?sort=semver&display_name=tag&label=release)](https://github.com/andret2344/ravel-android/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)](gradle/libs.versions.toml)
[![Android](https://img.shields.io/badge/Android-24%E2%80%9337-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](app/build.gradle.kts)
[![JDK](https://img.shields.io/badge/JDK-21%20build%20%2F%2011%20target-ED8B00?logo=openjdk&logoColor=white)](.github/workflows/tests.yml)
[![Languages](https://img.shields.io/badge/languages-polski%20%C2%B7%20english-blue)](generator/DICTIONARY.md)
[![Licence](https://img.shields.io/badge/licence-PolyForm%20Noncommercial%201.0.0-lightgrey)](LICENSE.md)

An Android word puzzle. The player is shown a grid of letters, a list of word *lengths*, and
nothing else. Each hidden word is a path of orthogonally adjacent cells, traced from either end,
and the free cells tile exactly once. A board is only publishable when that tiling is unique — so
the lengths alone are enough to solve it, and there is never a second answer.

Two languages, Polish and English, each with its own bundled dictionary of base forms.

## Modes

- **Daily** — one board per calendar date, the same board for everyone, with a streak and a
  calendar of past days. A solved day can be shared as a picture of its word lengths (`4🟥 5🟩 9🟨`)
  and nothing else.
- **Levels** — an endless ladder. Grid size ramps to 10×10 by level 250; how obscure the words may
  be keeps climbing to level 2500.

Hints cost coins, earned by completing puzzles, by keeping a daily streak, and by watching rewarded
ads in the shop.

## Modules

| Module | What it is |
| --- | --- |
| `:generator` | Plain Kotlin/JVM. Generates boards and proves them unique. No Android, so it runs and tests on a desktop JVM. |
| `:app` | The Android app: Compose, single activity, five tabs. Depends on `:generator`. |

`Ravel` (`generator/.../Ravel.kt`) is the generator's entire public surface. Generation is a search
with random restarts — it blocks, it has a long tail, and it can be interrupted.

The app keeps puzzle rules in `app/.../game/`, where `Puzzle` is immutable and every move returns a
new puzzle. Screens live under `ui/<area>/` as `XScreen.kt` + `XViewModel.kt`. Persistence is
SharedPreferences plus JSON.

## Build and test

Requires JDK 21 to run the build; both modules compile to Java 11. On Windows use `gradlew.bat`.

```bash
./gradlew :generator:test              # generator suite — the slow one, several tests build real boards
./gradlew :app:testDebugUnitTest       # game rules; pure JVM, milliseconds
./gradlew :app:assembleDebug           # resource and manifest errors no test sees
./gradlew :app:lintDebug               # a string in one language and not the other is an error
```

CI (`.github/workflows/tests.yml`) runs exactly those four, in that order.

## Developer tools

```bash
# The board a given level or day actually gets, through the real PuzzleSpec/seed path
./gradlew :app:printPuzzle --args="--level 12"
./gradlew :app:printPuzzle --args="--daily today --quiet"

# Time the whole ladder, CSV on stdout and the summary on stderr
./gradlew :app:sweepLevels --args="--to 300" --quiet > sweep.csv

# The generator directly: a width and a seed, for experiments with the difficulty dials
./gradlew :generator:run --args="--width 8 --language en --difficulty hard --count 5"

# Rebuild the bundled dictionaries from the corpora in generator/data/
./gradlew :generator:buildDictionaries
```

## Documentation

- `CLAUDE.md` — the rules: what to do and what never to do, for anyone working in this repo.
- `docs/DECISIONS.md` — the arguments behind those rules: what was measured, tried, and rejected.
- `generator/DICTIONARY.md` — dictionary sources, licences, and how the word lists are built.
- `docs/privacy-policy.md` — the published privacy policy.

## Licence

[PolyForm Noncommercial 1.0.0](LICENSE.md). Read it, build it, change it, use it for anything
noncommercial — commercial use, this game on a store included, is not licensed. The bundled word
lists are third-party material under their own terms; `LICENSE.md` says which files and
`generator/DICTIONARY.md` says under what.

## Attribution

The dictionaries are built from third-party corpora, three of which require a notice: **SJP.PL** and
**KWJP100** (CC BY 4.0, modified), **PoliMorf** (BSD 2-clause), and **WordNet 3.0** (Princeton).
The app carries all of them under "Word sources" in the settings tab; `generator/DICTIONARY.md`
records the full list.
