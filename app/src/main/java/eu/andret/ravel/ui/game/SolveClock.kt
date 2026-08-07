package eu.andret.ravel.ui.game

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * How long a board has been looked at, ticking, and banked as it goes.
 *
 * Shared by every mode with a board, because the rules about when a puzzle clock may run are about
 * the player and not about the puzzle:
 *
 * - the screen has to be up. A view model outlives the app going to the background, and a board
 *   nobody is looking at is not a board being solved.
 * - the board has to exist and be unfinished. Generation is a search with random restarts that also
 *   loads a dictionary of up to 25 000 words the first time; on a cold start that is seconds of the
 *   generator's work, and charging the player for it would make the first board of a session the
 *   slowest one for reasons that have nothing to do with them. The same goes for time spent staring
 *   at a failure message and its retry button.
 *
 * Both facts change independently, which is why they are two calls into one object rather than a
 * condition written out at each of the four places that learn about them.
 *
 * Time is *not* withheld until the first move. Reading the grid is the main activity in this game -
 * a player finds their first word by looking, not by touching - so a clock that started on the first
 * tap would hand the best times to whoever thought longest before making one.
 *
 * @param banked seconds already counted against this board by earlier sittings.
 * @param onBank called with each chunk of whole seconds as it stops being countable, never with
 *   zero. Banked as elapsed durations rather than as a start instant to subtract from later, because
 *   [SystemClock.elapsedRealtime] counts from the last boot: a start instant written to disk is
 *   meaningless after a restart, and one taken from the wall clock is meaningless after a time-zone
 *   change. Only a duration means the same thing tomorrow.
 */
internal class SolveClock(
    private val scope: CoroutineScope,
    banked: Int,
    private val onBank: (Int) -> Unit,
) {
    private var base: Int = banked
    private val _elapsed = MutableStateFlow(banked)

    /** Everything this board has cost so far, ticking while the clock runs. */
    val elapsed: StateFlow<Int> = _elapsed.asStateFlow()

    /** When the current run began, on the monotonic clock, or null while the clock is stopped. */
    private var startedAt: Long? = null
    private var visible = false
    private var playable = false
    private var ticker: Job? = null

    /** The screen is up, or has gone away. */
    fun onVisible(visible: Boolean) {
        this.visible = visible
        sync()
    }

    /** There is a board on screen and it is not finished. */
    fun onPlayable(playable: Boolean) {
        this.playable = playable
        sync()
    }

    /**
     * Stops the clock and banks what it counted, without waiting to be told why.
     *
     * For the moment a board is finished: the reward is paid before the finished state is published,
     * so the last seconds of the solve would otherwise be lost with the running clock. Marking it
     * unplayable here as well means a lifecycle event arriving afterwards cannot start it again on a
     * board that is over.
     */
    fun stop() {
        playable = false
        halt()
    }

    private fun sync() {
        if (visible && playable) start() else halt()
    }

    private fun start() {
        if (startedAt != null) return
        startedAt = SystemClock.elapsedRealtime()
        ticker = scope.launch {
            while (true) {
                _elapsed.value = base + running()
                delay(1_000)
            }
        }
    }

    private fun halt() {
        val seconds = running()
        startedAt = null
        ticker?.cancel()
        ticker = null
        if (seconds <= 0) return
        base += seconds
        _elapsed.value = base
        onBank(seconds)
    }

    private fun running(): Int {
        val began = startedAt ?: return 0
        return ((SystemClock.elapsedRealtime() - began) / 1_000).toInt()
    }
}
