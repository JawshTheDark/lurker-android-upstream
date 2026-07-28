// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `chat.events` tiers decide how much presence churn reaches the timeline
 * (lurker#672). The risk is hiding something that mattered, so the cases that
 * must always survive get the most attention here.
 */
class EventFilterTest {
    private val spoke = setOf("alice")

    private fun shows(type: String, nick: String, tier: String, self: Boolean = false) =
        passesEventFilter(type, nick, self, tier, spoke)

    @Test
    fun `all shows everything`() {
        assertTrue(shows("join", "bob", "all"))
        assertTrue(shows("quit", "bob", "all"))
    }

    @Test
    fun `none hides presence churn`() {
        assertFalse(shows("join", "bob", "none"))
        assertFalse(shows("part", "bob", "none"))
        assertFalse(shows("quit", "bob", "none"))
        assertFalse(shows("nick", "bob", "none"))
        assertFalse(shows("chghost", "bob", "none"))
    }

    @Test
    fun `smart keeps churn from people who have spoken`() {
        assertTrue(shows("quit", "alice", "smart"))
        assertFalse(shows("quit", "bob", "smart"))
    }

    @Test
    fun `smart matches speakers case-insensitively`() {
        assertTrue(shows("part", "Alice", "smart"))
        assertTrue(shows("part", "ALICE", "smart"))
    }

    @Test
    fun `your own events are never hidden`() {
        // Seeing your own join is how you know the channel worked.
        assertTrue(shows("join", "me", "none", self = true))
        assertTrue(shows("part", "me", "smart", self = true))
    }

    @Test
    fun `events that are not noise always show`() {
        // Something happened TO the channel — hiding it loses real information.
        for (tier in listOf("all", "smart", "none")) {
            assertTrue(tier, shows("kick", "bob", tier))
            assertTrue(tier, shows("topic", "bob", tier))
            assertTrue(tier, shows("mode", "bob", tier))
            assertTrue(tier, shows("invite", "bob", tier))
            assertTrue(tier, shows("message", "bob", tier))
        }
    }

    @Test
    fun `an unknown tier from a newer server shows everything`() {
        // Fail open: better a noisy timeline than silently swallowed events.
        assertTrue(shows("join", "bob", "some-future-tier"))
    }
}
