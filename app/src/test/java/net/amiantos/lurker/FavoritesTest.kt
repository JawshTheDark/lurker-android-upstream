// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lurker 2.0's favorites replaced contacts. ONE server list drives two sections,
 * split purely on whether the target is a channel — so the split rule and the
 * key format are what the buffer list depends on.
 */
class FavoritesTest {

    @Test fun `all four channel sigils count as channels, not friends`() {
        // A favorited "+modeless" filed under Friends would render with a presence
        // dot and try to probe a nick that doesn't exist (the bug lurker-ios#97 fixed).
        assertTrue(FavoriteEntry(1, "#lurker", 1).isChannel)
        assertTrue(FavoriteEntry(1, "&local", 2).isChannel)
        assertTrue(FavoriteEntry(1, "+modeless", 3).isChannel)
        assertTrue(FavoriteEntry(1, "!12345safe", 4).isChannel)
    }

    @Test fun `a plain nick is a friend`() {
        assertFalse(FavoriteEntry(1, "amiantos", 5).isChannel)
        assertFalse(FavoriteEntry(1, "d3fc0n", 6).isChannel)
    }

    @Test fun `favorite key matches the buffer key format exactly`() {
        // buildBufferSections pairs favorites to open buffers by this string; if the
        // two formats ever drift, every favorite silently synthesizes a duplicate
        // row instead of reusing the real buffer (and its unread badge).
        val fav = FavoriteEntry(2, "#lurker", 9)
        assertEquals("2::#lurker", fav.key)
        assertEquals(Buffer(2, "#lurker", "Libera").key, fav.key)
    }

    @Test fun `dm favorite key matches its buffer too`() {
        val fav = FavoriteEntry(7, "freakyy85", 11)
        assertEquals(Buffer(7, "freakyy85", "Libera").key, fav.key)
    }
}
