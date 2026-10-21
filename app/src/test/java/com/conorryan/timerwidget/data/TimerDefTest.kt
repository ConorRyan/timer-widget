package com.conorryan.timerwidget.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerDefTest {

    @Test
    fun totalSecondsCombinesHoursMinutesSeconds() {
        assertEquals(3661, TimerDef("id", "name", 1, 1, 1).totalSeconds)
    }

    @Test
    fun jsonRoundTrip() {
        val json = Json { ignoreUnknownKeys = true }
        val def = TimerDef("abc", "Tea", 0, 2, 30)
        val decoded = json.decodeFromString<TimerDef>(json.encodeToString(def))
        assertEquals(def, decoded)
    }

    @Test
    fun ignoresUnknownFields() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<TimerDef>(
            """{"id":"abc","name":"Tea","hours":0,"minutes":2,"seconds":30,"extra":1}""",
        )
        assertTrue(decoded.totalSeconds == 150)
    }
}
