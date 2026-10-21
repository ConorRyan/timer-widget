package io.github.conorryan.timerwidget.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FirstGraphemeTest {

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", firstGrapheme(""))
    }

    @Test
    fun whitespaceOnlyStaysEmpty() {
        assertEquals("", firstGrapheme("  \n\t"))
    }

    @Test
    fun singleEmojiPassesThrough() {
        assertEquals("⏰", firstGrapheme("⏰"))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("☕", firstGrapheme("  ☕ \n"))
    }

    @Test
    fun onlyTheFirstCharacterIsKept() {
        assertEquals("⏳", firstGrapheme("⏳🍜"))
    }

    @Test
    fun flagIsOneCharacter() {
        assertEquals("🇺🇸", firstGrapheme("🇺🇸🇫🇷"))
    }

    @Test
    fun zwjFamilySequenceIsOneCharacter() {
        val family = "\uD83D\uDC68\uD83C\uDFFD‍\uD83D\uDC69\uD83C\uDFFD‍\uD83D\uDC67\uD83C\uDFFB"
        assertEquals(family, firstGrapheme("$family⏰"))
    }

    @Test
    fun variationSelectorStaysAttached() {
        assertEquals("⏱️", firstGrapheme("⏱️⏳"))
    }

    @Test
    fun textIsAlsoLimitedToFirstCharacter() {
        assertEquals("a", firstGrapheme("abc"))
    }
}
