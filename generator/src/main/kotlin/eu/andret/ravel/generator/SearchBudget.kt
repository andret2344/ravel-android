package eu.andret.ravel.generator

/**
 * Caps how long a single backtracking search may run, counted in visited nodes.
 *
 * Some combinations of walls and word lengths admit no layout at all, and proving that
 * exhaustively can take minutes - one measured 7x7 board spent eleven of them before the search
 * gave up on its own. Nothing about those minutes is useful: the generator has hundreds of other
 * wall layouts to try, and abandoning a hopeless one costs nothing.
 *
 * Callers must treat an exhausted budget as "no answer", never as a negative answer. Notably a
 * uniqueness check that runs out of budget says nothing about how many solutions the board has,
 * so the board has to be discarded rather than published.
 *
 * A budget also expires when its thread is interrupted, which is how generation is called off from
 * outside - see [BoardGenerator.generate]. Interruption is routed through the existing "no answer"
 * path rather than thrown from inside the recursion on purpose: both searches carry a mutable
 * `blocked` array that their unwinding restores, and an exception thrown mid-descent would skip
 * that restoration. Expiring the budget makes the search unwind the way it already knows how, and
 * the caller notices the interruption a moment later at a point where nothing is half-built.
 */
internal class SearchBudget(private var remaining: Long) {
    var isExhausted: Boolean = false
        private set

    /**
     * Counts down to the next interruption check. Starts at one so a fresh budget notices a thread
     * that was already interrupted before it does any work at all.
     */
    private var untilInterruptCheck = 1

    /** Records one visited node. Returns false once the budget is gone. */
    fun consume(): Boolean {
        if (isExhausted) return false
        if (--remaining <= 0) {
            isExhausted = true
            return false
        }
        if (--untilInterruptCheck <= 0) {
            untilInterruptCheck = INTERRUPT_CHECK_NODES
            // isInterrupted rather than interrupted(): the flag has to survive for the generator's
            // own loop to see it and stop for good.
            if (Thread.currentThread().isInterrupted) {
                isExhausted = true
                return false
            }
        }
        return true
    }

    companion object {
        /**
         * Nodes between interruption checks. A uniqueness search visits millions of them, so this
         * is a compromise between reacting promptly and paying for the check: at roughly ten
         * million nodes a second, 4096 nodes is well under a millisecond of lag.
         */
        const val INTERRUPT_CHECK_NODES = 4096

        /**
         * Layouts are cheap when they work at all, so this binds only on hopeless ones - and every
         * node spent on a hopeless one is wasted, which is why it is small. At 500 000 a failed
         * 10x10 layout cost ~175 ms and failed layouts were nearly all of generation time; 20 000
         * cut the 10x10 worst case of levels 1-300 from 5.8 s to 1.4 s on a desktop JVM.
         *
         * Not lower: an exhausted budget counts as an unshapeable layout, and
         * `LAYOUTS_BEFORE_EASING` of those loosen the path-shape ceilings. At 10 000 the worst level
         * reached 164 of that 200; at 20 000 it reaches 86. Lower trades board shape for speed.
         *
         * Changing it reissues every board. See `docs/DECISIONS.md`, "Generation cost, measured".
         */
        const val LAYOUT_NODES = 20_000L

        /** Uniqueness checks on real boards finish in milliseconds, so this is pure insurance. */
        const val UNIQUENESS_NODES = 20_000_000L
    }
}
