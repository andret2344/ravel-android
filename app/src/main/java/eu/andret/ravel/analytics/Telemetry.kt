package eu.andret.ravel.analytics

import android.content.Context
import android.os.Bundle
import androidx.core.content.edit
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import com.google.firebase.crashlytics.FirebaseCrashlytics
import eu.andret.ravel.R
import eu.andret.ravel.settings.SETTINGS_FILE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Analytics and crash reports: the two switches in the options, and the one door events go out by.
 *
 * ## Three gates, and which one is whose
 *
 * 1. **The build** (`R.bool.telemetry`, set per build type in `app/build.gradle.kts`): only a release
 *    build sends anything. Debug shares the release's application id, and the developer's own play
 *    would drown the testers' during the alpha.
 * 2. **The player's switches**, here, both on by default and both in the options tab. Off means off:
 *    collection is disabled in the SDK, not merely filtered here, so nothing at all leaves - not even
 *    the consent-less pings of gate 3.
 * 3. **Consent**, for analytics only, through Google's consent mode - and not decided in this class.
 *    The manifest sets every consent type to denied, which is what a launch starts from everywhere.
 *    In the EEA, the UK and Switzerland the UMP form's answer is turned into consent mode by the UMP
 *    SDK itself (consent mode switched on in the AdMob console); everywhere else no form is owed and
 *    [eu.andret.ravel.ads.AdConsent.outsideRegulation] says so, and this class grants all four. In
 *    "advanced" consent mode, the owner's choice (2026-11-15), analytics with consent denied still
 *    sends pings with no identifier in them; the privacy policy says so.
 *
 * Crash reports are outside gate 3 on purpose, owner's choice: they are sent on the controller's
 * legitimate interest, not on consent, so the game's crashes are seen from every tester and not only
 * from those who said yes to the ad form. The switch is how a player objects.
 *
 * ## Why the manifest turns both off and this class turns them on
 *
 * `firebase_analytics_collection_enabled` and `firebase_crashlytics_collection_enabled` are false in
 * the manifest, so the SDKs start silent, and [start] enables them when all of gates 1 and 2 allow
 * it. The other way round - on in the manifest, switched off here - would send whatever the SDK does
 * in the moments before [start] runs, from a debug build or for a player who said no.
 *
 * One instance for the process, on [eu.andret.ravel.RavelApplication], for the reason every store
 * there is one: two copies of the switches would disagree.
 */
class Telemetry(context: Context, private val scope: CoroutineScope) {
    private val application = context.applicationContext

    private val preferences = application.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    /** Gate 1. Read once: a build does not change what it is while it runs. */
    private val allowed = application.resources.getBoolean(R.bool.telemetry)

    private val analytics by lazy { FirebaseAnalytics.getInstance(application) }

    private val crashlytics by lazy { FirebaseCrashlytics.getInstance() }

    private val _analyticsEnabled = MutableStateFlow(preferences.getBoolean(KEY_ANALYTICS, true))

    /** The player's analytics switch. */
    val analyticsEnabled: StateFlow<Boolean> = _analyticsEnabled.asStateFlow()

    private val _crashReportsEnabled =
        MutableStateFlow(preferences.getBoolean(KEY_CRASH_REPORTS, true))

    /** The player's crash reports switch. */
    val crashReportsEnabled: StateFlow<Boolean> = _crashReportsEnabled.asStateFlow()

    /**
     * Applies the switches to the SDKs, and grants consent once the consent service says none is
     * owed on this device.
     *
     * Called once, from `RavelApplication.onCreate`. The SDKs remember the last value they were
     * given across launches, which is why the switches are applied on every start rather than only
     * when flipped: the stored answer here is the one that counts, and the SDK's copy follows it.
     *
     * @param outsideRegulation [eu.andret.ravel.ads.AdConsent.outsideRegulation].
     */
    fun start(outsideRegulation: StateFlow<Boolean>) {
        if (!allowed) return
        analytics.setAnalyticsCollectionEnabled(_analyticsEnabled.value)
        crashlytics.setCrashlyticsCollectionEnabled(_crashReportsEnabled.value)
        // Once, on the first "no form owed". The service answers that per launch, and an answer it
        // gave stays given: a player who crosses into the EEA gets the form there, and the form's
        // answer then replaces this one through consent mode.
        scope.launch {
            outsideRegulation.filter { it }.first()
            analytics.setConsent(ConsentType.entries.associateWith { ConsentStatus.GRANTED })
        }
    }

    /**
     * Sends [event], or drops it when this build or the player says no. Dropped here rather than
     * left to the SDK so a debug build never builds a bundle for an event that is going nowhere.
     */
    fun log(event: AnalyticsEvent) {
        if (!allowed || !_analyticsEnabled.value) return
        val bundle = Bundle()
        event.params.forEach { (key, value) ->
            when (value) {
                is Long -> bundle.putLong(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        analytics.logEvent(event.name, bundle)
    }

    /** Written before it is published, as with [eu.andret.ravel.settings.HapticSettings.setEnabled]. */
    fun setAnalyticsEnabled(value: Boolean) {
        preferences.edit { putBoolean(KEY_ANALYTICS, value) }
        _analyticsEnabled.value = value
        if (allowed) analytics.setAnalyticsCollectionEnabled(value)
    }

    /** As [setAnalyticsEnabled]. Takes effect from the next crash; one already queued may still go. */
    fun setCrashReportsEnabled(value: Boolean) {
        preferences.edit { putBoolean(KEY_CRASH_REPORTS, value) }
        _crashReportsEnabled.value = value
        if (allowed) crashlytics.setCrashlyticsCollectionEnabled(value)
    }

    private companion object {
        /** Stored keys in [SETTINGS_FILE]. **Never rename**: a renamed key turns a switch back on. */
        const val KEY_ANALYTICS = "analytics"
        const val KEY_CRASH_REPORTS = "crashReports"
    }
}
