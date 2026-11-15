package eu.andret.ravel.game

import eu.andret.ravel.analytics.AnalyticsEvent
import eu.andret.ravel.generator.Language
import eu.andret.ravel.generator.Ravel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * Builds the board a [PuzzleId] names.
 *
 * One place for it because every mode needs the same three things and gets them wrong differently:
 * generation off the main thread, a deadline, and above all a seed that never varies. A retry that
 * reached for a fresh seed would quietly hand the player a different level 40 from the one everyone
 * else has - so there is no seed here to reach for, only [PuzzleSpec.of].
 */
object Puzzles {
    /**
     * The puzzle for [id] in [language], or null when generation ran out of time.
     *
     * Canceling the calling coroutine interrupts the search rather than merely abandoning it, so
     * leaving a screen stops the work instead of paying for a board nobody will see.
     *
     * The dispatcher is named rather than taken as a parameter, which Sonar asks for and which would
     * be wrong twice here. A test dispatcher does not run on a thread that can be interrupted, so
     * the seam would quietly trade away the cancellation this method's whole contract is about; and
     * there is nothing to gain by moving the work elsewhere anyway, since a board is a fixed amount
     * of CPU and the seed already makes it deterministic.
     *
     * @param report where the build's time goes, as [AnalyticsEvent.BoardBuilt] - the phone
     *   measurement, taken on every phone. Only a build that ran to an answer is reported: one
     *   canceled midway never reaches it.
     */
    @Suppress("kotlin:S6310")
    suspend fun generate(
        id: PuzzleId,
        language: Language,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        report: (AnalyticsEvent) -> Unit = {},
    ): Puzzle? {
        val spec = PuzzleSpec.of(id)
        // Timed here, on the real path, because that is what no desktop can answer: ART rather than
        // a desktop JVM, the dictionary read from the APK, and the phone's own heap. The desktop
        // sweep (:app:sweepLevels) has the breakdown; this has the phones.
        val startedAt = System.nanoTime()
        val board = runInterruptible(Dispatchers.Default) {
            Ravel.boardOrNull(
                width = spec.size,
                height = spec.size,
                language = language,
                difficulty = spec.difficulty,
                seed = spec.seed,
                timeoutMillis = timeoutMillis,
                maxWordRank = spec.maxWordRank,
            )
        }
        val millis = (System.nanoTime() - startedAt) / 1_000_000
        report(
            AnalyticsEvent.BoardBuilt(
                mode = id.mode,
                ordinal = id.ordinal,
                size = spec.size,
                millis = millis,
                found = board != null,
                timeoutMillis = timeoutMillis,
            ),
        )
        return board?.let(::Puzzle)
    }

    /**
     * Builds [language]'s dictionary now, so the first board of a session does not wait for it.
     *
     * Blocking and not cheap - call it off the main thread. It is idempotent and thread-safe: the
     * cache behind it is a `ConcurrentHashMap.computeIfAbsent`, so a preload racing the first real
     * generation ends with one of them building the trie and the other waiting for it, never two.
     *
     * Worth doing because the cost is real and lands in the worst place. Measured on a Redmi Note
     * 10 5G, the first generation in a fresh process spent **3.1 seconds** building the English trie
     * before the search started - against 121 ms on a desktop JVM - and the player pays it staring
     * at the spinner over their first board. Nothing else about that board is any faster for having
     * waited. See `docs/DECISIONS.md` for the measurement.
     *
     * The difficulty is [PuzzleSpec.DIFFICULTY] rather than a parameter, because a preload that
     * warmed a different one would build a second trie and leave the first still to build.
     */
    fun preload(language: Language) = Ravel.preload(language, PuzzleSpec.DIFFICULTY)

    /**
     * Generous, because the cost being waited on is mostly the one-off dictionary load rather than
     * the search itself. Boards of the sizes [PuzzleSpec] hands out are found in milliseconds once
     * the trie is warm.
     *
     * On a phone that second sentence was false for a while. On an older generator a Redmi Note 10
     * 5G took a median 7.7 seconds per 10x10, and 7 of the 24 10x10 levels below 300 could not be
     * built inside this deadline at all - deterministically, since the seed fixes the search. On the
     * current one the same phone builds the slowest board of levels 126-300 in 0.67 seconds
     * (2026-11-14, `docs/DECISIONS.md`, "Generation cost, measured").
     *
     * Raising the deadline was never the fix: level 238 once took about 68 seconds, and 68 seconds
     * of spinner is a player who has left rather than a player who got a board. The deadline stays
     * where a person's patience is; the board should already exist by the time it is asked for -
     * see [PREFETCH_TIMEOUT_MILLIS].
     *
     * Note also that this does not bound the wait. The generator checks it at the top of its layout
     * loop, so an attempt already running when it expires still finishes: the same device was
     * measured waiting 23.5 seconds to be told no, and 21.4 seconds to be given a board.
     */
    const val DEFAULT_TIMEOUT_MILLIS = 20_000L

    /**
     * The deadline for a board nobody is waiting for yet.
     *
     * Long where [DEFAULT_TIMEOUT_MILLIS] is short, because the two are answering different
     * questions. In front of a player a deadline asks "how long will they sit here"; behind one it
     * asks "when is this device hopeless enough that burning a core on it is waste". The player is
     * solving the previous board through all of it and sees none of it.
     *
     * Three minutes rather than none at all: the worst level once needed about 68 seconds on a Redmi
     * Note 10 5G, and needs under a second there now, so this leaves a wide margin for phones far
     * slower than that one and for generator changes to come, and a prefetch still
     * running after it has found a device where this board size does not work - which is a finding
     * for the size ramp, not a reason to hold a core forever.
     */
    const val PREFETCH_TIMEOUT_MILLIS = 180_000L
}
