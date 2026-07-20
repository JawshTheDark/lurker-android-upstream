// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0
package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SojuBouncerTest {
    @Test fun parsesNetworkAttributes() {
        val n = SojuBouncer.parseNetworkLine(
            listOf("NETWORK", "42", "name=Libera;host=irc.libera.chat;state=connected"),
        )!!
        assertEquals("42", n.id)
        assertEquals("Libera", n.name)
        assertEquals("irc.libera.chat", n.host)
        assertEquals("connected", n.state)
        assertTrue(!n.removed)
    }

    @Test fun removedNetwork() {
        val n = SojuBouncer.parseNetworkLine(listOf("NETWORK", "7", "*"))!!
        assertTrue(n.removed)
        assertEquals("7", n.id)
    }

    @Test fun nameFallsBackToHostThenId() {
        assertEquals("irc.example", SojuBouncer.parseNetworkLine(listOf("NETWORK", "1", "host=irc.example"))!!.name)
        assertEquals("9", SojuBouncer.parseNetworkLine(listOf("NETWORK", "9", "state=connecting"))!!.name)
    }

    @Test fun rejectsNonNetwork() {
        assertNull(SojuBouncer.parseNetworkLine(listOf("LISTNETWORKS")))
        assertNull(SojuBouncer.parseNetworkLine(listOf("NETWORK", "1"))) // no attrs
    }

    @Test fun unescapesSojuEscapes() {
        val attrs = SojuBouncer.parseAttrs("name=My\\sNet;note=a\\:b;path=c\\\\d")
        assertEquals("My Net", attrs["name"])   // \s -> space
        assertEquals("a;b", attrs["note"])       // \: -> ;
        assertEquals("c\\d", attrs["path"])      // \\ -> \
    }

    @Test fun semicolonInsideEscapeNotASeparator() {
        // "a\:b" is ONE value "a;b", not two pairs.
        val attrs = SojuBouncer.parseAttrs("x=a\\:b;y=2")
        assertEquals(2, attrs.size)
        assertEquals("a;b", attrs["x"])
        assertEquals("2", attrs["y"])
    }
}
