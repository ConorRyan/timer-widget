package io.github.conorryan.timerwidget.util

import java.text.BreakIterator

/**
 * First grapheme cluster of [input]: one user-perceived character. A pasted
 * multi-codepoint emoji (flags, ZWJ families, variation selectors) stays
 * whole; everything after the first character is dropped.
 */
fun firstGrapheme(input: String): String {
    val cleaned = input.trim()
    if (cleaned.isEmpty()) return ""
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(cleaned)
    val end = iterator.next()
    return if (end == BreakIterator.DONE) "" else cleaned.substring(0, end)
}
