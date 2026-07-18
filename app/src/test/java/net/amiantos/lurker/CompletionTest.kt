// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionTest {
    @Test
    fun wordAtFindsTheCursorWord() {
        val w = Completion.wordAt("hey ali there", 6) // inside "ali"
        assertEquals("ali", w.text)
        assertEquals(4, w.start)
    }

    @Test
    fun recentsRankAheadOfRoster() {
        val out = Completion.nicks(
            prefix = "a",
            recents = listOf("alice"),
            members = listOf("aaron", "alice", "abby"),
            self = null,
        )
        assertEquals(listOf("alice", "aaron", "abby"), out)
    }

    @Test
    fun selfExcludedAndPrefixMatched() {
        val out = Completion.nicks("bo", emptyList(), listOf("bob", "bobby", "carol"), "bob")
        assertEquals(listOf("bobby"), out)
    }

    @Test
    fun commandsPrefixMatch() {
        assertEquals(listOf("/join"), Completion.commands("/jo", Completion.VERBS))
    }
}
