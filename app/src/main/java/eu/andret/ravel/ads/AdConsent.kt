package eu.andret.ravel.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Whether this device has said an ad may be requested, and what it said about personalisation.
 *
 * The privacy policy promises a Google-certified CMP before a personalised ad is shown in the
 * EEA, the UK or Switzerland, and this is it: the User Messaging Platform SDK, asked once per
 * launch. Rolling our own dialog would be quicker and would not satisfy that promise - "certified"
 * is the whole point of the sentence, and the certification covers the consent string the form
 * writes, not the wording it shows.
 *
 * Two things are deliberately not decided here:
 *
 * - **Where the player is.** The SDK works that out and returns "no form needed" outside the
 *   regions that require one, so there is no geography check in this file to get wrong or to leave
 *   stale when the region list changes. Poland is inside that list, which is worth knowing while
 *   testing: the form really does appear on a local device.
 * - **Whether ads are wanted.** [canRequestAds] answers "is this allowed", never "should this
 *   happen". Nothing in this app requests an ad until the player asks for one in the shop - the
 *   policy says every ad is opted into, and a flag that says a request is permitted is not a player
 *   asking for one.
 *
 * A refusal is a normal outcome and not an error. The player keeps a fully playable game and simply
 * has no way to earn coins by watching; the shop says so rather than pretending an ad is loading.
 */
class AdConsent(context: Context, private val scope: CoroutineScope) {
    private val application = context.applicationContext

    private val information: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(application)

    private val _canRequestAds = MutableStateFlow(information.canRequestAds())

    /**
     * Whether an ad request would be legitimate right now.
     *
     * Seeded from what the SDK already has on disk rather than starting false and waiting for
     * [gather]: consent given on an earlier run is still consent, and a player who answered the
     * form yesterday should not watch the shop say "not yet" while a network round trip re-asks a
     * question that has already been answered.
     */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(isPrivacyOptionsRequired())

    /**
     * Whether the settings screen has to offer a way back into the form.
     *
     * The SDK decides this, and it is false for most of the world: a player who was never asked has
     * nothing to change, and a settings row that opens a form which immediately reports "no form
     * available" is worse than no row. This is why the row is conditional rather than always there.
     */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val _settled = MutableStateFlow(false)

    /**
     * Whether the question has been put to this device and answered, one way or another.
     *
     * The game waits on this before drawing anything - see `MainActivity`. That is the whole reason
     * it is a flag of its own rather than something read off [canRequestAds]: that one starts out
     * saying "no", so a launch which has not reached the consent service yet is indistinguishable
     * from a player who refused, and a splash screen watching it would have nothing to wait for.
     *
     * True means "stop waiting", which is not quite the same as "an answer arrived" - see [gather].
     */
    val settled: StateFlow<Boolean> = _settled.asStateFlow()

    /**
     * Asks the SDK for this device's status and shows the form when one is owed.
     *
     * Needs an [Activity] rather than the application, because a form is a dialog and a dialog needs
     * something on screen to sit in front of. Called from `MainActivity`, once per creation of it,
     * and it does nothing on the second call: the answer belongs to the process, so an activity
     * rebuilt by a language switch must not put the player back behind a loading screen for a
     * question they have already answered.
     *
     * Both callbacks end in the same place. A failed update is not a refusal: whatever consent was
     * already stored still stands, and the right response to "we could not reach the consent
     * service" is to carry on with what the device last said rather than to revoke it.
     *
     * ## The timer, and why the game is never held behind this
     *
     * Everything below happens over the network, and this app is a word puzzle that needs no
     * connection at all. A launch on a dead train line must not be a launch that never finishes, so
     * the wait has an end: after [SETTLE_TIMEOUT] the game opens whatever the SDK is doing.
     *
     * The timer is deliberately **not** cancelled when the round trip comes back, because the form
     * is the slow part and past that point the player is the one holding it up. Firing behind a
     * form that is already on screen costs nothing - a form is a dialog, and what it uncovers is
     * the game it was about to uncover anyway. Firing before one appears gives back exactly the old
     * behaviour, where the game opened first and the form arrived on top of it, which is the right
     * way for this to degrade.
     */
    fun gather(activity: Activity) {
        if (_settled.value) return
        scope.launch {
            delay(SETTLE_TIMEOUT)
            _settled.value = true
        }
        information.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                // Loads and shows in one call, and does nothing at all where no form is required.
                // The error it hands back is for logging only - a form that failed to load leaves
                // consent exactly as it was, which publish() below reads either way.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { publish() }
            },
            { publish() },
        )
    }

    /**
     * Reopens the form so the player can change their mind, as the policy says they can.
     *
     * @param onFinished runs when the form closes, whether or not anything changed, so the caller
     *   can drop whatever it was showing while the form was up.
     */
    fun showPrivacyOptions(activity: Activity, onFinished: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            publish()
            onFinished()
        }
    }

    /**
     * Republishes what the SDK now holds.
     *
     * Read back from the SDK rather than inferred from what the form reported, because the form is
     * not the only thing that changes this: a device can arrive with consent already stored, and a
     * failed update leaves the previous answer in place. One source of truth, asked again.
     */
    private fun publish() {
        _canRequestAds.value = information.canRequestAds()
        _privacyOptionsRequired.value = isPrivacyOptionsRequired()
        _settled.value = true
    }

    private fun isPrivacyOptionsRequired(): Boolean =
        information.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    private companion object {
        /**
         * How long the game waits for the consent service before opening anyway.
         *
         * Long enough that a working connection resolves inside it - the round trip is typically a
         * few hundred milliseconds - and short enough that a broken one is not a launch the player
         * gives up on. Four seconds of a plain screen is already at the edge of what an app is
         * allowed to spend before it looks broken, and this is the ceiling rather than the
         * expectation: almost every launch settles long before it, and every launch after the first
         * one on a device that needs no form settles immediately.
         *
         * A [Duration] rather than a `Long` of milliseconds, which costs the `const` - a duration
         * cannot be one - and is worth it here: the unit was in the name because the type could not
         * carry it, and a name is not checked by anything. `delay` takes both, and the overload that
         * takes a number is the one that lets a value in the wrong unit through.
         */
        val SETTLE_TIMEOUT = 4.seconds
    }
}
