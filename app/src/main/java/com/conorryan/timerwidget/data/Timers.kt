package com.conorryan.timerwidget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.timerDataStore by preferencesDataStore(name = "timers")

@Serializable
data class TimerDef(
    val id: String,
    val name: String,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
) {
    val totalSeconds: Int get() = hours * 3600 + minutes * 60 + seconds
}

class TimerStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val timers: Flow<List<TimerDef>> = context.timerDataStore.data
        .map { prefs ->
            prefs[KEY]?.let { raw ->
                runCatching { json.decodeFromString<List<TimerDef>>(raw) }.getOrNull()
            } ?: emptyList()
        }

    suspend fun timerById(id: String): TimerDef? = timers.first().find { it.id == id }

    suspend fun mutate(transform: (MutableList<TimerDef>) -> Unit) {
        context.timerDataStore.edit { prefs ->
            val list = (prefs[KEY]?.let { raw ->
                runCatching { json.decodeFromString<List<TimerDef>>(raw) }.getOrNull()
            } ?: emptyList()).toMutableList()
            transform(list)
            prefs[KEY] = json.encodeToString(list.toList())
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("timers_json")
    }
}
