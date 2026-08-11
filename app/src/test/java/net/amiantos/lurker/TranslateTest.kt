// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every rule here was a real bug or complaint on Scully's first day in the field.
 * They're cheap to re-earn by accident, so they're pinned.
 */
class TranslateTest {

    // ---- Noise must never leave the device --------------------------------

    @Test fun `interjections are skipped`() {
        listOf("lol", "LOL", "rofl", "haha", "hmm", "brb", "wtf", "omg", "meh", "oof")
            .forEach { assertTrue(it, isSkippable(it)) }
    }

    @Test fun `stretched interjections collapse and are skipped`() {
        // "it just doesn't send" — waiting on a translator to render "loooool".
        listOf("loooool", "hmmmmm", "haaaha", "wooooo").forEach { assertTrue(it, isSkippable(it)) }
    }

    @Test fun `near-textless messages are skipped`() {
        listOf(":)", "?!", "+1", "", "   ", "👍", "o/").forEach { assertTrue("[$it]", isSkippable(it)) }
    }

    @Test fun `real sentences are not skipped`() {
        listOf(
            "cual es tu pitufo favorito?",
            "wie geht es dir",
            "that is a genuinely long message",
        ).forEach { assertFalse(it, isSkippable(it)) }
    }

    @Test fun `collapseRuns squashes only repeats`() {
        assertEquals("lol", collapseRuns("loooool"))
        assertEquals("hm", collapseRuns("hmmmm"))
        assertEquals("abc", collapseRuns("abc"))
    }

    // ---- Only badge rows that actually changed ----------------------------

    @Test fun `an identical answer is not a translation`() {
        // Already in the target language — badging it would claim work we didn't do.
        assertFalse(isMeaningful("hello there", "hello there"))
        assertFalse(isMeaningful("hello there", "  hello there  "))
        assertFalse(isMeaningful("x", ""))
    }

    @Test fun `a changed answer is a translation`() {
        assertTrue(isMeaningful("guten tag", "good afternoon"))
    }

    // ---- OpenAI endpoint shapes -------------------------------------------

    @Test fun `v1 is appended only when missing`() {
        assertEquals(
            "http://localhost:11434/v1/chat/completions",
            chatCompletionsUrl("http://localhost:11434"),
        )
        assertEquals(
            "http://localhost:11434/v1/chat/completions",
            chatCompletionsUrl("http://localhost:11434/v1"),
        )
        // People paste trailing slashes; both forms must land on one URL.
        assertEquals(
            "http://localhost:11434/v1/chat/completions",
            chatCompletionsUrl("http://localhost:11434/v1/"),
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            chatCompletionsUrl("https://api.openai.com/"),
        )
    }

    // ---- The system prompt IS the product ---------------------------------

    @Test fun `system prompt names the target and forbids commentary`() {
        val p = systemPrompt("German")
        assertTrue(p.contains("German"))
        // A model that "helpfully" answers the message is worse than no translation.
        assertTrue(p.contains("ONLY the translation"))
        assertTrue(p.contains("unchanged"))
    }

    // ---- Confidence gate ---------------------------------------------------

    @Test fun `the confidence gate sits where misdetection was observed`() {
        // Short informal Spanish scored 57 as Italian against the live instance and
        // the wrong-source translation was neither language; clean speech scores 85+.
        assertTrue(CONFIDENCE_GATE > 57.0)
        assertTrue(CONFIDENCE_GATE < 85.0)
    }

    // ---- Language pickers --------------------------------------------------

    @Test fun `language list is usable as a picker`() {
        assertTrue(TRANSLATE_LANGUAGES.size >= 40)
        assertEquals("English", languageName("en"))
        assertEquals("Japanese", languageName("ja"))
        // Unknown codes degrade to the code rather than blanking the button.
        assertEquals("xx", languageName("xx"))
        // No duplicate codes, or the picker shows the same entry twice.
        assertEquals(TRANSLATE_LANGUAGES.size, TRANSLATE_LANGUAGES.map { it.first }.toSet().size)
    }
}
