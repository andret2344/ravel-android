package eu.andret.ravel.ui.settings

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import eu.andret.ravel.RavelApplication
import kotlinx.coroutines.flow.StateFlow

/**
 * The privacy page's switches and its one form. Thin, like [SettingsViewModel]: every answer here is
 * held and acted on by [eu.andret.ravel.analytics.Telemetry] or [eu.andret.ravel.ads.AdConsent].
 */
class PrivacyViewModel(application: Application) : AndroidViewModel(application) {
    private val telemetry = (application as RavelApplication).telemetry

    private val consent = (application as RavelApplication).consent

    /** Whether usage statistics are sent. */
    val analyticsEnabled: StateFlow<Boolean> = telemetry.analyticsEnabled

    /** Whether crash reports are sent. */
    val crashReportsEnabled: StateFlow<Boolean> = telemetry.crashReportsEnabled

    /**
     * Whether this device has to be offered a way back into the consent form - false for most of the
     * world. See [eu.andret.ravel.ads.AdConsent.privacyOptionsRequired].
     */
    val privacyOptions: StateFlow<Boolean> = consent.privacyOptionsRequired

    /** No confirmation, like the haptics: undone by the same switch. */
    fun setAnalytics(value: Boolean) = telemetry.setAnalyticsEnabled(value)

    fun setCrashReports(value: Boolean) = telemetry.setCrashReportsEnabled(value)

    /**
     * Reopens the consent form. Takes an [Activity] per call and never holds one, since this view
     * model outlives it; whatever the player changes is read back by
     * [eu.andret.ravel.ads.AdConsent], not applied here.
     */
    fun changePrivacyOptions(activity: Activity) = consent.showPrivacyOptions(activity)
}
