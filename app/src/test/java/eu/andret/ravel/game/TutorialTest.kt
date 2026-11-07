package eu.andret.ravel.game

import eu.andret.ravel.generator.Board
import eu.andret.ravel.generator.Difficulty
import eu.andret.ravel.generator.Dictionary
import eu.andret.ravel.generator.Grid
import eu.andret.ravel.generator.Language
import eu.andret.ravel.generator.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tutorial boards are written by hand, so nothing generated them under the rules every other
 * board obeys. These tests are those rules: a board that broke one would be teaching the game wrong
 * on the first screen a player ever sees.
 */
class TutorialTest {

    @Test
    fun `every placement spells its word along a connected path`() {
        Language.entries.forEach { language ->
            val board = Tutorial.boardFor(language)
            board.solution.forEach { placement ->
                val spelled = placement.cells.joinToString(separator = "") { board.letters[it].toString() }
                assertEquals("${language.code}: ${placement.word}", placement.word, spelled)
                placement.cells.toList().zipWithNext().forEach { (from, to) ->
                    assertTrue(
                        "${language.code}: ${placement.word} jumps from $from to $to",
                        areAdjacent(board.width, from, to),
                    )
                }
            }
        }
    }

    @Test
    fun `the words tile every free cell exactly once`() {
        Language.entries.forEach { language ->
            val board = Tutorial.boardFor(language)
            val covered = board.solution.flatMap { it.cells.toList() }
            val free = board.letters.indices.filterNot(board::isWall)
            assertEquals("${language.code}: a cell is used twice", covered.size, covered.toSet().size)
            assertEquals("${language.code}: cells left over", free.toSet(), covered.toSet())
        }
    }

    @Test
    fun `each board has exactly one solution against the whole shipped dictionary`() {
        // The last step tells the player to find a word on their own, which is only fair if there is
        // one answer. Proved against what the game loads, not what the board draws from, for the
        // reason Difficulty gives: any word the dictionary holds is one a player may know.
        Language.entries.forEach { language ->
            val board = Tutorial.boardFor(language)
            val dictionary = Dictionary.loadBundled(language, Difficulty.HARD)
            board.solution.forEach { placement ->
                assertTrue("${language.code}: '${placement.word}'", dictionary.isWord(placement.word))
            }
            val solutions = Solver(Grid(board.width, board.height), board.letters, dictionary)
                .countSolutions(board.wordLengths, limit = 2)
            assertEquals("${language.code}\n${board.render()}", 1, solutions)
        }
    }

    @Test
    fun `each decoy is a real word that is not an answer`() {
        Language.entries.forEach { language ->
            val board = Tutorial.boardFor(language)
            val decoy = Tutorial.decoyFor(language)
            val dictionary = Dictionary.loadBundled(language, Difficulty.HARD)
            assertTrue("${language.code}: '${decoy.word}'", dictionary.isWord(decoy.word))
            assertTrue(
                "${language.code}: '${decoy.word}' is an answer",
                board.solution.none { it.word == decoy.word },
            )
        }
    }

    @Test
    fun `each decoy lists every path that spells it`() {
        // The step moves on when the traced path is one of these, so a placement left out is a
        // place where the player traces the word the text asked for and nothing happens.
        Language.entries.forEach { language ->
            val board = Tutorial.boardFor(language)
            val decoy = Tutorial.decoyFor(language)
            assertEquals(
                "${language.code}: '${decoy.word}'",
                pathsSpelling(board, decoy.word),
                decoy.placements.toSet(),
            )
        }
    }

    @Test
    fun `each decoy is still there to trace once the first word is found`() {
        // The whole lesson of the decoy step: a real word, of a length the player is still looking
        // for, that does not count. Both halves have to hold right after the first word.
        Language.entries.forEach { language ->
            val board = Tutorial.boardFor(language)
            val decoy = Tutorial.decoyFor(language)
            val first = board.solution.first().cells.toSet()
            assertTrue(
                "${language.code}: every '${decoy.word}' runs through '${board.solution.first().word}'",
                decoy.placements.any { cells -> cells.none(first::contains) },
            )
            assertTrue(
                "${language.code}: no word of ${decoy.word.length} letters is left to find",
                board.solution.drop(1).any { it.word.length == decoy.word.length },
            )
        }
    }

    /** Every orthogonal path over free cells that reads [word] from its first letter. */
    private fun pathsSpelling(board: Board, word: String): Set<List<Int>> {
        val found = mutableSetOf<List<Int>>()
        fun extend(path: List<Int>) {
            if (path.size == word.length) {
                found += path
                return
            }
            board.letters.indices
                .filter { it !in path && !board.isWall(it) && areAdjacent(board.width, path.last(), it) }
                .filter { board.letters[it] == word[path.size] }
                .forEach { extend(path + it) }
        }
        board.letters.indices
            .filter { !board.isWall(it) && board.letters[it] == word[0] }
            .forEach { extend(listOf(it)) }
        return found
    }
}
