// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Relay-highlight notification retargeting: prefer the favourited (pinned) copy. */
class RelayNotifyTest {
    // net 1 = home (pinned #chan), net 2 = relay mirror (not pinned).
    private val pins = mapOf(1 to listOf("#chan", "#other"), 2 to listOf("#unrelated"))

    @Test
    fun retargetsToPinnedHomeWhenHighlightHitsRelayMirror() {
        // Highlight arrives on net 2's #chan (unpinned) → prefer net 1 (pinned).
        assertEquals(1, relayNotifyNetwork(2, "#chan", isChannel = true, pins))
    }

    @Test
    fun caseInsensitiveChannelMatch() {
        assertEquals(1, relayNotifyNetwork(2, "#CHAN", isChannel = true, pins))
    }

    @Test
    fun noRetargetWhenAlreadyPinnedOnThisNetwork() {
        assertNull(relayNotifyNetwork(1, "#chan", isChannel = true, pins))
    }

    @Test
    fun noRetargetWhenNoOtherNetworkPinnedIt() {
        assertNull(relayNotifyNetwork(2, "#solo", isChannel = true, pins))
    }

    @Test
    fun neverRetargetsDms() {
        assertNull(relayNotifyNetwork(2, "someone", isChannel = false, pins))
    }
}
