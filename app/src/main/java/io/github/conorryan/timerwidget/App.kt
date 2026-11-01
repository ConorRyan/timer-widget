package io.github.conorryan.timerwidget

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.timerwidget.ui.TimerEditor
import io.github.conorryan.timerwidget.ui.TimerListScreen
import io.github.conorryan.timerwidget.ui.TimerViewModel
import io.github.conorryan.timerwidget.ui.runsAt
import io.github.conorryan.timerwidget.util.AlarmSound
import io.github.conorryan.timerwidget.util.AlarmSounds
import io.github.conorryan.timerwidget.widget.TimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Editor target meaning "create a new timer"; any other value is a timer ID. */
private const val NEW_TIMER = ""

@Composable
fun App(viewModel: TimerViewModel) {
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    // The service bumps this on every change and ticker second; re-read the
    // static run map each time so countdowns stay live without polling.
    val runVersion by TimerService.version.collectAsStateWithLifecycle()
    val runs = remember(runVersion) { runsAt(TimerService.active, System.currentTimeMillis()) }
    var editorTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // The RingtoneManager cursor query can take a second; run it off the main
    // thread once at app open so opening the editor never blocks.
    var alarmSounds by remember { mutableStateOf<List<AlarmSound>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            alarmSounds = AlarmSounds.available(context.applicationContext)
        }
    }

    val target = editorTarget
    val editing = target?.takeIf { it != NEW_TIMER }?.let { id -> timers.find { it.id == id } }
    if (target == NEW_TIMER || editing != null) {
        BackHandler { editorTarget = null }
        key(target) {
            TimerEditor(
                initial = editing,
                sounds = alarmSounds,
                onClose = { editorTarget = null },
                onSave = { def ->
                    editorTarget = null
                    if (editing == null) viewModel.add(def) else viewModel.update(def)
                },
                onDelete = editing?.let { timer ->
                    {
                        editorTarget = null
                        viewModel.remove(timer.id)
                    }
                },
            )
        }
    } else {
        TimerListScreen(
            timers = timers,
            runs = runs,
            onToggle = viewModel::toggleRun,
            onStopRun = viewModel::stopRun,
            onEdit = { editorTarget = it },
            onCreate = { editorTarget = NEW_TIMER },
        )
    }
}
