// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPreviewTest {

    // ---- URL selection ------------------------------------------------------

    @Test fun `preview urls skip media and dupes`() {
        val text = "see https://example.com/a and https://cdn.x/pic.jpg and https://example.com/a again"
        val urls = previewUrlsIn(text)
        assertEquals(listOf("https://example.com/a"), urls) // .jpg excluded, dupe collapsed
    }

    @Test fun `preview urls are capped`() {
        val text = (1..5).joinToString(" ") { "https://site$it.example/page" }
        assertEquals(3, previewUrlsIn(text).size)
    }

    @Test fun `non-http schemes are ignored`() {
        assertTrue(previewUrlsIn("ftp://x/y mailto:a@b.c").isEmpty())
    }

    // ---- YouTube detection --------------------------------------------------

    @Test fun `youtube id from watch url`() {
        assertEquals("dQw4w9WgXcQ", youTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun `youtube id from watch url with leading params`() {
        assertEquals("dQw4w9WgXcQ", youTubeId("https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=5"))
    }

    @Test fun `youtube id from short link and shorts`() {
        assertEquals("abc-DEF_123", youTubeId("https://youtu.be/abc-DEF_123"))
        assertEquals("abc-DEF_123", youTubeId("https://www.youtube.com/shorts/abc-DEF_123"))
    }

    @Test fun `non-youtube is not youtube`() {
        assertNull(youTubeId("https://example.com/watch?v=nope"))
        assertFalse(isYouTube("https://vimeo.com/12345"))
    }

    // ---- OpenGraph parse ----------------------------------------------------

    @Test fun `parse opengraph pulls title description image and site`() {
        val html = """
            <html><head>
            <meta property="og:title" content="Cool &amp; Neat Page" />
            <meta property="og:description" content="A short summary here." />
            <meta property="og:image" content="/img/cover.png" />
            <meta property="og:site_name" content="Example" />
            </head></html>
        """.trimIndent()
        val p = parseOpenGraph("https://www.example.com/story", html)!!
        assertEquals("Cool & Neat Page", p.title)
        assertEquals("A short summary here.", p.description)
        assertEquals("https://www.example.com/img/cover.png", p.imageUrl) // relative → absolute
        assertEquals("Example", p.siteName)
        assertFalse(p.youtube)
    }

    @Test fun `parse opengraph tolerates reversed attribute order and single quotes`() {
        val html = "<meta content='Reversed' property='og:title'>"
        assertEquals("Reversed", parseOpenGraph("https://x.test/", html)!!.title)
    }

    @Test fun `parse opengraph falls back to title tag and host`() {
        val html = "<html><head><title>Just A Title</title></head></html>"
        val p = parseOpenGraph("https://news.example.org/x", html)!!
        assertEquals("Just A Title", p.title)
        assertEquals("news.example.org", p.siteName) // www-stripped host fallback
        assertNull(p.description)
    }

    @Test fun `parse opengraph returns null without a title`() {
        assertNull(parseOpenGraph("https://x.test/", "<html><head></head></html>"))
    }

    // ---- YouTube parse ------------------------------------------------------

    @Test fun `parse youtube prefers shortDescription and builds thumbnail`() {
        val html = """
            <meta property="og:title" content="My Video">
            <meta property="og:description" content="truncated snippet…">
            var x = {"shortDescription":"Line one\nLine two with \"quotes\" & more","foo":1};
        """.trimIndent()
        val p = parseYouTube("https://youtu.be/dQw4w9WgXcQ", "dQw4w9WgXcQ", html)
        assertEquals("My Video", p.title)
        assertEquals("Line one\nLine two with \"quotes\" & more", p.description)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", p.imageUrl)
        assertTrue(p.youtube)
    }

    @Test fun `parse youtube falls back to og description`() {
        val html = """<meta property="og:title" content="V"><meta property="og:description" content="og fallback">"""
        assertEquals("og fallback", parseYouTube("https://youtu.be/x", "x", html).description)
    }

    // ---- HTML entity decode -------------------------------------------------

    @Test fun `html unescape handles named and numeric entities`() {
        assertEquals("a & b < c > d \" e ' f", htmlUnescape("a &amp; b &lt; c &gt; d &quot; e &#39; f"))
        assertEquals("café", htmlUnescape("caf&#233;"))
        assertEquals("→", htmlUnescape("&#x2192;"))
    }
}
