package eu.andret.ravel.ui.about

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.navigation.NavBackStackEntry
import eu.andret.ravel.R

/**
 * Named once: two of the sources are under it, and a link that differs by a trailing slash is a bug
 * nobody notices until somebody follows it.
 *
 * Top level rather than in the enum's companion, which is not initialised yet when the entries are
 * built - the compiler says so, and the message is easy to mistake for a mistake of one's own.
 */
private const val CC_BY_4_0 = "https://creativecommons.org/licenses/by/4.0/"

/**
 * A licence the game has to show: the six sources it is built on, and its own.
 *
 * [RAVEL] is the game itself and is not a word source. It is in this enum rather than beside it
 * because everything that makes a licence a page - the row, the route, the detail screen - already
 * works for six entries and would have to be duplicated to serve a seventh thing that is a licence
 * in every respect except where it came from. What separates it is the list it appears in, and that
 * is [wordSources]' job.
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
 * @param licence the short licence label the row carries, for the same reason.
 * @param notice the attribution itself. English in every locale for the six sources: CC BY and the
 *   WordNet licence both say what has to appear, and what has to appear is their wording, not a
 *   rendering of it that nobody has approved. [RAVEL]'s is translated, because that sentence is this
 *   project's own and no one else's wording is being reproduced in it.
 * @param url where the licence proper lives. CC BY is satisfied by a link and is not carried in the
 *   app - the deed is four screens of legal text that changes nothing about what the player is
 *   being told here.
 * @param fullText the licence bundled verbatim, for the two that require their notice to be
 *   retained rather than pointed at. Null means the link above is the whole answer, which is why the
 *   detail screen has to work without one rather than treating it as a missing file.
 */
enum class Licence(
    @param:StringRes val source: Int,
    @param:StringRes val licence: Int,
    @param:StringRes val notice: Int,
    val url: String,
    @param:RawRes val fullText: Int? = null,
) {
    SJP(
        source = R.string.licence_sjp_source,
        licence = R.string.licence_name_cc_by,
        notice = R.string.licence_sjp_notice,
        url = CC_BY_4_0,
    ),
    KWJP(
        source = R.string.licence_kwjp_source,
        licence = R.string.licence_name_cc_by,
        notice = R.string.licence_kwjp_notice,
        url = CC_BY_4_0,
    ),
    POLIMORF(
        source = R.string.licence_polimorf_source,
        licence = R.string.licence_name_bsd,
        notice = R.string.licence_polimorf_notice,
        url = "https://morfeusz.sgjp.pl/doc/license/",
        fullText = R.raw.licence_polimorf,
    ),
    WORDNET(
        source = R.string.licence_wordnet_source,
        licence = R.string.licence_name_wordnet,
        notice = R.string.licence_wordnet_notice,
        url = "https://wordnet.princeton.edu/license-and-commercial-use",
        fullText = R.raw.licence_wordnet,
    ),
    ENABLE(
        source = R.string.licence_enable_source,
        licence = R.string.licence_name_public_domain,
        notice = R.string.licence_enable_notice,
        url = "https://github.com/dolph/dictionary",
    ),
    OANC(
        source = R.string.licence_oanc_source,
        licence = R.string.licence_name_unrestricted,
        notice = R.string.licence_oanc_notice,
        url = "https://anc.org/data/oanc/",
    ),

    /**
     * The game's own terms, last because the entries above are what it was built from.
     *
     * Not bundled in `res/raw` the way PoliMorf and WordNet are: PolyForm's own Notices section
     * accepts "these terms or the URL for them", and a second copy of a licence inside the APK is a
     * copy that can fall behind the one in the repository. The notice carries the copyright line
     * those terms ask to travel with the software, which is the part a URL cannot stand in for.
     */
    RAVEL(
        source = R.string.licence_ravel_source,
        licence = R.string.licence_name_polyform,
        notice = R.string.licence_ravel_notice,
        url = "https://polyformproject.org/licenses/noncommercial/1.0.0",
    );

    companion object {
        /**
         * Everything the dictionaries are built from - this enum without [RAVEL].
         *
         * Written as a subtraction rather than a second list so that adding a source is adding one
         * entry above and nothing else. The settings screen shows these under "word sources", where
         * the game's own licence would be a false claim about where the words come from.
         */
        val wordSources: List<Licence> = entries - RAVEL
    }
}

/**
 * The route to one licence, kept beside the enum rather than in the navigation graph.
 *
 * The path starts with the settings tab's own prefix so the bar stays lit on the settings while a
 * licence is open - see `RavelTab.prefix`. A licence is not a sixth destination; it is a page of the
 * screen the player was already on.
 */
object LicenceRoute {
    const val ARG_LICENCE = "licence"
    const val DETAIL = "settings/licence/{$ARG_LICENCE}"

    fun of(licence: Licence): String = "settings/licence/${licence.name}"

    /**
     * The licence an entry names, or [Licence.SJP] when the argument is missing or unknown.
     *
     * Falling back rather than throwing, because the only way to get here with a bad argument is a
     * renamed enum constant meeting a back stack restored from before the rename - a crash on
     * resume, over a screen of legal text nobody was reading at the time.
     */
    fun from(entry: NavBackStackEntry): Licence =
        entry.arguments?.getString(ARG_LICENCE)
            ?.let { name -> Licence.entries.firstOrNull { it.name == name } }
            ?: Licence.SJP
}
