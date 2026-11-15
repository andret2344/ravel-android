# To do

Work left, in the order it blocks a release. **Delete an item in the commit that finishes it**, and
add one in the commit that leaves something for later. Rules live in `CLAUDE.md` and their
arguments in `docs/DECISIONS.md`; an item here points at them rather than repeating them.

## Before the beta

1. **Remove the `RavelGen` log line** in `Puzzles.generate` - the phone table it was for is recorded
   (2026-11-14), unless it stays on as the `measure` build's timing.
2. **Release signing.** The upload key exists (alias `ravel-upload`, outside the repo) and
   `app/release/` is ignored by `app/.gitignore`. Left:
   1. `signingConfigs` in `app/build.gradle.kts` reading the path, alias and passwords from
      `~/.gradle/gradle.properties` (or the environment), failing with a clear message when they
      are missing. Nothing secret in the repo.
   2. `.gitignore`: `*.jks`, `*.keystore`.
3. **Play Console Data safety form**, matching `docs/privacy-policy.md`: data on the device only,
   AdMob rewarded ads, UMP consent, the `AD_ID` permission merged in by `play-services-ads`. The
   daily notification is local and collects nothing, so it changes no answer on the form.
4. **The daily notification on a real phone** - nothing here is covered by a test beyond
   `NotificationPlan`. With a fixed time a few minutes ahead: it arrives with the app closed; it is
   postponed an hour with the app open; a tap opens today's board without the launch prompt, both
   from a killed process and from the background; solving takes it down; it still comes after a
   reboot; the offer after the second solved daily, and the system dialog refused twice, then the
   options' "blocked" note and its button. Once on Samsung's battery saver, which may delay or drop
   inexact alarms (Xiaomi done 2026-11-14: Autostart off drops them; see `DECISIONS.md`).
5. **Open testing worldwide, Polish and English together, with listings that can be found.**
   Each listing has screenshots of a real board mid-solve and a short description built on what
   players search for ("gra słowna", "łamigłówka słowna"; "word puzzle", "word game") rather than
   on the name, which says nothing in search. In English the game meets LinkedIn's Wend and the big
   publishers, so the English listing carries more of the weight.

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
   If the game reads as buyable, the next step is `ADS_PER_DAY` 2, not higher hint prices.
   **Until that measurement the economy gets no leaner.** Up to some ten thousand daily players the
   money is in whether a player comes back and how they rate the game, not in how scarce coins are:
   a player who leaves frustrated watches no ads at all. Work that keeps players (a board that
   builds on a slow phone, word meanings) goes before accounts and purchases.
4. **Delete `LegacyLanguageKeys`** (and its call in `RavelApplication.onCreate` and its test)
   before 1.0.0, once every tester has launched a build that has it. Keep the bare `pl` / `en`
   alias in `LanguageSettings.byCode` for good - see `DECISIONS.md`, "Why the language codes carry
   a region".
5. **The last "coming soon" modifier** (`COMING_SOON_MODIFIERS` in `LevelMenuScreen`): it needs
   a name, a bonus inside the sum `PuzzleTest` holds, and its stored keys before it replaces a tile.
   Not chosen yet.

## Planned - decided, not built

Requirements are fixed; build to them, do not reopen them.

1. **Analytics and crash reports, before accounts** - Firebase Analytics and Crashlytics,
   14-month retention, off in the EEA until the player agrees, with its own toggle in the options.
   Before accounts because every economy and difficulty decision so far was made without a single
   player's data. The privacy policy and the Data safety form change in the same release. Few
   events, each answering a decision:
   1. board generation: size, time, timeout, device model - the phone measurement on every phone;
   2. the tutorial: started, finished, skipped;
   3. a level finished: number, time, hints - where players drop off;
   4. a hint tapped without the coins for it; the "stuck?" offer shown and taken;
   5. a rewarded ad requested, filled, watched - the fill rate is invisible today;
   6. a daily solved, a streak repaired, a launch from the notification;
   7. a tap on the shop's "Coin packs - coming soon" tile - interest in paying, measured before
      anything is built for it.
2. **Accounts** - Google sign-in through Firebase Auth, optional. Every requirement in `CLAUDE.md`,
   "Commitments made in the privacy policy", applies. Not started before the beta; taken up once
   it is out. Until then Android Auto Backup is what carries progress to a new phone.
3. **Tutorial "done" moves to the account**, with accounts.
4. **Coins for real money** - after accounts. Three consumable packs, verified on a server, buyer
   identified through Play Games Services signed in silently at launch (`DECISIONS.md`, "Coins for
   real money"). **Which paid product comes first waits for the analytics data**: many taps on the
   coin-pack tile and many refused ads among players who keep coming back point at a one-off
   purchase that grants the day's ad rewards without the ads (Play restores it, no server needed,
   but it needs the "device-only purchase" rule reopened); few of either mean nothing to build yet.
   Still open when it is built: whether buying requires signing in, what happens to a
   device balance on first sign-in. Revisits the backup rules; the privacy policy and the Data
   safety form change in the same commit.
5. **Leaderboard** - much later. Anonymous nickname chosen by the player, opt-out toggle shipped
   with it.
