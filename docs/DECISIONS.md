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

*2026-11-06:* `randomComposition` and its donor loop are gone. `lengthsFor` enumerates every list that obeys the band and draws
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

*2026-11-07:* the 10x10 half of that is answered. Above 7x7 the target word length dropped from one
letter per side to 0.75 (`LARGE_BOARD_WORD_LENGTH_PER_SIDE`). Polish, rank 6000, 30 boards, same
seeds: 8x8 went from 6.7 words averaging 8.4 letters to 8.9 averaging 6.5, 10x10 from 8.9 averaging
10.0 to 10.5 averaging 8.4, and the 10x10 worst case from 1.07 s to 0.28 s. 0.85 was measured too and
sits between. It reissued every board above 7x7.

The 5x5 half is not: more walls do not buy a fourth word there. Four distinct lengths in the band
need exactly 18 free cells (3, 4, 5, 6) or 22 to 23 (4, 5, 6, 7 / 8); the wall placement reaches 6
walls at most when asked for 7, so 28% gave 19 free cells, three words every time, and 52% corridor
cells. Two to four walls gave a fourth word on a fifth of boards with 30% corridor cells.

*Decided the same day:* 5x5 and smaller take 8-16% walls (`SMALL_BOARD_CELLS`), and the word count
there has no downward spread, since the fallback to one word fewer already happens when no list
fits. 30 boards, rank 3000: four words on 17 of 30 Polish and 19 of 30 English boards (none before),
corridor cells 29-30% (36-39% before), worst case 204 ms. With 23 free cells the only list is 4, 5,
6, 8, so most four-word boards share it; 22 free cells give 4, 5, 6, 7.

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

### Excluded answers: cut from the pool, not from the dictionary

Found on 2026-11-08, endless level 71 in Polish asked for *zgwałcić* ("to rape"). The build-time
blocklist (`generator/blocklist-*.txt`) only ever covered vulgar stems, so slurs, sexual violence,
killing and the rest passed straight through; a scan of the shipped lists found *żydek*, *cwel*,
*murzyn*, *pedał*, *samobójstwo*, *fag*, *retard* and a few hundred more, 62 Polish and 57 English
of them inside rank 8000, the cap of every level up to 100 and of every daily.

`excluded-answers-<language>.txt` (resources) holds them: **whole lemmas, matched exactly**, applied
by `Dictionary.poolTo` and nowhere else. Every one stays in the dictionary and in the trie.

- **Why not the trie as well.** An excluded word is still a word, and a player who sees it spelled
  across a board will trace it whatever the game thinks of it. Left in the trie it can only cause a
  board to fail the uniqueness check, which is the right outcome; taken out, it lets through a board
  whose second solution runs through it, and that board then rejects a trace the player can see is
  correct. "Pool and trie are different sets" above, applied to a new kind of word.
- **Why whole words, not stems.** The dictionaries ship base forms, so there is nothing to inflect:
  *zgwałcić* is the only form of it that exists to exclude. Stems were tried on the review scan and
  cut down too much that is ordinary and common - `zab-` takes *zabierać* (rank 1663), `gwałt-` takes
  *gwałtowny*, `pochw-` takes *pochwalić*, `ass-` takes *assess* and *assistant*. The low tiers draw
  from the top 1500, where those are exactly the words a board is built from.
- **Why two lists rather than one.** Folding these into the build-time blocklist would have needed an
  exact-match mode it does not have, a dictionary rebuild that moves every rank below each removed
  word, and it would take the words out of the trie, which is the point above. The opposite merger -
  moving the vulgar stems into the pool filter so they too return to the trie - is the more correct
  design and was declined as not worth a rebuild and a rewrite of tested code for about a hundred
  words. So the split is: the blocklist removes vulgar stems from the language of the game, the
  excluded answers keep words out of what a board may ask for. A new word goes in the second list
  unless it is a vulgarity.
- **Stems as an alarm.** A rebuild can bring in a lemma nobody reviewed - a *gwałcicielka* beside
  *gwałciciel*. `DictionaryTest` holds a list of sensitive stems and requires every dictionary word on
  one to be excluded or named as innocent (*gwałtowny*, *ciotka*, *jewel*, *sextant*). On its first
  run it found *pijana* and *pijano*, which the review had missed.
- **Where the line was drawn.** Reviewed word by word. Cut: vulgarities, slurs and insults
  (homographs with a slur sense included - *pedał*, *cock*, *fag*), sexual violence, killing and
  genocide and terror and torture, sex and anatomy, drugs, identity words of every kind (*żydowski*,
  *gay* - argued against as treating identity as shameful, and decided for, since no player can see
  a word that is missing from a hidden pool), drunkenness and alcoholism, bodily functions. Kept:
  death in general, war and weapons, drinks themselves (*piwo*, *beer*), mild oaths and the devil,
  and words whose dominant sense is ordinary (*członek*, *pierś*, *jądro*, *zbocze*).

The cost is about 1% of each pool, and an excluded word keeps its rank, so a cap still means "the N
most common words" minus the excluded ones rather than reaching deeper to make up the number.

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

The 2026-11-06 measurement above ran on `LAYOUT_NODES` 500 000, before the change below was merged
in. Measured together the same day, same seeds, rank 4000, worst of 30 (median): 6x6 72–88 ms
(12–17), 7x7 217–237 ms (25–39), 8x8 177–179 ms (33–39), 10x10 0.53–0.98 s (139–217 ms). The two
changes compose: the budget cut the 10x10 tail by about two thirds again.

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
  *Revisited:* a build that ended in null is now started again on the next request - see "Why the
  level screen builds through the cache" below.
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
- **Distinct lengths and the 28–40% wall band** (2026-11-06) reissued every puzzle in both modes
  again, already-played dailies included, on the same terms. Accepted explicitly before the change.
  Its same-day revision to 18–28% / 8–18% reissued everything a second time.
- **Excluded answers** (2026-11-08) reissued every puzzle in both modes: taking words out of a pool
  shifts every draw that follows. Boards already on the shelf keep their words until solved.

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

The same slot decides sharing from `review` (2026-11-07): a reread day gets the `ShareButton` only
when it is the day `shareFor` holds a card for, which is the last day solved. Per-day cards were
reconsidered (about 30 bytes a day) and left out on the argument above. The card carries lengths and
marks only, so showing it on a reread board gives nobody else an answer.

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
  down the middle of a wide column. **Both roads hold an odd number of levels** (five across in
  tall, nine as a block in wide) so that the level on offer has a middle square to sit in, with as
  many levels behind it as ahead; the strip was eight and put the player one place left of centre.
  That is the one thing the two counts have to agree on, and `firstOf` takes half the size rather
  than a constant of its own so they cannot drift apart. The strip went from seven to five on
  2026-11-07 because the squares carry numbers: at seven a four-digit level (~33dp) did not fit a
  ~39dp square at larger font scales or on narrow phones; five gives ~57dp on a 360dp phone.
- **Daily menu** — heading, run and the three tiles down the left, the week's ladder filling the
  right, and **no scroll at all**, which was the point: stacked on its side the tab came to nearly
  twice the height available, putting the play tile — the reason the tab exists — behind a scroll.
  `StreakCard` takes a `fillHeight` flag for this and only this; its rungs then size themselves off
  the height they are given rather than asking for one, so the seventh day cannot fall off the card.
- **Stats** — two columns of *groups*, never one row of six tiles. The grouping is the screen's whole
  structure (three questions, only the last counting both modes) and six tiles across reads as one
  undivided row of numbers under headings nobody can attach them to.
- **Shop** — the two offers side by side, top-aligned, still capped at `TILE_WIDTH`.
  *Revisited 2026-11-10,* with the daily gift: the gift and the ad tile sit side by side in both
  arrangements, so the shop no longer asks `AdaptiveScreen` and has one layout either way.
- **Options** — switches left, the word sources right, so a player looking for the language does not
  pass a Princeton disclaimer on the way. Still scrolls in both arrangements: the sources card grows
  by a row every time a dictionary is added, which is a height set by a licence obligation rather
  than by a layout.
  *Revisited 2026-11-11:* every licence moved to a page of its own (`LicencesScreen`), behind one row
  at the bottom of the tab. With the theme, the reminders and the reset added, the notices had become
  most of the tab's length - the argument that "a screen holding one setting has room" no longer
  described the screen. Attribution is unchanged: every source is still named, one tap in, and each
  still opens its notice. The tab's split is now the look and feel of the game left (language,
  theme, vibration, reminders) and everything else right (tutorial, ad privacy, reset, licenses);
  the licenses page puts its two groups side by side.

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
(`TutorialSettings.pendingFrom`).

Two places where it departs from the plan written before it, both confirmed by the owner on
2026-11-07: the plan had a step showing that either end of a word works, but the game accepts a word
only traced from its first letter, and that stays the rule the tutorial teaches; and the plan said
no grandfathering since nothing is released, but skipping players who already solved something
stays. Note that `levelsCompleted` in the lifetime totals skips levels
passed with the debug jump, so a debug install that only ever jumped counts as new.

Kept from the first plan (2026-10-11): the board is **not a `PuzzleMode`** - a third mode would have
to choose a size ramp and a rank cap for one board - and it **pays and costs nothing**, because a
tutorial that pays invites skipping back into it, and one that charges for a hint teaches the economy
as a toll. Dropped from it: a lesson on a word laid backwards, since the game accepts a word only from
its first letter, and **locking Daily and Shop until the tutorial is done**, which the overlay over
the whole shell makes moot - every tab waits for it, the daily included, so the daily is still nobody's
first contact with the rules.

### The tutorial's decoy

Decided 2026-11-07. Players meet real words of a fitting length that the game refuses on every board,
and nothing told them why. The tutorial now makes them trace one ("paw" / "man") after the first word
and explains it, with a button to move on.

- **No new boards.** Both boards already hold a dozen real words outside the answer. The catch was
  order: every four- and five-letter decoy runs through a cell of the three-letter answer, so with
  the short word taught first no decoy fitted a length still on the list. The four-letter word is now
  taught first, which retired the old "shortest word first" test.
- **The step is skipped, not forced,** when the player's own order has already taken the decoy's cells
  or its length - the lesson bends the way the outline already did.
- **The decoy is not outlined:** the outline is a hint and hints index the answer. The text names it.

### Hard mode

Decided 2026-11-07. A switch on the levels tab hides the word lengths and their count; found words'
lengths appear as they are found. Pays `FOR_COMPLETING_HARD` (45). Levels only.

- **UI only, knowing the cost.** Uniqueness is proved for the shown length multiset (`Solver`), so a
  board may admit a second tiling into real words once the lengths are hidden. Not measured. Accepted
  because the player's experience of it - a real word refused - is the one ordinary boards already
  give; the difference is only that it can no longer be reasoned out. Making hard boards unique without
  lengths would have made the mode part of a board's identity.
- **Frozen at first open, not first move.** A player who looked, backed out and switched would be paid
  for lengths already read. A level already started before the mode existed is frozen as ordinary.
  A change made on the tab applies from the next level, and the switch says so.
- **Giving up is allowed, on the board, never on the tab.** Hard to ordinary mid-level is not an
  exploit - the player pays with the bonus - and it is the one exit from a board chosen blind besides
  paid hints. A "Show lengths" button under the clue row asks first (it cannot be undone and costs
  coins). The tab's switch does not touch the open level, or setting the next level's mode would
  silently cost the current one its bonus. Hints bought before giving up change nothing. It is in
  effect the cheapest help in the game (20 against a 40 letter), but only for players who chose to
  play without what everyone else is given for free. The same dialog offers "Show and turn off hard
  mode" (added 2026-11-08): this board as above, and the tab's switch off from the next level on - the
  board reaching the switch, never the switch reaching the board. Outlined in `HardColors` red, the
  label left in the text colour for contrast on the dark theme.
- **45: started at 40, 55 turned down.** The switch is flipped once and then sets the rate for every
  level after. 55 (asked for, to make the switch tempting) would have made a letter hint cheaper than
  one level and an ad worth under two - the stuck player's way out made cheap, against "being stuck is
  meant to cost". 40 was the first figure; 45 is the owner's compromise. How much harder a board is
  without lengths is unmeasured - on a 5x5, barely - and raising a reward later is a gift where
  lowering it is a nerf. The switch shows the extra (+20), not the total: "+40" was read as on top.
- **Hints do not forfeit the bonus.** The extra 20 is below the cheapest hint (40), so no purchase can
  come out ahead; a forfeit rule would need explaining for nothing.
- **The card marks a hard level with a red badge** (`HardColors`, not `error`, which the "in progress"
  dot already wears), on a line laid out whether shown or not, so flipping the switch does not grow
  the card and move the switch from under the finger.
- **The full clue row is still laid out, invisibly,** so the footer and the board keep the size they
  have in an ordinary level. It leaks how many lines the row wraps to, which only says a long list is
  long.

### Why the daily menu follows the date, and the board checks it anyway

Reported 2026-11-08: the morning after solving a daily, the menu still showed today as solved and
would not offer the new board; killing the app fixed it. `DailyMenuViewModel` read the date once, at
construction, on the argument that a "today" ring moving under a player at midnight would be
stranger than one a minute stale. But the view model lives as long as the daily tab's back stack,
which is the process, and a process outlives a night on the home screen as a matter of course. The
stale ring was not a minute old but a night old.

The worse half was never reported: had yesterday been left unsolved, Play would have opened it as a
live board, and `reward` - which deliberately does not look at the date, so that a solve crossing
midnight still pays - would have paid a streak for a missed day. That is the one thing the streak
rules exist to prevent.

What was done, and what was turned down:

- **The menu follows the date** (`followToday`, run by `repeatOnLifecycle(STARTED)`): read again
  every time the screen starts, then at each local midnight while it stays started. The restart is
  what catches a night in the background, a time-zone change and a clock set by hand; the midnight
  wait covers a player watching the menu at 00:00. Nothing runs while the app is out of sight, so
  nothing depends on a frozen process waking on time. The calendar moves with today only where it
  was showing today (`CalendarFocus`), so a player reading an old month is not pulled out of it.
- **Rejected: refresh on start only.** Simpler, and leaves the ring wrong for anyone watching midnight
  pass. Chosen against, since the countdown costs one coroutine.
- **Rejected: reading the clock at every recomposition.** Nothing recomposes at midnight, so this is
  the same bug at less predictable moments.
- **The board decides its own playability** (`DailyRoute.opensForReview`): any day but today opens as
  a review, whatever the route asked. A guard in `reward` alone was turned down - the player would
  play the whole board and learn only at the end that it pays nothing, and hints could still be
  bought on it. The guard also covers a route restored after process death on a later day, which
  the menu fix cannot reach. Such a day may be unsolved, and its review then shows the answer; the
  day is over and nothing on it pays, so that is accepted.

### Why the haptics switch greys out, and only sometimes

Added 2026-11-08 after a report that the board did not vibrate. The board's haptics obey the phone's
touch-feedback setting (`BoardHaptics`), so with that off the game's switch does nothing either way.
`SystemHaptics` reads `Settings.System.HAPTIC_FEEDBACK_ENABLED` (public, no permission) and
`hasVibrator()` each time the settings tab starts; on a definite 0, or no vibrator, the switch is
disabled - still showing the player's choice - and the note says why.

Only a definite 0 counts. Android 13's intensity sliders and manufacturers' own screens may not keep
that value in step, and quiet modes can silence touch feedback with no public signal, so the check
can miss a silent phone; it is built never to warn about one that is not. Still no link to the
system setting, for the reason the strings comment gives: it lives somewhere different on every
phone. Not verified on a manufacturer's device at the time of writing.

### Progress reset, and why nothing pays twice

Built 2026-11-06: `RavelApplication.resetProgress`, behind a confirmation, wipes both level ladders,
the daily history with the streak, and the lifetime statistics, in every language. **Coins, today's ad
allowance and settings stay.** The clearing methods sit on the concrete stores, not on
`LevelProgress` / `DailyProgress`, which keep no way to move the ladder.

- **Every language, not one.** The first plan (2026-10-11) reset the current language's ladder and
  nothing else. Dropped because the daily history and the statistics are not kept per language, so a
  per-language reset could not clear what a player sees as their progress.
- **The wallet is not reset.** A reset to `STARTING_BALANCE` would refill a spent wallet.
- **Nothing pays twice.** `Rewards.FOR_COMPLETING` is paid per level and the first levels are 5x5
  boards solved in seconds, so a reset that paid again is a coin farm with a button - and as first
  built, it was one: every level up to where the player had been paid again, and today's daily paid
  its board and streak again after every reset. Closed the same day with the guard the first plan
  already had: each language's best level (`StoredLevelProgress`, `best`) and the latest day that
  paid (`StoredDailyProgress`, `lastPaid`) survive `clear`, and a replay at or below them pays
  nothing. Both are seeded from the ladder and the record on `clear`, for installs from before the
  keys existed.

### The daily notification

Built 2026-11-11. The brief was "smart, and not like Duolingo". What was taken from Duolingo is its
two good mechanics - a time that follows the player's habit, and backing off when ignored. What was
left behind is the rest: guilt, a mascot, several a day, escalation, invented urgency, and the
passive-aggressive "these don't seem to be working" message. Every rule is in `notify/NotificationPlan.kt`
and `NotificationPlanTest`.

- **Is it needed at all?** Raised and answered yes. Wordle grew with no notifications, on sharing
  alone, and Ravel has the share card. Before 1.0 there are no retention numbers to show either way
  (no analytics), so the notification was built as a handful of fixed rules, not as anything that
  learns beyond the last solve.
- **At most one a day; none once today is solved.** Decided when the alarm goes off, against the
  state then, never when it was set - an alarm set at noon knows nothing about a solve at 18:00.
  Solving takes down one still showing: a notification that is no longer true is a lie on the lock
  screen.
- **The automatic time is when the daily was last solved, not when it was opened.** Duolingo uses
  the time of yesterday's lesson, and there is no more to its "smart timing" than that - so neither
  is there here. The solve and not the open, because with the open time a player who starts at 8:00
  is notified at 8:00 every day, before sitting down to a board they were about to play anyway.
  With the solve time, a player keeping their habit has finished by then and gets nothing; only a
  player who has slipped from it hears from the game.
- **The solve before last does not matter.** One number, overwritten on every solve. A day without
  a solve keeps the last one rather than falling back to the default.
- **Quiet hours, 22:00 to 08:00, for the automatic time only; a solve inside them means the
  default, 19:00.** One rule covers the 23:40 solve, the after-midnight solve and the 6:00 solve.
  It also means the automatic notification can never land just before midnight, when a streak
  warning would be useless. An earlier draft had a separate 21:00 cap for late solves; dropped as a
  second number doing the same job.
- **A fixed time, if the player picks one.** Proposed against ("one less setting"), kept by the
  owner's decision. With it: the fixed time is a contract - no quiet hours, sent until midnight.
  Two switches (on/off, then automatic/fixed) rather than one three-way choice, because they are two
  questions asked in that order; the time is only drawn when it is fixed, and switching back to
  automatic keeps the fixed time for next time.
- **The game on screen postpones; it never skips.** An hour at a time, up to 22:00 (automatic) or
  midnight (fixed). "Skip" was the first word used, and it was wrong: a player on a level at 20:00
  who leaves at 20:10 would hear nothing that day. The alternative - sending a set time after the
  app leaves the screen - would be more accurate and is a second mechanism that tracks foreground
  state; the hourly retry is dumber and is the same one alarm. The same rule recovers a lost alarm:
  "due has passed, nothing went out, still owed" is just "an hour from now".
- **Three ignored in a row switch them off - visibly.** First proposed silent: stop after a few
  ignored, resume on the next solve, say nothing. Replaced by the owner's version, which is better:
  the switch in the options really goes off, so a player wondering why nothing comes sees why, and
  the next solve asks once whether to switch them back on. A no is final; a player who switched them
  off themselves is never asked. Applies to the fixed time too: with the state visible, the old
  argument - that a fixed time going quiet would look like a bug - no longer holds. *Ignored* means
  the day ended unsolved; a tap is not what counts, since swiping it away and then solving from the
  launcher did what it asked. A notification the system blocked was never seen and is not counted.
- **What it says, most pressing first:** a missed day that can still be bought back (the only real
  deadline in the game), a streak that ends at midnight, a board part-way through ("2 words left"),
  a fresh board ("Today: 8×8"). The streak kinds need a run of 3 - a two-day run is not worth an
  urgent sentence. The size comes from `PuzzleSpec`, the remainder from the saved board; nothing
  runs the generator in the background. Two channels, so the streak ones can be silenced alone.
- **Asked after the second solved daily, never at launch.** Our dialog first, the system's only
  after a yes - Android allows two system asks, and a no on our dialog spends neither. Off by
  default below Android 13 too, where the system would not have asked: the same opt-in everywhere.
  A player can always switch them on from the options; when the system blocks them, the options say
  so and open the system's page, the one place a block can be lifted.
- **Inexact alarm (`setAndAllowWhileIdle`), no exact-alarm permission.** `SCHEDULE_EXACT_ALARM`
  needs the player to grant it by hand and `USE_EXACT_ALARM` is reserved by Play for alarm clocks
  and calendars. Minutes late is still the same reminder. Set again on every process start and on
  boot, package update, time and time-zone change.
- **A tap opens today's board, and the launch prompt does not follow it** - asking "play today's
  board?" of a player who just tapped "today's board" is asking twice.
- **Stored in `ravel-settings`**, so the backup rules already cover it. A restore onto a new phone
  brings back "on" without the system permission; the options show that as blocked, which is true.
- **Not to be confused with `DailyReminder`**, the in-app question at launch. That one was named
  first; the two are separate switches because they are different intrusions.

---

## Economy

### The four numbers and the three ratios

`Rewards.FOR_COMPLETING` (15), `Streak.DAY` (15, `SEVENTH` 50), `Rewards.FOR_WATCHING_AD` (100),
against the `Hint` prices (40 / 100).

Three ratios are the actual design, and all three are written down where they live:

- a week of streak pays roughly 1.33× a week of solving (140 against 105) — `StreakTest` guards it
  as a band, deliberately not as two figures;
- an ad pays what six and two-thirds solved boards pay;
- a word costs 2.5 letters, which is what puts the hint break-even at two letters — `PuzzleTest`
  guards that as the pair of inequalities, not as the ratio.

Moving one number alone silently answers a question those comments already answered.

The first three were all multiplied by 2.5 together when solving turned out to earn 10 coins against
300 a day from ads, and cut together on 2026-11-11 - see below.

### Why playing pays less: 25 to 15 (2026-11-11)

The complaint was the opposite of the one that cut the hint prices: nobody needed the shop. At 25 a
board a word cost four boards, so any player stuck less often than once in four saw their balance
climb for good. A daily-only player was paid 555 a week (175 solving, 275 streak, 105 gift) for
seven boards - five and a half words, nearly one per board. Add 600 at install, and the first reason
to watch an ad arrived, if ever, in the second month. Rewarded ads only earn when a player has a
reason to want one, so the economy had to produce that reason.

What changed, together:

| Number | Was | Now |
|---|---|---|
| `FOR_COMPLETING` | 25 | 15 |
| `Streak.DAY` | 25 | 15 |
| `Streak.SEVENTH` | 125 (`5 * DAY`) | 50 |
| `FOR_COMPLETING_HARD` | 45 | 25 |
| `DAILY_GIFT` | 15 | 10 |
| `Wallet.STARTING_BALANCE` | 600 | 250 |

A daily-only week is now 105 + 140 + 70 = 315. The ad stays at 100, one whole word, so it is now
worth six and two-thirds boards instead of four: being stuck is one ad away from the word rather than
an evening of other boards away. That is the trade being made, on purpose.

- **Pay was cut, prices were left.** Raising the hint prices was asked about and turned down. Counted
  in boards the cut already made a word two-thirds dearer; raising prices too would have tightened
  the same screw twice before the first turn was measured, would have cut the ad loose from the word
  it buys, and is the one lever the grinding complaint already ruled out. The one argument for it -
  it is the only lever that devalues the coins existing testers have banked - was judged not worth
  the signal it sends to the first players.
- **`SEVENTH` stopped being `5 * DAY`.** 75 was the proportional figure; 50 was chosen to make the
  economy leaner still. It keeps the week inside `StreakTest`'s band and caps a broken run at 35
  coins, under the repair price - see "Streak repair".
- **`FOR_COMPLETING_HARD` is a flat +10**, chosen over the proportional 27.
- **`DAILY_GIFT` had to move**: at 15 it would equal a solved board, which `PuzzleTest` forbids.
- **`STARTING_BALANCE` 250** over the 150 recommended: two words and a letter, about seventeen boards.
- **Existing balances are not touched.** The economy may change before 1.0.0, but taking coins back
  from testers was rejected. The cost is a slow signal: players sitting on 600+ will not reach the
  shop for weeks, so "rewarded impressions per DAU" before and after will move slowly, and new
  installs are the ones to watch.

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

### Why the daily gift is 15, and why it exists at all

The shop's "Daily rewards" section has a second tile beside the ad: one tap, once a day,
`Rewards.DAILY_GIFT` coins. It is the only coin in the game paid for showing up rather than for
playing - the streak at least needs the daily solved - which runs against the rule `StreakTest`
guards: a game whose visits pay what its boards pay has an optimal player who opens it and closes it.

It exists anyway as a reason to look into the shop every day, where the ad tile is. The amount is
what keeps it that rather than income. Against a week of solving every daily (25 x 7 = 175):

| Gift | A week | Of 175 |
|---|---|---|
| 10 | 70 | 40% |
| 15 | 105 | 60% |
| 25 | 175 | 100% - a tap pays what a solved board pays |

15 was chosen deliberately over the 10 that was recommended. When `FOR_COMPLETING` came down to 15
on 2026-11-11 the gift came down to 10: `PuzzleTest` holds it under half the cheapest hint and under
`FOR_COMPLETING`, which is the line that keeps a tap from being worth a board. Against the new week
of solving (105) it is 70, two-thirds.

Its day rule is `AdAllowance`'s three-way comparison, stored as the day of the last claim in
`ravel-ads`: a restore on the same day keeps it taken, a clock wound back gets nothing, and a progress
reset does not touch it.

### Streak repair

Decided 2026-11-11. A single missed day can be bought back the next day, for `Streak.REPAIR_PRICE`
(100) coins or one rewarded ad, once per seven-day cycle. The rules are `game/StreakRun.kt`.

- **Why a repair after the fact and not a freeze bought in advance.** The question is asked at the
  moment the loss is real, which is when it is worth something to the player; a stock of freezes
  bought on a calm day is a decision made without that weight, and a stock is one more balance to
  explain.
- **Why 100, the same as an ad.** The dialog offers "100 coins or an ad" and so teaches the rate the
  shop already pays. In coins a broken run costs at most 35 (`SEVENTH - DAY`), so the price is
  deliberately above what it saves: a repair buys the count on the run card, not coins, and pricing
  it under the coins at stake would make it the automatic answer instead of a choice. Players who
  will not pay 100 for a count watch the ad, which is the point.
- **Why one day only, and only the next day.** Two missed days end the run, and a gap not repaired
  by the following midnight stays a gap - otherwise a player back after a month buys a month-old run
  back with one ad.
- **Why once per cycle.** Two per cycle were asked for first, with the second one priced higher; both
  were dropped for one. A run kept alive through two gaps in seven days is five days a week, which
  is not "in a row". One per cycle also needs no escalating price to explain. A repair belongs to the
  cycle of the solved day after it, so a repair at the turn of a cycle cannot be paired with another
  just after it.
- **Why a repaired day is not counted.** "N days in a row" stays N days actually solved; the repair
  only stops a gap from setting it to zero. It pays nothing and the ladder does not move. Solving
  today before repairing is allowed, and the repair then does not top up today's pay.
- **Why closing the dialog is not a refusal.** It opens by itself once per gap; a stray back gesture
  must not cost a thirty-day run, so the offer then waits on the run card until midnight. Only "Let
  it go" ends it.
- **Why it says so up front.** The week card carries one sentence about the repair, so it is a rule
  the player knew and not an offer that appears only when they are losing something.
- **Why a repaired day is marked.** The snowflake (`ic_streak_repaired`, Material Symbols `ac_unit`)
  on the run card and in the calendar keeps the run honest about having been mended.

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

### Why a release is built from a clean project

`0.1.0-alpha.3` crashed on every second drag across the board, on every board, tutorial included:
`ClassCastException: Puzzle cannot be cast to MutableState` in `rememberUpdatedState`, inside
`BoardGrid`'s restart lambda. Taps still worked, because a tap recomposes the board from its parent
and never goes through that lambda. The source was fine. The bundle had been made with Generate
Signed App Bundle on top of an `app/build` whose release classes came from incremental compiles on
two different days, and six of them (`LevelScreenKt`, `DailyScreenKt`, `LevelProgressState` among
them) differed from a clean compile of the same source: the Compose compiler had read stability at
runtime (`SavedBoard.$stable`) where a clean compile writes a constant, and added `changed()` checks
to match. On that input R8 kept the restart lambda's `$changed` as a field (`f$7`) instead of
propagating it as a constant, and the board's slots came out misaligned.

Measured on an emulator with a two-drag script, ten tries or more each:

| Build | Crashes |
|---|---|
| The alpha.3 bundle from Play | 10 / 10 |
| Same stale classes, full R8 | 5 / 5 |
| Same stale classes, `-dontoptimize` | 0 / 8 |
| Clean compile, full R8 | 0 / 5 |
| Debug | 0 / 5 |
| alpha.4, built after Clean Project | 0 / 10 |

It takes both halves; either alone runs. Whether the fault is R8's or the incremental output's was
not settled. The first suspect, commit `4445de3` (the off-board tap, whose line the crash pointed
at), was only where the misaligned slot was read.

What was ruled out:

- **`incremental = false` on `compileReleaseKotlin`.** AGP 9.5.1 overrides it after configuration;
  the task still compiled incrementally. Setting it in a `doFirst` does work - a full recompile,
  stale outputs removed, bytes identical to a clean build, debug left incremental - but it rests on
  a property AGP evidently manages itself, and the day it stops being read the build falls back to
  incremental without a word. A manual step that can be forgotten was preferred to an automatic
  one that can fail silently.
- **`-dontoptimize` for good.** It hides this one shape and costs a quarter of the APK (4.04 MB to
  5.09 MB).
- **Release bundles from CI.** A fresh checkout is clean by definition, but the upload key would
  have to live in GitHub secrets, which is not worth it for one developer on the alpha track.

The drag check on the bundle stays whichever way the build is made: it is what catches the next
tool bug of this kind, and "install and open", the step R8 earned the first time, did not.

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
answers winding the clock back. `DailyGift` keeps its claim in the same file on the same rule.

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

---

## Ads and consent

### Why consent is gathered before the game is drawn

`setContent` shows `LoadingScreen` until `AdConsent.settled` turns true, so the form is never a
dialog landing on a screen the player had already started reading.

That wait is capped at `SETTLE_TIMEOUT` and the timer is deliberately not cancelled — a word puzzle
that works offline must never have a launch that depends on a network, and past the cap the form
simply arrives on top of the game as it used to.

### Why an ad can start from two places, and a dialog can point at a third

Until 2026-11-11 the shop's tile was the only way into an ad. The streak repair dialog is now a
second: the moment a run is about to be lost is the moment an ad is most worth watching, and sending
the player to the shop and back would lose most of them on the way. It is still an ad the player
asked for, by a button naming what it buys, so the privacy policy's promise - rewarded only, nothing
unasked - holds. The policy's wording was widened from "for in-game coins" to "for in-game coins or
for saving your daily streak".

The "stuck?" dialog on a board is the one thing in the game that interrupts unasked, and it starts
no ad: it points at the shop, with a way out. A repair-style "watch now" button there was not chosen;
the shop keeps that role. Its rules:

- **99 seconds of solve-clock time without a word.** 23 was the first figure and a guess; a player
  on a 6x6 or larger routinely looks for a minute or two before the next word shows itself, so 23
  would have interrupted nearly every board in the middle of the thinking the board asks for.
- **Once a day, not once a board**, counted like the gift (`settings/StuckOffer.kt`, in `ravel-ads`).
- **Never when it leads nowhere**: the day's watches spent, ads refused at consent, a review, the
  tutorial.

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
second, parallel input model, not labels. The `Canvas` is separately a performance decision — the
largest board, 10x10, is 100 cells.

Accessibility **off** the board still applies in full: `ui/Coins.kt`, `CalendarCard.kt`,
`StreakCard.kt`, `SettingsScreen.kt` and the rest carry semantics, and new screens are expected to.

### No skip, no give-up, no free reveal

Decided 2026-10-11. Being stuck on a board is the game asking the player to think, and a way past it
that costs nothing would answer the question for them. The only exit is hints, paid for in coins.

This does not undo "Why the hint prices came down alone": that was about the *price* of the exit
being a grind, not about there being one. Skipping was also ruled out structurally before this -
`LevelProgress` has no setter, see "Why `LevelProgress` has no setter".

Reconfirmed 2026-11-06, when it was briefly listed as a known gap.

### On Polish boards every `ć` marks the end of a path

Measured 2026-11-06: 1249 of the 3877 words of 4–12 letters within rank 4000 end in `ć`
(infinitives), and the letter almost never occurs mid-word, so each one hands the player a path end
for free. A sample 6x6 showed three, i.e. three of its eight ends; two sample 10x10s showed six of
nine words and seven of eight, since the long Polish words are mostly verbs.

The obvious fix is capping infinitives per board. Left alone on purpose, the owner's decision of
2026-11-06.
