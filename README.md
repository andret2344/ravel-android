# Ravel

[![Tests](https://github.com/andret2344/ravel-android/actions/workflows/tests.yml/badge.svg)](https://github.com/andret2344/ravel-android/actions/workflows/tests.yml)
[![Release](https://img.shields.io/github/v/release/andret2344/ravel-android?sort=semver&display_name=tag&label=release)](https://github.com/andret2344/ravel-android/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)](gradle/libs.versions.toml)
[![Android](https://img.shields.io/badge/Android-24%E2%80%9337-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](app/build.gradle.kts)
[![JDK](https://img.shields.io/badge/JDK-21%20build%20%2F%2011%20target-ED8B00?logo=openjdk&logoColor=white)](.github/workflows/tests.yml)
[![Languages](https://img.shields.io/badge/languages-polski%20%C2%B7%20english-blue)](generator/DICTIONARY.md)
[![License](https://img.shields.io/badge/license-PolyForm%20Noncommercial%201.0.0-lightgray)](LICENSE.md)

An Android word puzzle. The player is shown a grid of letters, a list of word *lengths*, and
nothing else. Each hidden word is a path of orthogonally adjacent cells, traced from its first
letter, and the free cells tile exactly once. A board is only publishable when that tiling is unique — so
the lengths alone are enough to solve it, and there is never a second answer.

Two languages, Polish and English, each with its own bundled dictionary of base forms.

## Modes

- **Daily** — one board per calendar date, the same board for everyone, with a streak and a
  calendar of past days. A solved day can be shared as a picture of its word lengths (`4🟥 5🟩 9🟨`)
  and nothing else; its words stay listed in the calendar, each opening its meaning. An opt-in notification, at most once a day and only while the day's board is
  unsolved, comes around the time the player last solved one, or at a time they pick.
- **Levels** — an endless ladder. Grid size ramps to 10×10 by level 250; how obscure the words may
  be keeps climbing to level 2500. Modifiers on the game tab trade something away for extra coins -
  "No lengths" hides the word lengths and their count, "Hidden letters" covers some letters with a
  star until their word is found, "Reversed words" makes every word be traced from its last letter;
  a level keeps the modifiers it was first opened with.

Hints cost coins, earned by completing puzzles, by keeping a daily streak, and in the shop's daily
rewards: rewarded ads and a small free gift once a day. A single missed day can be saved the next
day, once per seven-day cycle, for coins or an ad. Ads are rewarded only, shown when the player
asks for one - from the shop or from that streak repair.

A short interactive tutorial on a hand-made board opens on first launch - including one real word
that lies on the board and still does not count; it can be skipped and
replayed from the options tab. The options tab also holds the language, board haptics, the theme
(system, light or dark), the daily notification, a privacy page - switches for usage statistics and
crash reports (Firebase Analytics and Crashlytics, release builds only), the ad consent choices and
the privacy policy - and a progress reset that never pays the same level or day twice.

## Modules

| Module | What it is |
| --- | --- |
| `:generator` | Plain Kotlin/JVM. Generates boards and proves them unique. No Android, so it runs and tests on a desktop JVM. |
| `:app` | The Android app: Compose, single activity, five tabs. Depends on `:generator`. |

`Ravel` (`generator/.../Ravel.kt`) is the generator's entire public surface. Generation is a search
with random restarts — it blocks, it has a long tail, and it can be interrupted.

The app keeps puzzle rules in `app/.../game/`, where `Puzzle` is immutable and every move returns a
new puzzle. Screens live under `ui/<area>/` as `XScreen.kt` + `XViewModel.kt`. Persistence is
SharedPreferences plus JSON. Boards are built ahead of the player and kept on the device until
solved (`app/.../game/PuzzleCache.kt`), because a large board can take a minute on a phone.
Everything the game reports is one class, `app/.../analytics/AnalyticsEvent.kt`.

The build reads `app/google-services.json`, the Firebase project's configuration, which is
committed: it identifies the project and holds no secret.

## Build and test

Requires JDK 21 to run the build; both modules compile to Java 11. On Windows use `gradlew.bat`.

```bash
./gradlew :generator:test              # generator suite — the slow one, several tests build real boards
./gradlew :app:testDebugUnitTest       # game rules; pure JVM, milliseconds
./gradlew :app:assembleDebug           # resource and manifest errors no test sees
./gradlew :app:lintDebug               # a string in one language and not the other is an error
```

CI (`.github/workflows/tests.yml`) runs exactly those four, in that order.

The smoke tests in `app/src/androidTest` - the five tabs' back stacks, turning the phone while a
board is built, the layouts on the phone's side, progress written to disk - run on an emulator
(API 30 or later), by hand before a release. Each test clears the app's data on the device it runs
on, so `ANDROID_SERIAL` names the emulator and keeps any connected phone out of it:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

## Developer tools

```bash
# The board a given level or day actually gets, through the real PuzzleSpec/seed path
./gradlew :app:printPuzzle --args="--level 12"
./gradlew :app:printPuzzle --args="--daily today --quiet"

# Time the whole ladder, CSV on stdout and the summary on stderr
./gradlew :app:sweepLevels --args="--to 300" --quiet > sweep.csv

# The generator directly: a width and a seed, for experiments with the difficulty dials
./gradlew :generator:run --args="--width 8 --language en-US --difficulty hard --count 5"

# A release build (R8, not debuggable) with the debug tools, installed beside the Play game
./gradlew :app:assembleMeasure

# Rebuild the bundled dictionaries from the corpora in generator/data/
./gradlew :generator:buildDictionaries
```

## Documentation

- `CLAUDE.md` — the rules: what to do and what never to do, for anyone working in this repo.
- `docs/DECISIONS.md` — the arguments behind those rules: what was measured, tried, and rejected.
- `docs/TODO.md` — work left before the beta, code still owed, and features already decided.
- `generator/DICTIONARY.md` — dictionary sources, licenses, and how the word lists are built.
- `docs/privacy-policy.md` — the published privacy policy.

## License

[PolyForm Noncommercial 1.0.0](LICENSE.md). Read it, build it, change it, use it for anything
noncommercial — commercial use, this game on a store included, is not licensed. The bundled word
lists and the Material icons (Apache 2.0) are third-party material under their own terms;
`LICENSE.md` says which files, and `generator/DICTIONARY.md` says under what for the words.

## Attribution

The dictionaries are built from third-party corpora, three of which require a notice: **SJP.PL** and
**KWJP100** (CC BY 4.0, modified), **PoliMorf** (BSD 2-clause), and **WordNet 3.0** (Princeton).
The app carries all of them under "Word sources" on the licenses page, one row in the options tab;
`generator/DICTIONARY.md` records the full list. The icons are Material Symbols by Google
(Apache 2.0), and the list of phone manufacturers behind the reminder settings' help link comes from
Don't kill my app! by Urbandroid Team (CC BY 4.0, modified); the same page carries both under
"Licenses", beside the game's own.
