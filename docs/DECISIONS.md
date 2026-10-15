# Decisions

Why the rules in `CLAUDE.md` are the rules: what was tried, what it cost, what the measurements
said. `CLAUDE.md` states the rule and links here; this file carries the argument.

Nothing here is optional reading before *changing* one of those rules — a rule whose reasoning has
been read and answered can be changed, one that merely looks arbitrary cannot.

**Adding to this file:** when a decision gets revisited, append to the existing entry rather than
replacing it. The rejected option is the valuable half.

---

## Generation

### The length band: no word shorter than half the longest

`randomComposition`. The floor used to be one letter under the mean, which meant a 10x10 could only
produce lengths 9..12 and often came out as six twelve-letter words — and *the list of lengths is the
only clue this game gives*, so a list repeating one number is not a clue.

Those are also the thinnest bands of the dictionary: within rank 4000 Polish has 574 five-letter
lemmas against 93 twelve-letter ones, so the biggest boards drew from the narrowest pool while the
most recognisable vocabulary never appeared on them.

The old flat list was a second layer of defence against short words coming out as squat blocks, and
the per-word fold gate in `PathShape.accepts` is the layer that actually does that job, at every
length — so the flat list was not paying for itself.

**The ratio is load-bearing in two places.** The donor loop that pays for the required long word must
stop at the floor, not at `MIN_WORD_LENGTH`, or an awkward shape like 10x4 breaks the band by the one
route the wobble never touches. And `BoardSpec.wordCountFor` must keep enough words that the floor is
*reachable*, or the band is a promise the arithmetic cannot keep — measured: with the old `fewest`, a
10x10 came out as seven twelve-letter words with the band wide open and unusable.

### Shape first, words second

`Partitioner` splits the free cells into disjoint paths of the chosen lengths before any word is
drawn. Choosing words first means backtracking over a dictionary instead of over a handful of shapes,
and does not scale.

### Pool and trie are different sets

Words are drawn from `Dictionary.poolTo(maxWordRank)`, while the trie holds every loaded word **and
its reverse** (a path is traceable from either end).

Narrowing the pool is not the same as loading less: every word the dictionary holds is one `Solver`
must rule out before calling a board unique, so a smaller load weakens the uniqueness claim rather
than easing the board. That is why "how much is loaded" (`Difficulty`) and "how obscure a board's own
words may be" (`BoardSpec.maxWordRank`) are two axes and not one.

### Path shape is checked per word, not on the finished board

`turnRatio`, `directness`, `foldRatio`, all in `Board.kt`. Enforced during enumeration and against
the worst word rather than the average. The comments in `Board.kt` and `Language.kt` explain what
averaging and post-hoc checking each produced — both were tried.

### Generation cost, measured

| board | median | worst of 30 |
|---|---|---|
| 10x10 | 0.22 s | 2.7 s |
| 8x8 | 0.08 s | 0.45 s |

The median came down from 0.8 s when the length band opened: more, shorter paths partition more
easily than a full set of twelve-letter ones. **The tail did not move** — it is the uniqueness search,
not the partition, and it is still the number to design against.

**Revisited: the tail is the partition, and it was the layout budget.** `:app:sweepLevels` over
levels 1-300 put 99% of every slow board in layout, with uniqueness at about a millisecond. Each
failed layout ran `SearchBudget.LAYOUT_NODES` (500 000) to exhaustion, ~175 ms on a 10x10, and the
slow levels were simply the ones that drew many failures. Desktop JVM, one run per level, ms:

| `LAYOUT_NODES` | 10x10 median | 10x10 p90 | 10x10 max | 9x9 max | 8x8 max | over 2 s |
|---|---|---|---|---|---|---|
| 500 000 | 703 | 3018 | 5791 | 1164 | 713 | 6 of 300 |
| 20 000 | 240 | 876 | 1383 | 381 | 155 | 0 of 300 |

On the 46 boards of 9x9 and up: 100 000 left a 3.2 s worst case, 30 000 1.7 s, 20 000 0.8 s and
10 000 0.8 s, with English following Polish. 10 000 was rejected on board shape, not speed: an
exhausted budget counts as an unshapeable layout, and 200 of those (`LAYOUTS_BEFORE_EASING`) loosen
the path-shape ceilings. The worst level reached 164 at 10 000 and 86 at 20 000.

This reissued every board in both modes (see "What reissuing has already cost"). The phone numbers
quoted in `PuzzleCache` and `Puzzles` predate it.

Dictionaries cost 23–33 MB at the shipped `HARD`, which is why they are cached per (language,
difficulty) and why `RavelApplication.onTrimMemory` calls `Ravel.forget()`. `Difficulty.HARD.maxRank`
is 35 000 for that reason as much as for what lies past it — `DictionaryFootprint` asserts the
budget, `generator/DICTIONARY.md` explains the content argument.

**The table above is a desktop JVM, and a phone is not one.** A first run on a Redmi Note 10 5G put
the same search an order of magnitude slower, with some 10x10 levels below 300 not finishing inside
`Puzzles.DEFAULT_TIMEOUT_MILLIS` at all — deterministically, since the seed fixes the search. The
device table is **not recorded yet**: it is to be measured with the `RavelGen` log line in
`Puzzles.generate`, which stays in the code until it is, and then goes. Until then, treat the
figures in `PuzzleCache` and `Puzzles` KDoc as a single preliminary run, not as the measurement.

### Why boards are built before they are asked for

`PuzzleCache` (on `RavelApplication`'s scope) builds the next board while the player is busy with
the current one: `LevelViewModel` orders level *n + 1* once level *n* is on screen, and
`DailyMenuViewModel` orders today's board while the calendar is showing. Both board screens ask the
cache first and fall through to `Puzzles.generate` on a miss.

- **It changes nothing about what a board is.** The other ways to make the slow levels cheaper —
  a gentler size ramp, looser shape dials, a different search order — all reissue puzzles. This
  fixes the wait without touching identity.
- **`take` never generates.** A miss answers null and the screen generates on its own scope, so
  leaving a screen still cancels the only search a player is waiting on. A screen that stops
  waiting for a prefetch does not cancel the prefetch: its result is kept, which is the point.
  *Revisited:* see "Why the level screen builds through the cache" below - this now holds for the
  daily screen only.
- **A prefetch is ordered only once the current board is on screen**, so the search the player is
  waiting for never shares the phone with the one they are not.
- **Starting level *n* orders *n + 1* and *n + 2*.** The first version ordered only *n + 1*, on the
  assumption that a prefetch keeps running when the player leaves the app. It does not: Android
  freezes a cached process within seconds of it leaving the foreground, so background work really
  happens only while the game is on screen. A fast player who finishes level *n* and closes the app
  leaves the slow *n + 1* half-built, and the process is later killed with it. Two levels of
  foreground play is the budget instead; a build lost to a kill restarts behind the level the
  player returns to, which is on the shelf and still has to be finished.
- **The two are built one after the other, not side by side** (a fair `Mutex` per mode). Side by
  side finishes both later on a phone that is already warm from the player's own board, and *n + 1*
  is the one needed first. Per mode, so today's daily never queues behind two slow levels.
- **Slots are counted per mode: `1 + LOOKAHEAD` (3) for endless, one for the daily.** The board
  being played and the two after it; fewer and the one being played is evicted by its own
  successors on a back-and-re-enter. A shared pool would let level prefetches push out today's
  board and the reverse.
- **A different language clears everything**, in-flight searches included.
- **A prefetch gets `PREFETCH_TIMEOUT_MILLIS` (3 min), not the player's 20 s.** Nobody is watching,
  so the question is "when is this device hopeless", not "how long will they sit here". A timed-out
  prefetch is cached as its null; retrying would be the same deterministic search again.
- **The daily menu orders today's board even for a player who only came to look at the streak.**
  This reverses the menu's earlier "starts no generation" rule, which assumed a board was a second
  or two. Not ordered for a day already solved — that board is only ever reviewed.
- **No `forget()`, and the cache is not dropped in `onTrimMemory`.** `TRIM_MEMORY_UI_HIDDEN` fires
  whenever the player leaves the app, often with a build still running that the freezer only
  pauses; finished boards are a few kilobytes.
- **`Puzzles.preload` builds the dictionary at process start** and again on every language switch,
  so the first board does not pay for the trie in front of the player.

**Built boards are written down, not only held in memory** (`StoredBoards`, preference file
`ravel-boards`, backed up). Memory alone loses everything when Android kills the process in the
background, and the player comes back to the level they were on with nothing held — for the slow
levels, a deterministic failure on every retry. A board goes on the shelf when a prefetch finishes
or a screen builds it, and leaves when it is solved; the shelf keeps the same per-mode count as
memory, per language, evicting the lowest ordinal. Stored whole (letters and solution), because
the seed is the thing that is expensive to turn back into a board.

A side effect, accepted: a shelved board survives a generator change. The player finishes the
board they started, against which their saved marks were made, instead of having the marks dropped
by `restored` onto a reissued grid. Boards not yet built still reissue.

What the shelf does not cover: a prefetch killed before it finished, and a level reached with
nothing built - a restore from a backup older than the board, a phone slower than the prefetch
deadline. On the 20-second screen deadline both failed on every retry; the next section is the fix.

### Why the level screen builds through the cache

The level screen asks `PuzzleCache.obtain`, which builds a miss on the cache's own scope with
`PREFETCH_TIMEOUT_MILLIS`, instead of `Puzzles.generate` with the 20-second deadline. Leaving the
screen stops the waiting, not the search.

- **Background from the start, not a hand-off after a timeout.** Handing over after 30 s was the
  other option and was rejected: the seed makes the search deterministic, so the background build
  would start from zero and the first 30 s would be thrown away - about 98 s instead of 68 s for
  level 238.
- **After 30 s (`SLOW_AFTER_MILLIS`) the spinner says the board is slow and offers the daily.** Not
  "you can leave and come back": Android freezes a backgrounded app within seconds, so the build only
  runs while the game is in front. The offer is a concrete place inside the game where the wait
  is not wasted.
- **A build that ended in null is retried on the next request.** The search is deterministic, but
  its deadline is wall-clock time, and a process frozen mid-build spends that deadline asleep.
- **The daily screen keeps the old path.** Its misses are days opened for review, which must not
  take today's one slot on the shelf, and its boards (8x8 at most) fit the deadline.

This hides the cost rather than removing it: the first board of a fresh install and any board
reached with nothing prefetched still pay in full. Removing it is the layout search, and changing
that reissues puzzles.

### Base forms only, by opposite routes

- **Polish** (51 685, from PoliMorf) keeps the lemmas. Three quarters of the old 247 012-entry list
  were oblique cases, which is not what a player recognises when the only clue is a length.
- **English** (42 554, from WordNet) folds regular inflections into a base *that is itself in the
  dictionary*. It cannot ask "is this a lemma?" — WordNet indexes only nouns, verbs, adjectives and
  adverbs, so that question deletes `the`, `and`, `for` and `with`. Asking about inflection instead
  lets function words through with nobody maintaining a list of them.

Both builds sum a folded form's frequency into its base; ranking a survivor by its own form's
frequency alone is what buries `robić` under `robi`.

---

## Puzzle identity

### Why `PuzzleMode` has exactly two entries

A `SPECIAL` entry existed, had no screen and no call site outside the tests, and its only live effect
was keeping an `else` branch open — a branch that handed anything unnamed the full size range and no
rank cap, which is the end-game spec the daily had to be rescued from.

Removing it makes `PuzzleSpec`'s `when`s exhaustive, so a third mode is a compile error until
somebody chooses its size range and its rank cap. Adding the mode back is one line; keeping the
`when`s exhaustive when you do is the point.

### Why size and vocabulary are two ramps and not one

`rampFor` (size, `SIZE_TIERS`) is measured in hundreds of levels and is over by 250. `rankFor`
(vocabulary, `RANK_TIERS`) is measured in thousands and runs to 2500.

Size stops because it runs out of room — `MAX_SIZE` is the last width the generator builds in a time
a player will wait. Vocabulary is not a skill playing confers, so it never stops mattering.

Three boundaries (15, 100, 250) happen to coincide and are left alone on purpose — see `rankFor`.

### Why the size curve introduces each width twice

A new ceiling always arrives in a two-value tier with floor 6, so the player meets it immediately;
the next tier drops the floor to 5, so it goes back to being occasional. Introduce, then dilute.

One tier per width with the floor rising alongside the ceiling is a staircase, and a run of boards
each bigger than the last reads as a difficulty slider — which size is not: a 9x9 of common words is
easier than a 6x6 of rare ones.

### Why the vocabulary curve stops at 2500 and cannot be extended

The dictionary ends at 35 000 for heap reasons and its last bands are near-indistinguishable. Making
later levels harder needs a second axis, not a deeper cap.

The path-shape dials are the measured candidate, and they trade against board size rather than
composing with it: `minTurnRatio` 0.62 with `maxDirectness` 0.68 costs about 470 ms on a 7x7, and the
same band blows the deadline on a 9x9. See `PuzzleSpec.rankFor`.

### Why the daily's ceilings are flat, and why 8 / 8000

A date is not a position on a curve, so there is nothing to ramp along.

The daily used to take the *unnarrowed* range and ended up the hardest board in the game: 5..10 meant
a 10x10 one day in six, and no rank cap meant all 35 000 words on day one, where `ENDLESS` does not
let go of 30 000 until level 2500. The mode built on a streak was running an end-game spec. A board
the player closes is not a hard daily — it breaks the streak, and the streak is what makes them open
the app.

**8 and not 7.** 8 is the largest grid that reads at a glance on a phone, and it is where the endless
ceiling sits through levels 51–125 — so a daily is a mid-game board, where 7 would put it at level 50.
Note the endless ramp now *passes* 8: from level 126 it reaches 9 and eventually 10, so the daily
stops being the bigger board around there. That is intended — a habit is not an end-game — but "the
daily is a break from the levels" only holds before it.

**8000** sits just under `kredens` at rank 10 000.

Measured when the daily was narrowed: fourteen consecutive dailies built in 194–615 ms, the 8x8 among
them, against 0.8 s median for the 10x10 it replaced. Re-measured after the length band opened: 8x8
is 77 ms median, 450 ms worst over thirty.

### What reissuing has already cost

Board identity is really *(id, generator version)*.

- **The length band** reissued every puzzle in the game, in **every mode at once** — wider than the
  size-ramp change.
- **Narrowing the daily's spec** reissued every daily ever played. `DailyViewModel.restored` drops
  saved marks that no longer fit, so nothing crashes, but reviewing an old day now shows a different
  board than was solved.
- **`LAYOUT_NODES` 500 000 to 20 000** reissued every puzzle in both modes, before any release
  (`versionName` 0.1.0), which is the only reason it cost nothing. See "Generation cost, measured".

### Why `DayResult.wordCount` is a record, not a derivation, and is `Int?`

A count written down at the moment of solving stays true across a generator change; a count derived
later describes today's board wearing an old date, which is not what the player found.

Null is "nobody counted" — a day still running, or one banked before boards counted their words — and
`null` is load-bearing rather than tidy. The field used to default to `0`, which is a number a solved
day can never legitimately hold, so it joined `totalWordsFound` and shortened a lifetime total by
exactly as much as it did not know, undetectably.

It is no longer a parameter anywhere: `complete` takes a `ShareCard` and the count is
`card.words.size`, so the only way to record a solved day is to hand over one entry per word. Null
can therefore only ever mean unknown.

---

## Sharing a solved day

### Why sharing exists before the leaderboard

It is the one mechanism by which a player can bring another one, and it is a cheap test of whether
anybody wants to show this game to anyone. If nobody taps it, that is the answer to whether a
leaderboard would have had entries — bought before paying for nickname moderation, ranking rules and
an account layer.

### Why the numbers sit on the same line as the squares

A digit and an emoji are different widths, so a row of lengths under a row of squares lines up
nowhere, and a row that has slid one place along attaches every mark to the wrong word.

### Why only lengths may appear

What may appear is bounded by what every player of that day already sees, which is the list of
lengths and nothing else. The grid, any path and any letter are between them the answer.

### Why `WordMark` is named for the board and not the wallet

`UNAIDED`, `PARTLY_REVEALED`, `REVEALED`. The tempting naming — found / bought a letter / bought the
word — is a claim the data cannot support: `Puzzle.hints` counts revealed cells per word and does not
record which hint revealed them, `Puzzle.bought` knows the two kinds apart but only as totals for the
whole board.

It also makes the common awkward case honest rather than false. Hints target the word the player is
furthest along on, so buying a letter one cell from the end is *ordinary*, and it reveals the whole
word. "Fully revealed" is true of that; "bought the word" is not.

### Why the order is by length

Position *n* is the same word for both people comparing — that is the entire comparability claim.
Discovery order is wrong twice: it differs per player, and it is not recorded anywhere
(`Puzzle.found` is a set). `WordMarkTest` holds the two orderings together, because nothing else
would notice one of them drifting.

### Why `ShareCard` is one slot and not a field on every `DayResult`

A day's cost is history and is kept forever; how a solve went is posted on the day and never looked
at again. Per-word data on every `DayResult` would grow the one unbounded map in the file to answer a
question nobody asks about last March.

The slot is checked against the day being asked about (`shareFor`), exactly like `board`, so a stale
card ages out by never matching rather than by anything waking up at midnight.

### Why the card records language and size rather than deriving them

**Language**, because the same date in two dictionaries is two unrelated grids and a card shared
after a language switch would otherwise mislabel itself — and because two players comparing times
across dictionaries are comparing nothing, which is what the `PL`/`EN` marker in the text is for.

**Size**, on the `DayResult.wordCount` argument: `PuzzleSpec` is arithmetic over the id, and moving a
constant in it would silently restate what an already-solved day looked like.

### Why the marks are checked for equal size rather than zipped

`zip` stops at the shorter list, so a truncated write would come back as a shorter board that looks
entirely valid, with every length after the gap attached to the wrong mark. Codes and not enum names:
a rename must not stop yesterday's card parsing.

### Why the squares are not in `strings.xml`

Colour is not language, and two locales disagreeing about which one means "found it" would break the
only thing the row exists to do. The surrounding layout *is* translatable (`share_text` with
placeholders) so a translator can move the separators.

---

## App layer

### Why the window is measured rather than the device asked

`ui/Adaptive.kt` — `AdaptiveScreen { wide -> }`, true when `maxWidth > maxHeight`. A tablet held
upright, a phone in split screen and a folded foldable are all answered without being named.

Material's three window size classes are deliberately not used: nothing here varies on *how much*
room there is, only on which of the two dimensions is scarce.

### What each screen does when wide

All of it is landscape-only; every portrait layout is the one that was there before, except where a
bullet says otherwise.

- **Board** (`ui/game/BoardLayout.kt`) — grid left, everything else in a column right.
- **Tabs** (`RavelApp`) — a `NavigationRail` down the side instead of a bottom bar, because height is
  what a wide window has least of and the board is square.
- **Level menu** — the play card takes a full-height column, and the road becomes three rows of three
  beside it. The block is sized as *one square that the nine rungs divide*, the same trick the board
  uses — sized off the width its bottom row is cut, sized off the height alone it is a narrow band
  down the middle of a wide column. **Both roads hold an odd number of levels** (seven across in
  tall, nine as a block in wide) so that the level on offer has a middle square to sit in, with as
  many levels behind it as ahead; the strip was eight and put the player one place left of centre.
  That is the one thing the two counts have to agree on, and `firstOf` takes half the size rather
  than a constant of its own so they cannot drift apart.
- **Daily menu** — heading, run and the three tiles down the left, the week's ladder filling the
  right, and **no scroll at all**, which was the point: stacked on its side the tab came to nearly
  twice the height available, putting the play tile — the reason the tab exists — behind a scroll.
  `StreakCard` takes a `fillHeight` flag for this and only this; its rungs then size themselves off
  the height they are given rather than asking for one, so the seventh day cannot fall off the card.
- **Stats** — two columns of *groups*, never one row of six tiles. The grouping is the screen's whole
  structure (three questions, only the last counting both modes) and six tiles across reads as one
  undivided row of numbers under headings nobody can attach them to.
- **Shop** — the two offers side by side, top-aligned, still capped at `TILE_WIDTH`.
- **Options** — switches left, the word sources right, so a player looking for the language does not
  pass a Princeton disclaimer on the way. Still scrolls in both arrangements: the sources card grows
  by a row every time a dictionary is added, which is a height set by a licence obligation rather
  than by a layout.

### Why `BoardLayout` is shared by both board screens

The layout was the last thing still copied into `DailyScreen` and `LevelScreen`, and it had already
drifted — the level learned to hang its heading off the grid and the daily never did. Unifying them
moved the daily's date up against its board in portrait too.

### Why the board's half is `weight(5f)` against `4f`

It is a ceiling, not a share. The square takes whichever is smaller of the height and the width it is
offered, so past the point where the height binds — which is every phone on its side — extra width is
waste on the left and is exactly what the column on the right is short of.

Half each cost the board a fifth of its size *and* pushed a hint button off the bottom of the screen.

### Why the level menu and the shop gained a scroll

Screens that were columns which could not overflow a portrait phone can overflow a landscape one. The
board is the one thing that must never scroll, in either arrangement: a grid that moves under a
finger being dragged across it is a grid that loses the drag.

### Why there is no `android:configChanges`

The player's locale is forced by wrapping the base context in `MainActivity.attachBaseContext`, and a
configuration change the activity swallows is one the system then applies to those resources — taking
the forced locale with it. Recreation re-runs `attachBaseContext`, which is the whole mechanism; the
view models outlive it, so a board being generated survives the turn.

### Why `screenOrientation="fullUser"` and not the default or `fullSensor`

The default is `unspecified`, which most phones resolve to the three orientations that are not
upside-down portrait — and a phone on a charger with the cable at the top is held that way.

`fullSensor` ignores the device's rotation lock, so a player who turned auto-rotate off would still
have the board spin when they lean over.

### Why the debug level jump is a pair of source-set files

`app/src/debug/.../debug/LevelJump.kt` holds the real button, `app/src/release/...` an empty body, so
the release APK does not contain the code rather than containing code R8 is trusted to drop.

Verified: the release dex holds neither the class, the string, nor `StoredLevelProgress.jumpTo`.
Editing one file without the other breaks `assembleRelease` only, which is the pair working.

Its strings are hard-coded English on purpose: a debug-only entry in `values/` would be a translation
obligation for a button no player sees.

### Why `LevelProgress` has no setter

The ladder being unskippable is enforced by the type rather than observed by a screen, and every
consumer in the app is typed as the interface. `StoredLevelProgress.jumpTo` is reachable only by a
caller holding the concrete store, and the only such caller is in `src/debug`.

The moment the setter is on the interface, "no skipping ahead" is a convention rather than a fact.

---

## Economy

### The four numbers and the three ratios

`Rewards.FOR_COMPLETING` (25), `Streak.DAY` (25, with `SEVENTH` defined as `5 * DAY`),
`Rewards.FOR_WATCHING_AD` (100), against the `Hint` prices (40 / 100).

Three ratios are the actual design, and all three are written down where they live:

- a week of streak pays roughly 1.5× a week of solving — `StreakTest` guards it as a band,
  deliberately not as two figures;
- an ad pays what four solved boards pay;
- a word costs 2.5 letters, which is what puts the hint break-even at two letters — `PuzzleTest`
  guards that as the pair of inequalities, not as the ratio.

Moving one number alone silently answers a question those comments already answered.

The first three were all multiplied by 2.5 together when solving turned out to earn 10 coins against
300 a day from ads.

### Why the hint prices came down alone

40 / 100 from 100 / 250, and nothing else moved with them — the one time a number here was changed
alone on purpose.

The complaint was grind, not pay: being stuck on a board whose only clue is a length is not
difficulty, and pricing the way out at four solved boards made it last longer rather than making the
game harder.

Two consequences are live and are recorded in `Rewards`: an ad now buys a whole `WORD` instead of a
`LETTER`, and `ADS_PER_DAY` (3) therefore reaches 2.5× as far as it was tuned to. **If the game turns
out to be winnable through the shop, drop `ADS_PER_DAY` to 2 rather than putting the prices back** —
that takes the shortcut from the player abusing it instead of from the player who was stuck.

### Why `STARTING_BALANCE` cannot be raised after release

Raising it silently grants coins to anyone who has not spent one. A grant has to be written instead.

### Why every coin goes through `CoinAmount`

The alternative already happened: the balance over a board was `"◈ $balance"`, the balance on a menu
was the counted noun from `coins_balance`, and a hint price was a number after a middle dot — three
notations for one currency, none of which taught the player the other two.

`CoinAmount` sizes the icon from the text style it is given, so it fits a top bar and a headline
without a call site picking a dimension, and it carries the counted noun as the row's content
description so screen readers still hear "100 coins" where the screen shows a figure and a coin.

`coins_balance` survives as an accessibility string only. Putting it back on screen beside a coin
gives Polish four forms of a noun the icon does not need.

---

## Shipping

### What R8 already broke

`play-services-ads` pins `work-runtime` → `room-runtime 2.2.5`, Room finds its generated database by
`Class.forName`, and full mode renamed it. The app died before its first frame while
`assembleRelease` and the whole unit suite stayed green.

The keep rule and the reasoning are in `app/proguard-rules.pro`, which is deliberately short.
Installing a release build and opening it is therefore a release-checklist step and not an optional
one, and it is the step to repeat whenever the ads SDK moves.

### Why `compose-group-mapping` is pinned in `app/build.gradle.kts`

After the move to AGP 9.4.1 / Kotlin 2.4.21, every release build failed with `Could not find
org.jetbrains.kotlin:compose-group-mapping:2.2.10`. The Compose compiler plugin requests that
artifact at the Kotlin version it believes is running, and under AGP 9's built-in Kotlin that is
AGP's bundled 2.2.10, which was never published. Debug builds do not shrink and never resolve it.

Pinned to `libs.versions.kotlin` rather than switched off with `includeComposeMappingFile = false`,
which also builds but leaves release stack traces through composables unreadable. Rolling AGP back
was the third option and was not wanted. The pin is a workaround: when commenting it out still
lets `:app:assembleRelease` pass, delete it.

### Why `ravel-ads` is in the backup rules

The three-way day comparison in `AdAllowance.remainingFrom` is what makes a restore honest — same day
keeps the watches spent, later day is a fresh allowance, *earlier* day gives nothing, which is what
answers winding the clock back.

**The commit that adds in-app purchases has to revisit this**: a restored balance is a restored
balance, and "spend, then restore" can only really be answered by a server.

### Coins for real money: planned, after accounts

Decided 2026-10-11, not built. Coins will be sold through Google Play Billing, as three consumable
packs (around 500 / 1 500 / 4 000 coins - 5 / 15 / 40 word hints - with the bonus on the larger
ones; prices live in the Play Console, the code knows only product ids).

- **Verified on a server, not on the phone.** Purchases wait for the account layer (Firebase Auth +
  Firestore, see the privacy policy). Checking on the device alone was the faster option and was
  turned down: a patched client grants itself coins, and a refunded purchase keeps them, with
  nothing on the device able to notice either.
- **`ravel-wallet` stays in the backup rules until then.** A consumable cannot be restored from the
  store, so dropping the wallet from backup would make a paying player lose what they bought on
  every new phone. "Spend, then restore an older backup" stays possible until the balance is
  server-owned, and it is the cheater's own game it spoils.
- Open when this is built: whether buying requires signing in (sign-in is optional, and a server
  balance needs an account to hang on), what happens to a device balance on first sign-in, and
  the privacy-policy and Data safety changes purchases bring.
- *Revisited 2026-10-11:* the buyer is identified through **Play Games Services** automatic sign-in,
  handed to Firebase as a `PlayGamesAuthProvider` credential. Chosen over an invisible Firebase
  anonymous account created at the first purchase: an anonymous account belongs to one device and
  is lost with it unless linked to Google first, which is exactly the paying player's coins. Play
  Games follows the player to a new phone with nothing to remember. **Signed in silently at
  launch**, as many games do, with no sign-in button in front of the game.

### Progress reset: one ladder, and no second payout

Decided 2026-10-11, not built. A reset clears the current language's ladder and nothing else. The
coins, the daily history, the streak and the lifetime word and time totals survive it.

The trap it is built around: `Rewards.FOR_COMPLETING` is paid per level, and the first levels are
5x5 boards solved in seconds, so a reset that paid again would be a coin farm with a button. A
per-language best level is kept, and replaying anything at or below it pays nothing. The wallet is
not reset either way - a reset to `STARTING_BALANCE` would refill a spent wallet.

### Sharing from `review`: the last solved day only

Decided 2026-10-11, not built. `review` gets the `ShareButton` for the day `shareFor` still holds a
card for, which is the last day solved. Per-day cards were reconsidered (about 30 bytes a day) and
left out on the argument in "Why `ShareCard` is one slot": nobody posts last March.

### The tutorial is a handmade level 0, and gates Daily and Shop

Decided 2026-10-11, not built. Generated boards cannot teach: a lesson needs to know which word is
laid backwards and which one is left for elimination. So level 0 is a fixed 4x4 per language, kept
as a constant with a test that proves it unique, and it is **not a `PuzzleMode`** - a third mode
would have to choose a size ramp and a rank cap for one board.

Skippable, because a player who already knows the rules should not be held. Pays and costs nothing,
because a tutorial that pays invites skipping back into it, and one that charges for a hint teaches
the economy as a toll. **Daily and Shop stay locked until it is finished or skipped**: the daily is
the one board everybody compares, and it should not be anybody's first contact with the rules.

---

## Ads and consent

### Why consent is gathered before the game is drawn

`setContent` shows `LoadingScreen` until `AdConsent.settled` turns true, so the form is never a
dialog landing on a screen the player had already started reading.

That wait is capped at `SETTLE_TIMEOUT` and the timer is deliberately not cancelled — a word puzzle
that works offline must never have a launch that depends on a network, and past the cap the form
simply arrives on top of the game as it used to.

### Why the reward amount is a local constant

Never the one the AdMob console attaches to the unit — that value is remote config that would retune
a shipped economy with no build and no review.

### Why `ad_test_devices` is not limited to debug builds

The AdMob ids in `res/values/ads.xml` are live (publisher `3410450408196791`), so every request the
app makes is real inventory. An impression from a developer's own phone is real, a tap on it is a
click on your own inventory, and AdMob answers invalid traffic by suspending the account rather than
by discounting the click.

A device whose hash is not in that array is unprotected, and testing a rewarded ad means tapping it —
that is how you find out the reward arrived. Naming a device costs that one device its real ads,
while a list that stopped working in a signed build would put a developer in front of live inventory
with nothing at all.

### Why the analytics toggle is not built

Firebase Analytics is not in the project. The toggle ships with analytics, not before it, since a
switch wired to nothing is a lie told to the player.

---

## Deliberate omissions

Features whose absence is a decision. Do not report these as gaps.

### No sound, anywhere

Decided 2026-08-10. There is no `SoundPool` or `MediaPlayer` in `:app` and that is the intended
state. Feedback to the player is haptic (`settings/HapticSettings.kt`, `ui/board/BoardHaptics.kt`).

Adding audio would mean assets, a settings toggle, and a decision about silent mode — cost without
return for a word puzzle. If it ever ships, the toggle goes beside haptics in
`ui/settings/SettingsScreen.kt`, same row structure.

### No screen-reader support on the board

Decided 2026-08-10. `ui/board/BoardGrid.kt` is a bare `Canvas` with no `semantics`, and the grid has
no per-cell descriptions.

The core interaction is dragging a finger along an orthogonal path; usable TalkBack support needs a
second, parallel input model, not labels. The `Canvas` is separately a performance decision — a 12x12
board is 144 cells.

Accessibility **off** the board still applies in full: `ui/Coins.kt`, `CalendarCard.kt`,
`StreakCard.kt`, `SettingsScreen.kt` and the rest carry semantics, and new screens are expected to.

### No skip, no give-up, no free reveal

Decided 2026-10-11. Being stuck on a board is the game asking the player to think, and a way past it
that costs nothing would answer the question for them. The only exit is hints, paid for in coins.

This does not undo "Why the hint prices came down alone": that was about the *price* of the exit
being a grind, not about there being one. Skipping was also ruled out structurally before this -
`LevelProgress` has no setter, see "Why `LevelProgress` has no setter".

---

## Known gaps that are not decisions

Recorded so they are not rediscovered as news, and not defended as intentional.

- **No tutorial.** The game's only clue is a list of lengths, and nothing on any screen explains that
  paths are orthogonal, traceable from either end, and tile the free cells exactly once.
- **Re-opening a solved day goes to `review`, which has no footer and so no share button.**
- **No instrumented or Compose UI tests** (`app/src/androidTest` does not exist). The navigation
  graph's five back stacks, rotation during generation, process death mid-board, and every one of the
  wide layouts are covered by nothing.
- **No progress reset** short of clearing app data.
