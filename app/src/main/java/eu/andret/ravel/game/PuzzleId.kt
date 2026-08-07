package eu.andret.ravel.game

/**
 * Which stream of puzzles a board belongs to.
 *
 * Each mode counts from its own 1, or from its own date, and those counters overlap constantly:
 * endless level 5 and special event 5 are the same number. If the number went to the generator as
 * its seed, they would be the same board, and a player who works through the endless mode would
 * meet boards they had already solved elsewhere - the more they play, the more often.
 *
 * The [salt] keeps the streams apart. It is mixed into the seed, so the same ordinal in two modes
 * lands in two unrelated places in the generator's space of boards.
 *
 * Salts are arbitrary 64-bit constants and **must never change**: a salt is part of a board's
 * identity, so editing one silently replaces every puzzle the mode has ever produced.
 */
enum class PuzzleMode(internal val salt: Long) {
    /** One board a day, the same for everyone on that date. Ordinal is the date as `yyyyMMdd`. */
    DAILY(0x5DA1_1EC0_FFEE_0001uL.toLong()),

    /** The main game: level 1, 2, 3 and on for as long as the player keeps going. */
    ENDLESS(0x5DA1_1EC0_FFEE_0002uL.toLong()),

    /** Events, seasons, whatever gets its own list later. Ordinal identifies the event's board. */
    SPECIAL(0x5DA1_1EC0_FFEE_0003uL.toLong()),
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
