package eu.andret.ravel.ui.about

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.navigation.NavBackStackEntry
import eu.andret.ravel.R

/**
 * Named once: two of the sources are under it, and a link that differs by a trailing slash is a bug
 * nobody notices until somebody follows it.
 *
 * Top level rather than in the enum's companion, which is not initialized yet when the entries are
 * built - the compiler says so, and the message is easy to mistake for a mistake of one's own.
 */
private const val CC_BY_4_0 = "https://creativecommons.org/licenses/by/4.0/"

/**
 * A license the game has to show: the six sources it is built on, its own, and its icons'.
 *
 * [RAVEL] is the game itself and [MATERIAL_SYMBOLS] the icons in it; neither is a word source. They
 * are in this enum rather than beside it because everything that makes a license a page - the row,
 * the route, the detail screen - already works for six entries and would have to be duplicated to
 * serve two more things that are licenses in every respect except where they came from. What
 * separates them is the list they appear in, and that is [wordSources]' and [game]'s job.
 *
 * Three of these entries are obligations rather than courtesies. The two Creative Commons sources
 * require the attribution *and* a statement that the material was modified - the game ships a subset
 * of each list, reordered by frequency, which is a modification whether or not it feels like one.
 * PoliMorf's BSD notice and Princeton's WordNet notice have to travel with anything derived from
 * them, and a generated dictionary is derived from them. The remaining two ask for nothing at all
 * and are listed anyway: a player reading this screen is being told where the words come from, and
 * a list that names four of six sources is a worse answer than one that names all of them.
 *
 * @param source what the player is looking at - the name of the word list, corpus or resource,
 *   never translated. These are proper names; "Słownik WordNet" is not a thing that exists.
 * @param license the short license label the row carries, for the same reason.
 * @param notice the attribution itself. English in every locale for the six sources: CC BY and the
 *   WordNet license both say what has to appear, and what has to appear is their wording, not a
 *   rendering of it that nobody has approved. [RAVEL]'s is translated, because that sentence is this
 *   project's own and no one else's wording is being reproduced in it.
 * @param url where the license proper lives. CC BY is satisfied by a link and is not carried in the
 *   app - the deed is four screens of legal text that changes nothing about what the player is
 *   being told here.
 * @param fullText the license bundled verbatim, for the three that require their text to travel with
 *   the work rather than be pointed at. Null means the link above is the whole answer, which is why the
 *   detail screen has to work without one rather than treating it as a missing file.
 */
enum class License(
    @param:StringRes val source: Int,
    @param:StringRes val license: Int,
    @param:StringRes val notice: Int,
    val url: String,
    @param:RawRes val fullText: Int? = null,
) {
    SJP(
        source = R.string.license_sjp_source,
        license = R.string.license_name_cc_by,
        notice = R.string.license_sjp_notice,
        url = CC_BY_4_0,
    ),
    KWJP(
        source = R.string.license_kwjp_source,
        license = R.string.license_name_cc_by,
        notice = R.string.license_kwjp_notice,
        url = CC_BY_4_0,
    ),
    POLIMORF(
        source = R.string.license_polimorf_source,
        license = R.string.license_name_bsd,
        notice = R.string.license_polimorf_notice,
        url = "https://morfeusz.sgjp.pl/doc/license/",
        fullText = R.raw.license_polimorf,
    ),
    WORDNET(
        source = R.string.license_wordnet_source,
        license = R.string.license_name_wordnet,
        notice = R.string.license_wordnet_notice,
        url = "https://wordnet.princeton.edu/license-and-commercial-use",
        fullText = R.raw.license_wordnet,
    ),
    ENABLE(
        source = R.string.license_enable_source,
        license = R.string.license_name_public_domain,
        notice = R.string.license_enable_notice,
        url = "https://github.com/dolph/dictionary",
    ),
    OANC(
        source = R.string.license_oanc_source,
        license = R.string.license_name_unrestricted,
        notice = R.string.license_oanc_notice,
        url = "https://anc.org/data/oanc/",
    ),

    /**
     * The game's own terms, last because the entries above are what it was built from.
     *
     * Not bundled in `res/raw` the way PoliMorf and WordNet are: PolyForm's own Notices section
     * accepts "these terms or the URL for them", and a second copy of a license inside the APK is a
     * copy that can fall behind the one in the repository. The notice carries the copyright line
     * those terms ask to travel with the software, which is the part a URL cannot stand in for.
     */
    RAVEL(
        source = R.string.license_ravel_source,
        license = R.string.license_name_polyform,
        notice = R.string.license_ravel_notice,
        url = "https://polyformproject.org/licenses/noncommercial/1.0.0",
    ),

    /**
     * The icons: Material Symbols vectors (and the path of Material Icons' padlock) copied into
     * `res/drawable`, which `LICENSE.md` lists as not covered by the game's own terms.
     *
     * Bundled in full, unlike [RAVEL]: Apache 2.0 asks that every recipient of the work be given a
     * copy of the license, and an APK is the work as the player receives it. Google's icon
     * repository has no NOTICE file, so the license text is the whole obligation. The notice is this
     * project's own wording and is translated, on [RAVEL]'s argument.
     */
    MATERIAL_SYMBOLS(
        source = R.string.license_icons_source,
        license = R.string.license_name_apache,
        notice = R.string.license_icons_notice,
        url = "https://www.apache.org/licenses/LICENSE-2.0",
        fullText = R.raw.license_apache,
    );

    companion object {
        /**
         * The game's own licenses, shown under their own heading below the word sources: its code
         * and the icons it carries.
         */
        val game: List<License> = listOf(RAVEL, MATERIAL_SYMBOLS)

        /**
         * Everything the dictionaries are built from - this enum without [game].
         *
         * Written as a subtraction rather than a second list so that adding a source is adding one
         * entry above and nothing else. [LicensesScreen] shows these under "word sources", where
         * the game's own licenses would be a false claim about where the words come from.
         */
        val wordSources: List<License> = entries - game.toSet()
    }
}

/**
 * The route to one license, kept beside the enum rather than in the navigation graph.
 *
 * The path starts with the settings tab's own prefix so the bar stays lit on the settings while a
 * license is open - see `RavelTab.prefix`. A license is not a sixth destination; it is a page of the
 * screen the player was already on.
 */
object LicenseRoute {
    const val ARG_LICENSE = "license"

    /**
     * Every license on one page, behind one row in the options. Under "settings/" like [DETAIL], so
     * the options tab stays lit while the player is in here.
     */
    const val LIST = "settings/licenses"

    const val DETAIL = "settings/license/{$ARG_LICENSE}"

    fun of(license: License): String = "settings/license/${license.name}"

    /**
     * The license an entry names, or [License.SJP] when the argument is missing or unknown.
     *
     * Falling back rather than throwing, because the only way to get here with a bad argument is a
     * renamed enum constant meeting a back stack restored from before the rename - a crash on
     * resume, over a screen of legal text nobody was reading at the time.
     */
    fun from(entry: NavBackStackEntry): License =
        entry.arguments?.getString(ARG_LICENSE)
            ?.let { name -> License.entries.firstOrNull { it.name == name } }
            ?: License.SJP
}
