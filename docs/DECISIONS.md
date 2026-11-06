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

*2026-11-06:* the donor loop is gone. `lengthsFor` enumerates every list that obeys the band and draws
one, so a list outside it is never built; the `wordCountFor` half still stands.

### Distinct word lengths, and the walls that make room for them

Decided 2026-11-06, after measuring 60 solved boards of LinkedIn's Wend — the game Ravel descends
from — against 40 of ours per size (English, rank 4000):

| | Wend | Ravel 5x5 | 6x6 | 7x7 |
|---|---|---|---|---|
| every length different | 57 of 60 | 22% | 17% | 2.5% |

The clue list is a set of keys only when no two lengths are equal: then each length names one word,
a word found is a word identified, and the list reads as a designed staircase (4, 5, 6, 7) rather
than a tally (7, 7, 8, 8).

**Why it needed walls.** With the band kept, distinct lengths only fit a small free area. At the old
8–18% walls a 6x6 could hold four distinct lengths only as something like 6, 7, 8, 11, and an 8x8
had no list at all; at about a third walled the same boards give 4, 6, 7, 8 and 5..10. Wend walls a
similar share. So the band went to 28–40% and `maxWallRun` from 2 to 3 — two kept colliding with the
larger orbit count. Even so the share achieved runs below the band asked for (about 25% on 6x6),
and an occasional broken column of single walls appears; seen on printed boards and accepted.

**Why only up to 7x7.** Above it the band leaves too few distinct values: strict lengths on 8x8 meant
five words with a twelve-letter one on nearly every board (mean 9.2), and 10x10 has no distinct list
at all — 6..12 is seven values for eight or more words. Above 49 cells each length may appear twice.
The threshold is area, not side, because what decides it is the number of cells the words fill.

**Never eased.** When no list fits, the walls are redrawn. A 6x6 that sometimes repeats a length is
the board this replaced.

**Rejected, with the same data:**

- *Lowering the band floor* so short words could join (Wend has a three-letter word on a third of its
  boards and breaks our band on 28%) — kept by decision.
- *Themed word sets* (one letter shared by every word: JOB JUMP EJECT ADJUST) — looked common, measured
  weak: an uncommon letter in ≥75% of the words on 30% of Wend boards against 23% for random words of
  the same lengths. Shared letters would also raise uniqueness rejections, which is the tail.
- *Favouring j/q/x/z* — Wend shows them twice as often as chance (38% vs 20%), but a rare letter is
  an anchor that makes a board easier, not better.

**Costs.** Fewer words: 3.8 on 6x6 (was 4.9), 4.2 on 7x7 (was 5.6), and mean length up by about half
a letter. Reissued every board in both modes — see "What reissuing has already cost".

**Revised the same day: 28–40% was too many walls.** The first board looked at on a device (an 8x8,
level 75) needed no letters to solve: the walls cut the grid into corridors that spelled out every
path. Measured on 30 boards per size, the share of free cells with two free neighbours or fewer —
cells a path can cross only one way — went from 16% to 35% on 8x8 and from 20% to 39% on 6x6, with
about half the walls in the middle of the grid rather than at its edge. (One Wend board measured 55%
such cells, but 86% of its walls on the border: it trims the outline and leaves the middle open.)

Two mistakes in the first version, both fixed:

- **Above 7x7 the walls had no job.** Lengths may repeat there, so nothing needs the free area
  smaller; 8x8 and up went back to 8–18%. The distinct-length table above that called 8x8 at 10%
  walls impossible was for *strict* lengths, which 8x8 does not use.
- **Up to 7x7, 18–28% is enough.** A fifth walled still fits distinct lengths inside the band (6x6:
  5, 7, 8, 9), and the corridor share comes down to 27–31% on 6x6 and 7x7. `maxWallRun` went back
  to two: at this share three bought nothing measurable (6x6 walled 18% against 17%, same corridor
  share) and had cost an occasional broken column of single walls.

After the revision, 30 boards per size, rank 4000:

| | 5x5 | 6x6 | 7x7 | 8x8 | 10x10 |
|---|---|---|---|---|---|
| walls | 17% | 18% | 18–20% | 11% | 11–12% |
| corridor cells | 36% | 30% | 27–29% | 17% | 13–14% |
| words | 3.0 | 4.1–4.2 | 4.9 | 6.6 | 8.8 |
| mean length | 6.9 | 7.1 | 8.0–8.2 | 8.5 | 10.0 |
| worst time | 72–260 ms | 79–131 ms | 0.6–0.9 s | 0.55 s | 2.0–2.8 s |

Still open: on 10x10 the "at most twice" rule draws pairs climbing to the ceiling — 8, 9, 9, 10, 10,
11, 11, 12, 12 is the typical list, so two twelve-letter words on most boards. And the corridor share
on 5x5 (36%) is the highest left; nobody has looked at one on a device yet. If boards still read as
given away, the measured next step is a gate on how many letter-free tilings the walls allow, not
another guess at a ratio.

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

*Re-measured 2026-11-06* after distinct lengths and the wider wall band, 30 boards per size at rank
4000 (the table above is not comparable: it predates the rank cap). Same machine, same seeds:

| board | before: median / worst | after, en | after, pl |
|---|---|---|---|
| 6x6 | 5 ms / 119 ms | 6 ms / 89 ms | 10 ms / 82 ms |
| 7x7 | 16 ms / 203 ms | 29 ms / 258 ms | 16 ms / 338 ms |
| 8x8 | 31–49 ms / 0.84–1.2 s | 50 ms / 0.40 s | 37 ms / 0.36 s |
| 10x10 | 0.77 s / 4.4 s | 0.54 s / 2.2 s | 0.41 s / 3.0 s |

Run-to-run noise on the old 8x8 tail was ±40%, so read only the order of magnitude: nothing got
slower in a way a player would see.

Dictionaries cost 23–33 MB at the shipped `HARD`, which is why they are cached per (language,
difficulty) and why `RavelApplication.onTrimMemory` calls `Ravel.forget()`. `Difficulty.HARD.maxRank`
is 35 000 for that reason as much as for what lies past it — `DictionaryFootprint` asserts the
budget, `generator/DICTIONARY.md` explains the content argument.

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
- **Distinct lengths and the 28–40% wall band** (2026-11-06) reissued every puzzle in both modes
  again, already-played dailies included, on the same terms. Accepted explicitly before the change.
  Its same-day revision to 18–28% / 8–18% reissued everything a second time.

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

### The tutorial

Decided 2026-11-06. Four choices, each with its rejected alternative:

- **An interactive board, not pages or tips.** The game is a gesture, and reading about a gesture
  teaches it worse than making it. Tips over level 1 were rejected because a generated board changes
  with every generator change and could not be pointed at.
- **Hand-made boards, one per language** (`game/Tutorial.kt`), picked from generator output at rank
  700 and proved unique against the full shipped dictionary by `TutorialTest`. That test is the reason
  a dictionary rebuild can break the tutorial: if it fails, pick a new board, do not loosen the test.
- **Shown on the first launch, after consent, over the whole shell** — not a nav destination, so a
  replay closes back onto the options tab with every tab's back stack intact. The daily prompt is
  held back while it is up.
- **Skipping is finishing.** Skip, the back gesture and the final button all write `tutorial_done`.
  A replay from the options tab is one tap with no confirmation and is not written down, so closing
  the app mid-replay does not bring it back on the next launch.

Players who already solved a level or a day when the tutorial shipped are treated as taught
(`TutorialSettings.pendingFrom`). Note that `levelsCompleted` in the lifetime totals skips levels
passed with the debug jump, so a debug install that only ever jumped counts as new.

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

### Why `ravel-ads` is in the backup rules

The three-way day comparison in `AdAllowance.remainingFrom` is what makes a restore honest — same day
keeps the watches spent, later day is a fresh allowance, *earlier* day gives nothing, which is what
answers winding the clock back.

**The commit that adds in-app purchases has to revisit this**: a restored balance is a restored
balance, and "spend, then restore" can only really be answered by a server.

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

---

## Known gaps that are not decisions

Recorded so they are not rediscovered as news, and not defended as intentional.

- ~~**No tutorial.**~~ Closed 2026-11-06 — see "The tutorial" under App layer. The original entry said
  paths are "traceable from either end"; the app has never accepted that (`matchesPlacement` compares
  in order), and the tutorial teaches what the app does.
- **No way out of a stuck board.** No skip, no give-up, no reveal. The only exit is hints, paid for
  with coins, and the coin faucet is capped at `ADS_PER_DAY` watches plus whatever solving pays.
- **Re-opening a solved day goes to `review`, which has no footer and so no share button.**
- **No instrumented or Compose UI tests** (`app/src/androidTest` does not exist). The navigation
  graph's five back stacks, rotation during generation, process death mid-board, and every one of the
  wide layouts are covered by nothing.
- **No in-app theme override.** `isSystemInDarkTheme` only.
- **No progress reset** short of clearing app data.
- **On Polish boards every `ć` marks the end of a path.** Measured 2026-11-06: 1249 of the 3877
  words of 4–12 letters within rank 4000 end in `ć` (infinitives), and the letter almost never
  occurs mid-word, so each one hands the player a path end for free. A sample 6x6 showed three, i.e.
  three of its eight ends. The obvious fix is capping infinitives per board; it was left alone on
  purpose for now, which is why this is listed rather than rediscovered.
