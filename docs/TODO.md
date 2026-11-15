# To do

Work left, in the order it blocks a release. **Delete an item in the commit that finishes it**, and
add one in the commit that leaves something for later. Rules live in `CLAUDE.md` and their
arguments in `docs/DECISIONS.md`; an item here points at them rather than repeating them.

## Before the beta

1. **Telemetry on the first build from Play** - checked on a locally signed release (2026-11-15):
   consent reaches Firebase, events and parameters arrive, the statistics switch stops them, a crash
   reaches Crashlytics. Left for the first Play build: events still arrive (the Play signing key's
   SHA-1 in the API key restriction), and the first crash with the game's own frames reads
   deobfuscated (the mapping file uploaded).

## Code

1. **Tests for the reset guard**: a level at or below the best pays nothing after `clear`
   (`StoredLevelProgress`), a day at or before `lastPaid` pays nothing (`paidBeforeReset`,
   `DailyViewModel`), and both keys are seeded from the ladder and the record on `clear`. Pull the
   decisions into pure functions where the stores make them hard to reach.
2. **The `compose-group-mapping` pin** in `app/build.gradle.kts`: on every AGP or Kotlin bump,
   comment it out and run `:app:assembleRelease`; delete it once that passes.
3. **Measure the leaner economy** (2026-11-11, `DECISIONS.md`, "Why playing pays less"): rewarded
   impressions per DAU from AdMob against DAU from the Play Console, before and after the release
   that ships it, read on new installs - existing testers are still spending their old balances.
   Analytics came after that release, so it has no "before"; from here on `ad_watched` and
   `hint_unaffordable` per active user say the same thing more directly.
   If the game reads as buyable, the next step is `ADS_PER_DAY` 2, not higher hint prices.
   **Until that measurement the economy gets no leaner.** Up to some ten thousand daily players the
   money is in whether a player comes back and how they rate the game, not in how scarce coins are:
   a player who leaves frustrated watches no ads at all. Work that keeps players goes before
   accounts and purchases.
4. **Delete `LegacyLanguageKeys`** (and its call in `RavelApplication.onCreate` and its test)
   before 1.0.0, once every tester has launched a build that has it. Keep the bare `pl` / `en`
   alias in `LanguageSettings.byCode` for good - see `DECISIONS.md`, "Why the language codes carry
   a region".
5. **The last "coming soon" modifier** (`COMING_SOON_MODIFIERS` in `LevelMenuScreen`): it needs
   a name, a bonus inside the sum `PuzzleTest` holds, and its stored keys before it replaces a tile.
   Not chosen yet. Candidates (2026-11-15), each leaving the board as it is:
   1. **"Od najdłuższego" / "Longest first"** - the favorite. Words count only in order, longest
      to shortest; a right word traced too early is refused like a wrong path, with no sign it was
      right (a "not yet" would confirm the answer for free). It takes away a tactic - finding the
      easy short words first to shrink the board - where the other three take away information.
      Open: with "No lengths" on, the player does not know which word is longest; allow that pair
      on purpose or rule it out.
   2. **"Jednym ruchem" / "One stroke"** - a word in one continuous drag; lifting the finger clears
      the path, no taps. Simple to state, but it tests the fingers rather than the head: the finger
      covers the letters, and an 11-letter word on a 9x9 board turns into a dexterity test. It
      takes back what `Puzzle.path` surviving a lift is there for.
   3. **"Pamięć" / "Memory"** - letters shown for a few seconds, then dark, the one under the
      finger lit. A different feel, but the same axis as "Hidden letters", turned up.
   4. **"Na czas" / "Against the clock"** - the bonus only inside a limit set by the board's size,
      on the existing solve clock. Advised against: it fights "being stuck is meant to make the
      player think", the phone interrupts, and the limit has to be tuned per size.
   5. **"Bez podpowiedzi" / "No hints"** - advised against: a player who never buys hints gets the
      bonus for nothing, which is what `inEffect` already refuses.

   Or none: `COMING_SOON_MODIFIERS` to 0 and three tiles, until analytics show anyone turns the
   modifiers on. A locked "coming soon" tile is a promise that has to be kept by 1.0.0.

6. **The coin-pack tap event**, planned with analytics and not built: the shop's coin packs are a
   heading and a "coming soon" pill, and nothing there takes a press. Decide what a tap on them
   answers (a note that they are coming? nothing?) before counting the taps; "Coins for real money"
   below waits on that count.

## Planned - decided, not built

Requirements are fixed; build to them, do not reopen them.

1. **Accounts** - Google sign-in through Firebase Auth, optional. Every requirement in `CLAUDE.md`,
   "Commitments made in the privacy policy", applies. Not started before the beta; taken up once
   it is out. Until then Android Auto Backup is what carries progress to a new phone.
2. **Tutorial "done" moves to the account**, with accounts.
3. **Coins for real money** - after accounts. Three consumable packs, verified on a server, buyer
   identified through Play Games Services signed in silently at launch (`DECISIONS.md`, "Coins for
   real money"). **Which paid product comes first waits for the analytics data**: many taps on the
   coin-pack tile and many refused ads among players who keep coming back point at a one-off
   purchase that grants the day's ad rewards without the ads (Play restores it, no server needed,
   but it needs the "device-only purchase" rule reopened); few of either mean nothing to build yet.
   Still open when it is built: whether buying requires signing in, what happens to a
   device balance on first sign-in. Revisits the backup rules; the privacy policy and the Data
   safety form change in the same commit. Ships with Play Billing protection switched on in the Play
   Console (Protected with Play) and a Play Integrity verdict checked by the server that verifies
   the purchase - both mean nothing before there are purchases and a server to check them.
4. **Leaderboard** - much later. Anonymous nickname chosen by the player, opt-out toggle shipped
   with it. A score is accepted only with a Play Integrity verdict checked on the server: without
   one, a modified client posts whatever it likes.
