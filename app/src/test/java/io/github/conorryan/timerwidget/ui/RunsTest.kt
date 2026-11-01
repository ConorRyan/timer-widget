package io.github.conorryan.timerwidget.ui

import io.github.conorryan.timerwidget.widget.TimerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunsTest {

    private val now = 1_000_000L

    private fun active(timerId: String, endAtMs: Long, totalMs: Long = 60_000, finished: Boolean = false) =
        TimerService.Active(
            timerId = timerId,
            name = timerId,
            emoji = "⏳",
            endAtMs = endAtMs,
            totalMs = totalMs,
        ).apply { this.finished = finished }

    @Test
    fun runningTimerShowsDisplaySecondsAtTheGivenTime() {
        val runs = runsAt(mapOf(-5 to active("tea", endAtMs = now + 30_000)), now)

        val run = runs.single()
        assertEquals(-5, run.key)
        assertEquals("tea", run.timerId)
        // One less than the ceiling, like the widget.
        assertEquals(29, run.displaySeconds)
        assertEquals(60, run.totalSeconds)
        assertEquals(29 / 60f, run.fractionLeft, 0.0001f)
    }

    @Test
    fun finishedTimerShowsZeroAndAnEmptyRing() {
        val run = runsAt(mapOf(3 to active("eggs", endAtMs = now - 1_000, finished = true)), now).single()

        assertTrue(run.finished)
        assertEquals(0, run.displaySeconds)
        assertEquals(0f, run.fractionLeft)
    }

    @Test
    fun ringingRunsComeFirstThenSoonestToFinish() {
        val runs = runsAt(
            mapOf(
                1 to active("late", endAtMs = now + 90_000, totalMs = 120_000),
                2 to active("soon", endAtMs = now + 10_000),
                -3 to active("done", endAtMs = now - 5_000, finished = true),
            ),
            now,
        )

        assertEquals(listOf("done", "soon", "late"), runs.map { it.timerId })
    }

    @Test
    fun zeroLengthTimerDoesNotDivideByZero() {
        val run = runsAt(mapOf(4 to active("zero", endAtMs = now, totalMs = 0)), now).single()

        assertEquals(0f, run.fractionLeft)
    }
}
