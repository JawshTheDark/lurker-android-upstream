// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A persisted server-voice row (`error`, `motd`) carries `"nick": null`. On
 * Android's org.json, `optString("nick", "*")` stringifies that null to the
 * literal "null" — the fallback only fires for an ABSENT key — which is how
 * "gnat isn't on this network." came to render under a sender called "null"
 * (stephanos, #lurker). The JVM test JSON (json.org) happens to be forgiving
 * here, so these pin the helper's contract rather than the library's quirk.
 */
class EventNickTest {
    @Test
    fun `a real nick passes through`() {
        assertEquals("stephanos", eventNick(JSONObject("""{"nick":"stephanos"}""")))
    }

    @Test
    fun `an explicit null nick is the server marker, never the string null`() {
        assertEquals("*", eventNick(JSONObject("""{"type":"error","nick":null,"text":"gnat isn't on this network."}""")))
    }

    @Test
    fun `an absent nick is the server marker too`() {
        assertEquals("*", eventNick(JSONObject("""{"type":"error","text":"x"}""")))
    }
}
