// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0
package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgnoreMatchTest {
    private fun rule(
        mask: String? = null, levels: List<String> = listOf("ALL"), isExcept: Boolean = false,
        networkId: Int? = null, channels: List<String>? = null, pattern: String? = null,
        patternKind: String = "substr",
    ) = IgnoreRule(1, networkId, mask, channels, pattern, patternKind, levels, isExcept)

    private fun eval(
        rules: List<IgnoreRule>, net: Int? = 2, channel: String? = "#chan", dm: Boolean = false,
        mask: String = "bob!bob@evil.example", type: String = "message", text: String = "hi",
    ) = IgnoreMatch.evaluate(rules, net, channel, dm, mask, type, text)

    @Test fun maskGlobs() {
        assertTrue(IgnoreMatch.maskMatches("bob", "bob!bob@evil.example"))          // bare nick -> nick!*@*
        assertTrue(IgnoreMatch.maskMatches("*!*@evil.example", "bob!bob@evil.example"))
        assertTrue(IgnoreMatch.maskMatches("bob!*@*", "bob!bob@evil.example"))
        assertFalse(IgnoreMatch.maskMatches("alice", "bob!bob@evil.example"))
        assertFalse(IgnoreMatch.maskMatches("*!*@good.example", "bob!bob@evil.example"))
    }

    @Test fun allDropsEverything() {
        assertTrue(eval(listOf(rule(mask = "bob"))).drop)
        assertTrue(eval(listOf(rule(mask = "bob")), type = "notice").drop)
        assertFalse(eval(listOf(rule(mask = "alice"))).drop) // different nick
    }

    @Test fun publicOnlyDropsChannelMessages() {
        val r = listOf(rule(mask = "bob", levels = listOf("PUBLIC")))
        assertTrue(eval(r).drop)                                   // channel message
        assertFalse(eval(r, dm = true, channel = null).drop)       // DM: not PUBLIC
        assertFalse(eval(r, type = "notice").drop)                 // notice: not PUBLIC
    }

    @Test fun noHighlightSoftensNotDrops() {
        val o = eval(listOf(rule(mask = "bob", levels = listOf("NOHIGHLIGHT"))))
        assertFalse(o.drop)
        assertTrue(o.suppressHighlight)
        assertFalse(o.suppressNotify)
    }

    @Test fun exceptWhitelists() {
        val rules = listOf(
            rule(mask = "*!*@evil.example"),                        // ignore all from evil
            rule(mask = "bob!*@*", isExcept = true),               // but not bob
        )
        assertFalse(eval(rules).drop)                              // bob excepted
        assertTrue(eval(rules, mask = "eve!eve@evil.example").drop) // eve still ignored
    }

    @Test fun channelAndNetworkScope() {
        assertFalse(eval(listOf(rule(mask = "bob", channels = listOf("#other")))).drop)
        assertTrue(eval(listOf(rule(mask = "bob", channels = listOf("#chan")))).drop)
        assertFalse(eval(listOf(rule(mask = "bob", networkId = 99))).drop) // wrong network
        assertTrue(eval(listOf(rule(mask = "bob", networkId = 2))).drop)
    }

    @Test fun textPattern() {
        assertTrue(eval(listOf(rule(mask = "bob", pattern = "spam")), text = "buy spam now").drop)
        assertFalse(eval(listOf(rule(mask = "bob", pattern = "spam")), text = "hello").drop)
    }

    @Test fun emptyRulesNoOp() {
        assertEquals(IgnoreOutcome(), eval(emptyList()))
    }
}
