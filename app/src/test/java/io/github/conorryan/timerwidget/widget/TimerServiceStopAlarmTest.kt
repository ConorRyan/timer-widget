package io.github.conorryan.timerwidget.widget

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimerServiceStopAlarmTest {

    @Before
    fun resetState() {
        TimerService.active.clear()
        TimerService.alarmPlayer.stopAll()
    }

    @Test
    fun stoppingTheAlarmClearsTheFinishedTimerSoTheNextWidgetTapStarts() {
        val entry = TimerService.Active(
            timerId = "t1",
            name = "Tea",
            emoji = "🍵",
            endAtMs = System.currentTimeMillis() - 1000,
            totalMs = 60_000,
        ).apply { finished = true }
        TimerService.active[3] = entry

        val intent = Intent(ApplicationProvider.getApplicationContext(), TimerService::class.java)
            .setAction(TimerService.ACTION_STOP_ALARM)
            .putExtra(TimerService.EXTRA_WIDGET_ID, 3)
        Robolectric.buildService(TimerService::class.java, intent).startCommand(0, 0)

        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(TimerService.active.containsKey(3))
        assertFalse(TimerService.alarmPlayer.isPlaying(3))
    }
}
