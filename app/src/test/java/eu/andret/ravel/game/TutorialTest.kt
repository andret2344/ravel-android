package eu.andret.ravel.game

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
    fun `the guided words are the shorter ones the player meets first`() {
        // Not a rule of the game, a rule of the lesson: the first word traced should be a short one.
        Language.entries.forEach { language ->
            val lengths = Tutorial.boardFor(language).solution.map { it.word.length }
            assertEquals("${language.code}: $lengths", lengths.min(), lengths.first())
        }
    }
}
