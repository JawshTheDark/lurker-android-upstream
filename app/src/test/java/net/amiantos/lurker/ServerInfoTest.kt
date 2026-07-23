// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the ISUPPORT CHANMODES/PREFIX parser that drives network-aware modes. */
class ServerInfoTest {
    @Test
    fun parsesLiberaStyleChanmodesIntoFourGroups() {
        val si = ServerInfo.parse(
            software = "solanum-1.0-dev",
            network = "Libera.Chat",
            chanModes = "eIbq,k,flj,CFLMPQScgimnprstuz",
            prefix = "(ov)@+",
            chanTypes = "#",
        )
        assertEquals(setOf('e', 'I', 'b', 'q'), si.typeA)
        assertEquals(setOf('k'), si.typeB)
        assertEquals(setOf('f', 'l', 'j'), si.typeC)
        assertTrue('m' in si.typeD && 'i' in si.typeD && 'n' in si.typeD)
        // Flag chips are exactly the paramless (type-D) modes.
        assertEquals(si.typeD, si.flagModes)
    }

    @Test
    fun paramModesAreDetectedAcrossGroups() {
        val si = ServerInfo.parse("", "", "eIbq,k,flj,mnt", "(ov)@+", "#")
        assertTrue(si.takesParam('k')) // type B
        assertTrue(si.takesParam('l')) // type C
        assertTrue(si.takesParam('b')) // type A
        assertFalse(si.takesParam('m')) // type D flag — no param
    }

    @Test
    fun parsesPrefixLadderHighToLow() {
        val si = ServerInfo.parse("", "", "b,k,l,imnt", "(qaohv)~&@%+", "#")
        assertEquals(
            listOf('q' to '~', 'a' to '&', 'o' to '@', 'h' to '%', 'v' to '+'),
            si.prefixes,
        )
    }

    @Test
    fun simpleOpVoicePrefix() {
        val si = ServerInfo.parse("", "", "b,k,l,imnt", "(ov)@+", "#")
        assertEquals(listOf('o' to '@', 'v' to '+'), si.prefixes)
    }

    @Test
    fun toleratesEmptyAndMalformedIsupport() {
        val si = ServerInfo.parse("", "", "", "", "")
        assertTrue(si.typeA.isEmpty() && si.typeD.isEmpty())
        assertTrue(si.prefixes.isEmpty())
        assertEquals("#", si.chanTypes) // defaulted
        // A PREFIX missing its parenthesised mode list yields no ladder, not a crash.
        val bad = ServerInfo.parse("", "", "a,b,c,d", "@+", "#")
        assertTrue(bad.prefixes.isEmpty())
    }
}
