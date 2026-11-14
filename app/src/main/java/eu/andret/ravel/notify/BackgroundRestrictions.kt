package eu.andret.ravel.notify

import java.util.Locale

/**
 * Where a player reads how to let the game run in the background on their phone: the page for its
 * manufacturer on dontkillmyapp.com, or the site's front page when it has none.
 *
 * Needed because some manufacturers stop an app the player closes from the recent apps list as if
 * it had been force-stopped, and a stopped app gets no alarm. Seen on a Redmi Note 10 5G (MIUI 14)
 * with Autostart off: the alarm went off on the minute, and the system refused to start the game
 * for it - "Unable to launch app ... process is bad" - so the reminder never existed. Nothing in
 * the app can get past that; the player has to change a setting, and every manufacturer names it
 * and hides it differently, changing it between system versions.
 *
 * So the instructions are linked rather than kept here: that site follows a dozen manufacturers and
 * their updates, which a game cannot. It is in English only, which the link text says.
 *
 * The table is the site's own, copied from its vendor list on 2026-11-14
 * (`https://dontkillmyapp.com/api/v1/output.json`): each page's address and the manufacturer names it answers for, as phones report
 * them. Copied rather than fetched, because the list changes rarely and a request from the app to
 * another server would be data sent off the device, which the privacy policy says the app does not
 * do; the browser opening the page is the player's own request. Refresh it by hand from that file.
 * Only the facts are taken - names and addresses - not the site's instructions, which are under CC BY
 * 4.0 and stay on the site.
 *
 * A manufacturer missing from the list gets the site's page for everyone else, `/general`, rather
 * than its front page: that one has the general advice, the front page only the list.
 */
object BackgroundRestrictions {
    private const val SITE = "https://dontkillmyapp.com/"

    /** The page for anything not in [PAGES]. */
    private const val GENERAL = "general"

    /**
     * Manufacturer name, lowercased, to the page for it - the site's `manufacturer` and `url` fields.
     * Redmi and POCO are not in the site's list: those phones report "Xiaomi", and the two entries
     * only catch one that ever reports its brand instead.
     */
    private val PAGES = mapOf(
        "xiaomi" to "xiaomi",
        "redmi" to "xiaomi",
        "poco" to "xiaomi",
        "samsung" to "samsung",
        "oneplus" to "oneplus",
        "huawei" to "huawei",
        "ulefone" to "ulefone",
        "oppo" to "oppo",
        "meizu" to "meizu",
        "asus" to "asus",
        "wiko" to "wiko",
        "vivo" to "vivo",
        "tecno" to "tecno",
        "realme" to "realme",
        "motorola" to "motorola",
        "lenovo" to "lenovo",
        "blackview" to "blackview",
        "unihertz" to "unihertz",
        "sony" to "sony",
        "google" to "google",
        "stock_android" to "google",
        "nokia" to "nokia",
        "hmd global" to "nokia",
        "htc" to "htc",
    )

    /** The page for [manufacturer], as `Build.MANUFACTURER` reports it, or the one for everyone else. */
    fun helpUrl(manufacturer: String): String =
        SITE + (PAGES[manufacturer.trim().lowercase(Locale.ROOT)] ?: GENERAL)
}
