// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A connect burst is the server's statement of what's open, so a buffer it never
 * mentions was closed elsewhere while we were away (lurker-ios#78). The dangerous
 * direction is over-pruning — wrongly dropping a live buffer — so the guards
 * matter more than the happy path.
 */
class BufferReconcileTest {
    private val a = "1::#alpha"
    private val b = "1::#beta"
    private val c = "2::someone"

    @Test
    fun `a buffer the burst skipped was closed elsewhere`() {
        assertEquals(setOf(b), staleBufferKeys(baseline = setOf(a, b), seen = setOf(a)))
    }

    @Test
    fun `nothing is stale when the burst enumerated everything`() {
        assertEquals(emptySet<String>(), staleBufferKeys(setOf(a, b, c), setOf(a, b, c)))
    }

    @Test
    fun `an empty burst prunes nothing`() {
        // The whole buffer list would go otherwise. A burst that enumerated nothing
        // is one the server abandoned, not proof that everything closed.
        assertEquals(emptySet<String>(), staleBufferKeys(setOf(a, b, c), emptySet()))
    }

    @Test
    fun `a buffer opened during the burst is not stale`() {
        // It isn't in the baseline (captured when the burst began), so it can't be
        // pruned no matter what the burst enumerated.
        assertEquals(emptySet<String>(), staleBufferKeys(baseline = emptySet(), seen = setOf(a)))
    }

    @Test
    fun `buffers the burst added beyond the baseline are ignored`() {
        assertEquals(emptySet<String>(), staleBufferKeys(baseline = setOf(a), seen = setOf(a, b, c)))
    }

    @Test
    fun `a first connect with nothing held prunes nothing`() {
        assertEquals(emptySet<String>(), staleBufferKeys(emptySet(), setOf(a, b)))
    }
}
