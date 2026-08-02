// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reopen-flap fix: with a background anchor keeping the socket alive, coming
 * back to the foreground must REUSE the live socket, not force-cycle it (which
 * opened a duplicate connection the server closed, thrashing green<->Connecting).
 */
class ForegroundReconnectTest {

    @Test fun `background-connect keeps the live socket on reopen`() {
        // The freakyy85 repro: connected, long trip away (not a quick hop), anchor on.
        assertTrue(shouldReuseSocketOnForeground(connected = true, quickHop = false, backgroundConnect = true))
    }

    @Test fun `quick task-switch hop reuses the socket`() {
        assertTrue(shouldReuseSocketOnForeground(connected = true, quickHop = true, backgroundConnect = false))
    }

    @Test fun `long trip without an anchor cycles (possible frozen-process zombie)`() {
        assertFalse(shouldReuseSocketOnForeground(connected = true, quickHop = false, backgroundConnect = false))
    }

    @Test fun `a disconnected socket always cycles`() {
        assertFalse(shouldReuseSocketOnForeground(connected = false, quickHop = true, backgroundConnect = true))
        assertFalse(shouldReuseSocketOnForeground(connected = false, quickHop = false, backgroundConnect = true))
        assertFalse(shouldReuseSocketOnForeground(connected = false, quickHop = false, backgroundConnect = false))
    }
}
