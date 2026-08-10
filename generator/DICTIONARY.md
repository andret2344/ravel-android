# Game dictionaries — sources, licences, reproduction

The game generates boards in two languages. Each has its own alphabet, its own dictionary and its
own blocklist — none of the three carries across.

| | Polish | English |
|---|---|---|
| admissibility | SJP.PL word-game list | ENABLE |
| licence | **CC BY 4.0** (choice of GPL 2, LGPL 2.1, MPL 1.1, Apache 2.0) | **public domain** |
| frequency | KWJP100, IPI PAN | OANC (Open American National Corpus) |
| licence | **CC BY 4.0** | **unrestricted**, commercial use allowed |
| ARF | supplied by the source | computed by `buildDictionaryEn` |
| inflection | PoliMorf (SGJP) | WordNet 3.0 (Princeton) |
| licence | **BSD 2-clause** | **WordNet licence** (permissive, notice required) |
| words after intersection | 51 685 **lemmas** | 42 554 **base forms** |

## Licences

### Why these sources

**Polish, CC BY instead of GPL.** The SJP.PL lists are offered under a **choice** of GPL 2, LGPL 2.1,
CC BY 4.0, MPL 1.1 or Apache 2.0. The licences are alternative, not cumulative, so choosing CC BY 4.0
means copyleft does not apply and the app can stay closed.

**Rejected for share-alike** (it would infect the result): `hermitdave/FrequencyWords` (CC BY-SA 4.0),
`rspeer/wordfreq` — data under CC BY-SA 4.0, the BNC/Leech lists (CC BY-SA 2.0 UK).

**Rejected for having no licence:** Norvig's `count_1w.txt`. The page licenses the code as MIT, says
nothing about the data, and the data comes from an LDC corpus. No licence is not a free licence.

**PoliMorf** entered the pipeline on 2026-08-08 precisely because it is 2-clause BSD — no copyleft,
minimal attribution, and it is the only complete inflectional resource for Polish under a free
licence. Fetched from the IPI PAN mirror rather than `download.sgjp.pl`: the latter has a **revoked**
TLS certificate (`CRYPT_E_REVOKED`), which is a stronger signal than OANC's merely expired one and
not worth working around.

### Attribution obligations — placed in the app

Carried by the settings tab, under "Word sources": every source below is a row, and each row opens a
page with its notice and, for the two that require it, the licence text itself. The strings live in
`app/src/main/res/values/strings.xml` as `licence_*`, the list in `app/.../ui/about/Licences.kt`.

#### Polish sources

Required. The notice must contain the following (the wording may be reworked, the elements
may not disappear):

> Polish dictionary based on the **SJP.PL Dictionary** (https://sjp.pl) and on the **KWJP100**
> frequency lists — Corpus of Contemporary Polish, Institute of Computer Science, Polish Academy of
> Sciences (https://kwjp.pl). Both resources are made available under
> **Creative Commons Attribution 4.0 International**
> (https://creativecommons.org/licenses/by/4.0/). The material has been modified: a subset of words
> was selected and ordered by frequency.

That last sentence is not decoration — CC BY 4.0 requires indicating that the material was modified.

PoliMorf is **BSD 2-clause**, which requires neither a modification notice nor a mention in the UI,
but does require the copyright notice to be retained on redistribution. The game dictionary is a
derivative work, so the safest course is to add it to the same screen:

> Polish lemmatisation based on **PoliMorf** (Linguistic Engineering Group, ICS PAS, and the Grammatical
> Dictionary of Polish team), licensed under **BSD 2-clause**
> (https://morfeusz.sgjp.pl/doc/license/).

#### English sources

**WordNet** requires the Princeton copyright notice to be retained — the full text sits in the header
of every `index.*` file in `generator/data/wordnet/`. To the same screen:

> English lemmatisation based on **WordNet 3.0**, Princeton University
> (https://wordnet.princeton.edu/). WordNet 3.0 Copyright 2006 by Princeton University. All rights
> reserved. Used under the terms of the WordNet licence.

The remaining English sources require no attribution. ENABLE is public domain ("The ENABLE master
word list, WORD.LST, is herewith formally released into the Public Domain") and OANC imposes no
restrictions. Naming them anyway is honest but optional.

## How the words are chosen

### The Polish dictionary is lemmas, not forms

Until 2026-08-08 the Polish dictionary held 247 012 entries, **74% of them inflected forms**. A board
was far likelier to spell `dokumentację` or `wystąpiło` than `dokumentacja` or `wystąpić` — a bad
trade in a game whose only clue is a word's length. A player recognises a word they know; an oblique
case of a word they know is not the same thing.

PoliMorf supplies the form → lemma map, and the pipeline keeps only lemmas.

The map is needed for a second reason that matters more than the filter. **ARF is measured on
forms**, and a lemma is often rare *as a form* — Polish writes `robi` and `robił` far more often than
the infinitive `robić`. Ranking lemmas by the ARF of their own form would bury the commonest verbs
in the language. The pipeline therefore sums the ARF of every form of a lemma, which puts `robić`
around rank 100 rather than 8000, where its infinitive sits alone.

An ambiguous form (`mam` is both `mama` and `mieć`) splits its ARF evenly between its lemmas.
Deciding which lemma a corpus occurrence meant needs a tagged corpus, and KWJP's orthographic list
is not one. Splitting evenly is at least symmetric.

### English asks the question the other way round

The same problem at 30% of the loaded dictionary rather than 74% — and it **cannot be solved the
same way**. WordNet indexes only nouns, verbs, adjectives and adverbs. A "keep the lemmas" filter
would delete `the`, `and`, `for`, `with`, `than`, `were`, `been` — exactly the short, common words a
readable early board is built from.

The English pass therefore asks: **is this word a regular inflection of something that is itself in
this dictionary?** If so the word goes and its ARF goes to the base. A function word is not an
inflection of anything, so it survives with nobody having to maintain a list of function words — and
such a list is what this would otherwise need, with all the judgement calls that go into one.

The suffix rules are deliberately modest: they recognise only the regular endings, because an
irregular form (`went`, `mice`) is a lemma in WordNet in its own right and never reaches them. Every
candidate base is checked against the index before it is believed, so an over-generous rule costs
nothing.

Two decisions worth remembering:

- **Bare `-s` restores no `e`.** `hopes` → `hope` comes out of plain stripping, while the `e` variant
  turned `his` into the archaic verb `hie` — the only false positive this rule set ever produced.
- **No base shorter than 3 letters is proposed.** Such a base cannot be a board word, so folding into
  it would delete a playable word in favour of an unplayable one.

The judgement belongs to WordNet, not to the rule. `stressed` stays, because WordNet knows it as an
adjective and not only as the past tense of `stress`. So do `greatest`, `longest`, `fastest` and
`latest` — all adverb lemmas. `largest`, `highest`, `smallest` and `strongest` are not, and were
folded away. It is the same principle that kept `leżąca` on the Polish side.

### The alphabets

They follow from the data: Polish is the 32 Polish letters plus `q`, `v`, `x` (loanwords admissible
in word games), English is 26 letters. Lower case only, no hyphens or spaces.

### What the pipeline does

1. Takes words of length 3–12 from the admissibility source, composed only of the language's own
   alphabet.
2. **Polish only:** reads PoliMorf and keeps only common-noun lemmas. Rows qualified `nazwisko`,
   `geograficzna`, `imię` and the blank one (about 0.5 million together) drop out — a board spelling
   somebody's surname is not a puzzle.
3. Intersects with the frequency data. A word absent from the corpus falls out.
   **Polish only:** a lemma's ARF is the sum over all its forms, not the ARF of its own form. Every
   form on the frequency list contributes, including forms longer than 12 letters —
   `dokumentacjami` is unplayable and is still evidence that `dokumentacja` is common.
   **English only:** regular inflections fold into their base when the base is itself in the
   dictionary, and hand it their ARF. The reason for summing is the same as in Polish — deleting
   `shadows` without crediting `shadow` would rank the survivor on a fraction of the evidence for it.
4. Sorts by descending **ARF** (average reduced frequency). ARF discounts words that look frequent
   only because they cluster inside a single document, which raw counts reward by accident. For
   Polish the ARF comes ready in KWJP100; for English it is computed from token positions in OANC
   (Savický–Hlaváčová, cyclic gaps).
5. Removes words matching `blocklist-<language>.txt` — see "Blocklists" below. Every removed form is
   written to `build/removed-<language>.txt`.
6. Writes the words alone, one per line. **The line number is the difficulty rank.**

Summing ARF over a lemma is an approximation, and a conservative one: ARF penalises a word for
clustering in few documents, and a lemma is spread over at least as many documents as any one of its
forms, so the sum **understates** dispersion rather than flattering it. It understates every entry
the same way, which is all a ranking needs.

Proper names drop out twice over: the admissibility lists do not contain them, and Polish adds the
PoliMorf qualifier filter. Neither catches the relational adjectives derived from place and family
names that PoliMorf marks `pospolita` — see "What lives in the tail".

### Blocklists

Matched from the **start** of a word, not as a substring. The first version matched substrings and
cut down innocents: `cechuje`, `wymachuje` and `dowcipach` each contain a vulgar stem by accident.
This is a variant of the Scunthorpe problem.

Anchoring has a cost of its own, and it has already been paid twice — once in each language, for
different reasons.

**Polish.** The list of prefixed stems for `pierd` did not include `popierd`, so `popierdolić` got
through. In the old dictionary that verb's forms sat at rank 130 000+, outside the loaded range, and
the problem was unreachable; in the lemma dictionary `popierdolić` ranks **30 322**, well inside it.
Added 2026-08-08.

**English.** Anchoring's blind spot here is compounding, not prefixing:

| | Polish | English |
|---|---|---|
| derivation | prefixing | compounding |
| the stem lands | at the start — the anchor catches it | **at the end — the anchor cannot see it** |
| what got through | `popierdolić` (rank 30 322) | `bullshit` (11 358), `horseshit` (33 925) |

English compounds cannot be caught by a rule; they have to be named one at a time. A scan for words
**ending** in a listed stem finds them, and everything else that scan turns up is innocent (`grape`,
`drape`, `scrape`, `swank`, `serape`, `therapist`). The test
`no shipped dictionary carries a vulgar compound` holds that line.

**After any change to the loaded range, scan the band that has just become reachable.** Both bugs
above were invisible until a cap moved — so this is the companion rule to every threshold in
"How much of the dictionary is loaded" below.

Deliberate compromises worth knowing about:

- **Polish**, 62 lemmas removed, no false positives. The only word containing a blocked stem past the
  first position and kept anyway is `dowcipkować` — which is precisely the evidence that anchoring is
  doing its job.
- **English**, 45 forms removed, several of them innocent: `niggard`, `niggardly`, `niggle`,
  `niggling` (ordinary words caught by the stem `nigg`), `rapeseed`, `pussycat`, `pussyfoot`,
  `shittim` (a biblical wood). Kept out because the surface form alone is inflammatory — but that is
  an editorial decision rather than a technical one, and it can be reversed.

## Reproducing the dictionaries

### A note on downloading OANC

The TLS certificate on `anc.org` has expired. Downloading requires disabling certificate verification
(`curl -k`), which means the file's provenance cannot be confirmed. The risk is low — it is a text
corpus rather than code, and the content passes through the alphabet filter and the intersection with
ENABLE anyway — but it is not zero.

Checksum of the 2026-08-05 download, for comparison on a re-fetch:

```
SHA-256  5a26559a1becba41a527cb674fff4fb9c4fb70b276f60422c4f07a0ef23fd867
file     OANC_GrAF.zip, 655 230 430 bytes
```

anc.org publishes no checksum of its own, so the above is not proof of authenticity — it only checks
whether a later download produced the same file.

### The commands

```bash
mkdir -p generator/data

# --- Polish ---
# The file name changes with each release; check https://sjp.pl/sl/growy/ for the current one.
curl -L -o /tmp/sjp.zip https://sjp.pl/slownik/growy/sjp-20260803.zip
unzip -p /tmp/sjp.zip slowa.txt > generator/data/sjp-words.txt

curl -L https://raw.githubusercontent.com/ipipan/kwjp100-varia/main/freqlists/kwjp100-slowa-orth_lc-all.csv.gz \
  | gunzip > generator/data/kwjp100-orth_lc-all.csv

# 37 MB compressed, 391 MB expanded — read straight from the gzip, do NOT unpack it.
# IPI PAN mirror, because download.sgjp.pl has a revoked certificate.
curl -L -o generator/data/polimorf.tab.gz \
  "http://zil.ipipan.waw.pl/PoliMorf?action=AttachFile&do=get&target=PoliMorf-0.6.7.tab.gz"
# SHA-256 of the 2026-08-08 download:
# e9b2633bf384b7a778bdb4d17dd4c8055b1d040da9f5113dae513718854216b2

# --- English ---
curl -L -o generator/data/enable1.txt \
  https://raw.githubusercontent.com/dolph/dictionary/master/enable1.txt

# 625 MB, expired certificate — see the note above
curl -kL -o /tmp/oanc.zip http://www.anc.org/OANC/OANC_GrAF.zip
# unpack only the .txt files into generator/data/oanc/ (8824 files, 115 MB)

# WordNet — only the index.* files are needed, not the synset data.
curl -L -o /tmp/wn.tar.gz https://wordnetcode.princeton.edu/3.0/WNdb-3.0.tar.gz
mkdir -p generator/data/wordnet
tar xzf /tmp/wn.tar.gz -C /tmp
cp /tmp/dict/index.{noun,verb,adj,adv} generator/data/wordnet/

./gradlew :generator:buildDictionaries
```

## How much of the dictionary is loaded

### Thresholds

`Difficulty` controls only **how much of the dictionary is loaded**, not how hard a board's words
are. That is a separate axis, `BoardSpec.maxWordRank`, whose curve lives in `PuzzleSpec.rankFor`. The
distinction matters: every word in the trie is a word `Solver` must rule out before calling a board
unique, so loading fewer words does not make boards easier — it makes the uniqueness claim weaker.

`Difficulty.EASY` = rank 10 000, `Difficulty.HARD` = rank 35 000.

The level curve (`PuzzleSpec.RANK_TIERS`, ENDLESS mode only):

| up to level | 15 | 40 | 100 | 250 | 500 | 900 | 1500 | 2500 | beyond |
|---|---|---|---|---|---|---|---|---|---|
| rank | 1 500 | 4 000 | 8 000 | 12 000 | 16 000 | 20 000 | 25 000 | 30 000 | no cap |

The tiers get longer as they go, because the interesting part of the dictionary is its head: the step
from 1 500 to 4 000 changes what a board asks far more than 25 000 to 30 000 does.

**The curve runs out at level 2500, and that is not a number to raise.** There is nothing above it to
add — 35 000 words is what fits in the heap, and the steps up there are already thin (`czyściutki` →
`rudzik` → `płaszczka`). Making later levels harder needs a second axis, not a deeper cap. The
path-shape dials are the candidate, and they are measured: `minTurnRatio` 0.62 with `maxDirectness`
0.68 costs about 470 ms on a 7x7, but the same band blows the deadline on a 9x9. Shape and size
cannot both be pushed on one board.

**The same rank means different things in the two languages**, which is worth remembering when
tuning:

| rank | Polish, min ARF | English, min ARF |
|---|---|---|
| 1 500 | 3389.1 | 368.9 |
| 4 000 | 1095.3 | 93.5 |
| 10 000 | 298.1 | 19.4 |
| 25 000 | 49.9 | 2.7 |
| 35 000 | 17.1 | 1.0 |

The difference comes from corpus size: 100 million tokens against 14.7 million. Rank still works as a
shared dial, because difficulty follows position in the ranking rather than the absolute ARF.

### Length distribution

This is the table to read before moving any threshold. A cap is only usable when every length a board
can ask for has enough candidates behind it to letter the board without repeating itself — and the
bottleneck is always the **shortest** words. `buildDictionaryPl` prints this table on every run.

Polish (lemmas):

| rank | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 500 | 78 | 160 | 273 | 271 | 258 | 194 | 126 | 73 | 54 | 13 |
| 4 000 | 123 | 317 | 574 | 659 | 692 | 620 | 444 | 290 | 188 | 93 |
| 10 000 | 230 | 620 | 1138 | 1457 | 1648 | 1645 | 1286 | 989 | 621 | 366 |
| 25 000 | 449 | 1227 | 2386 | 3246 | 3898 | 4084 | 3609 | 2836 | 2023 | 1242 |
| 35 000 | 575 | 1543 | 3115 | 4333 | 5236 | 5761 | 5139 | 4219 | 3118 | 1961 |
| 51 685 | 744 | 2000 | 4204 | 5953 | 7410 | 8404 | 7910 | 6623 | 5129 | 3308 |

English (base forms):

| rank | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 500 | 120 | 293 | 277 | 246 | 224 | 149 | 89 | 60 | 33 | 9 |
| 4 000 | 229 | 569 | 602 | 655 | 623 | 479 | 361 | 271 | 145 | 66 |
| 10 000 | 416 | 1077 | 1320 | 1521 | 1501 | 1380 | 1119 | 849 | 546 | 271 |
| 25 000 | 722 | 1860 | 2645 | 3564 | 3807 | 3717 | 3307 | 2582 | 1746 | 1050 |
| 35 000 | 854 | 2246 | 3278 | 4552 | 5084 | 5224 | 4892 | 3984 | 2918 | 1968 |
| 42 554 | 901 | 2504 | 3751 | 5303 | 6049 | 6526 | 6027 | 5042 | 3810 | 2641 |

When a cap leaves a length fewer than `Dictionary.MIN_POOL` = 64 words, the pool widens instead of
starving — otherwise the board would start repeating words.

### What lives in the tail, and why 35 000 rather than everything

Two reasons, and they agree.

**Heap.** Measured by `DictionaryFootprint`, one dictionary resident at a time:

| words | Polish | English |
|---|---|---|
| 10 000 | 10.2 MB | 7.2 MB |
| 25 000 | 24.1 MB | 17.0 MB |
| 35 000 | 33.2 MB | 23.0 MB |
| everything | 48.8 MB | 39.5 MB |

The app goes down to API 24. 48.8 MB resident for a word game is not a figure to hold.

**Content.** Past roughly rank 35 000 the Polish list stops being words a board should spell:
surname-derived adjectives that PoliMorf tags `pospolita` (`ostaszewski`, `rylski`, `czermiński`),
loanwords SJP admits but nobody plays (`steak`, `speech`), dialect (`czerecha`). Level 2000 before
the cut: `ząbkowicki, niejedno, malarka, przyrosnąć, chiński, hajcować, czermiński, młynarski`. After:
`oligarchia, cellulit, kalifat, destylacja, powziąć, zaczepny, zaowocować, truskawka`.

Cutting for heap removed exactly the entries that were the weakest argument for keeping them. In the
25 001–35 000 band place-derived adjectives still occur (`sztokholmski`, `hawajski`, `racławicki`) —
those are legitimate and stay.
