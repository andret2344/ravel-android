package eu.andret.ravel.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.andret.ravel.RavelApplication

/**
 * Where the daily notification's alarm lands, and where the system says the alarm may be gone.
 *
 * Our own [DailyNotifications.ACTION_ALARM] decides and maybe sends. Everything else the manifest
 * lets in - a reboot, which clears every alarm; an update, which may; the clock or the time zone
 * changing under a wall-clock time - only sets the alarm again.
 *
 * Not exported. The system still delivers its own broadcasts to it, and nothing else should be able
 * to make the game post a notification.
 *
 * The work is synchronous and small - a few preferences read, one alarm set - so there is no
 * `goAsync` here.
 */
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifications = (context.applicationContext as RavelApplication).notifications
        if (intent.action == DailyNotifications.ACTION_ALARM) {
            notifications.fire()
        } else {
            notifications.reschedule()
        }
    }
}
