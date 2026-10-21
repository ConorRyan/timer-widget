package com.conorryan.timerwidget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.conorryan.timerwidget.data.TimerDef
import com.conorryan.timerwidget.data.TimerStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    private val store = TimerStore(app)

    val timers: StateFlow<List<TimerDef>> = store.timers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add() {
        viewModelScope.launch {
            store.mutate { list ->
                list += TimerDef(
                    id = UUID.randomUUID().toString(),
                    name = "Timer ${list.size + 1}",
                    hours = 0,
                    minutes = 5,
                    seconds = 0,
                )
            }
        }
    }

    fun update(def: TimerDef) {
        viewModelScope.launch {
            store.mutate { list ->
                val index = list.indexOfFirst { it.id == def.id }
                if (index >= 0) list[index] = def
            }
        }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            store.mutate { list -> list.removeAll { it.id == id } }
        }
    }
}
