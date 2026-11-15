package eu.andret.ravel.game

/**
 * A rule a player adds to the levels they play, giving something up for coins on top of
 * [Rewards.FOR_COMPLETING].
 *
 * Chosen on the levels tab, from a popup behind one tile, and frozen into a level when its board first
 * comes up - see [eu.andret.ravel.settings.LevelProgress.open]. Levels only: the daily has no
 * modifiers, so a day stays a day for everyone.
 *
 * **A modifier changes what the player is shown or allowed, never the board.** The board and its
 * identity are the same with any set of them on; a modifier that needed a different board would make
 * the player's choice part of the puzzle's id, which [eu.andret.ravel.game.PuzzleSpec] refuses.
 *
 * Every entry pays its own [bonus] and they add up: [Rewards.forCompleting]. **All of them together
 * must stay under the cheapest hint** - that is what lets a hint leave the bonus alone, and
 * `PuzzleTest` holds it over every entry, so a new modifier with too big a bonus fails a test rather
 * than opening a route to coins through the shop.
 *
 * The popup also shows tiles marked "coming soon" (`ModifiersDialog`). They are not entries here: a
 * modifier exists once it has a name, a bonus and stored keys, and not before.
 *
 * @param bonus the coins a level finished with this modifier on pays on top of the flat rate - once
 *   it is [inEffect].
 * @param inEffect whether the board does anything about it yet. An entry can ship ahead of its rule,
 *   chosen, stored and frozen into levels like any other, so the choice and its bonus are visible
 *   while the board work is still to come; until then it pays nothing ([Rewards.forCompleting]),
 *   because a bonus for giving up nothing is coins for a tap.
 */
enum class LevelModifier(val bonus: Int, val inEffect: Boolean = true) {
    /**
     * The word count and lengths hidden; a found word's length appears as it is found. Shown to the
     * player as "Bez długości" / "No lengths".
     */
    NO_LENGTHS(Rewards.FOR_NO_LENGTHS),

    /**
     * Some letters covered by a star until their word is found - which cells, and how many, in
     * [HiddenLetters].
     */
    HIDDEN_LETTERS(Rewards.FOR_HIDDEN_LETTERS),
}
