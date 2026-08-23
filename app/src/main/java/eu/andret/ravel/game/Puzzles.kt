package eu.andret.ravel.game

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
     * Cancelling the calling coroutine interrupts the search rather than merely abandoning it, so
     * leaving a screen stops the work instead of paying for a board nobody will see.
     *
     * The dispatcher is named rather than taken as a parameter, which Sonar asks for and which would
     * be wrong twice here. A test dispatcher does not run on a thread that can be interrupted, so
     * the seam would quietly trade away the cancellation this method's whole contract is about; and
     * there is nothing to gain by moving the work elsewhere anyway, since a board is a fixed amount
     * of CPU and the seed already makes it deterministic.
     */
    @Suppress("kotlin:S6310")
    suspend fun generate(
        id: PuzzleId,
        language: Language,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ): Puzzle? {
        val spec = PuzzleSpec.of(id)
        // TEMPORARY - measurement scaffolding, remove once the device numbers are recorded.
        // The desktop sweep (:app:sweepLevels) has the breakdown; what no desktop can answer is how
        // much slower this phone is, and that needs the real path: ART rather than a desktop JVM,
        // the dictionary read from the APK, and the device's own heap.
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
        android.util.Log.i(
            "RavelGen",
            "${id.mode}/${id.ordinal} ${spec.size}x${spec.size} rank=${spec.maxWordRank} " +
                "lang=${language.code} found=${board != null} " +
                "millis=${(System.nanoTime() - startedAt) / 1_000_000}",
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
     * **That second sentence is false on a phone and the number is the wrong shape.** Measured on a
     * Redmi Note 10 5G with the trie warm, the median 10x10 takes 7.7 seconds and 7 of the 24 10x10
     * levels below 300 cannot be built inside this deadline at all - deterministically, since the
     * seed fixes the search, so the retry the board screen offers fails identically every time.
     *
     * Raising it is not the fix and would make things worse: the same measurement puts level 238 at
     * roughly 68 seconds, and 68 seconds of spinner is a player who has left rather than a player
     * who got a board. The deadline stays where a person's patience is; what changes is that the
     * board should already exist by the time it is asked for - see [PREFETCH_TIMEOUT_MILLIS].
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
     * Three minutes rather than none at all: the worst level measured needs about 68 seconds on a
     * Redmi Note 10 5G, so this leaves roughly 2.5x for something slower, and a prefetch still
     * running after it has found a device where this board size does not work - which is a finding
     * for the size ramp, not a reason to hold a core forever.
     */
    const val PREFETCH_TIMEOUT_MILLIS = 180_000L
}
