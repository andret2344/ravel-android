# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**This file holds rules; `docs/DECISIONS.md` holds the arguments for them.** A rule here is a
sentence: what to do, or what never to do. The measurement behind it, what was tried and rejected,
and the incident that produced it live in `DECISIONS.md` under the matching section.

Keep it that way. When you learn *why* something is the way it is — a benchmark, a regression, an
option ruled out — it goes in `DECISIONS.md`; only the resulting rule comes back here. When you add
a rule here that needs more than one sentence of justification, that justification is already
`DECISIONS.md` material. This file is loaded into context every session; the other one is read when
somebody is about to change something.

`README.md` is the third file, and it faces outward: what Ravel is, how it is built, run and
tested, and where the other documents are. **It must describe the project as it stands right now
and must be updated in the same commit as any change that makes it wrong** — a module, a Gradle
command, a mode, a documentation file, a dictionary source. **It is not a changelog**: never
"now", "no longer", "recently", "used to"; a reader who has never seen an earlier version is the
only reader it is written for. Git already holds the history. Its badges are part of that: the
Kotlin, Android SDK and JDK ones carry hard-coded numbers, so **moving a version in
`libs.versions.toml`, `app/build.gradle.kts` or the CI workflow means moving the badge too** —
nothing fails when they drift apart.

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

- `buildDictionaryPl` prints a per-length histogram at each rank tier. **Read it before moving any
  cap in `PuzzleSpec.rankFor`**: the binding constraint is always the *shortest* words.
- `printPuzzle` lives in `:app`'s **test** source set (`app/src/test/.../tools/PrintPuzzle.kt`) so a
  developer tool is never packaged into the APK. Gradle's own `--quiet` would silence the board along
  with Gradle — pass `--quiet` inside `--args` instead.
- **"Debug: jump to level"** is a *pair* of source-set files, `app/src/debug/.../debug/LevelJump.kt`
  and `app/src/release/...` with an empty body. **Edit both or neither.** Its strings are hard-coded
  English deliberately. Level *n* means every level below counts as completed, paying nothing.

## Architecture

Rationale for everything in this section: `docs/DECISIONS.md`.

### Generation pipeline (`:generator`)

`Ravel` (Ravel.kt) is the entire public surface; everything else is machinery.

1. `BoardSpec.forDifficulty` turns a `Difficulty` into the dials.
2. Walls are placed as whole orbits under a `WallSymmetry`, so the pattern holds by construction.
3. `Partitioner` splits the free cells into disjoint paths — **shape first, words second**.
4. Words are drawn from `Dictionary.poolTo(maxWordRank)`; the trie holds every loaded word **and its
   reverse**.
5. `Solver` counts tilings; the board is rejected unless exactly one exists.

Rules:

- **How much dictionary is loaded (`Difficulty`) and how obscure a board's words may be
  (`BoardSpec.maxWordRank`) are two separate axes.** Loading less weakens the uniqueness claim; it
  does not make boards easier.
- **The word lengths span a band: none shorter than half the longest** (`randomComposition`). The
  ratio is load-bearing in the donor loop and in `BoardSpec.wordCountFor` — see `DECISIONS.md`.
- **Path shape (`turnRatio`, `directness`, `foldRatio`, all in `Board.kt`) is enforced per word
  during enumeration and against the worst word**, never on the finished board, never as an average.
- **Generation blocks and must be called inside `runInterruptible`** so cancelling its coroutine
  stops the search - a screen's own build when the screen goes, a cache build on eviction or a
  language switch. Design against the tail, not the median — and against a phone, not the desktop table
  (10x10 worst 2.7 s there; an order of magnitude more on a mid-range phone).
- Dictionaries are cached per (language, difficulty); `RavelApplication.onTrimMemory` calls
  `Ravel.forget()`. `DictionaryFootprint` asserts the heap budget — `generator/DICTIONARY.md`
  explains the 35 000 cap.
- Both dictionaries ship **base forms only** (Polish 51 685 lemmas, English 42 554 base forms).

### Puzzle identity (`:app`, `game/`)

This is the part most easily broken by an innocent-looking change.

- `PuzzleId(mode, ordinal)` names a board; the seed is a SplitMix64 hash of it mixed with a per-mode
  salt.
- **`PuzzleMode` salts must never change.** A salt is part of a board's identity.
- **There are two modes, `DAILY` and `ENDLESS`, and `PuzzleSpec`'s `when`s over them must stay
  exhaustive.** A third mode should be a compile error until somebody chooses its size range and its
  rank cap.
- **`PuzzleSpec.of(id)` derives size, difficulty and rank cap from the id alone. Never accept them
  from a call site** — a caller-chosen size means the id no longer names one board.
- **Two ramps, two tables, and they must not be collapsed onto shared tiers**: `rampFor` /
  `SIZE_TIERS` (size, over by level 250) and `rankFor` / `RANK_TIERS` (vocabulary, runs to 2500).
- **The size curve introduces each width twice** — new ceiling with floor 6, then the next tier drops
  the floor to 5. Introduce, then dilute; never a staircase.
- **The vocabulary curve cannot be extended past 2500 by raising the cap.** Harder late levels need a
  second axis.
- **The daily's two ceilings are flat, not ramps**: `DAILY_MAX_SIZE` (8), `DAILY_MAX_RANK` (8000).
- **Board identity is really *(id, generator version)*** — except for a board already on the shelf,
  which stays the board the player started across an update. Changing search order, a difficulty
  constant, the length band or the dictionary reissues every other puzzle — a deliberate decision
  every time, never a side effect.
- **`DayResult.wordCount` is a record, not a derivation, and `Int?` means "nobody counted".** Never
  default it to `0`. `complete` takes a `ShareCard`, so a solved day cannot be recorded without one
  entry per word.
- Language is **not** part of a puzzle's identity, but progress *is* per-language.

### Sharing a solved day

- **The result is a picture, not a sentence** — `4🟥 5🟩 9🟨`, one entry per word. **Numbers stay on
  the same line as the squares.**
- **Only lengths may appear.** No grid, no path, no letter — between them they are the answer.
- **`WordMark` describes the board, not the wallet**: `UNAIDED`, `PARTLY_REVEALED`, `REVEALED`.
- **The order is by length, matching `Puzzle.clues`** — that is the entire comparability claim.
  `WordMarkTest` holds the two orderings together.
- **`ShareCard` is one slot, checked against the day asked about (`shareFor`)**, not a field on every
  `DayResult`.
- **The card records language and grid size rather than deriving them.**
- **On disk, marks and lengths are checked for equal size, never zipped.** Marks are `WordMark.code`
  characters, not enum names.
- **The squares must never go into `strings.xml`.** The surrounding layout (`share_text`) is
  translatable; the colours are not.
- Reachable from exactly two places, both via `ShareButton`: the completion dialog and the calendar's
  day card. `review` has none — a gap, not a decision.

### App layer

`Puzzle` (game/Puzzle.kt) is immutable; every move returns a new puzzle and **every route into the
board — drag, tap, hint — funnels through `withPath`**. The whole rule set is testable with no device.

`RavelApplication` holds the process-wide singletons — `wallet`, `language`, `progress` (daily),
`levels`, `puzzles`. **Screens reach them through their `AndroidViewModel`, never by constructing their own.**

`RavelApp.kt` is the whole navigation graph: five tabs, each keeping its own back stack. Screens live
under `ui/<area>/` as `XScreen.kt` + `XViewModel.kt`; board rendering splits across `ui/board/`
(`BoardView` decides what a cell means, `BoardPainter` draws it).

Boards are built ahead in `PuzzleCache` (`game/PuzzleCache.kt`, held by `RavelApplication`):

- **The level screen builds through the cache (`obtain`), never on its own scope**, so leaving the
  screen stops the waiting, not the search. After `SLOW_AFTER_MILLIS` (30 s) it says so and offers
  the daily - never "you can leave the app": the build only runs while the app is in front.
- **The daily screen still builds its misses itself** (`take`, then `Puzzles.generate`): its misses
  are reviews, which must not take today's slot.
- **A build that ended in null is started again on the next request**, not answered from memory: a
  frozen process spends its wall-clock deadline asleep.
- **Never order a prefetch while a screen is still waiting on its own search** — level *n + 1* goes
  out once level *n* is on screen; today's daily goes out from the menu, where nothing is waiting.
- **Starting level *n* orders *n + 1* and *n + 2* (`LOOKAHEAD`).** Background work runs only while
  the app is on screen - Android freezes cached processes - so the lookahead is counted in levels
  of foreground play, not in wall-clock time. **Built one at a time per mode, nearest first.**
- **Slots are per mode (`capacityOf`, exhaustive): `1 + LOOKAHEAD` endless, one daily.** Never a
  shared pool.
- **Never cancel the cache from `onTrimMemory`.**
- **Every built board goes on the shelf (`StoredBoards`, `ravel-boards`) until solved**: prefetches
  on completion, a screen's own build through `keep`, removal through `solved`. A daily *review*
  is never kept — it would take today's one slot.
- **The `RavelGen` log line in `Puzzles.generate` is temporary**: remove it once the device numbers
  are in `DECISIONS.md`.

Persistence is SharedPreferences plus JSON (`settings/SavedBoards.kt`, single-letter keys).
`LevelProgress` and `DailyProgress` are interfaces because storage is expected to become an account.

- **`LevelProgress` has no method for setting the level and must not gain one.** The debug hatch is
  `StoredLevelProgress.jumpTo`, on the concrete store, reachable only from `src/debug`.
- **Every screen that lays out differently on its side asks `AdaptiveScreen { wide -> }`
  (`ui/Adaptive.kt`), never the device.** Material's window size classes are deliberately unused.
- **`BoardLayout` is shared by both board screens.** Do not re-copy it.
- **The board's half is a ceiling, `weight(5f)` against `4f`, not a share.**
- **The board must never scroll**, in either arrangement. Every other overflowing screen may.
- **No `android:configChanges` in the manifest**, deliberately — it would strip the forced locale.
- **`android:screenOrientation="fullUser"`**; leaving the attribute off is not the same thing, and
  `fullSensor` would ignore the device's rotation lock.

### Ads and consent

- **`ads/RewardedAds.kt` is reachable only from `ShopScreen`'s ad card.** No other call site may start
  an ad — that is the structural form of the rewarded-only promise.
- **The reward amount is `Rewards.FOR_WATCHING_AD`, never the value the AdMob console attaches to the
  unit.** `ADS_PER_DAY` (3) watches, counted per *completed* watch by `settings/AdAllowance.kt`.
- **Consent is gathered in `MainActivity.onCreate` before any of the game is drawn**
  (`ads/AdConsent.kt`; `LoadingScreen` until `AdConsent.settled`, capped at `SETTLE_TIMEOUT`).
- **The AdMob ids in `res/values/ads.xml` are live.** Any device testing ads must have its hash in
  `ad_test_devices` in the same file — and that array is deliberately not debug-only.
- `play-services-ads` merges in the `AD_ID` permission on its own; the Data safety form must say so.

### Localization

`LanguageSettings` drives **both** the dictionary and the UI locale, forced in
`MainActivity.attachBaseContext` — not appcompat (not a dependency), not `LocaleManager` (API 33,
this ships to 24). The activity recreates itself on a switch.

**Every user-facing string goes into both `app/src/main/res/values/strings.xml` and
`values-pl/strings.xml`.** `lintDebug` treats a missing translation as an error, which is the main
reason the lint step is in CI.

Three deliberate exceptions, each argued where it lives: the debug jump's hard-coded English, the
share card's squares, and the `translatable="false"` licence notices.

## Conventions

- **Comments explain why, at length, including what was tried and rejected.** This is the strongest
  convention in the repo — see `Board.kt`, `PuzzleId.kt`, `RavelApp.kt`. A KDoc block that only
  restates the signature is not in keeping. **Comments are written in English** even though the UI
  and the project's working language are Polish.
- Both modules compile to **Java 11** with `-Xjdk-release=11`: `:app` cannot read newer class files,
  and the release flag also hides newer JDK APIs the Android runtime does not ship.
- `:generator` tests use `kotlin.test`; `:app` unit tests use JUnit 4 (`org.junit.Assert`). Test
  names are backticked sentences.
- The Gradle configuration cache is on (`gradle.properties`). **New tasks must not read project state
  at execution time** — see how `printPuzzle` borrows a classpath through a provider.

### The economy

**Four numbers, tuned as a set**: `Rewards.FOR_COMPLETING` (25), `Streak.DAY` (25, `SEVENTH` =
`5 * DAY`), `Rewards.FOR_WATCHING_AD` (100), against the `Hint` prices (40 / 100).

- **Never move one alone.** Three ratios are the design, guarded by `StreakTest` (as a band) and
  `PuzzleTest` (as a pair of inequalities).
- **`Wallet.STARTING_BALANCE` cannot be raised after release** — that silently grants coins. Write a
  grant instead.
- **If the game turns out winnable through the shop, drop `ADS_PER_DAY` to 2 rather than putting the
  hint prices back.**

### Showing a coin

- **Every visible coin figure goes through `CoinAmount` (`ui/Coins.kt`), and there is one coin
  drawable, `res/drawable/ic_coin.xml`.** No second glyph, no `Text("$balance")` beside a number that
  happens to be coins.
- **`coins_balance` is an accessibility string only.** Never put it back on screen beside a coin.

### Shipping a release

- **Release builds are shrunk (`optimization { enable = true }`) and R8 breaks things no test can
  see.** Keep rules live in `app/proguard-rules.pro`, deliberately short.
- **Installing a release build and opening it is a release-checklist step, not an optional one** —
  and the step to repeat whenever the ads SDK moves.
- **Backup rules are written out by name in both `res/xml/data_extraction_rules.xml` (API 31+) and
  `res/xml/backup_rules.xml` (below). Both are live at `minSdk 24` and must be edited together.**
  Every preference file is included, `ravel-ads` among them.
- **The commit that adds in-app purchases has to revisit the backup rules.**

## Commitments made in the privacy policy

`docs/privacy-policy.md` is the published policy (GitHub Pages, `main` + `/docs`). It describes an
account layer that **does not exist in the code yet**. Everything below is a promise already made in
writing — a requirement on the feature that implements it, not a preference to revisit. Changing any
of it means changing the policy and the Play Console Data safety form in the same breath.

Not yet built:

- **Sign-in is Google OAuth through Firebase Auth, and optional.** The game stays fully playable with
  no account, storing nothing on a server.
- **Leaderboard nickname is chosen by the user and anonymous.** Never default it to the Google
  display name, the email local part, or the profile picture.
- **The leaderboard needs an opt-out toggle in settings.** Ship it with the leaderboard, not after.
- **Firestore security rules must scope a signed-in user to their own data.**
- **Firebase Analytics retention must be set to 14 months** in the console; the project default is 2.
- **Account deletion is by email only** (andret2344@gmail.com), within 30 days, removing the account,
  its game data and its leaderboard entries. If a Play review rejects the app over the missing in-app
  path, an in-app delete button is the fix — not a change to *what* gets deleted.

Built — see "Ads and consent" above:

- **Ads are rewarded only.** No banners, no interstitials, nothing unsolicited.
- **EEA/UK/CH consent before personalised ads**, through a Google-certified CMP (UMP SDK), changeable
  afterwards from settings. Analytics consent ships with analytics, which is not in the project yet.

## Attribution

`generator/DICTIONARY.md` records the dictionary sources and licences. Three require a notice, and
the app carries all of them: the settings tab lists every source under "Word sources"
(`ui/about/Licences.kt`), and each name opens `LicenceScreen`.

- **SJP.PL word list and KWJP100 frequency data** — CC BY 4.0, attribution **plus** a statement that
  the material was modified. Both notices carry that sentence; deleting it breaks the licence.
- **PoliMorf** (Polish lemmatisation) — BSD 2-clause, copyright notice retained on redistribution.
- **WordNet 3.0** (English lemmatisation) — Princeton copyright notice retained.

ENABLE and OANC need nothing and are listed anyway.

The notices and the source names are `translatable="false"` on purpose: a required notice is required
in its own wording, and these are proper names.

The repository's own code is under **PolyForm Noncommercial 1.0.0** (`LICENSE.md`), which covers the
code and nothing else. **Every third-party data file in the tree must be named in that file's "What
it does not cover" list** — a corpus that ships without being carved out is being relicensed by
accident.

**Adding a dictionary source means adding an entry here in the same commit** — the licence obligation
starts when the data ships, not when somebody remembers.

The two licences that ask for their text to travel with the work are bundled verbatim in `res/raw/`.
PoliMorf's was reconstructed from the standard BSD 2-clause template plus the copyright line on
`morfeusz.sgjp.pl` — that host has a **revoked** TLS certificate, so it cannot be re-fetched to check
without working around the revocation. CC BY 4.0 is linked rather than bundled, as that licence asks.

## Deliberate omissions

Absences that are decisions. **Do not report these as gaps or defects.** Reasoning in
`docs/DECISIONS.md`.

- **No sound anywhere.** Haptics only. Do not add audio, or an audio setting, without being asked.
- **No screen-reader support on the board.** `ui/board/BoardGrid.kt` is a bare `Canvas` by design.
  Accessibility everywhere *else* in the app still applies in full and new screens are expected to
  carry semantics.

## Known gaps

Real, unresolved, and listed so they are neither rediscovered as news nor defended as intentional:
no tutorial; no skip or give-up on a stuck board; no share button on `review`; no instrumented or
Compose UI tests at all; no in-app theme override; no progress reset.
