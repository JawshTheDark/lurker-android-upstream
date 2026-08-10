// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rotating the phone recreates the Activity, and the nav state used to be plain
 * `remember` — so you lost the channel you were reading and landed back on the
 * buffer list (d3fc0n). Screens are now saved, which means this codec is what
 * stands between a rotate and losing your place. Round-trip it.
 */
class ScreenSaverTest {

    private fun roundTrip(s: Screen): Screen = decodeScreen(encodeScreen(s))

    @Test fun `a chat survives with its buffer intact`() {
        val s = Screen.Chat(Buffer(2, "#lurker-spooky", "Libera"))
        val out = roundTrip(s) as Screen.Chat
        assertEquals(2, out.buffer.networkId)
        assertEquals("#lurker-spooky", out.buffer.target)
        assertEquals("Libera", out.buffer.networkName)
        // Same key = the restored screen addresses the SAME buffer state.
        assertEquals(s.buffer.key, out.buffer.key)
        assertNull(out.scrollToMsgId)
    }

    @Test fun `a notification jump target survives`() {
        val out = roundTrip(Screen.Chat(Buffer(7, "amiantos", "Libera"), scrollToMsgId = 3219876L)) as Screen.Chat
        assertEquals(3219876L, out.scrollToMsgId)
    }

    @Test fun `the system buffer's null networkId survives`() {
        // Encoded as an empty field — it must come back null, not 0, or the key
        // becomes "0::…" instead of "sys::…" and matches nothing.
        val out = roundTrip(Screen.Chat(Buffer(null, ":system:", "system"))) as Screen.Chat
        assertNull(out.buffer.networkId)
        assertEquals("sys::" + ":system:", out.buffer.key)
    }

    @Test fun `every objectless screen round-trips to itself`() {
        listOf(
            Screen.Buffers, Screen.Settings, Screen.Dcc, Screen.Networks,
            Screen.Search, Screen.Friends, Screen.Ignores, Screen.Multichan,
        ).forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test fun `channel list keeps or drops its query`() {
        assertEquals(Screen.ChannelList("spooky"), roundTrip(Screen.ChannelList("spooky")))
        assertEquals(Screen.ChannelList(null), roundTrip(Screen.ChannelList(null)))
    }

    @Test fun `network edit lands on the network list rather than a blank editor`() {
        assertEquals(Screen.Networks, roundTrip(Screen.NetworkEdit(null)))
    }

    @Test fun `garbage decodes to the buffer list instead of throwing`() {
        // A saved Bundle can outlive an app update that renamed a screen.
        assertEquals(Screen.Buffers, decodeScreen(""))
        assertEquals(Screen.Buffers, decodeScreen("something-we-removed"))
        assertEquals(Screen.Buffers, decodeScreen("chat")) // truncated chat payload
    }

    @Test fun `separator cannot collide with a real target`() {
        // Channel names, nicks and network names are printable; the separator is a
        // control character, so no target can split its own encoding.
        val s = Screen.Chat(Buffer(1, "#a-b_c|d[e]", "Some Network"))
        val out = roundTrip(s) as Screen.Chat
        assertEquals("#a-b_c|d[e]", out.buffer.target)
        assertTrue(SCREEN_SEP.code < 32)
    }
}
