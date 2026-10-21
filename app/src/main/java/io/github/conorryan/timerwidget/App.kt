package io.github.conorryan.timerwidget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conorryan.timerwidget.data.TimerDef
import io.github.conorryan.timerwidget.ui.TimerViewModel
import io.github.conorryan.timerwidget.ui.TimerWidgetTheme
import io.github.conorryan.timerwidget.util.formatHms
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: TimerViewModel) {
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    var editingId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val editing = editingId?.let { id -> timers.find { it.id == id } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_timer))
            }
        },
    ) { padding ->
        if (timers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.no_timers_hint))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(timers, key = { it.id }) { timer ->
                    TimerCard(
                        timer = timer,
                        onOpen = { editingId = timer.id },
                        onDelete = { viewModel.remove(timer.id) },
                    )
                }
            }
        }
    }

    if (creating) {
        TimerDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { def ->
                creating = false
                viewModel.add(def)
            },
        )
    }
    editing?.let { timer ->
        TimerDialog(
            initial = timer,
            onDismiss = { editingId = null },
            onSave = { def ->
                editingId = null
                viewModel.update(def)
            },
        )
    }
}

@Composable
private fun TimerCard(
    timer: TimerDef,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    timer.name.ifBlank { stringResource(R.string.timer_fallback) },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    formatHms(timer.totalSeconds),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_timer))
            }
        }
    }
}

@Composable
private fun TimerDialog(
    initial: TimerDef?,
    onDismiss: () -> Unit,
    onSave: (TimerDef) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var hours by remember { mutableStateOf(initial?.hours ?: 0) }
    var minutes by remember { mutableStateOf(initial?.minutes ?: 5) }
    var seconds by remember { mutableStateOf(initial?.seconds ?: 0) }
    val titleRes = if (initial == null) R.string.new_timer else R.string.edit_timer

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.timer_name)) },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = hours,
                        range = 0..99,
                        label = stringResource(R.string.hours),
                        modifier = Modifier.weight(1f),
                    ) { hours = it }
                    NumberField(
                        value = minutes,
                        range = 0..59,
                        label = stringResource(R.string.minutes),
                        modifier = Modifier.weight(1f),
                    ) { minutes = it }
                    NumberField(
                        value = seconds,
                        range = 0..59,
                        label = stringResource(R.string.seconds),
                        modifier = Modifier.weight(1f),
                    ) { seconds = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    TimerDef(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        name = name,
                        hours = hours,
                        minutes = minutes,
                        seconds = seconds,
                    ),
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun NumberField(
    value: Int,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if ((text.toIntOrNull() ?: -1) != value) text = value.toString()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter(Char::isDigit).take(2)
            text.toIntOrNull()?.let { onChange(it.coerceIn(range)) }
        },
        modifier = modifier.onFocusChanged { state ->
            if (!state.isFocused && text.isEmpty()) text = value.toString()
        },
        label = { Text(label) },
        singleLine = true,
    )
}

@Preview(showBackground = true)
@Composable
private fun AppPreview() {
    TimerWidgetTheme {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
    }
}
