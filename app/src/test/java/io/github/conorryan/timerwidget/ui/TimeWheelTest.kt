package io.github.conorryan.timerwidget.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeWheelTest {

    @Test
    fun labelsPadMinutesAndSecondsButNotHours() {
        assertEquals("07", wheelLabel(7, pad = true))
        assertEquals("42", wheelLabel(42, pad = true))
        assertEquals("7", wheelLabel(7, pad = false))
    }

    @Test
    fun tierMarksTheCenteredRowAndItsNeighbors() {
        assertEquals(0, wheelTier(5, 5))
        assertEquals(1, wheelTier(4, 5))
        assertEquals(1, wheelTier(6, 5))
        assertEquals(2, wheelTier(3, 5))
        assertEquals(2, wheelTier(7, 5))
    }

    @Test
    fun tierClampsFarRowsToOneLook() {
        assertEquals(2, wheelTier(0, 59))
        assertEquals(2, wheelTier(59, 0))
        assertEquals(2, wheelTier(0, 99))
    }

    @Test
    fun typedValueIsClampedToTheWheelRange() {
        assertEquals(10, parseWheelEntry("10", 0..59))
        assertEquals(59, parseWheelEntry("75", 0..59))
        assertEquals(0, parseWheelEntry("00", 0..59))
    }

    @Test
    fun strayCharactersAreIgnored() {
        assertEquals(25, parseWheelEntry(" 2a5", 0..59))
    }

    @Test
    fun emptyEntryKeepsTheCurrentValue() {
        assertNull(parseWheelEntry("", 0..59))
        assertNull(parseWheelEntry("ab", 0..59))
    }

    @Test
    fun presetMatchesOnlyWholeMinutes() {
        assertEquals(10, matchingPreset(0, 10, 0))
        assertNull(matchingPreset(0, 10, 5))
        assertNull(matchingPreset(1, 10, 0))
        assertNull(matchingPreset(0, 7, 0))
    }
}
