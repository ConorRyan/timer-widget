package com.conorryan.timerwidget

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.conorryan.timerwidget.data.TimerDef
import com.conorryan.timerwidget.ui.TimerViewModel
import com.conorryan.timerwidget.ui.TimerWidgetTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: TimerViewModel) {
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::add) {
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
                    TimerRow(
                        timer = timer,
                        onUpdate = viewModel::update,
                        onDelete = viewModel::remove,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerRow(
    timer: TimerDef,
    onUpdate: (TimerDef) -> Unit,
    onDelete: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = timer.name,
                    onValueChange = { name -> onUpdate(timer.copy(name = name.take(40))) },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.timer_name)) },
                    singleLine = true,
                )
                IconButton(onClick = { onDelete(timer.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_timer))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = timer.hours,
                    range = 0..99,
                    label = stringResource(R.string.hours),
                    modifier = Modifier.weight(1f),
                ) { hours -> onUpdate(timer.copy(hours = hours)) }
                NumberField(
                    value = timer.minutes,
                    range = 0..59,
                    label = stringResource(R.string.minutes),
                    modifier = Modifier.weight(1f),
                ) { minutes -> onUpdate(timer.copy(minutes = minutes)) }
                NumberField(
                    value = timer.seconds,
                    range = 0..59,
                    label = stringResource(R.string.seconds),
                    modifier = Modifier.weight(1f),
                ) { seconds -> onUpdate(timer.copy(seconds = seconds)) }
            }
        }
    }
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
