// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    @Test
    fun peelsStructuredFilters() {
        val q = Search.parse("from:alice in:#chan on:libera hello world")
        assertEquals(listOf("alice"), q.from)
        assertEquals("#chan", q.inTarget)
        assertEquals("libera", q.onNetwork)
        assertEquals("hello world", q.text)
    }

    @Test
    fun fromRepeatsIntoList() {
        val q = Search.parse("from:a from:b needle")
        assertEquals(listOf("a", "b"), q.from)
        assertEquals("needle", q.text)
    }

    @Test
    fun bareOrUnknownTokenStaysInText() {
        val q = Search.parse("from: what:ever plain")
        assertEquals(emptyList<String>(), q.from)
        assertEquals("from: what:ever plain", q.text)
    }
}
