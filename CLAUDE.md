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

`buildDictionaryPl` prints a per-length histogram at each rank tier. Read it before moving any cap
in `PuzzleSpec.rankFor`: the binding constraint is always the *shortest* words, not the longest.

`printPuzzle` lives in `:app`'s **test** source set (`app/src/test/.../tools/PrintPuzzle.kt`) so a
developer tool is never packaged into the APK. Gradle's own `--quiet` would silence the board along
with Gradle — pass `--quiet` inside `--args` instead.

**"Debug: jump to level"** on the level menu sets the ladder outright — level *n* means every level
below it counts as completed, paying nothing (no coins, no words, no time) and discarding the
current level's board and hints. It is a **pair of source-set files**, `app/src/debug/.../debug/
LevelJump.kt` with the real button and `app/src/release/...` with an empty body, so the release APK
does not contain the code rather than containing code R8 is trusted to drop — verified: the release
dex holds neither the class, the string, nor `StoredLevelProgress.jumpTo`. Editing one file without
the other breaks `assembleRelease` only, which is the pair working. Its strings are hard-coded
English on purpose: a debug-only entry in `values/` would be a translation obligation for a button
no player sees. The hatch is `StoredLevelProgress.jumpTo`, deliberately **not** on the
`LevelProgress` interface — see below.

## Architecture

### Generation pipeline (`:generator`)

`Ravel` (Ravel.kt) is the entire public surface; everything else is machinery.

1. `BoardSpec.forDifficulty` turns a `Difficulty` into the dials: the bands on path shape, and how
   much of the frequency-ranked dictionary gets loaded. **How obscure a board's own words may be is
   a separate axis**, `BoardSpec.maxWordRank`, whose curve lives in `PuzzleSpec.rankFor`. Narrowing
   that pool is not the same as loading less: every word the dictionary holds is one `Solver` must
   rule out before calling a board unique, so a smaller load weakens the uniqueness claim rather
   than easing the board.
2. Walls are placed as whole orbits under a `WallSymmetry`, so the pattern holds by construction.
3. `Partitioner` splits the free cells into disjoint paths of the chosen lengths — **shape first,
   words second**. Choosing words first means backtracking over a dictionary instead of over a
   handful of shapes, and does not scale.
   **The lengths span a band: no word shorter than half the longest** (`randomComposition`). The
   floor used to be one letter under the mean, which meant a 10x10 could only produce lengths 9..12
   and often came out as six twelve-letter words — and *the list of lengths is the only clue this
   game gives*, so a list repeating one number is not a clue. Those are also the thinnest bands of
   the dictionary: within rank 4000 Polish has 574 five-letter lemmas against 93 twelve-letter ones,
   so the biggest boards drew from the narrowest pool while the most recognisable vocabulary never
   appeared on them. The old flat list was a second layer of defence against short words coming out
   as squat blocks, and the per-word fold gate in `PathShape.accepts` is the layer that actually
   does that job, at every length. **The ratio is load-bearing in two places** — the donor loop that
   pays for the required long word must stop at the floor, not at `MIN_WORD_LENGTH`, or an awkward
   shape like 10x4 breaks the band by the one route the wobble never touches; and
   `BoardSpec.wordCountFor` must keep enough words that the floor is *reachable*, or the band is a
   promise the arithmetic cannot keep (measured: with the old `fewest`, a 10x10 came out as seven
   twelve-letter words with the band wide open and unusable).
4. Words are drawn from `Dictionary.poolTo(maxWordRank)`, while its trie holds every loaded word
   **and its reverse** (a path is traceable from either end). Pool and trie are deliberately
   different sets — see step 1.
5. `Solver` counts tilings; the board is rejected unless exactly one exists.

Path shape is governed by three independent measures, all in `Board.kt` — `turnRatio` (how much
paths bend), `directness` (how far they travel end to end), `foldRatio` (how much a path runs
alongside itself). They are enforced **per word during enumeration**, not on the finished board, and
against the worst word rather than the average. Both choices are load-bearing: the comments in
`Board.kt` and `Language.kt` explain what averaging and post-hoc checking each produced.

Generation blocks (long tail: 10x10 ≈ 0.22 s median, 2.7 s worst over 30 boards; 8x8 ≈ 0.08 s
median, 0.45 s worst) and is interruptible — call it inside `runInterruptible` so leaving a screen
cancels the search. The median came down from 0.8 s when the length band opened: more, shorter paths
partition more easily than a full set of twelve-letter ones. **The tail did not move** — it is the
uniqueness search, not the partition, and it is still the number to design against. Dictionaries are cached per
(language, difficulty) and cost 23–33 MB at the shipped `HARD`; `RavelApplication.onTrimMemory`
calls `Ravel.forget()`. `Difficulty.HARD.maxRank` is 35 000 for that reason as much as for what
lies past it — `DictionaryFootprint` asserts the budget, `generator/DICTIONARY.md` explains it.

Both dictionaries ship **base forms only**, and the two get there by opposite routes:

- **Polish** (51 685, from PoliMorf) keeps the lemmas. Three quarters of the old 247 012-entry list
  were oblique cases, which is not what a player recognises when the only clue is a length.
- **English** (42 554, from WordNet) folds regular inflections into a base *that is itself in the
  dictionary*. It cannot ask "is this a lemma?" — WordNet indexes only nouns, verbs, adjectives and
  adverbs, so that question deletes `the`, `and`, `for` and `with`. Asking about inflection instead
  lets function words through with nobody maintaining a list of them.

Both builds also sum a folded form's frequency into its base; ranking a survivor by its own form's
frequency alone is what buries `robić` under `robi`.

### Puzzle identity (`:app`, `game/`)

This is the part most easily broken by an innocent-looking change.

- `PuzzleId(mode, ordinal)` names a board. The generator seed is derived from it via a SplitMix64
  hash mixed with a per-mode salt, so `ENDLESS 5` and `SPECIAL 5` are unrelated boards.
- **`PuzzleMode` salts must never change.** A salt is part of a board's identity; editing one
  silently replaces every puzzle that mode has ever produced.
- `PuzzleSpec.of(id)` derives size, difficulty and word-rank cap **from the id alone**. Never accept
  them from a call site — a seed picks one board *for a given size*, so a caller-chosen size means
  the id no longer names one board.
- Two ramps, deliberately different lengths and each driven by its own table: `rampFor`
  (size, `SIZE_TIERS`) is measured in hundreds of levels and is over by 250, `rankFor`
  (vocabulary, `RANK_TIERS`) in thousands and runs to 2500. Size stops because it runs out of room —
  `MAX_SIZE` is the last width the generator builds in a time a player will wait; vocabulary is not
  a skill playing confers, so it never stops mattering. Do not collapse them onto shared tiers.
  Three boundaries (15, 100, 250) happen to coincide and are left alone on purpose — see `rankFor`.
- **The size curve introduces each width twice**: a new ceiling always arrives in a two-value tier
  with floor 6 (so the player meets it immediately), and the next tier drops the floor to 5 (so it
  goes back to being occasional). Introduce, then dilute. One tier per width with the floor rising
  alongside the ceiling is a staircase, and a run of boards each bigger than the last reads as a
  difficulty slider — which size is not: a 9x9 of common words is easier than a 6x6 of rare ones.
- **The vocabulary curve is exhausted at 2500 and cannot be extended by raising the cap** — the
  dictionary ends at 35 000 for heap reasons and its last bands are near-indistinguishable. Making
  later levels harder needs a second axis; the path-shape dials are the measured candidate, and
  they trade against board size rather than composing with it. See `PuzzleSpec.rankFor`.
- **The daily has its own two ceilings and they are flat, not ramps**: `DAILY_MAX_SIZE` (8) and
  `DAILY_MAX_RANK` (8000). A date is not a position on a curve, so there is nothing to ramp along —
  which is exactly why the daily used to take the *unnarrowed* range and end up the hardest board in
  the game: 5..10 meant a 10x10 one day in six, and no rank cap meant all 35 000 words on day one,
  where `ENDLESS` does not let go of 30 000 until level 2500. The mode built on a streak was running
  an end-game spec. A board the player closes is not a hard daily — it breaks the streak, and the
  streak is what makes them open the app. 8 is the largest grid that reads at a glance on a phone and
  is deliberately *not* 7: 8 is where the endless ceiling sits through levels 51–125, so a daily is a
  mid-game board, where 7 would put it at level 50. Note the endless ramp now *passes* 8 — from level
  126 it reaches 9 and eventually 10 — so the daily stops being the bigger board around there; that is
  intended (a habit is not an end-game) but "the daily is a break from the levels" only holds before
  it. 8000 sits just under `kredens` at rank 10 000. Measured when the daily was narrowed: fourteen
  consecutive dailies built in 194–615 ms, the 8x8 among them, against 0.8 s median for the 10x10 it
  replaced. Re-measured after the length band opened: 8x8 is 77 ms median, 450 ms worst over thirty.
  **`SPECIAL` still takes the whole range on both axes** — it is opted into and no streak rides on it.
- Board identity is really *(id, generator version)*. Changing the search order, a difficulty
  constant, the length band, or the dictionary reissues every puzzle in the game — the length band
  did exactly that, in **every mode at once**, which is wider than the size-ramp change below.
  Treat that as a deliberate decision
  each time, never a side effect. Narrowing the daily's spec above did exactly that to every daily
  ever issued: `DailyViewModel.restored` drops saved marks that no longer fit, so nothing crashes,
  but reviewing an old day now shows a different board than was solved, and `DayResult.wordCount`
  for days already banked is stale in `totalWordsFound`.
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

**`LevelProgress` has no method for setting the level, and must not gain one.** The ladder being
unskippable is enforced by the type rather than observed by a screen, and every consumer in the app
is typed as the interface. The debug jump is a public method on `StoredLevelProgress` instead,
reachable only by a caller holding the concrete store — and the only such caller is in `src/debug`.
Keep it that way: the moment the setter is on the interface, "no skipping ahead" is a convention.

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
- **The economy is four numbers and they are tuned as a set**: `Rewards.FOR_COMPLETING` (25),
  `Streak.DAY` (25, with `SEVENTH` defined as `5 * DAY`), and `Rewards.FOR_WATCHING_AD` (100) against
  the `Hint` prices (40 / 100). Three ratios are the actual design and all three are written down
  where they live — a week of streak pays roughly 1.5× a week of solving (`StreakTest` guards it as a
  band, deliberately not as two figures), an ad pays what four solved boards pay, and a word costs
  2.5 letters, which is what puts the hint break-even at two letters (`PuzzleTest` guards that as the
  pair of inequalities, not as the ratio). Moving one number alone silently answers a question those
  comments already answered. The first three were all multiplied by 2.5 together when solving turned
  out to earn 10 coins against 300 a day from ads.
- **The hint prices came down to 40 / 100 from 100 / 250 and nothing else moved with them**, which is
  the one time a number here was changed alone on purpose. The complaint was grind, not pay: being
  stuck on a board whose only clue is a length is not difficulty, and pricing the way out at four
  solved boards made it last longer rather than making the game harder. Two consequences are live and
  are recorded in `Rewards`: an ad now buys a whole `WORD` instead of a `LETTER`, and `ADS_PER_DAY`
  (3) therefore reaches 2.5× as far as it was tuned to. **If the game turns out to be winnable
  through the shop, drop `ADS_PER_DAY` to 2 rather than putting the prices back** — that takes the
  shortcut from the player abusing it instead of from the player who was stuck.
- **Release builds are shrunk (`optimization { enable = true }`) and R8 breaks things no test can
  see.** It already did: `play-services-ads` pins `work-runtime` → `room-runtime 2.2.5`, Room finds
  its generated database by `Class.forName`, and full mode renamed it — the app died before its
  first frame while `assembleRelease` and the whole unit suite stayed green. The keep rule and the
  reasoning are in `app/proguard-rules.pro`, which is deliberately short. **Installing a release
  build and opening it is a release-checklist step, not an optional one**, and it is the step to
  repeat whenever the ads SDK moves.
- **Backup rules are written out by name in both `res/xml/data_extraction_rules.xml` (API 31+) and
  `res/xml/backup_rules.xml` (below).** Both files are live at `minSdk 24` and must be edited
  together. Every preference file is included, `ravel-ads` included: the three-way day comparison in
  `AdAllowance.remainingFrom` is what makes a restore honest — same day keeps the watches spent,
  later day is a fresh allowance, *earlier* day gives nothing, which is what answers winding the
  clock back. **The commit that adds in-app purchases has to revisit this**: a restored balance is a
  restored balance, and "spend, then restore" can only really be answered by a server.
- **Every visible coin figure goes through `CoinAmount` (`ui/Coins.kt`), and there is one coin
  drawable, `res/drawable/ic_coin.xml`.** No exceptions, no second glyph, no `Text("$balance")` next
  to a number that happens to be coins. This is a rule because the alternative already happened: the
  balance over a board was `"◈ $balance"`, the balance on a menu was the counted noun from
  `coins_balance`, and a hint price was a number after a middle dot — three notations for one
  currency, none of which taught the player the other two. `CoinAmount` sizes the icon from the text
  style it is given, so it fits a top bar and a headline without a call site picking a dimension,
  and it carries the counted noun as the row's content description so screen readers still hear
  "100 coins" where the screen shows a figure and a coin.
- The `coins_balance` plural still exists and is now **only** an accessibility string. Do not put it
  back on screen beside a coin: the icon is the noun, and in Polish that noun has four forms the
  icon does not need.

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
  banners, no interstitials, nothing unsolicited. **Built** — `ads/RewardedAds.kt`, reachable only
  from `ShopScreen`'s ad card, which is the structural version of that promise: no other call site
  can start one. Three a day (`Rewards.ADS_PER_DAY`) for 100 coins each (`Rewards.FOR_WATCHING_AD`),
  counted per *completed* watch by `settings/AdAllowance.kt`. The reward amount is a constant here
  and never the one the AdMob console attaches to the unit — that value is remote config that would
  retune a shipped economy with no build and no review.
- **EEA/UK/CH consent before personalised ads**, through a Google-certified CMP (the UMP SDK), and
  changeable afterwards from settings. Analytics consent lives on the same screen. **Built for ads**
  — `ads/AdConsent.kt`, gathered in `MainActivity.onCreate` **before any of the game is drawn**:
  `setContent` shows `LoadingScreen` until `AdConsent.settled` turns true, so the form is never a
  dialog landing on a screen the player had already started reading. That wait is capped at
  `SETTLE_TIMEOUT` and the timer is deliberately not cancelled — a word puzzle that works offline
  must never have a launch that depends on a network, and past the cap the form simply arrives on
  top of the game as it used to. Reopened afterwards from the settings screen's privacy row (shown
  only where the SDK says a form is required). *Analytics consent is not built,
  because Firebase Analytics is not in the project*; the toggle ships with analytics, not before it,
  since a switch wired to nothing is a lie told to the player.
- **The AdMob ids in `res/values/ads.xml` are live** (publisher `3410450408196791`). Every request
  the app makes is real inventory. The consequence is `ad_test_devices` in the same file: an
  impression from a developer's own phone is real, a tap on it is a click on your own inventory, and
  AdMob answers invalid traffic by suspending the account rather than by discounting the click. A
  device whose hash is not in that array is unprotected, and testing a rewarded ad means tapping it
  — that is how you find out the reward arrived. The array is deliberately **not** limited to debug
  builds: naming a device costs that one device its real ads, while a list that stopped working in a
  signed build would put a developer in front of live inventory with nothing at all.
- **`play-services-ads` merges in the `AD_ID` permission on its own.** The app therefore has access
  to the advertising id whatever it does with it directly, which is nothing, and the Data safety
  form has to say so.
- **Account deletion is by email only** (andret2344@gmail.com), handled within 30 days, removing the
  account, its game data and its leaderboard entries. This was a deliberate decision against
  Google Play's expectation of an in-app deletion path; if a review rejects the app over it, an
  in-app delete button is the fix, not a change to the policy's promise about *what* gets deleted.
- **Firestore security rules must scope a signed-in user to their own data.** The policy claims it.
- **Firebase Analytics retention must be set to 14 months** in the console, which is what the policy
  states; the project default is 2 months.

## Attribution

`generator/DICTIONARY.md` records the dictionary sources and licences. Three of them require a
notice, and the app carries all of them: the settings tab lists every source under "Word sources"
(`ui/about/Licences.kt`), and each name opens `LicenceScreen`.

- **SJP.PL word list and KWJP100 frequency data** — CC BY 4.0, attribution **plus** a statement that
  the material was modified. Both notices carry that sentence; deleting it breaks the licence.
- **PoliMorf** (Polish lemmatisation) — BSD 2-clause, copyright notice retained on redistribution.
- **WordNet 3.0** (English lemmatisation) — Princeton copyright notice retained.

ENABLE and OANC need nothing and are listed anyway.

The notices and the source names are `translatable="false"` on purpose: a required notice is
required in its own wording, and these are proper names. **Adding a dictionary source means adding
an entry here in the same commit** — the licence obligation starts when the data ships, not when
somebody remembers.

The two licences that ask for their text to travel with the work are bundled verbatim in
`res/raw/`, and PoliMorf's was reconstructed from the standard BSD 2-clause template plus the
copyright line on `morfeusz.sgjp.pl` — that host has a **revoked** TLS certificate, so it cannot be
re-fetched to check without working around the revocation. CC BY 4.0 is linked rather than bundled,
which is what that licence asks for.
