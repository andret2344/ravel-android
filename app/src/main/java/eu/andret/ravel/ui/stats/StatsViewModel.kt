package eu.andret.ravel.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.andret.ravel.RavelApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Everything the player has done, from both modes at once.
 *
 * Every number here counts every language. The main mode is a ladder per language - see
 * [eu.andret.ravel.settings.LevelProgressState] - but that is about where the player is, and this
 * screen is about what they have done: a total that halved because they changed a setting would be
 * reporting a loss that did not happen. The screen says so in as many words underneath, because a
 * count with no scope written next to it is read as counting everything, and here that is true.
 *
 * @param days days of the daily challenge finished, in total and not in a row, and [longest] the
 *   best run among them. Both stay daily-only because they are about a calendar: the main mode has
 *   no dates and cannot break a streak by not being played. The streak the player is on right now is
 *   deliberately absent - it belongs to the daily tab, which is built around it.
 * @param boughtLetters hints bought, across both modes. They used to be daily-only, because a level
 *   throws its costs away when the ladder moves on and there was no lifetime figure to add - so
 *   [eu.andret.ravel.settings.LevelTotals] now keeps one, and this counts what the ladder cost too.
 * @param boards every board finished anywhere, [wordsFound] every word those boards held, [seconds]
 *   the time they took.
 */
data class Stats(
    val days: Int = 0,
    val longest: Int = 0,
    val boughtLetters: Int = 0,
    val boughtWords: Int = 0,
    val boards: Int = 0,
    val wordsFound: Int = 0,
    val seconds: Int = 0,
)

/**
 * Reads both stores and adds them up.
 *
 * Combined here rather than in the screen so the arithmetic is in one place and testable, and so the
 * screen is handed numbers rather than two histories to reconcile while it draws.
 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val daily = (application as RavelApplication).progress

    private val levels = (application as RavelApplication).levels

    /**
     * The lifetime figures and not the ladder in front of the player.
     *
     * [eu.andret.ravel.settings.LevelProgress.state] would answer with the current language's level,
     * which is the one number on this screen that must not come from there: it changes when the
     * player switches language, and every other total here does not.
     */
    val state: StateFlow<Stats> = combine(daily.state, levels.totals) { day, level ->
        Stats(
            days = day.totalSolved,
            longest = day.longest,
            boughtLetters = day.totalBoughtLetters + level.boughtLetters,
            boughtWords = day.totalBoughtWords + level.boughtWords,
            // Levels finished are boards finished: the ladder is walked in order and never repeats,
            // so the count of levels behind the player is the count of their boards - summed over
            // every language's ladder, because each of them produced boards of its own.
            boards = day.totalSolved + level.levelsCompleted,
            wordsFound = day.totalWordsFound + level.wordsFound,
            seconds = day.totalSeconds + level.seconds,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Stats())
}
