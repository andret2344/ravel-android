# To do

Work left, in the order it blocks a release. **Delete an item in the commit that finishes it**, and
add one in the commit that leaves something for later. Rules live in `CLAUDE.md` and their
arguments in `docs/DECISIONS.md`; an item here points at them rather than repeating them.

## Before the beta

1. **Phone measurements.** Generate boards on a real mid-range device (`adb logcat -s RavelGen`)
   with the current generator and record the table in `DECISIONS.md`, "Generation cost, measured".
   The cache, the lookahead and the 30-second message are all sized by numbers measured on the old
   generator; re-check them against the new table.
2. **Remove the `RavelGen` log line** in `Puzzles.generate` - only after item 1 is recorded.
3. **Release signing.** The upload key exists (alias `ravel-upload`, outside the repo) and
   `app/release/` is ignored by `app/.gitignore`. Left:
   1. `signingConfigs` in `app/build.gradle.kts` reading the path, alias and passwords from
      `~/.gradle/gradle.properties` (or the environment), failing with a clear message when they
      are missing. Nothing secret in the repo.
   2. `.gitignore`: `*.jks`, `*.keystore`.
4. **Play Console Data safety form**, matching `docs/privacy-policy.md`: data on the device only,
   AdMob rewarded ads, UMP consent, the `AD_ID` permission merged in by `play-services-ads`. The
   daily notification is local and collects nothing, so it changes no answer on the form.
5. **The daily notification on a real phone** - nothing here is covered by a test beyond
   `NotificationPlan`. With a fixed time a few minutes ahead: it arrives with the app closed; it is
   postponed an hour with the app open; a tap opens today's board without the launch prompt, both
   from a killed process and from the background; solving takes it down; it still comes after a
   reboot; the offer after the second solved daily, and the system dialog refused twice, then the
   options' "blocked" note and its button. Once on a manufacturer's battery saver (Xiaomi, Samsung),
   which may delay or drop inexact alarms.

## Code

1. **Tests for the reset guard**: a level at or below the best pays nothing after `clear`
   (`StoredLevelProgress`), a day at or before `lastPaid` pays nothing (`paidBeforeReset`,
   `DailyViewModel`), and both keys are seeded from the ladder and the record on `clear`. Pull the
   decisions into pure functions where the stores make them hard to reach.
2. **A release smoke test.** `app/src/androidTest` does not exist: the five back stacks, rotation
   during generation, process death mid-board and the wide layouts are covered by nothing.
3. **The `compose-group-mapping` pin** in `app/build.gradle.kts`: on every AGP or Kotlin bump,
   comment it out and run `:app:assembleRelease`; delete it once that passes.
4. **Measure the leaner economy** (2026-11-11, `DECISIONS.md`, "Why playing pays less"): rewarded
   impressions per DAU from AdMob against DAU from the Play Console, before and after the release
   that ships it, read on new installs - existing testers are still spending their old balances.
   If the game reads as buyable, the next step is `ADS_PER_DAY` 2, not higher hint prices.
5. **Delete `LegacyLanguageKeys`** (and its call in `RavelApplication.onCreate` and its test)
   before 1.0.0, once every tester has launched a build that has it. Keep the bare `pl` / `en`
   alias in `LanguageSettings.byCode` for good - see `DECISIONS.md`, "Why the language codes carry
   a region".

## Planned - decided, not built

Requirements are fixed; build to them, do not reopen them.

1. **Accounts** - Google sign-in through Firebase Auth, optional. Every requirement in `CLAUDE.md`,
   "Commitments made in the privacy policy", applies. Not started before the beta; taken up once
   it is out. Until then Android Auto Backup is what carries progress to a new phone.
2. **Tutorial "done" moves to the account**, with accounts.
3. **Analytics** - Firebase, 14-month retention, its own consent and toggle.
4. **Coins for real money** - after accounts. Three consumable packs, verified on a server, buyer
   identified through Play Games Services signed in silently at launch (`DECISIONS.md`, "Coins for
   real money"). Still open when it is built: whether buying requires signing in, what happens to a
   device balance on first sign-in. Revisits the backup rules; the privacy policy and the Data
   safety form change in the same commit.
5. **Leaderboard** - much later. Anonymous nickname chosen by the player, opt-out toggle shipped
   with it.
