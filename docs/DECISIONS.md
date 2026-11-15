# Decisions

Why the rules in `CLAUDE.md` are the rules: what was tried, what it cost, what the measurements
said. `CLAUDE.md` states the rule and links here; this file carries the argument.

Nothing here is optional reading before *changing* one of those rules — a rule whose reasoning has
been read and answered can be changed, one that merely looks arbitrary cannot.

**Writing in this file:** every entry opens with what holds *now* — the rule, the number, the
measurement it rests on. When a decision is revisited, rewrite the entry to the new state and keep
what it replaced in a sentence or two: the old value, and why it was left. The rejected option is
the valuable half, so it stays; the long story of how it was found is in git. Section titles quoted
from code and other documents ("Generation cost, measured", "Excluded answers", "The daily
notification", "Why playing pays less", "Why the language codes carry a region", "Coins for real
money") keep their wording.

---

## Generation

### Shape first, words second

`Partitioner` splits the free cells into disjoint paths of the chosen lengths before any word is
drawn. Choosing words first means backtracking over a dictionary instead of over a handful of shapes,
and does not scale.

### Pool and trie are different sets

Words are drawn from `Dictionary.poolTo(maxWordRank)`, while the trie holds every loaded word **and
its reverse** (the uniqueness proof reads a path from either end, which is stricter than what the
player may trace).

Narrowing the pool is not the same as loading less: every word the dictionary holds is one `Solver`
must rule out before calling a board unique, so a smaller load weakens the uniqueness claim rather
than easing the board. That is why "how much is loaded" (`Difficulty`) and "how obscure a board's own
words may be" (`BoardSpec.maxWordRank`) are two axes and not one.

### Excluded answers: cut from the pool, not from the dictionary

`excluded-answers-<language>.txt` (resources) holds **whole lemmas, matched exactly**, applied by
`Dictionary.poolTo` and nowhere else. Every one stays in the dictionary and in the trie. The
build-time blocklist (`generator/blocklist-*.txt`) is a different list: vulgar stems, removed from
the dictionary altogether.

Found on 2026-11-08, when endless level 71 in Polish asked for *zgwałcić* ("to rape"). The blocklist
only ever covered vulgar stems, so slurs, sexual violence, killing and the rest passed straight
through; a scan of the shipped lists found *żydek*, *cwel*, *murzyn*, *pedał*, *samobójstwo*, *fag*,
*retard* and a few hundred more, 62 Polish and 57 English of them inside rank 8000, the cap of every
level up to 100 and of every daily.

- **Why not the trie as well.** An excluded word is still a word, and a player who sees it spelled
  across a board will trace it whatever the game thinks of it. Left in the trie it can only cause a
  board to fail the uniqueness check, which is the right outcome; taken out, it lets through a board
  whose second solution runs through it, and that board then rejects a trace the player can see is
  correct. "Pool and trie are different sets", applied to a new kind of word.
- **Why whole words, not stems.** The dictionaries ship base forms, so there is nothing to inflect:
  *zgwałcić* is the only form of it that exists to exclude. Stems were tried on the review scan and
  cut down too much that is ordinary and common - `zab-` takes *zabierać* (rank 1663), `gwałt-` takes
  *gwałtowny*, `pochw-` takes *pochwalić*, `ass-` takes *assess* and *assistant*. The low tiers draw
  from the top 1500, where those are exactly the words a board is built from.
- **Why two lists rather than one.** Folding these into the blocklist would need an exact-match mode
  it does not have, a dictionary rebuild that moves every rank below each removed word, and it would
  take the words out of the trie. The opposite merger - moving the vulgar stems into the pool filter
  so they too return to the trie - is the more correct design and was declined as not worth a
  rebuild and a rewrite of tested code for about a hundred words. A new word goes in the excluded
  answers unless it is a vulgarity.
- **Stems as an alarm.** A rebuild can bring in a lemma nobody reviewed - a *gwałcicielka* beside
  *gwałciciel*. `DictionaryTest` holds a list of sensitive stems and requires every dictionary word on
  one to be excluded or named as innocent (*gwałtowny*, *ciotka*, *jewel*, *sextant*). On its first
  run it found *pijana* and *pijano*, which the review had missed.
- **Where the line is drawn.** Reviewed word by word. Cut: vulgarities, slurs and insults
  (homographs with a slur sense included - *pedał*, *cock*, *fag*), sexual violence, killing and
  genocide and terror and torture, sex and anatomy, drugs, identity words of every kind (*żydowski*,
  *gay* - argued against as treating identity as shameful, and decided for, since no player can see
  a word that is missing from a hidden pool), drunkenness and alcoholism, bodily functions. Kept:
  death in general, war and weapons, drinks themselves (*piwo*, *beer*), mild oaths and the devil,
  and words whose dominant sense is ordinary (*członek*, *pierś*, *jądro*, *zbocze*).
- **Medical words** (2026-11-12, at the owner's request): sex organs and their derivatives
  (*płciowy*, *wagina*, *genitalia*, *foreskin*), venereal disease (*kiła*, *syphilis*),
  menstruation (*podpaska*, *menopause*) and excretion (*biegunka*, *urinate*) - 61 Polish lemmas,
  81 English. Left alone: general medicine (*prostata*, *ovary*, *uterus*), household words
  (*pielucha*, *diaper*, *potty*), and every word with an innocent second sense (*kupa*, *bąk*,
  *zaparcie*, *wytrysk*, *nasienie*, *stool*, *bosom*, *intercourse*). The blocklist was the first
  place named for them and was turned down: they are not vulgar, and it would have taken them out
  of the trie.

The cost is about 1% of each pool, and an excluded word keeps its rank, so a cap still means "the N
most common words" minus the excluded ones rather than reaching deeper to make up the number.

### Base forms only, by opposite routes

- **Polish** (51 685, from PoliMorf) keeps the lemmas. Three quarters of the old 247 012-entry list
  were oblique cases, which is not what a player recognizes when the only clue is a length.
- **English** (42 554, from WordNet) folds regular inflections into a base *that is itself in the
  dictionary*. It cannot ask "is this a lemma?" — WordNet indexes only nouns, verbs, adjectives and
  adverbs, so that question deletes `the`, `and`, `for` and `with`. Asking about inflection instead
  lets function words through with nobody maintaining a list of them.

Both builds sum a folded form's frequency into its base; ranking a survivor by its own form's
frequency alone is what buries `robić` under `robi`.

### The length band: no word shorter than half the longest

`lengthsFor` enumerates every list of lengths that obeys the band and draws one, so a list outside it
is never built.

*The list of lengths is the only clue this game gives*, so a list repeating one number is not a
clue. The floor used to be one letter under the mean, which let a 10x10 produce only lengths 9..12
and often six twelve-letter words. Those are also the thinnest bands of the dictionary: within rank
4000 Polish has 574 five-letter lemmas against 93 twelve-letter ones, so the biggest boards drew from
the narrowest pool while the most recognizable vocabulary never appeared on them. The old floor was
also a defense against short words coming out as squat blocks; the per-word fold gate in
`PathShape.accepts` does that job at every length, so the floor was not paying for itself.

**The ratio is load-bearing in `BoardSpec.wordCountFor`**: it must keep enough words that the floor
is *reachable*, or the band is a promise the arithmetic cannot keep — measured with the old
`fewest`: a 10x10 came out as seven twelve-letter words with the band wide open and unusable.

Before 2026-11-06 the lengths came from `randomComposition` and a donor loop, which had to stop at the
band's floor rather than at `MIN_WORD_LENGTH`; enumerating the lists removed that trap with the loop.

### Distinct word lengths, and the walls that make room for them

**The rule.** Up to 7x7 (by area) every word length on a board is different; above it, none appears
more than twice (`BoardSpec.maxLengthRepeats`). Walls take 8–16% of a board up to 5x5
(`SMALL_BOARD_CELLS`), 18–28% up to 7x7 and 8–18% above (`BoardSpec.minWallRatio`). Words aim at one
letter per side up to 7x7 and 0.75 above (`LARGE_BOARD_WORD_LENGTH_PER_SIDE`). When no list of lengths
fits, the walls are redrawn; the rule is never eased.

**Why distinct.** Measured 2026-11-06 on 60 solved boards of LinkedIn's Wend — the game Ravel
descends from — against 40 of ours per size (English, rank 4000):

| | Wend | Ravel 5x5 | 6x6 | 7x7 |
|---|---|---|---|---|
| every length different | 57 of 60 | 22% | 17% | 2.5% |

The clue list is a set of keys only when no two lengths are equal: each length then names one word,
a word found is a word identified, and the list reads as a designed staircase (4, 5, 6, 7) rather
than a tally (7, 7, 8, 8).

**Why only up to 7x7.** Above it the band leaves too few distinct values: strict lengths on 8x8 meant
five words with a twelve-letter one on nearly every board (mean 9.2), and 10x10 has no distinct list
at all — 6..12 is seven values for eight or more words. The threshold is area, not side, because what
decides it is the number of cells the words fill.

**Why the middle band needs walls, and only that one.** With the band kept, distinct lengths fit
only a small free area: at 8–18% walls a 6x6 could hold four distinct lengths only as something like
6, 7, 8, 11. A fifth walled fits them inside the band (6x6: 5, 7, 8, 9). Above 7x7 lengths may repeat,
so nothing needs the free area smaller and the walls stay low.

**Why no more walls than that.** Walls in the middle of the grid turn into corridors that give the
words away. The first version (2026-11-06) walled 28–40% on every size, with `maxWallRun`'s floor
raised to three; the first board looked at on a device (8x8, level 75) needed no letters to solve.
The share of free cells with two free neighbors or fewer — cells a path can cross only one way — had
gone from 16% to 35% on 8x8 and from 20% to 39% on 6x6, half the walls in the middle of the grid. (One
Wend board measured 55% such cells, but 86% of its walls on the border: it trims the outline and
leaves the middle open.) The same day both bands came down to the current ones and the run floor went
back to two: at a fifth walled three bought nothing measurable and cost an occasional broken column
of single walls.

**Why 5x5 has its own band.** More walls do not buy a fourth word there. Four distinct lengths in the
band need exactly 18 free cells (3, 4, 5, 6) or 22 to 23 (4, 5, 6, 7 / 8); at 28% the placement gave
19 free cells, three words every time and 52% corridor cells. At 8–16% (2026-11-07), 30 boards at
rank 3000: four words on 17 of 30 Polish and 19 of 30 English boards (none before), corridor cells
29–30% (36–39% before), worst case 204 ms. Most four-word boards share the list 4, 5, 6, 8 (23 free
cells); 22 free cells give 4, 5, 6, 7. The word count there has no downward spread, since the
fallback to one word fewer already happens when no list fits.

**Why shorter words above 7x7.** "At most twice" at one letter per side drew pairs climbing to the
ceiling — 8, 9, 9, 10, 10, 11, 11, 12, 12 — so two twelve-letter words on most 10x10s. At 0.75
(2026-11-07; Polish, rank 6000, 30 boards, same seeds) 8x8 went from 6.7 words averaging 8.4 letters
to 8.9 averaging 6.5, 10x10 from 8.9 averaging 10.0 to 10.5 averaging 8.4. 0.85 was measured too and
sits between.

The boards up to 7x7 at the current bands, 30 per size, rank 4000 (2026-11-06):

| | 6x6 | 7x7 |
|---|---|---|
| walls | 18% | 18–20% |
| corridor cells | 30% | 27–29% |
| words | 4.1–4.2 | 4.9 |
| mean length | 7.1 | 8.0–8.2 |

Against the boards before distinct lengths: 3.8 words on 6x6 instead of 4.9, 4.2 on 7x7 instead of
5.6, mean length up by about half a letter.

**Rejected, with the same Wend data:**

- *Lowering the band floor* so short words could join (Wend has a three-letter word on a third of its
  boards and breaks our band on 28%) — kept by decision.
- *Themed word sets* (one letter shared by every word: JOB JUMP EJECT ADJUST) — looked common, measured
  weak: an uncommon letter in ≥75% of the words on 30% of Wend boards against 23% for random words of
  the same lengths. Shared letters would also raise uniqueness rejections.
- *Favoring j/q/x/z* — Wend shows them twice as often as chance (38% vs 20%), but a rare letter is
  an anchor that makes a board easier, not better.

**Still open:** if boards still read as given away, the measured next step is a gate on how many
letter-free tilings the walls allow, not another guess at a ratio.

### Path shape is checked per word, not on the finished board

`turnRatio`, `directness`, `foldRatio`, all in `Board.kt`. Enforced during enumeration and against
the worst word rather than the average. The comments in `Board.kt` and `Language.kt` explain what
averaging and post-hoc checking each produced — both were tried.

### Generation cost, measured

**The tail is the layout search, not uniqueness.** `:app:sweepLevels` over levels 1-300 put 99% of
every slow board's time in layout, with uniqueness at about a millisecond. Each failed layout runs
`SearchBudget.LAYOUT_NODES` to exhaustion, so the slow levels are the ones that draw many failures.
An earlier reading of the first table here, that the tail was the uniqueness search, was wrong.

**`LAYOUT_NODES` is 20 000**, down from 500 000, where a failed 10x10 layout cost ~175 ms. Desktop
JVM, levels 1-300, one run per level, ms:

| `LAYOUT_NODES` | 10x10 median | 10x10 p90 | 10x10 max | 9x9 max | 8x8 max | over 2 s |
|---|---|---|---|---|---|---|
| 500 000 | 703 | 3018 | 5791 | 1164 | 713 | 6 of 300 |
| 20 000 | 240 | 876 | 1383 | 381 | 155 | 0 of 300 |

On the 46 boards of 9x9 and up: 100 000 left a 3.2 s worst case, 30 000 1.7 s, 20 000 and 10 000
0.8 s, with English following Polish. 10 000 was rejected on board shape, not speed: an exhausted
budget counts as an unshapeable layout, and 200 of those (`LAYOUTS_BEFORE_EASING`) loosen the
path-shape ceilings. The worst level reached 164 at 10 000 and 86 at 20 000.

**The latest desktop figures**, 2026-11-06, distinct lengths and the current wall bands, `LAYOUT_NODES`
20 000, rank 4000, worst of 30 (median): 6x6 72–88 ms (12–17), 7x7 217–237 ms (25–39), 8x8 177–179 ms
(33–39), 10x10 0.53–0.98 s (139–217 ms). The shorter words above 7x7 the next day took the 10x10 worst
case down again, 1.07 s to 0.28 s (Polish, rank 6000, 30 boards). Tails vary by ±40% run to run, so
read only orders of magnitude.

Dictionaries cost 23–33 MB at the shipped `HARD`, which is why they are cached per (language,
difficulty) and why `RavelApplication.onTrimMemory` calls `Ravel.forget()`. `Difficulty.HARD.maxRank`
is 35 000 for that reason as much as for what lies past it — `DictionaryFootprint` asserts the
budget, `generator/DICTIONARY.md` explains the content argument.

**On a phone, measured 2026-11-14**: a Redmi Note 10 5G (Dimensity 700, Android 13), the `measure`
build (R8, not debuggable), Polish. Desktop times are `:app:sweepLevels` on the same levels.

| level | board | phone | desktop |
|---|---|---|---|
| 128 | 7x7 | 569 ms | 277 ms |
| 130 | 9x9 | 236 ms | 175 ms |
| 201 | 9x9 | 650 ms | 220 ms |
| 231 | 9x9 | 674 ms | 190 ms |
| 254 | 10x10 | 657 ms | 191 ms |
| 270 | 9x9 | 514 ms | 190 ms |

Those six are the slowest of levels 126-300 on the desktop (175 levels, none over 277 ms, none
failed). Eleven more boards built ahead of them in the same run took 23-211 ms; the smallest boards
of a Play install (alpha.7, levels 1-7 and a daily, both languages) took 9-158 ms. **The phone runs
two to four times the desktop, and the worst board is under 0.7 s.** The cache hides even that, since
the next level is built while the player solves the current one.

The first phone run, on the generator before `LAYOUT_NODES` came down and the words above 7x7 got
shorter, was a different picture: about 19 times the desktop, a median 10x10 of 7.7 s, level 238 at
about 68 s, and 7 of the 24 10x10 levels below 300 failing the 20 s deadline every time. That run is
why `PuzzleCache` exists, and its figures are history now.

### Building boards ahead

`PuzzleCache` (on `RavelApplication`'s scope) builds the next boards while the player is busy with
the current one, and keeps every built board on a shelf until it is solved.

- **It changes nothing about what a board is.** The other ways to make the slow levels cheaper —
  a gentler size ramp, looser shape dials, a different search order — all reissue puzzles. This
  hides the wait without touching identity.
- **The level screen builds through the cache** (`obtain`): a miss is built on the cache's own scope
  with `PREFETCH_TIMEOUT_MILLIS` (3 min), so leaving the screen stops the waiting, not the search.
  Handing a search over to the background after a timeout was the alternative and was rejected: the
  seed makes the search deterministic, so the background build starts from zero and the first part
  is thrown away - about 98 s instead of 68 s for level 238.
- **After 30 s (`SLOW_AFTER_MILLIS`) the spinner says the board is slow and offers the daily.** Not
  "you can leave and come back": Android freezes a backgrounded app within seconds, so the build only
  runs while the game is in front. The offer is a place inside the game where the wait is not wasted.
- **The daily screen builds its own misses** (`take`, then `Puzzles.generate` with the 20 s deadline).
  Its misses are days opened for review, which must not take today's one slot, and its boards (8x8
  at most) fit the deadline.
- **A prefetch is ordered only once the current board is on screen**, so the search the player is
  waiting for never shares the phone with one they are not. Today's daily is ordered from the daily
  menu, even for a player who only came to look at the streak; not for a day already solved.
- **Starting level *n* orders *n + 1* and *n + 2* (`LOOKAHEAD`).** The first version ordered only
  *n + 1*, assuming a prefetch keeps running when the player leaves the app. It does not: Android
  freezes a cached process within seconds of it leaving the foreground, so background work happens
  only while the game is on screen, and the lookahead is counted in levels of foreground play.
- **Built one at a time per mode, nearest first** (a fair `Mutex` per mode). Side by side finishes
  both later on a phone already warm from the player's own board. Per mode, so today's daily never
  queues behind two slow levels.
- **Slots are counted per mode: `1 + LOOKAHEAD` (3) for endless, one for the daily.** The board being
  played and the two after it; fewer, and the one being played is evicted by its own successors on a
  back-and-re-enter. A shared pool would let level prefetches push out today's board and the reverse.
- **A build that ended in null is started again on the next request.** The search is deterministic,
  but its deadline is wall-clock time, and a process frozen mid-build spends that deadline asleep.
  Caching the null was the first version.
- **A different language clears everything**, in-flight searches included.
- **No `forget()`, and the cache is not dropped in `onTrimMemory`.** `TRIM_MEMORY_UI_HIDDEN` fires
  whenever the player leaves the app, often with a build still running that the freezer only
  pauses; finished boards are a few kilobytes.
- **`Puzzles.preload` builds the dictionary at process start** and again on every language switch,
  so the first board does not pay for the trie in front of the player.

**The shelf** (`StoredBoards`, preference file `ravel-boards`, backed up). Memory alone loses
everything when Android kills the process in the background, and the player comes back to the level
they were on with nothing held — for the slow levels, a deterministic failure on every retry. A board
goes on the shelf when a prefetch finishes or a screen builds it (`keep`), and leaves when it is
solved; the shelf keeps the same per-mode count as memory, per language, evicting the lowest ordinal.
Stored whole (letters and solution), because the seed is the thing that is expensive to turn back
into a board. A daily *review* is never kept: it would take today's one slot.

A side effect, accepted: a shelved board survives a generator change, so the player finishes the
board they started, against which their saved marks were made. Boards not yet built still reissue.

What none of this removes: the first board of a fresh install and any board reached with nothing
built still pay in full. Removing that cost is the layout search, and changing it reissues puzzles.

---

## Puzzle identity

### Why `PuzzleMode` has exactly two entries

A `SPECIAL` entry existed, had no screen and no call site outside the tests, and its only live effect
was keeping an `else` branch open — a branch that handed anything unnamed the full size range and no
rank cap, which is the end-game spec the daily had to be rescued from.

Without it `PuzzleSpec`'s `when`s are exhaustive, so a third mode is a compile error until somebody
chooses its size range and its rank cap. Adding the mode back is one line; keeping the `when`s
exhaustive when you do is the point.

### Size and vocabulary: two ramps, two tables

`rampFor` (size, `SIZE_TIERS`) is measured in hundreds of levels and is over by 250. `rankFor`
(vocabulary, `RANK_TIERS`) is measured in thousands and runs to 2500. They must not be collapsed onto
shared tiers: size stops because it runs out of room — `MAX_SIZE` is the last width the generator
builds in a time a player will wait — while vocabulary is not a skill playing confers, so it never
stops mattering. Three boundaries (15, 100, 250) happen to coincide and are left alone on purpose —
see `rankFor`.

**The size curve introduces each width twice.** A new ceiling always arrives in a two-value tier with
floor 6, so the player meets it at once; the next tier drops the floor to 5, so it goes back to being
occasional. One tier per width with the floor rising alongside the ceiling is a staircase, and a run
of boards each bigger than the last reads as a difficulty slider — which size is not: a 9x9 of common
words is easier than a 6x6 of rare ones.

**The vocabulary curve stops at 2500 and cannot be extended by raising the cap.** The dictionary ends
at 35 000 for heap reasons and its last bands are near-indistinguishable. Harder late levels need a
second axis. The path-shape dials are the measured candidate, and they trade against board size rather
than composing with it: `minTurnRatio` 0.62 with `maxDirectness` 0.68 costs about 470 ms on a 7x7, and
the same band blows the deadline on a 9x9.

### Why the daily's ceilings are flat, and why 8 / 8000

A date is not a position on a curve, so there is nothing to ramp along.

The daily once took the *unnarrowed* range and was the hardest board in the game: 5..10 meant a 10x10
one day in six, and no rank cap meant all 35 000 words on day one, where `ENDLESS` does not let go of
30 000 until level 2500. A board the player closes is not a hard daily — it breaks the streak, and
the streak is what makes them open the app.

**8 and not 7.** 8 is the largest grid that reads at a glance on a phone, and it is where the endless
ceiling sits through levels 51–125 — so a daily is a mid-game board, where 7 would put it at level 50.
The endless ramp passes 8 from level 126, so the daily stops being the bigger board around there; a
habit is not an end-game, and that is intended.

**8000** sits just under `kredens` at rank 10 000.

Measured when the daily was narrowed: fourteen consecutive dailies built in 194–615 ms against 0.8 s
median for the 10x10 it replaced; after the length band opened, 8x8 is 77 ms median, 450 ms worst
over thirty.

### Records, not derivations: `DayResult.wordCount` and `DayResult.words`

What the player solved is written down at the moment of solving; anything derived later describes
today's generator wearing an old date, which is not what the player found - see "What reissuing has
already cost".

- **`wordCount` is `Int?`, and null means "nobody counted"** — a day still running, or one banked
  before boards counted their words. The field used to default to `0`, a number a solved day can
  never legitimately hold, so it joined `totalWordsFound` and shortened a lifetime total by exactly
  as much as it did not know, undetectably. The count comes off the `ShareCard` handed to `complete`
  (`card.words.size`), so a solved day cannot be recorded without one entry per word.
- **`words`** (2026-11-13) are the board's words in clue order, with their language, for the
  calendar's list - see "A finished board's words". Days solved before they were stored have none,
  and none is regenerated for them. They travel beside the card into `complete`, checked against it
  (`describes`), and never on it.

### What reissuing has already cost

Board identity is really *(id, generator version)*; a board already on the shelf stays the board the
player started. Every change below reissued puzzles, before any release, which is the only reason
each cost nothing:

- **The length band** reissued every puzzle in every mode at once.
- **Narrowing the daily's spec** reissued every daily ever played. `DailyViewModel.restored` drops
  saved marks that no longer fit, so nothing crashes, but reviewing an old day shows a different
  board than was solved.
- **`LAYOUT_NODES` 500 000 to 20 000** reissued every puzzle in both modes.
- **Distinct lengths and the 28–40% wall band** (2026-11-06) reissued every puzzle in both modes,
  already-played dailies included, accepted explicitly before the change; its same-day revision to
  the current bands reissued everything a second time.
- **Shorter words above 7x7** (2026-11-07) reissued every board above 7x7; **the 5x5 wall band**
  (2026-11-07) every board of 5x5 and smaller.
- **Excluded answers** (2026-11-08) reissued every puzzle in both modes: taking words out of a pool
  shifts every draw that follows.
- **Medical words** (2026-11-12) reissued the **Polish daily** only. Of the 142 lemmas, two are
  inside any pool - *płciowy* (5956) and *pośladek* (7095), under `DAILY_MAX_RANK` (8000) - and
  none is under the levels' cap (2500), so the level ladders and the English daily are untouched.

---

## The daily challenge

### Why the daily menu follows the date, and the board checks it anyway

Reported 2026-11-08: the morning after solving a daily, the menu still showed today as solved and
would not offer the new board; killing the app fixed it. `DailyMenuViewModel` read the date once, at
construction. But the view model lives as long as the daily tab's back stack, which is the process,
and a process outlives a night on the home screen as a matter of course.

The worse half was never reported: had yesterday been left unsolved, Play would have opened it as a
live board, and `reward` - which deliberately does not look at the date, so that a solve crossing
midnight still pays - would have paid a streak for a missed day. That is the one thing the streak
rules exist to prevent.

- **The menu follows the date** (`followToday`, run by `repeatOnLifecycle(STARTED)`): read again every
  time the screen starts, then at each local midnight while it stays started. The restart catches a
  night in the background, a time-zone change and a clock set by hand; the midnight wait covers a
  player watching the menu at 00:00. Nothing runs while the app is out of sight. The calendar moves
  with today only where it was showing today (`CalendarFocus`), so a player reading an old month is
  not pulled out of it. Rejected: refreshing on start only (wrong for anyone watching midnight pass)
  and reading the clock at every recomposition (nothing recomposes at midnight).
- **The board decides its own playability** (`DailyRoute.opensForReview`): any day but today opens as
  a review, whatever the route asked. A guard in `reward` alone was turned down - the player would
  play the whole board and learn only at the end that it pays nothing, and hints could still be
  bought on it. The check also covers a route restored after process death on a later day. Such a day
  may be unsolved, and its review then shows the answer; the day is over and nothing on it pays.
- **The shell follows the date too** (`RavelAppViewModel.followToday`), for the attention dots.

### Streak repair

Decided 2026-11-11. A single missed day can be bought back the next day, for `Streak.REPAIR_PRICE`
(100) coins or one rewarded ad, once per seven-day cycle. The rules are `game/StreakRun.kt`.

- **A repair after the fact, not a freeze bought in advance.** The question is asked at the moment
  the loss is real, which is when it is worth something to the player; a stock of freezes bought on
  a calm day is a decision made without that weight, and one more balance to explain.
- **100, the same as an ad.** The dialog offers "100 coins or an ad" and so teaches the rate the shop
  already pays. A broken run costs at most 35 coins (`SEVENTH - DAY`), so the price is deliberately
  above what it saves: a repair buys the count on the run card, not coins, and pricing it under the
  coins at stake would make it the automatic answer instead of a choice. Players who will not pay
  100 for a count watch the ad, which is the point.
- **One day only, and only the next day.** Two missed days end the run, and a gap not repaired by the
  following midnight stays a gap - otherwise a player back after a month buys a month-old run back
  with one ad.
- **Once per cycle.** Two per cycle, the second priced higher, were asked for first and dropped: a run
  kept alive through two gaps in seven days is five days a week, which is not "in a row". A repair
  belongs to the cycle of the solved day after it, so a repair at the turn of a cycle cannot be paired
  with another just after it.
- **A repaired day is not counted.** "N days in a row" stays N days actually solved; the repair only
  stops a gap from setting it to zero. It pays nothing and the ladder does not move. Solving today
  before repairing is allowed, and the repair then does not top up today's pay.
- **Closing the dialog is not a refusal.** It opens by itself once per gap; a stray back gesture must
  not cost a thirty-day run, so the offer then waits on the run card until midnight. Only "Let it go"
  ends it.
- **It is said up front.** The week card carries one sentence about the repair, so it is a rule the
  player knew and not an offer that appears only when they are losing something.
- **A repaired day is marked** with the snowflake (`ic_streak_repaired`, Material Symbols `ac_unit`)
  on the run card and in the calendar, which keeps the run honest about having been mended.

### The daily notification

Built 2026-11-11. The brief was "smart, and not like Duolingo". What was taken from Duolingo is its
two good mechanics - a time that follows the player's habit, and backing off when ignored. What was
left behind is the rest: guilt, a mascot, several a day, escalation, invented urgency, and the
passive-aggressive "these don't seem to be working" message. Every rule is in
`notify/NotificationPlan.kt` and `NotificationPlanTest`.

- **Is it needed at all?** Raised and answered yes. Wordle grew with no notifications, on sharing
  alone, and Ravel has the share card. With no retention numbers either way, the notification is a
  handful of fixed rules, not anything that learns beyond the last solve.
- **At most one a day; none once today is solved.** Decided when the alarm goes off, against the
  state then, never when it was set - an alarm set at noon knows nothing about a solve at 18:00.
  Solving takes down one still showing: a notification that is no longer true is a lie on the lock
  screen.
- **The automatic time is when the daily was last solved, not when it was opened.** With the open
  time a player who starts at 8:00 is notified at 8:00 every day, before sitting down to a board they
  were about to play anyway. With the solve time, a player keeping their habit has finished by then
  and gets nothing; only a player who has slipped from it hears from the game. One number,
  overwritten on every solve; a day without a solve keeps the last one.
- **Quiet hours, 22:00 to 08:00, for the automatic time only; a solve inside them means 19:00.** One
  rule covers the 23:40 solve, the after-midnight solve and the 6:00 solve, and the automatic
  notification can never land just before midnight, when a streak warning would be useless. A
  separate 21:00 cap for late solves was dropped as a second number doing the same job.
- **A fixed time, if the player picks one.** Proposed against ("one less setting"), kept by the
  owner's decision. The fixed time is a contract - no quiet hours, sent until midnight. Two switches
  (on/off, then automatic/fixed) rather than one three-way choice, because they are two questions
  asked in that order; switching back to automatic keeps the fixed time for next time.
- **The game on screen postpones; it never skips.** An hour at a time, up to 22:00 (automatic) or
  midnight (fixed). Skipping would leave a player who leaves a level at 20:10 with nothing that day.
  Sending a set time after the app leaves the screen would be more accurate and is a second mechanism
  tracking foreground state; the hourly retry is the same one alarm, and it also recovers a lost one.
- **Three ignored in a row switch them off - visibly.** First proposed silent; replaced by the owner's
  version, which is better: the switch in the options really goes off, so a player wondering why
  nothing comes sees why, and the next solve asks once whether to switch them back on. A no is final;
  a player who switched them off themselves is never asked. Applies to the fixed time too. *Ignored*
  means the day ended unsolved; a tap is not what counts, since swiping it away and then solving from
  the launcher did what it asked. A notification the system blocked was never seen and is not counted.
- **What it says, most pressing first:** a missed day that can still be bought back (the only real
  deadline in the game), a streak that ends at midnight, a board part-way through ("2 words left"),
  a fresh board ("Today: 8×8"). The streak kinds need a run of 3. The size comes from `PuzzleSpec`,
  the remainder from the saved board; nothing runs the generator in the background. Two channels, so
  the streak ones can be silenced alone.
- **Asked after the second solved daily, never at launch.** Our dialog first, the system's only after
  a yes - Android allows two system asks, and a no on our dialog spends neither. Off by default below
  Android 13 too, where the system would not have asked: the same opt-in everywhere. When the system
  blocks them, the options say so and open the system's page, the one place a block can be lifted.
- **Inexact alarm (`setAndAllowWhileIdle`), no exact-alarm permission.** `SCHEDULE_EXACT_ALARM` needs
  the player to grant it by hand and `USE_EXACT_ALARM` is reserved by Play for alarm clocks and
  calendars. Minutes late is still the same reminder. Set again on every process start and on boot,
  package update, time and time-zone change.
- **A tap opens today's board, and the launch prompt does not follow it** - asking "play today's
  board?" of a player who just tapped "today's board" is asking twice.
- **Stored in `ravel-settings`**, so the backup rules already cover it. A restore onto a new phone
  brings back "on" without the system permission; the options show that as blocked, which is true.
- **Phones that stop an app closed from recents** (2026-11-14). On a Redmi Note 10 5G (MIUI 14) with
  Autostart off, a reminder set for a game closed from the recent apps list never came: the alarm
  went off on the minute, and the system refused to start the app - "Unable to launch app ... for
  broadcast ... process is bad". With Autostart on, the same test delivered. No exact alarm or
  service gets past a stopped process, so the options say what happens and link dontkillmyapp.com
  at the manufacturer's page (`BackgroundRestrictions`), on every phone while reminders are on. The
  table of pages is copied from the site's vendor list (`api/v1/output.json`), not fetched: a
  request from the app to another server would be data sent off the device. Its instructions (`api/v2`,
  HTML, CC BY 4.0) are not shown in the game - still English, and an attribution and a renderer to
  carry for it. The
  site is English only, and the link says so. Rejected: our own instructions per manufacturer (a
  dozen, each changing with system versions), libraries of manufacturer shortcuts (the same fragile
  list in someone else's code), and reading MIUI's Autostart switch through a hidden AppOps call.
  Discussed and not built yet: noticing after the fact that an armed alarm never reached the
  receiver, and a hint when reminders are switched on on a Xiaomi.
  The inexact alarm's window, which Android caps at an hour, was suspected first and cleared: the
  alarm fired within 12 ms of its minute.
- **Not to be confused with `DailyReminder`**, the in-app question at launch. The two are separate
  switches because they are different intrusions.

---

## Sharing a solved day

### Why sharing exists before the leaderboard

It is the one mechanism by which a player can bring another one, and a cheap test of whether anybody
wants to show this game to anyone. If nobody taps it, that is the answer to whether a leaderboard
would have had entries — bought before paying for nickname moderation, ranking rules and an account
layer.

### What a shared result may show

- **Only lengths.** What may appear is bounded by what every player of that day already sees, which
  is the list of lengths and nothing else. The grid, any path and any letter are between them the
  answer.
- **The numbers sit on the same line as the squares.** A digit and an emoji are different widths, so
  a row of lengths under a row of squares lines up nowhere, and a row that has slid one place along
  attaches every mark to the wrong word.
- **The order is by length.** Position *n* is the same word for both people comparing — that is the
  entire comparability claim. Discovery order is wrong twice: it differs per player, and it is not
  recorded anywhere (`Puzzle.found` is a set). `WordMarkTest` holds the two orderings together.
- **`WordMark` is named for the board, not the wallet**: `UNAIDED`, `PARTLY_REVEALED`, `REVEALED`.
  Found / bought a letter / bought the word is a claim the data cannot support: `Puzzle.hints` counts
  revealed cells per word and not which hint revealed them, and `Puzzle.bought` knows the two kinds
  apart only as totals for the whole board. It also keeps the common awkward case honest: hints target
  the word the player is furthest along on, so a letter bought one cell from the end reveals the whole
  word. "Fully revealed" is true of that; "bought the word" is not.
- **The squares are not in `strings.xml`.** Color is not language, and two locales disagreeing about
  which one means "found it" would break the only thing the row exists to do. The surrounding layout
  *is* translatable (`share_text`) so a translator can move the separators.

### How the card is kept

- **One slot, not a field on every `DayResult`.** A day's cost is history and is kept forever; how a
  solve went is posted on the day and never looked at again. Per-word marks on every day would grow
  the one unbounded map in the file to answer a question nobody asks about last March. The slot is
  checked against the day being asked about (`shareFor`), so a stale card ages out by never matching
  rather than by anything waking up at midnight. The same slot decides sharing from a reread day
  (2026-11-07): the `ShareButton` appears only on the day `shareFor` holds a card for. Per-day cards
  (about 30 bytes a day) were reconsidered and left out on the argument above.
- **Language and size are recorded, not derived.** The same date in two dictionaries is two unrelated
  grids, so a card shared after a language switch would otherwise mislabel itself — and two players
  comparing times across dictionaries are comparing nothing, which is what the `PL`/`EN` marker is
  for. The size on `DayResult.wordCount`'s argument: `PuzzleSpec` is arithmetic over the id, and
  moving a constant in it would silently restate what an already-solved day looked like.
- **Marks and lengths are checked for equal size, never zipped.** `zip` stops at the shorter list,
  so a truncated write would come back as a shorter board that looks entirely valid, with every length
  after the gap attached to the wrong mark. Marks are stored as codes, not enum names: a rename must
  not stop yesterday's card parsing.

---

## Levels

### Level modifiers, and "no lengths"

Decided 2026-11-07 as hard mode, a single switch; turned into modifiers on 2026-11-15, the switch
becoming the first of them as "Bez długości" / "No lengths" (`NO_LENGTHS`; called blind in between,
the same day). A tile on the levels tab under the card lists every modifier and what those on add to
a level's pay; a tap opens a popup with a 2x2 grid of tiles, each pressed in or not, the real
modifiers first and locked "coming soon" tiles after. Levels only - the daily has no modifiers, so a
day stays a day for everyone.

"No lengths" hides the word lengths and their count; found words' lengths appear as they are found.
Pays `Rewards.FOR_NO_LENGTHS` (+5) on top of `FOR_COMPLETING`; `Rewards.forCompleting` sums the
bonus of every modifier the board acts on.

- **One tile and a popup, not a switch per modifier.** A row each would push the road off a short
  phone as the list grows. The tile's height never changes: every chip is always there, the same size
  on or off, as tall as one
  whether any are on or not, and the "next level" line is always laid out.
- **In the popup, tiles two across rather than a row with a switch each** (owner's choice). Four
  across leaves a tile too narrow for its name and bonus on a phone; three across leaves a ragged
  row at four. The description does not fit a tile, so it sits behind an (i) in the tile's corner and
  opens as a persistent tooltip - the owner chose that over a fixed line under the grid. The (i) is
  a tap target inside a tap target, which is why it keeps its full 48dp.
- **Each tile: the modifier's own icon on top with the (i) level with it in the right corner, the
  name under the icon, the bonus under that** (owner's layout). Pressed in shows as fill, a thick
  primary outline and a check badged on the icon's corner. The "no lengths" icon is a ruler struck
  through (`ic_modifier_no_lengths`), drawn for
  the project: Material Symbols has a ruler but no struck-through one, and the owner chose the ruler
  over the crossed-out eye, which says "hidden" but not what is hidden.
- **The name says what the modifier takes away: "Bez długości" / "No lengths"** (owner's choice over
  "Na ślepo", 2026-11-15). The choice is made before the board is seen and frozen for the level, so a
  name that needs its (i) to be understood costs a wrong press; "na ślepo" could as well mean hidden
  letters or no path highlight. A state, not an order ("Ukryj długości" was the first proposal), so it
  reads right on the card's badge too. It does not mention the word count, which the (i) says.
- **The icon, not the name, shares the top row with the (i)**, centered in a 48dp band - the (i)'s
  touch target - so the two share a center by construction. The name was there first: placed on its
  own the (i) sat a few dp below it, and in a shared row the margins the (i) needed left the name
  about 60dp, which broke "Bez długości" in two with the (i) hanging between the lines. The icon is
  narrow, so the corner beside it is free, and the name below gets the tile's whole width.
- **"Ukryte litery" / "Hidden letters" (`HIDDEN_LETTERS`, 2026-11-15)** covers some letters with a
  star until their word is found. It shipped for a few hours ahead of its rule as `inEffect = false`,
  paying nothing - the owner's choice over paying for a modifier that gave nothing up; the flag stays
  for the next one built that way. Its icon is an asterisk drawn as a vector, centered, because a
  typed "*" sits high like a superscript; the board draws the same shape on a covered cell, and the
  same again, large and faint, behind a letter that was uncovered - as three lines on the canvas, not
  the drawable: a `VectorPainter` caches one bitmap at the last size asked for, and a board draws
  stars at three sizes in a frame, which tore them into fragments. The +5 matches "no lengths" until
  somebody has played both. The rules (`HiddenLetters`), all the owner's:
  - **How many: from half the words, rounded up and at least three, to one per word.** The owner
    first proposed three to one per word; on a fifteen-word board that draws three stars nobody
    notices as often as fifteen, for the same bonus.
  - **Never a word's first letter.** Words are traced from it and hints reveal from it; hiding it
    turns "which word" into "where does it start".
  - **Spread freely, at most one per three letters of a word, never two side by side in it.** One
    per word was proposed and turned down by the owner as too even; uncapped was turned down by
    both, since three stars in one word can only be guessed. Every word has room for one, so a count
    of at most one per word always fits.
  - **A hint marks a covered cell and leaves the star** (owner's choice over uncovering it): the
    letter comes back with the word, which a bought word finds.
  - **Drawn from the level's id, through its own salted SplitMix64 stream**, not `kotlin.random`,
    whose algorithm is the library's to change: the stars must be the same cells on every visit, after
    process death, and across updates. Nothing about them is stored.
  - **The card wears a stamp for it**, in the tile's soft tonal fill beside the red one: covered
    letters change a few cells, not how the whole board is read.
- **The levels tab's tile lists every modifier, as soft chips** (owner's choice): on in the
  secondary tonal fill, off in a gray outline with grayed text. The red stamps it showed first
  shouted from what is a setting; red stays on the level card, which marks the level itself. Its
  coin figure counts only what the board acts on, so it is what the level will pay.
- **The placeholders are tiles, not enum entries.** A modifier exists once it has a rule, a bonus and
  stored keys; until then there is nothing for the rest of the game to read. The three tiles say the
  list will grow, which is what makes a popup worth it for one switch today. Generic "coming soon",
  no names and no figures, by the owner's choice: a named one in a store build is a promise.
- **UI only, knowing the cost.** Uniqueness is proved for the shown length multiset (`Solver`), so a
  board may admit a second tiling into real words once the lengths are hidden. Not measured. Accepted
  because the player's experience of it - a real word refused - is the one ordinary boards already
  give; it can only no longer be reasoned out. Making such boards unique without lengths would have
  made the player's choice part of a board's identity, and that holds for every modifier.
- **Frozen at first open, not first move.** A player who looked, backed out and switched would be paid
  for lengths already read. A change made on the tab applies from the next level, and the tile and the
  popup say so.
- **Giving up is allowed, on the board, never on the tab.** Taking a modifier off mid-level is not
  an exploit - the player pays with its bonus - and it is the one exit from a board chosen harder
  besides paid hints. The tab does not touch the open level, or choosing the next level's modifiers
  would silently cost the current one its bonus. `LevelProgress.drop` takes one modifier off, never
  adds.
  - **One "Modifiers" button under the hints, on every level, grayed out when nothing is left to take
    off** (owner's choice, 2026-11-15). It replaced "Show lengths", which served one modifier, came
    and went with it - growing the board when pressed - and left a player stuck under stars with no
    way out. A button per modifier was turned down: each would take a row off the board. Always
    there, so the footer is one height on every level and the board one size whatever was chosen.
  - **It opens a dialog listing the level's modifiers**, each with its icon, what it pays ("+5", as
    on the levels tab - the owner's choice over "-5") and "Turn off"; marking one strikes the name
    and the figure through. **Nothing changes until "OK"** (owner's choice): "Turn off" strikes the row through
    and reads "Turned off", pressed again it takes the mark back, "OK" appears once something is
    marked and applies it all, and closing the dialog any other way changes nothing - so a player can
    change their mind before a choice that cannot be undone and costs coins. A first version acted on
    the tap. One checkbox, "Also turn off for the next levels", off by default, replaces the old
    second button ("Show and turn off", 2026-11-08): the board reaching the choice, never the choice
    reaching the board.
  - **Taking hidden letters off uncovers every star at once, without the faint star behind**: that
    mark means "found under a star", and these were shown, not found.
- **The board says what it is played with as a row of icons, under it and over the lengths**
  (`ActiveModifiers`, owner's choice, 2026-11-15), not in the heading. The heading said "· no
  lengths" while that was the only modifier, to explain the empty row of lengths; with two it could
  only list some, and listing all of them in words would break the line. Icons scale with the count
  and are the ones the popup and the dialog already use. The row is always there on a level, empty
  without modifiers, so the board's size does not depend on the choice - about 24dp on every level.
  It sits over the "Modifiers" button, last in the footer, in the owner's order: traced word,
  lengths, hints to buy, hints bought, modifiers.
- **The bonus: +5.** It was 45 against 25 a board (40 first, 55 asked for and turned down), came down
  with the economy on 2026-11-11 to a flat +10, and to +5 on 2026-11-15 by the owner's choice, when
  modifiers began to stack. A modifier is flipped once and then sets the rate for every level after,
  so a large bonus would make a letter hint cheaper than a level or two and an ad worth very little -
  the stuck player's way out made cheap. How much harder a board is without lengths is unmeasured -
  on a 5x5, barely - and raising a reward later is a gift where lowering it is a nerf. The tile and the
  popup show the extra, not the total: a total was read as on top.
- **Hints do not forfeit the bonus.** Every modifier's bonus added together stays below the cheapest
  hint (40), so no purchase can come out ahead; a forfeit rule would need explaining for nothing.
  `PuzzleTest` holds the sum over every `LevelModifier` entry, so a new modifier cannot break it
  quietly.
- **The card marks a level with no lengths with a red badge** (`NoLengthsColors`, not `error`, which the "in
  progress" dot already wears), on a line laid out whether shown or not.
- **The full clue row is still laid out, invisibly,** so the footer and the board keep the size they
  have in an ordinary level. It leaks how many lines the row wraps to, which only says a long list is
  long.
- **The stored keys were renamed without a migration, twice on 2026-11-15** (`hardMode` to
  `blindMode` to `noLengths`, `openedHard` to `openedBlind` to `openedNoLengths`), by the owner's
  choice: an alpha tester who had hard mode on finds the modifier off, and a level opened hard before
  the update opens ordinary. Accepted before 1.0.0 so the code says what the player sees; from 1.0.0
  a stored key is never renamed.

### Why `LevelProgress` has no setter

The ladder being unskippable is enforced by the type rather than observed by a screen, and every
consumer in the app is typed as the interface. `StoredLevelProgress.jumpTo` is reachable only by a
caller holding the concrete store, and the only such caller is in `src/debug`. The moment the setter
is on the interface, "no skipping ahead" is a convention rather than a fact.

### Why the debug level jump is a pair of source-set files

`app/src/debug/.../debug/LevelJump.kt` holds the real button, `app/src/release/...` an empty body, so
the release APK does not contain the code rather than containing code R8 is trusted to drop.
Verified: the release dex holds neither the class, the string, nor `StoredLevelProgress.jumpTo`.
Editing one file without the other breaks `assembleRelease` only, which is the pair working.
`ShopDebug.kt` is the same kind of pair.

Its strings are hard-coded English on purpose: a debug-only entry in `values/` would be a translation
obligation for a button no player sees.

### Progress reset, and why nothing pays twice

`RavelApplication.resetProgress`, behind a confirmation, wipes both level ladders, the daily history
with the streak, and the lifetime statistics, in every language. **Coins, today's ad allowance and
gift, and settings stay.** The clearing methods sit on the concrete stores, not on `LevelProgress` /
`DailyProgress`, which keep no way to move the ladder.

- **Every language, not one.** The first plan (2026-10-11) reset the current language's ladder only;
  dropped because the daily history and the statistics are not kept per language, so a per-language
  reset could not clear what a player sees as their progress.
- **The wallet is not reset.** A reset to `STARTING_BALANCE` would refill a spent wallet.
- **Nothing pays twice.** The first levels are 5x5 boards solved in seconds, so a reset that paid
  again is a coin farm with a button - and as first built, it was one. Each language's best level
  (`StoredLevelProgress`, `best`) and the latest day that paid (`StoredDailyProgress`, `lastPaid`)
  survive `clear`, and a replay at or below them pays nothing. Both are seeded from the ladder and
  the record on `clear`, for installs from before the keys existed.

---

## The tutorial

### What it is

Decided 2026-11-06.

- **An interactive board, not pages or tips.** The game is a gesture, and reading about a gesture
  teaches it worse than making it. Tips over level 1 were rejected because a generated board changes
  with every generator change and could not be pointed at.
- **Hand-made boards, one per language** (`game/Tutorial.kt`), picked from generator output at rank
  700 and proved unique against the full shipped dictionary by `TutorialTest`. That test is the reason
  a dictionary rebuild can break the tutorial: if it fails, pick a new board, do not loosen the test.
- **Not a `PuzzleMode`**, since a third mode would have to choose a size ramp and a rank cap for one
  board, and it **pays and costs nothing**: a tutorial that pays invites skipping back into it, and
  one that charges for a hint teaches the economy as a toll.
- **Shown on the first launch, after consent, over the whole shell** — not a nav destination, so a
  replay closes back onto the options tab with every tab's back stack intact. Every tab waits for it,
  the daily included, which made the first plan's locking of Daily and Shop moot.
- **Skipping is finishing.** Skip, the back gesture and the final button all write `tutorial_done`. A
  replay from the options tab is one tap with no confirmation and is not written down.
- **Players who already solved a level or a day are treated as taught** (`TutorialSettings.pendingFrom`).
  `levelsCompleted` skips levels passed with the debug jump, so a debug install that only ever jumped
  counts as new.
- **A word only from its first letter.** The first plan had a step showing that either end works; the
  game accepts a word only traced from its first letter, and that is what the tutorial teaches.

### The decoy

Decided 2026-11-07. Players meet real words of a fitting length that the game refuses on every board,
and nothing told them why. The tutorial makes them trace one ("paw" / "man") after the first word and
explains it.

- **No new boards.** Both boards already hold a dozen real words outside the answer. Every four- and
  five-letter decoy runs through a cell of the three-letter answer, so the four-letter word is taught
  first, while the decoy's length is still on the list.
- **The step is skipped, not forced,** when the player's own order has already taken the decoy's cells
  or its length.
- **The decoy is not outlined:** the outline is a hint and hints index the answer. The text names it.

### The correction step

Decided 2026-11-12. The step that says how to undo a path ("Went wrong? Slide back along your trail,
or tap a letter...") passes on a cut, not on a word, and the refused decoy stays on the board for it.

Before, the button under the decoy's explanation cleared the board, so the text arrived with nothing
on it to correct, and the step was chosen by counting found words, so a player who found a second word
early never saw it - the owner, testing it, could not make it appear.

- **A cut is a shorter start of the previous path, the empty one included** (`TutorialViewModel.cutBack`).
  "A letter of the decoy went" was rejected: nearly every move does that, so the step would pass
  without the gesture it teaches. The empty path lets a tap off the board through too; a stricter
  rule risks a player left with nothing to cut.
- **Past the first word, steps come from the lesson's state, not from the count of found words.**
  That closes both ways the step used to be skipped. With no decoy, the step waits for a cut on any
  path; nobody is trapped, since the board can still be finished and the finished step outranks every
  other. The last step's closing sentence is "Finish the board on your own", since two words may be
  left at that point.
- **The second word's outline stays through the last step** until that word is found. The cost,
  accepted: "on your own" beside an outlined letter.
- Rejected: moving the correction sentence into the decoy's explanation (already the longest text),
  and freezing the board during the explanation (made the board look broken).

---

## Screens and layout

### Arranging a screen by its window

`ui/Adaptive.kt` — `AdaptiveScreen { wide -> }`, true when `maxWidth > maxHeight`. A tablet held
upright, a phone in split screen and a folded foldable are all answered without being named.
Material's three window size classes are deliberately not used: nothing here varies on *how much*
room there is, only on which of the two dimensions is scarce.

What each screen does when wide (every portrait layout is unchanged):

- **Board** (`ui/game/BoardLayout.kt`) — grid left, everything else in a column right.
- **Tabs** (`RavelApp`) — a `NavigationRail` down the side instead of a bottom bar, because height is
  what a wide window has least of and the board is square.
- **Level menu** — the play card takes a full-height column, and the road becomes three rows of three
  beside it, sized as *one square that the nine rungs divide*, as the board is. **Both roads hold an
  odd number of levels** (five across in tall, nine as a block in wide) so the level on offer has a
  middle square to sit in; `firstOf` takes half the size rather than a constant of its own so the two
  cannot drift apart. The tall strip went from seven to five on 2026-11-07: at seven a four-digit
  level did not fit its square at larger font scales or on narrow phones.
- **Daily menu** — heading, run and tiles down the left, the week's ladder filling the right, and **no
  scroll at all**: stacked on its side the tab came to nearly twice the height available, putting the
  play tile behind a scroll. `StreakCard`'s `fillHeight` exists for this alone; its rungs size
  themselves off the height they are given, so the seventh day cannot fall off the card.
- **Stats** — two columns of *groups*, never one row of six tiles. The grouping is the screen's whole
  structure, and six tiles across reads as one undivided row of numbers.
- **Shop** — one layout either way: the gift and the ad tile side by side, capped at `TILE_WIDTH`.
- **Options** — the look and feel of the game left (language, theme, vibration, reminders), everything
  else right (tutorial, ad privacy, reset, licenses). Every license is on a page of its own
  (`LicensesScreen`) behind one row since 2026-11-11: the notices had become most of the tab's length.
  Attribution is unchanged - every source named, one tap in, each opening its notice. Still scrolls in
  both arrangements.

### The board's layout

- **`BoardLayout` is shared by both board screens.** It was the last thing still copied into
  `DailyScreen` and `LevelScreen`, and it had already drifted.
- **The board's half is `weight(5f)` against `4f`, a ceiling, not a share.** The square takes whichever
  is smaller of the height and the width it is offered, so past the point where the height binds —
  every phone on its side — extra width is waste on the left and is exactly what the column on the
  right is short of. Half each cost the board a fifth of its size *and* pushed a hint button off the
  bottom of the screen.
- **The board never scrolls**, in either arrangement: a grid that moves under a finger being dragged
  across it is a grid that loses the drag. Every other screen that can overflow a landscape phone
  scrolls (the level menu and the shop gained one for that).

### Why `screenOrientation="fullUser"`

The default is `unspecified`, which most phones resolve to the three orientations that are not
upside-down portrait — and a phone on a charger with the cable at the top is held that way.
`fullSensor` ignores the device's rotation lock, so a player who turned auto-rotate off would still
have the board spin when they lean over.

### Why the menus share one header, aligned by baseline

`MenuHeader` is the one copy for both game menus, and aligns the title and the coin figure by first
baseline, so the date under the daily's title adds height without moving either.

Reported 2026-11-12: the title and the coins moved "by about a pixel" between the game and daily
tabs - measured 8 dp for the title and 2 dp for the coins. Each menu had its own header row centering
against the 48 dp balance button, and the daily's two-line title block set its row's height itself.
Rejected: a fixed 8 dp over the title (matches today's picture and comes apart at a larger font
scale), and an empty second line under the game's title (fakes content to match a height).

### The attention dots

A dot on the daily tab while today's board is unsolved, on the shop tab and on the gift tile while
today's gift is untaken (2026-11-12).

- **`AttentionDot`, not Material's `Badge`.** The `Badge` read as timid: 6 dp at the corner of a 24 dp
  icon, in `colorScheme.error`, which this palette does not set - a brick red close to the brown
  primary in the light theme and a pale pink in the dark one. `AttentionDot` is 10 dp of `#E53935` in
  both themes, with a 2 dp ring in the container's color so it cuts into the icon's outline instead of
  merging into it. Still hung on `BadgedBox`, which places it in the bar and the rail alike.
- **No continuous pulse.** Something blinking at the edge of the eye all day, including while a board
  is being solved, is the pressure the notification's "no guilt" rule already refuses. One pop on
  appearing is the compromise.
- **No dot for ad watches.** A dot lit until three ads are watched pressures toward ads, against the
  rewarded-only promise; the gift is one free tap and goes out with it.
- **The daily dot does not follow `DailyReminder`.** The switch is about the launch question only.
- **The gift's dot sits on the lid's corner** (`GIFT_DOT_SHIFT_X/Y`); in the glyph box's corner it
  hung in the empty band beside the bow, belonging to nothing.
- **The shell follows the date** the way the daily menu does, and refreshes `DailyGift` as it goes -
  which also turns the gift tile back on at midnight for a player looking at the shop.

### Why every coin goes through `CoinAmount`

The alternative already happened: the balance over a board was `"◈ $balance"`, the balance on a menu
was the counted noun from `coins_balance`, and a hint price was a number after a middle dot — three
notations for one currency, none of which taught the player the other two.

`CoinAmount` sizes the icon from the text style it is given, so it fits a top bar and a headline
without a call site picking a dimension, and it carries the counted noun as the row's content
description so screen readers still hear "100 coins" where the screen shows a figure and a coin.
`coins_balance` survives as an accessibility string only; back on screen beside a coin it would give
Polish four forms of a noun the icon does not need.

### The board screen's margins and the board's place

The board screen keeps 6dp all round and 6dp between its bands, not the menus' 20dp and 12dp, and
stacks the heading and the board from the top rather than centering them in the spare height
(owner's choice, 2026-11-15). The board is held back by the screen's width or by its height,
whichever runs out first - on a tall phone with an ordinary footer the width, and once the level's
footer gained the modifiers' icons and button, the height - so both kinds of margin are taken
straight off every cell. 12dp at the sides came first and did not help where the height was the
limit. From the top, the board sits just under the bar and does not drift as
the footer changes; the spare height goes under it. Both modes, since `BoardLayout` is shared.

### A finished board's words

Both completion dialogs and the calendar's day card list the board's words, each opening its meaning
in the browser (2026-11-13). From level 250 a board may draw words down to rank 12 000, later ones
deeper still, and the daily down to 8 000. A player who traces a word they have never seen and
finishes the board still not knowing it is the player who writes "these are not real words" in a
review.

- **Closing the dialog does not lose the words** (2026-11-15). Closing it used to throw the summary
  away, and a player who closed it to look at the board could not get back to the definitions - on a
  level, ever, since nothing else keeps a level's words. The summary now outlives the dialog, and
  "Show summary" under the finished board brings the same dialog back, at once rather than after the
  solved pause. The owner's choice over a separate "Words" button: the dialog already holds the
  words, the next level and, on the daily, the share button, so one way back reaches all three. It
  takes the place of "Next level" on a level (two taps to the next level after closing the dialog,
  accepted) and of the grayed-out hints on the daily, so the footer keeps its height. On a level the
  direct "Next level" button stays only for a finished board with no summary behind it.
- **Each word drawn as the board drew it once found** (2026-11-15, owner's choice - the plain list
  read as a settings screen, not a reward): a row of small tiles in the found colors, capitals, a
  faint star behind a letter that was under one (`SolvedWords.hidden`, levels only). **One tile size
  for the whole list**, set by the longest word between 18 and 28dp, so the words line up like the
  board; per-word sizing looked like mixed fonts, and breaking a long word over two rows read as a
  bug. Only a word too long even for 18dp shrinks on its own. The calendar's day card shares the
  list, so it shows tiles too.
- **The completion dialogs celebrate a fresh finish, once** (`Celebration.kt`): the body grows in,
  the total counts up from zero, and a ring of dots in the board's and the coin's colors bursts from
  behind it. Not on "Show summary" - that summary is already known. No sound; the system's animation
  scale applies. Under the total, **soft chips say what it was for**: the board, and on a level each
  modifier that paid, by its icon - the one place a modifier's bonus is seen being earned. The
  daily's "board" and "day N" rows became the same chips.
- **Folded under one line, and the choice remembered** (`WordListSettings`). The dialog leads with
  what the board paid; a list above the buttons would push the way on below the fold on a short
  phone. A player who opened it once wants it on the next board too.
- **Never on a board being played.** The board must not scroll and the band under it is full.
- **The calendar's words are a record** - see "Records, not derivations".
- **Polish to sjp.pl, English to Wiktionary.** Polish Wiktionary was measured first, 150 random words
  of the shipped dictionary per rank band, counting words with no page at all:

  | rank | no page |
  |---|---|
  | 1-8 000 | 7 of 150 (5%) |
  | 8 000-16 000 | 22 of 150 (15%) |
  | 16 000-30 000 | 28 of 150 (19%) |

  The missing ones (*odmęt*, *dziergać*, *przekątny*, *zaśmiecić*) are the rare words a player wants
  explained. sjp.pl is the source of the Polish word list, so it has a page for every one of them.
  English Wiktionary was not measured - the API rate-limited the check - and was taken for its
  coverage of common base forms like ENABLE's. Wiktionary's search URL, which redirects to an
  existing entry and lists results otherwise, was the one-source alternative and was not chosen.
- **A definition as a third kind of hint** was raised and turned down: it needs definitions on the
  device, with their licenses and their size.

### Why the haptics switch grays out, and only sometimes

The board's haptics obey the phone's touch-feedback setting (`BoardHaptics`), so with that off the
game's switch does nothing either way (reported 2026-11-08). `SystemHaptics` reads
`Settings.System.HAPTIC_FEEDBACK_ENABLED` (public, no permission) and `hasVibrator()` each time the
settings tab starts; on a definite 0, or no vibrator, the switch is disabled - still showing the
player's choice - and the note says why.

Only a definite 0 counts. Android 13's intensity sliders and manufacturers' own screens may not keep
that value in step, and quiet modes can silence touch feedback with no public signal, so the check
can miss a silent phone; it is built never to warn about one that is not. No link to the system
setting: it lives somewhere different on every phone. Not verified on a manufacturer's device.

---

## Language

### Why there is no `android:configChanges`

Below API 33 the player's locale is forced by wrapping the base context in
`MainActivity.attachBaseContext`, and a configuration change the activity swallows is one the system
then applies to those resources — taking the forced locale with it. Recreation re-runs
`attachBaseContext`, which is the whole mechanism; the view models outlive it, so a board being
generated survives the turn. From API 33 a language switch *is* a configuration change, and the
system's rebuild of the activity is what puts the new language on screen.

### Why the language is the system's from Android 13, and why language splits are off

Decided 2026-11-12, after a tester's phone (English, MIUI 14, API 33, Play install of
`0.1.0-alpha.6`) showed Polish boards under an English interface with Polish chosen in the game.

**The splits are off** (`app/build.gradle.kts`). `pm path` listed `base`, `split_config.en`,
`arm64_v8a` and `xxhdpi` - no `pl`. Play installs only the language splits matching the phone, so the
forced Polish locale had no strings and fell back to English; the dictionaries are not split, so the
boards were right. The pre-upload check uses a universal APK, which holds every language, so it could
not have shown it. Two languages of strings are a few kilobytes. Rejected: requesting the split on
demand through Play Core (a dependency, a network wait and a "downloading" state, and nothing for
installs outside Play), and relying on Play fetching it for a per-app language by itself (API 33 and
later only, late, and Play-only).

**From API 33 the game's picker is the system's per-app language** (`LocaleManager`), so Android's own
app-language screen and the game's are one setting, and the system backs it up. Before, the system
screen was obeyed only until the player first chose in the game, after which a pick there changed
nothing, silently. Below 33 the stored code and `attachBaseContext` stay.

- **One store per device, never a mirror.** A copy in the settings file would bring the conflict
  back: "System default" on Android's screen would fall back to the copy, not to the phone.
- **No hand-over of a code stored before the update to 13.** Before 1.0.0 only testers have one.
- **The game's picker stays in the game** rather than sending the player to Android's screen: the API
  keeps the two in step without the player leaving, and an OEM build without that screen would have
  needed a fallback.
- **Read again on every activity creation and every configuration change** (`refresh`), since a
  change made in Android's settings arrives while the process lives. The cost, accepted: a switch
  made there skips the in-game warning about an unfinished board.
- **The activity does not recreate itself from 33**: the system rebuilds it, and a second rebuild
  would race the first.

### Why the language codes carry a region

Decided 2026-11-12. The codes are `pl-PL` and `en-US` everywhere - the picker ("English (United
States)", "Polski (Polska)"), the tag the system is given, the dictionary and filter files, the Gradle
tasks (`buildDictionaryPlPl`, `buildDictionaryEnUs`) and the keys on the device. The region is true of
the data: the English list is ranked by OANC, an American corpus. They were `pl` and `en`; the prompt
was Android's app-language screen listing a bare "English" beside "English (United States)", and the
first answer was to change only the tag the system sees. The owner chose the whole rename instead,
before 1.0.0 while it is cheap.

- **One migration, at start, not one stage per release.** Staging (write new, read both, sweep, drop)
  is for data two versions read at once; a device runs one version at a time, and staging would lose
  data for a tester whose auto-update skipped the sweeping release. `LegacyLanguageKeys` renames keys
  on every process start - idempotent, no flag - and goes before 1.0.0 (`docs/TODO.md`).
- **The old codes are read forever** (`LanguageSettings.byCode`). They sit inside stored JSON until it
  is next written, and a backup from before the rename can be restored at any time; a code nobody
  reads is a saved board that throws on load.
- **Anything from the system is matched by language alone** (`byLanguage`): a phone in `en-GB`, or
  "English (Poland)" picked on Android's screen, is English.
- **The share card says "EN" / "PL"**, not "EN-US": the region describes the dictionary, and to the
  friend reading the message it is noise.
- **Language is still not part of a board's identity**, so no board changed.
- Rejected: a separate `en-GB` with a British dictionary. A third ladder, tutorial, filter set and
  frequency source (BNC's license unverified) for spelling differences; the cheaper answer, if it is
  ever wanted, is to keep words spelled differently in the UK out of the answer pool through
  `excluded-answers-en-US.txt`.

---

## Economy

### The numbers and the ratios

`Rewards.FOR_COMPLETING` (15), `Streak.DAY` (15, `SEVENTH` 50), `Rewards.FOR_WATCHING_AD` (100), against
the `Hint` prices (40 / 100). Around them: `FOR_NO_LENGTHS` (+5), `DAILY_GIFT` (10),
`Streak.REPAIR_PRICE` (100), `ADS_PER_DAY` (3), `Wallet.STARTING_BALANCE` (250).

The ratios are the design, and each is written down where it lives:

- a week of streak pays roughly 1.33× a week of solving (140 against 105) — `StreakTest` guards it as a
  band, deliberately not as two figures;
- an ad pays what six and two-thirds solved boards pay, and exactly one `Hint.WORD`;
- a word costs 2.5 letters, which puts the hint break-even at two letters — `PuzzleTest` guards that as
  a pair of inequalities, not as the ratio;
- every modifier's bonus together stays under the cheapest hint, the gift under half the cheapest hint and under a
  board (`PuzzleTest`), and the repair price equals the ad and exceeds `SEVENTH - DAY` (`StreakTest`).

Moving one number alone silently answers a question those comments already answered. History: the
first three were multiplied by 2.5 together when solving earned 10 coins against 300 a day from ads,
the hint prices came down alone, and pay came down on 2026-11-11 - the next three entries.

### Why playing pays less: 25 to 15 (2026-11-11)

Nobody needed the shop. At 25 a board a word cost four boards, so any player stuck less often than
once in four saw their balance climb for good. A daily-only player was paid 555 a week (175 solving,
275 streak, 105 gift) for seven boards - five and a half words, nearly one per board. Add 600 at
install, and the first reason to watch an ad arrived, if ever, in the second month. Rewarded ads only
earn when a player has a reason to want one.

What changed, together:

| Number | Was | Now |
|---|---|---|
| `FOR_COMPLETING` | 25 | 15 |
| `Streak.DAY` | 25 | 15 |
| `Streak.SEVENTH` | 125 (`5 * DAY`) | 50 |
| level without lengths, in total (then hard mode) | 45 | 25 |
| `DAILY_GIFT` | 15 | 10 |
| `Wallet.STARTING_BALANCE` | 600 | 250 |

A daily-only week is now 105 + 140 + 70 = 315. The ad stays at 100, one whole word, so it is worth six
and two-thirds boards instead of four: being stuck is one ad away from the word rather than an evening
of other boards away. That is the trade, on purpose.

- **Pay was cut, prices were left.** Raising the hint prices was asked about and turned down. Counted
  in boards the cut already made a word two-thirds dearer; raising prices too would have tightened the
  same screw twice before the first turn was measured, would have cut the ad loose from the word it
  buys, and is the one lever the grinding complaint already ruled out. The one argument for it - it
  alone devalues the coins existing testers have banked - was judged not worth the signal it sends to
  the first players.
- **`SEVENTH` stopped being `5 * DAY`.** 75 was the proportional figure; 50 makes the economy leaner
  still, keeps the week inside `StreakTest`'s band and caps a broken run at 35 coins, under the repair
  price.
- **The hard-mode bonus became a flat +10**, chosen over the proportional +12 (27). Since 2026-11-15
  it is the "no lengths" modifier's +5 - see "Level modifiers, and "no lengths"".
- **`STARTING_BALANCE` 250** over the 150 recommended: two words and a letter, about seventeen boards.
- **Existing balances are not touched.** Taking coins back from testers was rejected. The cost is a
  slow signal: players sitting on 600+ will not reach the shop for weeks, so new installs are the ones
  to read the change on (`docs/TODO.md`).

### Why the hint prices came down alone

40 / 100 from 100 / 250, and nothing else moved with them — the one time a number here was changed
alone on purpose. The complaint was grind: being stuck on a board whose only clue is a length is not
difficulty, and pricing the way out at four solved boards made it last longer rather than making the
game harder. The ratio between the two, 2.5, was kept, since it is the break-even rule.

Two consequences are live and are recorded in `Rewards`: an ad buys a whole `WORD` instead of a
`LETTER`, and `ADS_PER_DAY` (3) therefore reaches 2.5× as far as it was tuned to. **If the game turns
out to be winnable through the shop, drop `ADS_PER_DAY` to 2 rather than putting the prices back** —
that takes the shortcut from the player abusing it instead of from the player who was stuck.

### The daily gift: why it exists, and why 10

One tap in the shop, once a day, `Rewards.DAILY_GIFT` coins. It is the only coin in the game paid for
showing up rather than for playing, which runs against the rule `StreakTest` guards: a game whose
visits pay what its boards pay has an optimal player who opens it and closes it. It exists anyway as a
reason to look into the shop every day, where the ad tile is, and the amount is what keeps it that
rather than income: 70 a week against 105 for solving every daily, two-thirds.

It was 15 against a board worth 25 - the owner's choice over the 10 recommended - and came down to 10
with the board on 2026-11-11, because `PuzzleTest` holds it under half the cheapest hint and under
`FOR_COMPLETING`: a tap that pays what a board pays is a game that has stopped paying for boards.

Its day rule is `AdAllowance`'s three-way comparison, stored as the day of the last claim in
`ravel-ads`: a restore on the same day keeps it taken, a clock wound back gets nothing, and a progress
reset does not touch it.

### Why `STARTING_BALANCE` cannot be raised after release

Raising it silently grants coins to anyone who has not spent one. A grant has to be written instead.

### Coins for real money: planned, after accounts

Decided 2026-10-11, not built. Coins sold through Google Play Billing as three consumable packs
(around 500 / 1 500 / 4 000 coins - 5 / 15 / 40 word hints - with the bonus on the larger ones; prices
live in the Play Console, the code knows only product ids).

- **Verified on a server, not on the phone.** Purchases wait for the account layer (Firebase Auth +
  Firestore). Checking on the device alone was the faster option and was turned down: a patched client
  grants itself coins, and a refunded purchase keeps them, with nothing on the device able to notice
  either.
- **The buyer is identified through Play Games Services**, signed in silently at launch and handed to
  Firebase as a `PlayGamesAuthProvider` credential, with no sign-in button in front of the game.
  Chosen over an invisible Firebase anonymous account created at the first purchase: an anonymous
  account belongs to one device and is lost with it unless linked to Google first, which is exactly
  the paying player's coins. Play Games follows the player to a new phone with nothing to remember.
- **`ravel-wallet` stays in the backup rules until then.** A consumable cannot be restored from the
  store, so dropping the wallet from backup would make a paying player lose what they bought on every
  new phone. "Spend, then restore an older backup" stays possible until the balance is server-owned,
  and it is the cheater's own game it spoils.
- Open when this is built: whether buying requires signing in, what happens to a device balance on
  first sign-in, and the privacy-policy and Data safety changes purchases bring. Which paid product
  comes first waits for analytics data (`docs/TODO.md`).

---

## Ads and consent

### Why consent is gathered before the game is drawn

`setContent` shows `LoadingScreen` until `AdConsent.settled` turns true, so the form is never a dialog
landing on a screen the player had already started reading. The wait is capped at `SETTLE_TIMEOUT`
and the timer is deliberately not canceled — a word puzzle that works offline must never have a launch
that depends on a network, and past the cap the form arrives on top of the game.

**A launch with no connection gets no answer, and the shop asks again** (2026-11-13). The consent
service unreachable, nothing is decided and `canRequestAds` stays false for the rest of the process,
which the ads show as `RewardedAds.State.BLOCKED`. The shop said "Ads are off, because of the privacy choice you made",
which was false for nearly everyone who saw it: a player who refuses in the form has still answered,
`canRequestAds` turns true, and the SDK serves what the answer allows or nothing (`UNAVAILABLE`). The
tile now says that the choice needs a connection and asks again on a tap (`AdConsent.retry`), so a
form that comes up does so in answer to the tap, never unasked. Found by the smoke tests, which run
offline. Rejected: retrying on every shop visit (a form could open on a player who came for the gift)
and on network changes (a form over a board).

### Where an ad can start, and the dialog that only points

**Two call sites start an ad: the shop's tile and the streak repair dialog** (since 2026-11-11). The
moment a run is about to be lost is the moment an ad is most worth watching, and sending the player to
the shop and back would lose most of them on the way. It is still an ad the player asked for, by a
button naming what it buys, so the privacy policy's promise - rewarded only, nothing unasked - holds;
its wording says "for in-game coins or for saving your daily streak".

**The "stuck?" dialog on a board is the one thing in the game that interrupts unasked, and it starts no
ad**: it points at the shop, with a way out. A repair-style "watch now" button there was not chosen;
the shop keeps that role.

- **99 seconds of solve-clock time without a word.** 23 was the first figure and a guess; a player on a
  6x6 or larger routinely looks for a minute or two before the next word shows itself, so 23 would
  have interrupted nearly every board in the middle of the thinking the board asks for.
- **Once a day, not once a board**, counted like the gift (`settings/StuckOffer.kt`, in `ravel-ads`).
- **Never when it leads nowhere**: the day's watches spent, ads not allowed by consent, a review, the
  tutorial.

### Why the reward amount is a local constant

Never the one the AdMob console attaches to the unit — that value is remote configuration that would
retune a shipped economy with no build and no review. The reward callback is a signal that the ad was
watched, nothing more.

### Why `ad_test_devices` is not limited to debug builds

The AdMob ids in `res/values/ads.xml` are live (publisher `3410450408196791`), so every request the app
makes is real inventory. An impression from a developer's own phone is real, a tap on it is a click on
your own inventory, and AdMob answers invalid traffic by suspending the account rather than by
discounting the click.

A device whose hash is not in that array is unprotected, and testing a rewarded ad means tapping it —
that is how you find out the reward arrived. Naming a device costs that one device its real ads, while
a list that stopped working in a signed build would put a developer in front of live inventory with
nothing at all.

### Why the analytics toggle is not built

Firebase Analytics is not in the project. The toggle ships with analytics, not before it, since a
switch wired to nothing is a lie told to the player.

---

## Shipping

### What R8 already broke

`play-services-ads` pins `work-runtime` → `room-runtime 2.2.5`, Room finds its generated database by
`Class.forName`, and full mode renamed it. The app died before its first frame while `assembleRelease`
and the whole unit suite stayed green.

The keep rule and the reasoning are in `app/proguard-rules.pro`, which is deliberately short.
Installing a release build and opening it is therefore a release-checklist step and not an optional
one, and it is the step to repeat whenever the ads SDK moves.

### Why a release is built from a clean project

`0.1.0-alpha.3` crashed on every second drag across the board, on every board, tutorial included:
`ClassCastException: Puzzle cannot be cast to MutableState` in `rememberUpdatedState`, inside
`BoardGrid`'s restart lambda. Taps still worked, because a tap recomposes the board from its parent and
never goes through that lambda. The source was fine. The bundle had been made with Generate Signed App
Bundle on top of an `app/build` whose release classes came from incremental compiles on two different
days, and six of them (`LevelScreenKt`, `DailyScreenKt`, `LevelProgressState` among them) differed from
a clean compile of the same source: the Compose compiler had read stability at runtime
(`SavedBoard.$stable`) where a clean compile writes a constant, and added `changed()` checks to match.
On that input R8 kept the restart lambda's `$changed` as a field (`f$7`) instead of propagating it as a
constant, and the board's slots came out misaligned.

Measured on an emulator with a two-drag script, ten tries or more each:

| Build | Crashes |
|---|---|
| The alpha.3 bundle from Play | 10 / 10 |
| Same stale classes, full R8 | 5 / 5 |
| Same stale classes, `-dontoptimize` | 0 / 8 |
| Clean compile, full R8 | 0 / 5 |
| Debug | 0 / 5 |
| alpha.4, built after Clean Project | 0 / 10 |

It takes both halves; either alone runs. Whether the fault is R8's or the incremental output's was not
settled. The first suspect, commit `4445de3` (the off-board tap, whose line the crash pointed at), was
only where the misaligned slot was read.

Ruled out:

- **`incremental = false` on `compileReleaseKotlin`.** AGP 9.5.1 overrides it after configuration; the
  task still compiled incrementally. Setting it in a `doFirst` does work, but rests on a property AGP
  evidently manages itself, and the day it stops being read the build falls back to incremental without
  a word. A manual step that can be forgotten was preferred to an automatic one that can fail silently.
- **`-dontoptimize` for good.** It hides this one shape and costs a quarter of the APK (4.04 MB to
  5.09 MB).
- **Release bundles from CI.** A fresh checkout is clean by definition, but the upload key would have
  to live in GitHub secrets, which is not worth it for one developer.

The drag check on the bundle stays whichever way the build is made: it is what catches the next tool
bug of this kind, and "install and open", the step R8 earned the first time, did not.

### The smoke tests

`app/src/androidTest/.../smoke`, four tests on the real activity, run by hand on an emulator before a
signed bundle (2026-11-13): every tab keeps its back stack, a board turned mid-build arrives in both
arrangements, every tab and a board fit on the phone's side, and a hint bought on a board is on disk.

- **Against the debug variant**, which is not shrunk, so they say nothing about R8. The hand check on
  the release bundle stays.
- **By hand, not in CI.** An emulator in CI is minutes per run and a flaky surface; the four steps CI
  runs are the ones a commit can break silently, while these guard a release.
- **Through the orchestrator with `clearPackageData`**, so each test starts as a fresh install - the
  tutorial, the starting balance, level one. The cost is that they wipe the app's data wherever they
  run, and debug shares the release's application id, hence emulator only and `ANDROID_SERIAL`.
- **Offline, in airplane mode** (`OfflineRule`). From the EEA the consent form opens on every fresh
  install as a web view no test can answer reliably; offline the request fails and the game opens at
  once. It also keeps tests from asking live AdMob ids for inventory.
- **Texts from the app's resources**, so the tests pass whatever language the emulator is in - they
  first ran on a Polish one.
- **Process death is half covered.** A test cannot kill its own process, so `SavedProgressTest` reads
  the disk through fresh stores; the screen restoring it is a hand step on the release build.
- **Espresso 3.7.0 is named explicitly**: the version Compose's test library brings calls
  `InputManager.getInstance`, which recent Android no longer has, and every test failed on its first
  wait.
- **The turn may not land mid-search on a fast machine.** Level 238 was among the slowest on a phone;
  on a desktop emulator it built in seconds. The test still proves the board arrives after a turn in
  both arrangements; it cannot force the race.

### Why `compose-group-mapping` is pinned in `app/build.gradle.kts`

Since AGP 9.4.1 / Kotlin 2.4.21, release builds fail without it: `Could not find
org.jetbrains.kotlin:compose-group-mapping:2.2.10`. The Compose compiler plugin requests that artifact
at the Kotlin version it believes is running, and under AGP 9's built-in Kotlin that is AGP's bundled
2.2.10, which was never published. Debug builds do not shrink and never resolve it.

Pinned to `libs.versions.kotlin` rather than switched off with `includeComposeMappingFile = false`,
which also builds but leaves release stack traces through composables unreadable. Rolling AGP back was
the third option and was not wanted. The pin is a workaround: when commenting it out still lets
`:app:assembleRelease` pass, delete it. Checked 2026-11-13 on AGP 9.5.1 / Kotlin 2.4.21: still
needed, the same error without it.

### What the backup rules carry, and why

Every preference file is included, by name, in both rule files.

- **`ravel-ads`**, for the three-way day comparison in `AdAllowance.remainingFrom`, which is what makes
  a restore honest — same day keeps the watches spent, later day is a fresh allowance, *earlier* day
  gives nothing, which answers winding the clock back. `DailyGift` keeps its claim in the same file
  on the same rule, and `StuckOffer` its day.
- **`ravel-wallet`** - see "Coins for real money".
- **The commit that adds in-app purchases has to revisit this**: a restored balance is a restored
  balance, and "spend, then restore" can only really be answered by a server.

---

## Deliberate omissions

Features whose absence is a decision. Do not report these as gaps.

### No sound, anywhere

Decided 2026-08-10. There is no `SoundPool` or `MediaPlayer` in `:app` and that is the intended state.
Feedback to the player is haptic (`settings/HapticSettings.kt`, `ui/board/BoardHaptics.kt`).

Adding audio would mean assets, a settings toggle, and a decision about silent mode — cost without
return for a word puzzle. If it ever ships, the toggle goes beside haptics in
`ui/settings/SettingsScreen.kt`, same row structure.

### No screen-reader support on the board

Decided 2026-08-10. `ui/board/BoardGrid.kt` is a bare `Canvas` with no `semantics`, and the grid has no
per-cell descriptions.

The core interaction is dragging a finger along an orthogonal path; usable TalkBack support needs a
second, parallel input model, not labels. The `Canvas` is separately a performance decision — the
largest board, 10x10, is 100 cells.

Accessibility **off** the board still applies in full: `ui/Coins.kt`, `CalendarCard.kt`,
`StreakCard.kt`, `SettingsScreen.kt` and the rest carry semantics, and new screens are expected to.

### No skip, no give-up, no free reveal

Decided 2026-10-11, reconfirmed 2026-11-06. Being stuck on a board is the game asking the player to
think, and a way past it that costs nothing would answer the question for them. The only exit is
hints, paid for in coins. "Why the hint prices came down alone" was about the *price* of the exit being
a grind, not about there being one. Skipping is also ruled out structurally - see "Why `LevelProgress`
has no setter".

### On Polish boards every `ć` marks the end of a path

Measured 2026-11-06: 1249 of the 3877 words of 4–12 letters within rank 4000 end in `ć` (infinitives),
and the letter almost never occurs mid-word, so each one hands the player a path end for free. A sample
6x6 showed three, i.e. three of its eight ends; two sample 10x10s showed six of nine words and seven of
eight, since the long Polish words are mostly verbs.

The obvious fix is capping infinitives per board. Left alone on purpose, the owner's decision of
2026-11-06.
