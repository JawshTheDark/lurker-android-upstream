// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/**
 * Instance feature gating (`/api/config` → `features`, plus each registry
 * setting's `requiresFeature`).
 *
 * The server does NOT filter feature-gated settings out of the bootstrap payload,
 * so hiding them is the client's job. Get it wrong and the settings screen shows
 * authentic-looking toggles for endpoints that were never mounted.
 */
class FeatureGateTest {

    /** Mirrors LurkerClient.settingAvailable against a supplied flag map. */
    private fun available(option: SettingOption, flags: Map<String, Boolean>): Boolean =
        option.requiresFeature?.let { flags[it] == true } ?: true

    private fun gated(feature: String?) =
        SettingOption(key = "chat.link_previews.enabled", label = "x", type = "bool", group = "chat", requiresFeature = feature)

    @Test fun `an ungated setting is always available`() {
        assertTrue(available(gated(null), emptyMap()))
        assertTrue(available(gated(null), mapOf("linkPreviews" to false)))
    }

    @Test fun `a gated setting needs its feature ON`() {
        assertTrue(available(gated("linkPreviews"), mapOf("linkPreviews" to true)))
        assertFalse(available(gated("linkPreviews"), mapOf("linkPreviews" to false)))
    }

    @Test fun `a MISSING flag means off, never on`() {
        // The load-bearing rule: an older server that doesn't advertise a feature
        // doesn't have it, and a failed /api/config fetch leaves an empty map — so
        // neither may conjure a feature the instance lacks.
        assertFalse(available(gated("linkPreviews"), emptyMap()))
        assertFalse(available(gated("linkPreviews"), mapOf("voice" to true)))
    }

    // ---- /api/config parsing ------------------------------------------------

    /** Mirrors the parse in fetchServerConfig. */
    private fun parseFeatures(body: String): Map<String, Boolean> {
        val feats = JSONObject(body).optJSONObject("features") ?: return emptyMap()
        return feats.keys().asSequence().associateWith { feats.optBoolean(it, false) }
    }

    @Test fun `parses the live 2_1_2 payload`() {
        val flags = parseFeatures(
            """{"edition":"standalone","protocolVersion":1,"minProtocolVersion":1,
               "features":{"linkPreviews":true}}""",
        )
        assertEquals(true, flags["linkPreviews"])
        // Voice is no longer advertised at all — absent must read as off.
        assertNull(flags["voice"])
        assertFalse(flags["voice"] == true)
    }

    @Test fun `a payload with no features block yields no flags`() {
        assertTrue(parseFeatures("""{"edition":"standalone"}""").isEmpty())
    }

    @Test fun `unknown future features round-trip without breaking`() {
        // Additive protocol: a new flag must not disturb the ones we know.
        val flags = parseFeatures("""{"features":{"linkPreviews":true,"somethingNew":true}}""")
        assertEquals(true, flags["linkPreviews"])
        assertEquals(true, flags["somethingNew"])
    }

    @Test fun `requiresFeature survives the registry parse shape`() {
        // Absent requiresFeature must be null, not "" — "" would gate on a feature
        // named empty-string and hide the setting forever.
        assertNull(gated(null).requiresFeature)
        assertEquals("linkPreviews", gated("linkPreviews").requiresFeature)
    }
}
