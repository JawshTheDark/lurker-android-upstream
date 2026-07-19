package net.amiantos.lurker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTest {
    // notify, active, self, system, hasId, foreground
    private fun bg(notify: Boolean = true, active: Boolean = false, self: Boolean = false, system: Boolean = false, hasId: Boolean = true) =
        shouldNotify(notify, active, self, system, hasId, foreground = false)

    @Test fun highlightWhileBackgrounded() = assertTrue(bg())

    @Test fun notForeground() =
        assertFalse(shouldNotify(notify = true, isActiveBuffer = false, self = false, system = false, hasId = true, foreground = true))

    @Test fun notWhenNotFlagged() = assertFalse(bg(notify = false))
    @Test fun notForOwnMessage() = assertFalse(bg(self = true))
    @Test fun notForSystemEvent() = assertFalse(bg(system = true))
    @Test fun notForOptimisticNoId() = assertFalse(bg(hasId = false))
    @Test fun notWhenBufferIsOpen() = assertFalse(bg(active = true))
}
