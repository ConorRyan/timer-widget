package io.github.conorryan.timerwidget.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatHmsTest {

    @Test
    fun zero() {
        assertEquals("0:00", formatHms(0))
    }

    @Test
    fun secondsOnly() {
        assertEquals("0:05", formatHms(5))
    }

    @Test
    fun minutesAndSeconds() {
        assertEquals("1:05", formatHms(65))
    }

    @Test
    fun hoursMinutesSeconds() {
        assertEquals("1:01:01", formatHms(3661))
    }

    @Test
    fun padsMinutesAndSeconds() {
        assertEquals("10:00", formatHms(600))
    }
}
