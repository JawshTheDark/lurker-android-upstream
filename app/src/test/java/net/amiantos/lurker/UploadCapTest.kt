// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server advertises its effective upload ceiling (lurker#627 / PR #648) so an
 * oversized file is refused before it streams up over cellular. The value is
 * advisory and only sometimes present, so the check has to stay silent whenever it
 * can't be sure — a false refusal is worse than a 413.
 */
class UploadCapTest {
    private val cap = 100_000_000L // ~100 MB, a typical Cloudflare-fronted ceiling

    @Test
    fun `file under the cap is allowed`() {
        assertNull(uploadTooLarge(50_000_000L, cap))
    }

    @Test
    fun `file exactly at the cap is allowed`() {
        // The server already subtracted its multipart headroom, so the advertised
        // number is spendable in full.
        assertNull(uploadTooLarge(cap, cap))
    }

    @Test
    fun `file over the cap is refused`() {
        assertNotNull(uploadTooLarge(cap + 1, cap))
    }

    @Test
    fun `older server advertising no cap never refuses`() {
        assertNull(uploadTooLarge(900_000_000L, 0L))
    }

    @Test
    fun `unknown file size never refuses`() {
        // The content provider didn't report SIZE; let the 413 arbitrate.
        assertNull(uploadTooLarge(-1L, cap))
        assertNull(uploadTooLarge(0L, cap))
    }

    @Test
    fun `message names both sizes in decimal MB`() {
        // Decimal MB (10^6) matches the server's own 413 text, so the two can't
        // contradict each other in front of the user.
        val msg = uploadTooLarge(210_000_000L, cap)!!
        assertTrue(msg, msg.contains("210 MB"))
        assertTrue(msg, msg.contains("100 MB"))
    }

    @Test
    fun `message keeps one decimal place when it is meaningful`() {
        val msg = uploadTooLarge(150_500_000L, cap)!!
        assertTrue(msg, msg.contains("150.5 MB"))
    }
}
