package io.github.conorryan.timerwidget.data

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
        val def = TimerDef("abc", "Tea", 0, 2, 30, emoji = "🍵")
        val decoded = json.decodeFromString<TimerDef>(json.encodeToString(def))
        assertEquals(def, decoded)
    }

    @Test
    fun jsonRoundTripWithSound() {
        val json = Json { ignoreUnknownKeys = true }
        val def = TimerDef(
            "abc", "Tea", 0, 2, 30,
            emoji = "🍵",
            soundUri = "content://media/internal/audio/media/42",
            soundName = "Cesium",
        )
        val decoded = json.decodeFromString<TimerDef>(json.encodeToString(def))
        assertEquals(def, decoded)
    }

    @Test
    fun soundDefaultsToNullWhenMissingFromJson() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<TimerDef>(
            """{"id":"abc","name":"Tea","hours":0,"minutes":2,"seconds":30}""",
        )
        assertEquals(null, decoded.soundUri)
        assertEquals(null, decoded.soundName)
    }

    @Test
    fun ignoresUnknownFields() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<TimerDef>(
            """{"id":"abc","name":"Tea","hours":0,"minutes":2,"seconds":30,"extra":1}""",
        )
        assertTrue(decoded.totalSeconds == 150)
    }

    @Test
    fun emojiDefaultsToBlankWhenMissingFromJson() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<TimerDef>(
            """{"id":"abc","name":"Tea","hours":0,"minutes":2,"seconds":30}""",
        )
        assertEquals("", decoded.emoji)
    }

    @Test
    fun labelPrefersEmojiThenNameThenFallback() {
        assertEquals("⏳", TimerDef("a", "Tea", 0, 0, 10, emoji = "⏳").label("Timer"))
        assertEquals("Tea", TimerDef("a", "Tea", 0, 0, 10).label("Timer"))
        assertEquals("Timer", TimerDef("a", "", 0, 0, 10).label("Timer"))
    }
}
