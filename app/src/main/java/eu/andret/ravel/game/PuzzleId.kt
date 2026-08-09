package eu.andret.ravel.game

/**
 * Which stream of puzzles a board belongs to.
 *
 * The two modes here happen not to collide on their ordinals: an endless level is a small integer
 * and a daily ordinal is a date as `yyyyMMdd`, so the ladder will never reach 20260807. That is a
 * coincidence of these particular two and not a property of the scheme. Any third mode counting
 * from its own 1 would share every number with the ladder, and if the number went to the generator
 * as its seed they would be the same board - a player working through the endless mode would meet
 * boards they had already solved elsewhere, more often the more they played.
 *
 * The [salt] is what makes that a thing nobody has to check. It is mixed into the seed, so the same
 * ordinal in two modes lands in two unrelated places in the generator's space of boards, whether or
 * not those ordinals were ever going to overlap.
 *
 * Salts are arbitrary 64-bit constants and **must never change**: a salt is part of a board's
 * identity, so editing one silently replaces every puzzle the mode has ever produced.
 *
 * There was a third entry here, `SPECIAL`, for events and seasons, holding salt
 * `0x5DA1_1EC0_FFEE_0003`. It is gone. Nothing in the app ever built one of its boards - no screen,
 * no list of events, no call site outside the tests - and an enum entry no production code names
 * reserves nothing: a salt becomes binding the moment a board built from it reaches a player, and
 * none ever did. What the entry *was* doing was holding open an `else` branch in [PuzzleSpec], and
 * that branch was a liability rather than a convenience; see [PuzzleSpec] for what it handed out.
 * Bringing the mode back later is one line, and it may reuse that constant or pick another.
 */
enum class PuzzleMode(internal val salt: Long) {
    /** One board a day, the same for everyone on that date. Ordinal is the date as `yyyyMMdd`. */
    DAILY(0x5DA1_1EC0_FFEE_0001uL.toLong()),

    /** The main game: level 1, 2, 3 and on for as long as the player keeps going. */
    ENDLESS(0x5DA1_1EC0_FFEE_0002uL.toLong()),
}

/**
 * A puzzle's name: which stream it comes from and where in that stream it sits.
 *
 * This is the identity the rest of the game stores, shares and compares. The generator's [seed] is
 * derived from it and never handled directly, which is what stops one mode's numbering from
 * colliding with another's.
 *
 * The derivation is deliberately not reversible and not sequential: neighbouring ordinals produce
 * seeds with nothing in common, so level 51 is not a variation on level 50.
 */
data class PuzzleId(val mode: PuzzleMode, val ordinal: Long) {
    /**
     * The number handed to the generator.
     *
     * Stable for all time: the same id yields the same seed on every device and every release, so a
     * daily challenge is the same challenge for everyone and a level the player replays is the
     * level they remember.
     *
     * The one thing this cannot promise is that the *board* never changes. The seed selects a path
     * through the generator's search; change the search - a constant in the difficulty table, the
     * dictionary, the order paths are tried - and the same seed lands somewhere else. Board
     * identity is therefore (id, generator version), and shipping a generator change silently
     * reissues every puzzle. Worth a deliberate decision every time, not a surprise.
     */
    val seed: Long get() = mix(mode.salt xor mix(ordinal))

    private companion object {
        /**
         * The SplitMix64 finaliser: an integer hash that spreads any pattern in its input across
         * all 64 output bits.
         *
         * Dates and level numbers are about as patterned as inputs get - consecutive, small,
         * sharing every high bit - and a seed built by adding or concatenating them would hand the
         * generator neighbouring streams for neighbouring puzzles. Hashing costs three multiplies
         * once per board.
         */
        fun mix(value: Long): Long {
            var z = value + -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
