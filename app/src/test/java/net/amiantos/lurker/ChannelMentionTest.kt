// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Channel mentions in message text are tappable to offer a join (freakyy85). The
 * detector has to stay off URL fragments and issue references, or it turns ordinary
 * chat into a minefield of bogus join prompts.
 */
class ChannelMentionTest {
    private fun names(text: String): List<String> {
        val urls = Mirc.findUrls(text)
        return findChannelMentions(text, urls).map { text.substring(it) }
    }

    @Test
    fun `finds a plain channel`() {
        assertEquals(listOf("#lurker"), names("come join #lurker sometime"))
    }

    @Test
    fun `finds several, including an ampersand channel`() {
        assertEquals(listOf("#one", "&local"), names("try #one or &local"))
    }

    @Test
    fun `finds a channel with punctuation in the name`() {
        assertEquals(listOf("##chat"), names("we're in ##chat"))
        assertEquals(listOf("#lurker-spooky"), names("#lurker-spooky is the dev channel"))
    }

    @Test
    fun `trims trailing sentence punctuation`() {
        assertEquals(listOf("#lurker"), names("it's in #lurker."))
        assertEquals(listOf("#lurker"), names("really, #lurker!"))
        assertEquals(listOf("#lurker"), names("(see #lurker)"))
    }

    @Test
    fun `ignores a url fragment`() {
        // The '#section' here belongs to the URL — offering it as a channel would
        // also collide with the URL's own link annotation.
        assertEquals(emptyList<String>(), names("https://example.com/docs#section"))
    }

    @Test
    fun `finds a channel alongside a url`() {
        assertEquals(
            listOf("#lurker"),
            names("docs at https://example.com/a#b and chat in #lurker"),
        )
    }

    @Test
    fun `ignores issue-style references`() {
        assertEquals(emptyList<String>(), names("fixed in #92"))
        assertEquals(emptyList<String>(), names("see #1 and #2"))
    }

    @Test
    fun `ignores a bare hash`() {
        assertEquals(emptyList<String>(), names("what # even is that"))
        assertEquals(emptyList<String>(), names("#"))
    }

    @Test
    fun `keeps a channel that mixes digits and letters`() {
        assertEquals(listOf("#4chan"), names("browsing #4chan"))
    }
}
