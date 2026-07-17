// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies mIRC control-code parsing and URL detection stay pure and correct. */
class MircTest {
    private val bold = "\u0002"
    private val color = "\u0003"
    private val reset = "\u000F"

    @Test
    fun plainTextIsOneSpan() {
        val spans = Mirc.parse("just text")
        assertEquals(1, spans.size)
        assertEquals("just text", spans[0].text)
        assertEquals(false, spans[0].bold)
    }

    @Test
    fun boldTogglesAndStripsControlBytes() {
        val spans = Mirc.parse("${bold}loud${bold} quiet")
        assertEquals("loud quiet", spans.joinToString("") { it.text })
        assertEquals(true, spans.first { it.text == "loud" }.bold)
        assertEquals(false, spans.first { it.text.contains("quiet") }.bold)
    }

    @Test
    fun colorCodeSetsForeground() {
        // 04 == red in the classic palette.
        val spans = Mirc.parse("${color}04red")
        assertEquals("red", spans.last().text)
        assertEquals(0xFFFF0000.toInt(), spans.last().fg)
    }

    @Test
    fun colorWithBackground() {
        val spans = Mirc.parse("${color}00,01x")
        assertEquals(0xFFFFFFFF.toInt(), spans.last().fg) // 00 white
        assertEquals(0xFF000000.toInt(), spans.last().bg) // 01 black
    }

    @Test
    fun bareColorCodeClearsColor() {
        val spans = Mirc.parse("${color}04red${color}plain")
        assertEquals(0xFFFF0000.toInt(), spans.first { it.text == "red" }.fg)
        assertNull(spans.first { it.text == "plain" }.fg)
    }

    @Test
    fun resetClearsEverything() {
        val spans = Mirc.parse("${bold}${color}04hot${reset}cold")
        val cold = spans.first { it.text == "cold" }
        assertEquals(false, cold.bold)
        assertNull(cold.fg)
    }

    @Test
    fun stripRemovesAllCodes() {
        assertEquals("hello", Mirc.strip("${bold}${color}04hel${color}lo${reset}"))
    }

    @Test
    fun findsAPlainUrl() {
        val text = "see https://example.com/page now"
        val ranges = Mirc.findUrls(text)
        assertEquals(1, ranges.size)
        assertEquals("https://example.com/page", text.substring(ranges[0]))
    }

    @Test
    fun trimsTrailingSentencePunctuation() {
        val text = "go to https://example.com."
        assertEquals("https://example.com", text.substring(Mirc.findUrls(text)[0]))
    }

    @Test
    fun ignoresNonHttpText() {
        assertEquals(0, Mirc.findUrls("no links here, ftp://nope").size)
    }
}
