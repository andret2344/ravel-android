package eu.andret.ravel.generator

/**
 * A language the game can generate boards in. Each one carries its own alphabet, its own ranked
 * dictionary resource and its own blocklist, because none of the three transfers across languages.
 */
enum class Language(
    val code: String,
    val alphabet: Alphabet,
    val dictionaryResource: String,
    val blocklistFile: String,
) {
    POLISH("pl", Alphabet.POLISH, "/slownik-pl.txt", "blocklist-pl.txt"),
    ENGLISH("en", Alphabet.ENGLISH, "/slownik-en.txt", "blocklist-en.txt"),
    ;

    companion object {
        fun of(code: String): Language = entries.firstOrNull { it.code == code }
            ?: throw IllegalArgumentException(
                "Unknown language '$code', expected one of ${entries.joinToString { it.code }}"
            )
    }
}

/**
 * Difficulty turns two dials at once, because vocabulary alone does not make a board hard.
 *
 * [maxRank] is how deep into the frequency-ranked dictionary a board may reach. A word's position
 * in the dictionary is how common it is in a balanced corpus, so cutting the list short keeps
 * boards to words players actually know.
 *
 * [minTurnRatio] and [maxTurnRatio] bracket how much the words wind - see [turnRatioOf]. A floor
 * alone was a mistake: a path that turns at every single cell is a staircase, and a board of
 * staircases is as monotonous as a board of straight lines. The reference board this was
 * calibrated against measures 0.60, with words running two or three cells before they bend, so
 * the target is a band around that rather than "as much as possible".
 *
 * [turnBias] steers the layout search towards winding paths in the first place. Unbiased random
 * layouts already average about 0.6, which is the band, so this stays small: a strong bias is what
 * produced the staircases, and it also makes every board look alike.
 *
 * [maxDirectness] caps how straight a word's travel may be, see [worstDirectnessOf]. It is a cap
 * and not a target: a path can turn at every cell and still march from corner to corner, which is
 * a staircase, and the reference measures 0.60 on every one of its words.
 *
 * [directnessBias] is the old lever that pushed the other way and produced those staircases. It
 * stays at zero; doubling back is controlled where it is actually visible, by [maxFoldRatio].
 *
 * [maxFoldRatio] caps how much a word may run alongside itself - see [foldRatioOf]. Left alone,
 * paths fill 2x2 and 3x3 squares and the board reads as coloured clumps rather than threaded
 * words. The reference's worst word carries one fold over six cells, so the cap sits just above
 * that. [foldAvoidance] steers the search away from those cells in the first place, so the cap
 * rarely has to reject anything.
 */
enum class Difficulty(
    val maxRank: Int,
    val minTurnRatio: Double,
    val maxTurnRatio: Double,
    val maxDirectness: Double,
    val turnBias: Double,
    val directnessBias: Double,
    val maxFoldRatio: Double,
    val foldAvoidance: Double,
) {
    EASY(
        maxRank = 10_000, minTurnRatio = 0.45, maxTurnRatio = 0.80, maxDirectness = 0.85,
        turnBias = 0.0, directnessBias = 0.0, maxFoldRatio = 0.20, foldAvoidance = 0.9,
        // 0.20 lets a five-letter word double back once, and a ten-letter word twice.
    ),
    HARD(
        maxRank = 25_000, minTurnRatio = 0.55, maxTurnRatio = 0.80, maxDirectness = 0.75,
        // 0.17 is one doubling-back per word up to eleven letters, which is what the reference does.
        turnBias = 0.2, directnessBias = 0.0, maxFoldRatio = 0.17, foldAvoidance = 0.95,
    ),
    ;

    companion object {
        fun of(name: String): Difficulty = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "Unknown difficulty '$name', expected one of ${entries.joinToString { it.name.lowercase() }}"
            )
    }
}
