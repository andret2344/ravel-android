package eu.andret.ravel.generator

/**
 * A language the game can generate boards in. Each one carries its own alphabet, its own ranked
 * dictionary resource and its own two word filters, because none of them transfers across languages.
 *
 * The two filters answer different questions and act at different times. [blocklistFile] lists
 * vulgar *stems* and is applied when the dictionary is built, so what it matches is not a word of
 * the game at all. [excludedAnswersResource] lists whole *words* that stay words - the solver still
 * reads them - but that no board may ask the player to find. See [Dictionary.poolTo].
 *
 * [code] is a full language tag, `pl-PL` and `en-US`, and it is also what names the language's files
 * and its keys on a device. The region says what the data is: the English word list is ranked by an
 * American corpus (OANC), and a player choosing it should see "English (United States)". Both used to
 * be the bare `pl` and `en`; the app still reads those wherever they were stored (see the app's
 * `LanguageSettings.byCode`).
 */
enum class Language(
    val code: String,
    val alphabet: Alphabet,
    val dictionaryResource: String,
    val blocklistFile: String,
    val excludedAnswersResource: String,
) {
    POLISH(
        code = "pl-PL",
        alphabet = Alphabet.POLISH,
        dictionaryResource = "/dictionary-pl-PL.txt",
        blocklistFile = "blocklist-pl-PL.txt",
        excludedAnswersResource = "/excluded-answers-pl-PL.txt",
    ),
    ENGLISH(
        code = "en-US",
        alphabet = Alphabet.ENGLISH,
        dictionaryResource = "/dictionary-en-US.txt",
        blocklistFile = "blocklist-en-US.txt",
        excludedAnswersResource = "/excluded-answers-en-US.txt",
    ),
    ;

    /**
     * The Gradle task that builds this language's dictionary: `buildDictionaryPlPl`,
     * `buildDictionaryEnUs`. Named here so the messages that tell somebody to run it cannot drift
     * from it; `generator/build.gradle.kts` derives the name by the same rule.
     */
    val buildTask: String
        get() = "buildDictionary" + code.split('-').joinToString("") { part ->
            part.lowercase().replaceFirstChar(Char::uppercase)
        }

    companion object {
        fun of(code: String): Language = entries.firstOrNull { it.code == code }
            ?: throw IllegalArgumentException(
                "Unknown language '$code', expected one of ${entries.joinToString { it.code }}"
            )
    }
}

/**
 * Difficulty shapes how the words wind. What they *are* is a separate axis - see
 * [BoardSpec.maxWordRank].
 *
 * [maxRank] is how much of the frequency-ranked dictionary gets **loaded**, which is not the same
 * question as how obscure a board's own words may be, and the difference matters. Every word the
 * dictionary holds is a word [Solver] must rule out before calling a board unique, so loading less
 * does not make boards easier - it makes the uniqueness claim weaker. A board proved unique against
 * 25 000 words can have a second solution spelled out of the 26 000th, and a player who happens to
 * know that word finds a board the game insisted had one answer.
 *
 * [HARD] therefore loads far more than any board draws from, and the vocabulary curve is applied to
 * the draw pool instead. It stops short of the whole file for two reasons that happen to agree:
 *
 * - **Heap.** Measured by `DictionaryFootprint`, Polish costs 24.1 MB at 25 000 words, 33.2 MB at
 *   35 000 and 48.8 MB at all 51 685. This ships to API 24, where the last figure is not a number
 *   to hold resident for a word game.
 * - **What is down there.** Past about 35 000 the Polish list stops being words a board should
 *   spell: surname-derived adjectives that PoliMorf tags as common (*ostaszewski*, *rylski*),
 *   loanwords SJP admits but nobody plays (*steak*), and dialect (*czerecha*). Cutting for memory
 *   removes exactly the entries that were the weakest argument for keeping them.
 *
 * [EASY] loads less again, because nothing that ships uses it - it is the CLI's and the tests'
 * shorthand for "a smaller world", and it is cheaper to build.
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
 * paths fill 2x2 and 3x3 squares and the board reads as colored clumps rather than threaded
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
        maxRank = 35_000, minTurnRatio = 0.55, maxTurnRatio = 0.80, maxDirectness = 0.75,
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
