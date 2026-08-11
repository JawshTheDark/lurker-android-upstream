// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The descriptor, the byte-URL rule, the re-ask ladder and the selection rule.
 * Upstream's migration doc names each of these as a bug that shipped on iOS
 * before it was written down; these assertions are the difference between having
 * read that and having implemented it.
 */
class ServerPreviewTest {

    // ---- descriptor ---------------------------------------------------------

    @Test fun `decodes a full descriptor`() {
        val p = parseServerPreview(
            JSONObject(
                """{"url":"https://youtube.com/watch?v=x","status":"ok","kind":"video-embed",
                    "title":"T","description":"D","siteName":"YouTube","author":"A",
                    "thumb":"/api/link-preview/media/tok","thumbWidth":1200,"thumbHeight":630,
                    "embedUrl":"https://www.youtube-nocookie.com/embed/x","mime":"text/html",
                    "expiresAt":"2026-08-18T12:00:00.000Z"}""",
            ),
        )!!
        assertEquals("video-embed", p.kind)
        assertTrue(p.ok)
        assertEquals(1200, p.thumbWidth)
        assertEquals("https://www.youtube-nocookie.com/embed/x", p.embedUrl)
    }

    @Test fun `decodes leniently — unknown fields and missing optionals`() {
        // Additive-only protocol: a strict decoder breaks on a server upgrade.
        val p = parseServerPreview(
            JSONObject("""{"url":"https://x/","status":"ok","kind":"page","somethingNew":42}"""),
        )!!
        assertEquals("https://x/", p.url)
        assertNull(p.title)
        assertNull(p.thumbWidth)
    }

    @Test fun `a descriptor without a url is dropped`() {
        assertNull(parseServerPreview(JSONObject("""{"status":"ok","kind":"page"}""")))
    }

    @Test fun `unavailable is an answer, and is never allowed to render`() {
        val p = parseServerPreview(JSONObject("""{"url":"https://x/","status":"unavailable","kind":"page"}"""))!!
        assertFalse(p.ok)
        assertFalse(p.isAllowed(inlineMedia = true, linkPreviews = true))
    }

    // ---- the two settings select different kinds ----------------------------

    @Test fun `files answer to inlineMedia, pages to linkPreviews`() {
        fun of(kind: String) = ServerLinkPreview("u", "ok", kind)
        for (k in listOf("image", "video", "audio")) {
            assertTrue(k, of(k).isAllowed(inlineMedia = true, linkPreviews = false))
            assertFalse(k, of(k).isAllowed(inlineMedia = false, linkPreviews = true))
        }
        for (k in listOf("page", "video-embed")) {
            assertTrue(k, of(k).isAllowed(inlineMedia = false, linkPreviews = true))
            assertFalse(k, of(k).isAllowed(inlineMedia = true, linkPreviews = false))
        }
    }

    // ---- byte URLs are opaque -----------------------------------------------

    @Test fun `a relative path resolves against the instance and may carry the token`() {
        val t = previewMediaTarget("/api/link-preview/media/tok", "https://chat.example")
        assertEquals(MediaTarget.OwnServer("https://chat.example/api/link-preview/media/tok"), t)
    }

    @Test fun `an absolute CDN url is NOT concatenated and gets NO token`() {
        // iOS concatenated, produced https://chat.examplehttps://cdn…, and latched
        // every preview blank for the session.
        val t = previewMediaTarget("https://cdn.lurker.chat/abc", "https://chat.example")
        assertEquals(MediaTarget.ThirdParty("https://cdn.lurker.chat/abc"), t)
        assertTrue(t is MediaTarget.ThirdParty) // the branch that must not send auth
    }

    @Test fun `a non-root-relative path is refused rather than moving the host`() {
        // baseUrl + "api/media/x" = https://chat.exampleapi/media/x — registrable
        // by someone else, and this is the branch that attaches the token.
        assertNull(previewMediaTarget("api/link-preview/media/tok", "https://chat.example"))
        assertNull(previewMediaTarget("", "https://chat.example"))
    }

    @Test fun `a trailing slash on the base does not double up`() {
        assertEquals(
            MediaTarget.OwnServer("https://chat.example/api/m/t"),
            previewMediaTarget("/api/m/t", "https://chat.example/"),
        )
    }

    // ---- the re-ask ladder ---------------------------------------------------

    @Test fun `a long expiry is a verdict, not an invitation`() {
        // 7-day success and 1-hour failure TTLs both mean "don't come back".
        assertNull(reaskDelaySeconds(7 * 24 * 3600.0, 1, 0.5))
        assertNull(reaskDelaySeconds(3600.0, 1, 0.5))
        assertNull(reaskDelaySeconds(61.0, 1, 0.5))
    }

    @Test fun `a short expiry invites a retry`() {
        assertNotNull(reaskDelaySeconds(15.0, 1, 0.5))
    }

    @Test fun `no stated expiry is a verdict — this is the poller trap`() {
        // Absent / unparseable / already past must NOT become "zero seconds left",
        // which would sail through the short-TTL test and arm a poller nothing
        // clears. 300 dead links scrolled past would be 300 fetches an hour.
        assertNull(reaskDelaySeconds(null, 1, 0.5))
        assertNull(secondsUntil(null, 0L))
        assertNull(secondsUntil("not-a-date", 0L))
        // Already lapsed — the fast-clock case.
        assertNull(secondsUntil("2020-01-01T00:00:00.000Z", System.currentTimeMillis()))
    }

    @Test fun `the ladder doubles and is capped`() {
        fun d(tries: Int) = reaskDelaySeconds(15.0, tries, 0.5)!!
        assertTrue(d(2) > d(1))
        assertTrue(d(3) > d(2))
        // Ceiling at 5 minutes (before jitter).
        assertTrue(d(5) <= REASK_CEILING_SECONDS * 1.25 + 0.001)
    }

    @Test fun `the ladder stops rather than polling forever`() {
        assertNull(reaskDelaySeconds(15.0, REASK_MAX_TRIES, 0.5))
    }

    @Test fun `jitter actually spreads the herd`() {
        // The server jitters its transient TTL so the losers of one saturation
        // event don't all return together; dropping it re-synchronises every client
        // onto the same millisecond.
        val low = reaskDelaySeconds(15.0, 1, 0.0)!!
        val high = reaskDelaySeconds(15.0, 1, 0.999)!!
        assertTrue("expected spread, got $low..$high", high > low * 1.4)
    }

    // ---- which URLs to ask about --------------------------------------------

    @Test fun `bracketed urls are the poster opting out`() {
        val urls = serverPreviewUrls("see <https://example.com/a> please", true, true)
        assertTrue(urls.isEmpty())
    }

    @Test fun `brackets speak for the occurrence, not the address`() {
        // The same address posted bare earlier must still resolve.
        val urls = serverPreviewUrls("https://example.com/a and <https://example.com/a>", true, true)
        assertEquals(listOf("https://example.com/a"), urls)
    }

    @Test fun `media is NOT filtered out — both classes go to the resolver`() {
        val urls = serverPreviewUrls("https://i.imgur.com/a.png https://example.com/post", true, true)
        assertEquals(listOf("https://i.imgur.com/a.png", "https://example.com/post"), urls)
    }

    @Test fun `an extensionless url is wanted when EITHER toggle is on`() {
        // The load-bearing asymmetry: mediaKindForUrl returns null both for
        // "definitely a page" and "nothing to judge by", and extensionless image
        // hosts are the common case on IRC. Requiring linkPreviews for that second
        // case means they could never render for someone with only inline media on.
        val u = "https://i.imgur.com/abcdef"
        assertEquals(listOf(u), serverPreviewUrls(u, inlineMedia = true, linkPreviews = false))
        assertEquals(listOf(u), serverPreviewUrls(u, inlineMedia = false, linkPreviews = true))
        assertTrue(serverPreviewUrls(u, inlineMedia = false, linkPreviews = false).isEmpty())
    }

    @Test fun `the two budgets are counted separately`() {
        val media = (1..6).joinToString(" ") { "https://h/img$it.png" }
        val pages = (1..6).joinToString(" ") { "https://h/page$it" }
        val urls = serverPreviewUrls("$media $pages", true, true, maxCards = 3, maxMedia = 20)
        assertEquals(6, urls.count { mediaKindForUrl(it) != null })  // media budget intact
        assertEquals(3, urls.count { mediaKindForUrl(it) == null })  // cards clamped
    }

    @Test fun `only message and action can carry a preview`() {
        // Joining a channel whose topic is a URL, or scrolling past
        // "Quit: HexChat https://…", must not make the server fetch anything.
        assertTrue(previewableEvent("message"))
        assertTrue(previewableEvent("action"))
        listOf("notice", "topic", "quit", "part", "join", "mode").forEach {
            assertFalse(it, previewableEvent(it))
        }
    }

    // ---- spoilers -------------------------------------------------------------

    private val C = "\u0003"

    @Test fun `a url inside a spoiler run is skipped`() {
        // fg == bg, both renderable → hidden text. Unfurling it renders the target
        // full-size beside the click-to-reveal box.
        val text = "${C}1,1https://example.com/secret$C"
        assertTrue(serverPreviewUrls(text, true, true).isEmpty())
    }

    @Test fun `slots above 15 paint nothing, so 99,99 is not a spoiler`() {
        // Lurker closes a spoiler with 99,99 when a digit follows, making the TAIL
        // of those messages a 99,99 run. Without the renderable bound every URL
        // after a spoiler would silently lose its preview.
        val text = "${C}99,99https://example.com/visible"
        assertEquals(listOf("https://example.com/visible"), serverPreviewUrls(text, true, true))
    }

    @Test fun `a url after a spoiler closes is kept`() {
        val text = "${C}1,1hidden$C then https://example.com/after"
        assertEquals(listOf("https://example.com/after"), serverPreviewUrls(text, true, true))
    }

    @Test fun `differing colours are not a spoiler`() {
        val text = "${C}4,1https://example.com/red"
        assertEquals(listOf("https://example.com/red"), serverPreviewUrls(text, true, true))
    }

    // ---- one parser for where a URL ends -------------------------------------

    @Test fun `trailing punctuation matches the linkifier, brackets balanced`() {
        // Two parsers disagreeing about where a URL ends is the bug: the card
        // silently never appears and the 404 is cached under a string that appears
        // nowhere in the message. serverPreviewUrls uses Mirc.findUrls, the same
        // one the renderer links.
        assertEquals(
            listOf("https://en.wikipedia.org/wiki/Rust_(programming_language)"),
            serverPreviewUrls("see https://en.wikipedia.org/wiki/Rust_(programming_language)", true, true),
        )
        assertEquals(
            listOf("https://example.com/a"),
            serverPreviewUrls("look at https://example.com/a.", true, true),
        )
    }

    // ---- the reveal gate ------------------------------------------------------

    @Test fun `the gate fails OPEN for everything except an in-flight request`() {
        // It hides a WHOLE message's attachments while it answers "pending", so any
        // "not sure" turns a partial failure into a blank message. A URL on the
        // re-ask ladder is SETTLED — counting it pending blanked every message that
        // shared a batch with one 502 during a deploy, including ones that resolved.
        val urls = listOf("a", "b")
        assertTrue(attachmentsSettled(urls, inFlight = emptySet()))
        assertTrue(attachmentsSettled(urls, inFlight = setOf("unrelated")))
        assertFalse(attachmentsSettled(urls, inFlight = setOf("b")))
        assertTrue(attachmentsSettled(emptyList(), inFlight = setOf("a")))
    }
}
