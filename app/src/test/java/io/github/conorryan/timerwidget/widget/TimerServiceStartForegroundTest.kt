package io.github.conorryan.timerwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The widget tap is dispatched with [android.app.PendingIntent.getForegroundService], so
 * every delivered command must call startForeground before doing anything else — including
 * the commands that only stop or clear timers — or the system raises
 * RemoteServiceException ("did not then call Service.startForeground").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimerServiceStartForegroundTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetState() {
        TimerService.active.clear()
        TimerService.alarmPlayer.stopAll()
    }

    @Test
    fun togglingARunningTimerOffStartsForegroundFirst() {
        TimerService.active[3] = runningEntry(3)

        val shadow = startCommand(toggleIntent(3))

        assertEquals(TimerService.NOTIF_ID, shadow.lastForegroundNotificationId)
    }

    @Test
    fun clearingAllTimersStartsForegroundFirst() {
        TimerService.active[3] = runningEntry(3)

        val shadow = startCommand(
            Intent(context, TimerService::class.java).setAction(TimerService.ACTION_CLEAR_ALL),
        )

        assertEquals(TimerService.NOTIF_ID, shadow.lastForegroundNotificationId)
    }

    @Test
    fun stoppingAnAlarmStartsForegroundFirst() {
        TimerService.active[3] = runningEntry(3).apply { finished = true }

        val shadow = startCommand(
            Intent(context, TimerService::class.java)
                .setAction(TimerService.ACTION_STOP_ALARM)
                .putExtra(TimerService.EXTRA_WIDGET_ID, 3),
        )

        assertEquals(TimerService.NOTIF_ID, shadow.lastForegroundNotificationId)
    }

    @Test
    fun aCommandWithNoWidgetIdStillStartsForeground() {
        val shadow = startCommand(
            Intent(context, TimerService::class.java)
                .setAction(TimerService.ACTION_TOGGLE)
                .putExtra(TimerService.EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
        )

        assertEquals(TimerService.NOTIF_ID, shadow.lastForegroundNotificationId)
    }

    private fun startCommand(intent: Intent): org.robolectric.shadows.ShadowService {
        val controller = Robolectric.buildService(TimerService::class.java, intent)
        controller.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        val service = controller.get()
        return shadowOf(service)
    }

    private fun toggleIntent(widgetId: Int) =
        Intent(context, TimerService::class.java)
            .setAction(TimerService.ACTION_TOGGLE)
            .putExtra(TimerService.EXTRA_WIDGET_ID, widgetId)

    private fun runningEntry(widgetId: Int) = TimerService.Active(
        timerId = "t$widgetId",
        name = "Tea",
        emoji = "🍵",
        endAtMs = System.currentTimeMillis() + 60_000,
        totalMs = 60_000,
    )
}
