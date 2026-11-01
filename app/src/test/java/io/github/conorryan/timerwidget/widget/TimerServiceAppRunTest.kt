package io.github.conorryan.timerwidget.widget

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimerServiceAppRunTest {

    @Before
    fun resetState() {
        TimerService.active.clear()
        TimerService.alarmPlayer.stopAll()
    }

    private fun entry(timerId: String, finished: Boolean = false) = TimerService.Active(
        timerId = timerId,
        name = timerId,
        emoji = "🍵",
        endAtMs = System.currentTimeMillis() + if (finished) -1_000 else 60_000,
        totalMs = 60_000,
    ).apply { this.finished = finished }

    private fun toggle(timerId: String) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), TimerService::class.java)
            .setAction(TimerService.ACTION_TOGGLE_TIMER)
            .putExtra(TimerService.EXTRA_TIMER_ID, timerId)
        Robolectric.buildService(TimerService::class.java, intent).startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun appKeysAreNegativeAndWidgetIdsAreNot() {
        assertTrue(TimerService.isAppRun(-1))
        assertTrue(TimerService.isAppRun(Int.MIN_VALUE + 1))
        assertFalse(TimerService.isAppRun(42))
    }

    @Test
    fun tappingARunningTimerInTheAppStopsEveryRunOfIt() {
        TimerService.active[-2] = entry("tea")
        TimerService.active[7] = entry("tea", finished = true)
        TimerService.active[8] = entry("eggs")

        toggle("tea")

        assertEquals(setOf(8), TimerService.active.keys)
        assertFalse(TimerService.alarmPlayer.isPlaying(7))
    }

    @Test
    fun changesBumpTheVersionTheAppWatches() {
        TimerService.active[-2] = entry("tea")
        val before = TimerService.version.value

        toggle("tea")

        assertTrue(TimerService.version.value > before)
    }
}
