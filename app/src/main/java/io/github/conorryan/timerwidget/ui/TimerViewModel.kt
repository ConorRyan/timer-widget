package io.github.conorryan.timerwidget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.conorryan.timerwidget.data.TimerDef
import io.github.conorryan.timerwidget.data.TimerStore
import io.github.conorryan.timerwidget.widget.TimerService
import io.github.conorryan.timerwidget.widget.TimerWidgetViews
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimerViewModel @JvmOverloads constructor(
    app: Application,
    /** Injectable so tests can observe start/stop requests without the service. */
    private val runControl: RunControl = ServiceRunControl(app),
    /** Injectable so tests can observe widget refreshes without AppWidgetManager. */
    private val refreshWidgets: suspend (Application) -> Unit = { application ->
        TimerWidgetViews.refreshAll(application)
    },
) : AndroidViewModel(app) {

    private val store = TimerStore(app)

    private val loaded = CompletableDeferred<Unit>()

    private val _timers = MutableStateFlow<List<TimerDef>>(emptyList())
    val timers = _timers.asStateFlow()

    init {
        viewModelScope.launch {
            _timers.value = store.timers.first()
            loaded.complete(Unit)
        }
    }

    fun add(def: TimerDef) = mutate { list -> list += def }

    fun update(def: TimerDef) = mutate { list ->
        val index = list.indexOfFirst { it.id == def.id }
        if (index >= 0) list[index] = def
    }

    fun remove(id: String) = mutate { list -> list.removeAll { it.id == id } }

    /** Starts [timerId] without a widget, or stops it if it is running anywhere. */
    fun toggleRun(timerId: String) = runControl.toggle(timerId)

    /** Stops one run by its key in [TimerService.active]. */
    fun stopRun(key: Int) = runControl.stop(key)

    /**
     * Applies [transform] to the in-memory UI state synchronously on the
     * caller's frame, then persists the resulting list in the background.
     * The UI state must never wait on the DataStore round trip: a TextField
     * bound to state that lags behind keystrokes reverts typed characters.
     */
    private fun mutate(transform: (MutableList<TimerDef>) -> Unit) {
        viewModelScope.launch {
            loaded.await()
            var next: List<TimerDef> = emptyList()
            _timers.update { current ->
                next = current.toMutableList().also(transform)
                next
            }
            store.save(next)
            refreshWidgets(getApplication())
        }
    }
}

/** Start/stop requests the UI sends to [TimerService]. */
interface RunControl {
    fun toggle(timerId: String)
    fun stop(key: Int)
}

private class ServiceRunControl(private val app: Application) : RunControl {
    override fun toggle(timerId: String) = TimerService.toggleTimer(app, timerId)
    override fun stop(key: Int) = TimerService.stopRun(app, key)
}
