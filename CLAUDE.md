# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Ravel is an Android word puzzle. The player is shown a grid of letters, a list of word *lengths*,
and nothing else; each hidden word is a path of orthogonally adjacent cells, traced from either end,
and the free cells tile exactly once. A board is only publishable when that tiling is unique.

Two Gradle modules:

- **`:generator`** — plain Kotlin/JVM. Generates and validates boards. No Android in it, so all of it
  runs and tests on a desktop JVM.
- **`:app`** — the Android app (Compose, single activity). Depends on `:generator`.

## Commands

On Windows use `gradlew.bat`; the paths below use the POSIX wrapper.

```bash
./gradlew :generator:test              # generator suite - slowest, several tests generate real boards
./gradlew :app:testDebugUnitTest       # game rules; pure JVM, runs in milliseconds
./gradlew :app:assembleDebug           # catches resource/manifest errors no test sees
./gradlew :app:lintDebug               # see "Localization" below - this is not a formatting check

# One test class or method
./gradlew :generator:test --tests '*SolverTest'
./gradlew :app:testDebugUnitTest --tests '*PuzzleTest'
./gradlew :app:testDebugUnitTest --tests '*PuzzleTest.tapping a marked cell*'   # names are sentences
```

CI (`.github/workflows/tests.yml`) runs exactly those four, in that order, on JDK 21.

### Developer tools

```bash
# The board a given level or day actually gets, through the real PuzzleSpec/seed path
./gradlew :app:printPuzzle --args="--level 12"
./gradlew :app:printPuzzle --args="--daily today --quiet"

# The generator directly: a width and a seed, for experiments with the difficulty dials
./gradlew :generator:run --args="--width 8 --language en --difficulty hard --count 5"

# Rebuild the bundled dictionaries from the corpora in generator/data/
./gradlew :generator:buildDictionaries        # or buildDictionaryPl / buildDictionaryEn
```

`printPuzzle` lives in `:app`'s **test** source set (`app/src/test/.../tools/PrintPuzzle.kt`) so a
developer tool is never packaged into the APK. Gradle's own `--quiet` would silence the board along
with Gradle — pass `--quiet` inside `--args` instead.

## Architecture

### Generation pipeline (`:generator`)

`Ravel` (Ravel.kt) is the entire public surface; everything else is machinery.

1. `BoardSpec.forDifficulty` turns a `Difficulty` into the dials: how deep into the frequency-ranked
   dictionary words may reach, and the bands on path shape.
2. Walls are placed as whole orbits under a `WallSymmetry`, so the pattern holds by construction.
3. `Partitioner` splits the free cells into disjoint paths of the chosen lengths — **shape first,
   words second**. Choosing words first means backtracking over a dictionary instead of over a
   handful of shapes, and does not scale.
4. Words are drawn from `Dictionary`, whose trie holds every word **and its reverse** (a path is
   traceable from either end).
5. `Solver` counts tilings; the board is rejected unless exactly one exists.

Path shape is governed by three independent measures, all in `Board.kt` — `turnRatio` (how much
paths bend), `directness` (how far they travel end to end), `foldRatio` (how much a path runs
alongside itself). They are enforced **per word during enumeration**, not on the finished board, and
against the worst word rather than the average. Both choices are load-bearing: the comments in
`Board.kt` and `Language.kt` explain what averaging and post-hoc checking each produced.

Generation blocks (long tail: 9x9 ≈ 1.3 s, 12x12 ≈ 4.5 s) and is interruptible — call it inside
`runInterruptible` so leaving a screen cancels the search. Dictionaries are cached per
(language, difficulty) and cost 5–17 MB; `RavelApplication.onTrimMemory` calls `Ravel.forget()`.

### Puzzle identity (`:app`, `game/`)

This is the part most easily broken by an innocent-looking change.

- `PuzzleId(mode, ordinal)` names a board. The generator seed is derived from it via a SplitMix64
  hash mixed with a per-mode salt, so `ENDLESS 5` and `SPECIAL 5` are unrelated boards.
- **`PuzzleMode` salts must never change.** A salt is part of a board's identity; editing one
  silently replaces every puzzle that mode has ever produced.
- `PuzzleSpec.of(id)` derives size and difficulty **from the id alone**. Never accept them from a
  call site — a seed picks one board *for a given size*, so a caller-chosen size means the id no
  longer names one board.
- Board identity is really *(id, generator version)*. Changing the search order, a difficulty
  constant, or the dictionary reissues every puzzle in the game. Treat that as a deliberate decision
  each time, never a side effect.
- Language is deliberately **not** part of a puzzle's identity — level 40 is the same level in both
  languages — but progress *is* per-language, so the two ladders are walked independently.

### App layer

`Puzzle` (game/Puzzle.kt) is an immutable data class; every move returns a new puzzle, and every
route into the board (drag, tap, hint) funnels through `withPath`. The whole rule set is therefore
testable with no device, view model or frame of animation.

`RavelApplication` holds the process-wide singletons — `wallet`, `language`, `progress` (daily),
`levels` — because a second instance over the same preference file is a second answer to the same
question. Screens reach them through their `AndroidViewModel`, never by constructing their own.

`RavelApp.kt` is the whole navigation graph: five tabs, each keeping its own back stack across
switches. Screens live under `ui/<area>/` as `XScreen.kt` + `XViewModel.kt`; board rendering is split
across `ui/board/` (`BoardView` decides what a cell means, `BoardPainter` draws it).

Persistence is SharedPreferences plus JSON (`settings/SavedBoards.kt`, single-letter keys because a
part-solved 10x10 is rewritten on every move). `LevelProgress` and `DailyProgress` are interfaces
precisely because storage is the part expected to be replaced by an account later.

### Localization

`LanguageSettings` drives **both** the dictionary and the UI locale. The locale is forced in
`MainActivity.attachBaseContext` — not through appcompat (not a dependency) or `LocaleManager`
(API 33, this ships to 24) — and the activity recreates itself on a switch.

Every user-facing string must be added to both `app/src/main/res/values/strings.xml` and
`values-pl/strings.xml`. `lintDebug` treats a missing translation as an error, and that is the main
reason the lint step is in CI.

## Conventions

- **Comments explain why, at length, including what was tried and rejected.** This is the strongest
  convention in the repo — see `Board.kt`, `PuzzleId.kt`, `RavelApp.kt`. New code is expected to
  match it; a KDoc block that only restates the signature is not in keeping.
- Both modules compile to **Java 11** with `-Xjdk-release=11`, deliberately: `:app` cannot read newer
  class files, and the release flag also hides newer JDK APIs the Android runtime does not ship.
- `:generator` tests use `kotlin.test`; `:app` unit tests use JUnit 4 (`org.junit.Assert`). Test
  names are backticked sentences.
- The Gradle configuration cache is on (`gradle.properties`). New tasks must not read project state
  at execution time — see how `printPuzzle` borrows a classpath through a provider.
- `Wallet.STARTING_BALANCE` and the `Hint` prices are pre-release numbers. Once the app ships,
  raising the starting balance silently grants coins to anyone who has not spent one; a grant has to
  be written instead.

## Commitments made in the privacy policy

`docs/privacy-policy.md` is the published policy (served from GitHub Pages, `main` + `/docs`). It
describes an account layer that **does not exist in the code yet**. Everything below is a promise
already made in writing, so it is a requirement on the feature that implements it, not a preference
to revisit. Changing any of it means changing the policy and the Play Console Data safety form in
the same breath — the two must never disagree, in either direction.

- **Sign-in is Google OAuth through Firebase Auth, and optional.** The game must stay fully playable
  with no account, storing nothing on a server.
- **Leaderboard nickname is chosen by the user and anonymous.** Never default it to the Google
  display name, the email local part, or the profile picture — the policy states none of those are
  ever shown to other players.
- **The leaderboard needs an opt-out toggle in settings.** Ship it with the leaderboard, not after.
- **Ads are rewarded only.** The user opts into each one in exchange for an in-game reward. No
  banners, no interstitials, nothing unsolicited.
- **EEA/UK/CH consent before personalised ads**, through a Google-certified CMP (the UMP SDK), and
  changeable afterwards from settings. Analytics consent lives on the same screen.
- **Account deletion is by email only** (andret2344@gmail.com), handled within 30 days, removing the
  account, its game data and its leaderboard entries. This was a deliberate decision against
  Google Play's expectation of an in-app deletion path; if a review rejects the app over it, an
  in-app delete button is the fix, not a change to the policy's promise about *what* gets deleted.
- **Firestore security rules must scope a signed-in user to their own data.** The policy claims it.
- **Firebase Analytics retention must be set to 14 months** in the console, which is what the policy
  states; the project default is 2 months.

## Outstanding obligation

`generator/DICTIONARY.md` (written in Polish) records the dictionary sources and licences. The Polish
word list and frequency data are **CC BY 4.0 and require attribution**, including a note that the
material was modified. There is no "About" screen yet to carry it. Do not ship a release without it.
