package io.github.conorryan.timerwidget.ui

import io.github.conorryan.timerwidget.widget.TimerService

/** One running (or ringing) timer as the app's list shows it. */
data class RunUi(
    /** Key in [TimerService.active]: a widget ID, or a negative app-run key. */
    val key: Int,
    val timerId: String,
    val name: String,
    val emoji: String,
    val displaySeconds: Int,
    val totalSeconds: Int,
    val endAtMs: Long,
    val finished: Boolean,
) {
    /** Share of the run still to go, 1 at the start and 0 when it rings. */
    val fractionLeft: Float
        get() = if (finished || totalSeconds <= 0) 0f else displaySeconds / totalSeconds.toFloat()
}

/** Snapshot of [active] at [nowMs], soonest to finish first (ringing ones on top). */
fun runsAt(active: Map<Int, TimerService.Active>, nowMs: Long): List<RunUi> =
    active.map { (key, entry) ->
        RunUi(
            key = key,
            timerId = entry.timerId,
            name = entry.name,
            emoji = entry.emoji,
            displaySeconds = if (entry.finished) 0 else entry.displaySecondsAt(nowMs),
            totalSeconds = entry.totalSeconds,
            endAtMs = entry.endAtMs,
            finished = entry.finished,
        )
    }.sortedWith(compareByDescending<RunUi> { it.finished }.thenBy { it.endAtMs })
