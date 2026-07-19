// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MessageOrderTest {
    private fun m(id: Long, text: String = "t") = Msg(id = id, type = "message", nick = "n", text = text, self = false)

    @Test fun inOrderIsUntouched() {
        val list = listOf(m(1), m(2), m(3))
        // Fast path returns the same instance (no allocation) when already sorted.
        assertSame(list, orderMessagesById(list))
    }

    @Test fun outOfOrderIsSortedById() {
        val out = orderMessagesById(listOf(m(3), m(1), m(2))).map { it.id }
        assertEquals(listOf(1L, 2L, 3L), out)
    }

    @Test fun theRacingSendCase() {
        // An MCP post (id 101) delivered before the app's own send (id 100).
        val out = orderMessagesById(listOf(m(99, "a"), m(101, "mcp"), m(100, "app"))).map { it.text }
        assertEquals(listOf("a", "app", "mcp"), out)
    }

    @Test fun clientRowsStayAnchoredAfterTheirRealMessage() {
        // id<=0 rows (a local notice, an e2e status) must NOT jump to the end;
        // they stay right after the real message they were injected below.
        val notice = m(0, "notice")
        val list = listOf(m(10, "a"), notice, m(9, "b")) // 9 arrived out of order
        val out = orderMessagesById(list).map { it.text }
        assertEquals(listOf("b", "a", "notice"), out)
    }
}
