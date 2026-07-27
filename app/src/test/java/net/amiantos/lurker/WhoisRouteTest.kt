// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `whois_result` is broadcast to every client on the account with no sender
 * attribution, so replies are matched against the requests this client made —
 * otherwise a whois run in Lurker web printed into whatever buffer Spooky had
 * open, even on a different network (freakyy85).
 */
class WhoisRouteTest {
    @Test
    fun `plain whois yields the nick`() {
        assertEquals("someone", whoisTargetNick("WHOIS someone"))
    }

    @Test
    fun `server-targeted whois yields the nick, not the server`() {
        // WHOIS <server> <nick> — the nick is last in both forms.
        assertEquals("someone", whoisTargetNick("WHOIS irc.example.net someone"))
    }

    @Test
    fun `matching is case-insensitive on both command and nick`() {
        assertEquals("someone", whoisTargetNick("whois SomeOne"))
        assertEquals("someone", whoisTargetNick("WhoIs SOMEONE"))
    }

    @Test
    fun `surrounding and inner whitespace is tolerated`() {
        assertEquals("someone", whoisTargetNick("   WHOIS    someone   "))
    }

    @Test
    fun `non-whois raw lines are ignored`() {
        assertNull(whoisTargetNick("PRIVMSG #chan :WHOIS someone"))
        assertNull(whoisTargetNick("WHOWAS someone"))
        assertNull(whoisTargetNick("MODE #chan +o someone"))
    }

    @Test
    fun `a bare WHOIS with no argument is not a request`() {
        assertNull(whoisTargetNick("WHOIS"))
        assertNull(whoisTargetNick("  WHOIS  "))
    }

    @Test
    fun `null and empty are safe`() {
        assertNull(whoisTargetNick(null))
        assertNull(whoisTargetNick(""))
    }
}
