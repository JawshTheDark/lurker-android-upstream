// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.amiantos.lurker.ui.theme.AccentBlue
import net.amiantos.lurker.ui.theme.CanvasBlack
import net.amiantos.lurker.ui.theme.GlassBorder
import net.amiantos.lurker.ui.theme.SurfaceDark
import net.amiantos.lurker.ui.theme.TextPrimary
import net.amiantos.lurker.ui.theme.TextSecondary
import net.amiantos.lurker.ui.theme.Ui
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * A fetched link-preview card for a pasted URL (OpenGraph metadata, or a YouTube
 * video's title + description). Populated off the main thread by [LinkPreviews]
 * and rendered by [LinkPreviewCards] beneath a message, alongside the existing
 * inline media embeds.
 */
data class LinkPreview(
    val url: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val siteName: String?,
    val youtube: Boolean,
)

/**
 * Fetches + caches link previews. Media flows client<->the linked site directly;
 * nothing goes through Lurker. Two device-local Settings toggles gate this
 * (`Ui.linkPreviews` for general OpenGraph cards, `Ui.youtubeDescriptions` for the
 * richer YouTube card) — see the guards in [LinkPreviewCards].
 *
 * The cache is process-lived and negative (a URL that yields no preview is
 * remembered as empty, so a busy channel doesn't re-hammer the same dead link).
 */
object LinkPreviews {
    // Present+empty = fetched, nothing to show. Absent = not fetched yet. Bounded
    // LRU (access-order) so a long-lived process that scrolls past thousands of
    // links can't grow the cache without limit; only DEFINITIVE outcomes are
    // stored (see load) so a transient network blip doesn't poison a link forever.
    private const val CACHE_CAP = 256
    private val cache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Optional<LinkPreview>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Optional<LinkPreview>>?) =
                size > CACHE_CAP
        },
    )
    private val locks = ConcurrentHashMap<String, Mutex>()

    // A realistic desktop UA — some sites (YouTube included) only serve full
    // OpenGraph tags / the description JSON to a browser-shaped client.
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    // Own client with FINITE timeouts — the chat socket's OkHttpClient uses
    // readTimeout(0) for the long-lived WebSocket, which would hang a dead fetch.
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** A cached preview if we already have one, else null (never blocks). */
    fun peek(url: String): LinkPreview? = cache[url]?.orElse(null)

    /** Fetch (or return cached) the preview for [url]. Safe to call from many
     *  composables at once — per-URL locked so a link on screen twice fetches once. */
    suspend fun load(url: String, youtube: Boolean): LinkPreview? {
        cache[url]?.let { return it.orElse(null) }
        val mu = locks.getOrPut(url) { Mutex() }
        mu.withLock {
            cache[url]?.let { return it.orElse(null) }
            // Separate a DEFINITIVE outcome (the server answered — even if the page
            // has no OG tags) from a TRANSIENT failure (DNS/timeout/offline/5xx,
            // surfaced as an IOException). Cache only the definitive one; leave a
            // transient miss uncached so the preview can appear once the network
            // recovers, instead of being dead until the app restarts.
            val outcome = withContext(Dispatchers.IO) {
                runCatching { if (youtube) fetchYouTube(url) else fetchOpenGraph(url) }
            }
            locks.remove(url)
            return if (outcome.isSuccess) {
                val preview = outcome.getOrNull()
                cache[url] = Optional.ofNullable(preview)
                preview
            } else {
                null // transient — don't cache; a later view retries
            }
        }
    }

    private fun fetchOpenGraph(url: String): LinkPreview? {
        val req = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en")
            .build()
        http.newCall(req).execute().use { res ->
            // 5xx is transient (server hiccup) → throw so load() won't cache it.
            // 4xx is definitive (gone/forbidden) → no preview, and cache that.
            if (res.code >= 500) throw java.io.IOException("server ${res.code}")
            if (!res.isSuccessful) return null
            val ct = res.header("Content-Type").orEmpty()
            if (ct.isNotBlank() && !ct.contains("html", ignoreCase = true)) return null
            // OG tags live in <head>; cap the read so a huge page can't blow memory.
            val html = res.peekBody(384L * 1024).string()
            return parseOpenGraph(url, html)
        }
    }

    private fun fetchYouTube(url: String): LinkPreview? {
        val id = youTubeId(url) ?: return fetchOpenGraph(url)
        val req = Request.Builder().url("https://www.youtube.com/watch?v=$id")
            .header("User-Agent", UA)
            .header("Accept-Language", "en")
            // Skip the EU consent interstitial, which otherwise hides the player JSON.
            .header("Cookie", "CONSENT=YES+cb")
            .build()
        http.newCall(req).execute().use { res ->
            if (res.code >= 500) throw java.io.IOException("server ${res.code}")
            if (!res.isSuccessful) return null
            // The full description ("shortDescription") sits deep in the page's
            // ytInitialPlayerResponse JSON, so read more than for plain OG.
            val html = res.peekBody(2_000L * 1024).string()
            return parseYouTube(url, id, html)
        }
    }
}

// ---- Pure parsing (network-free, unit-tested) -------------------------------

/** Extract a preview from page [html]; null if it has no usable title. */
internal fun parseOpenGraph(url: String, html: String): LinkPreview? {
    val og = metaMap(html)
    val title = og["og:title"] ?: titleTag(html) ?: return null
    val desc = og["og:description"]?.trim()?.ifBlank { null }
    val image = og["og:image"]?.let { absolutize(url, it) }
    val site = og["og:site_name"]?.trim()?.ifBlank { null } ?: hostOf(url)
    return LinkPreview(url, title.trim(), desc, image, site, youtube = false)
}

/** Build a YouTube card: OG title, scraped full description, canonical thumbnail. */
internal fun parseYouTube(url: String, id: String, html: String): LinkPreview {
    val og = metaMap(html)
    val title = og["og:title"]?.trim()?.ifBlank { null } ?: "YouTube video"
    val desc = shortDescription(html) ?: og["og:description"]?.trim()?.ifBlank { null }
    val thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    return LinkPreview(url, title, desc, thumb, "YouTube", youtube = true)
}

/** The http(s) URLs in [text] that aren't already handled as inline media, capped
 *  so one paste can't spawn a wall of fetches. */
fun previewUrlsIn(text: String, limit: Int = 3): List<String> =
    Mirc.urls(text)
        .filter { it.startsWith("http", ignoreCase = true) && mediaKindForUrl(it) == null }
        .distinct()
        .take(limit)

private val YT_ID = Regex(
    """(?:youtu\.be/|youtube\.com/(?:watch\?(?:[^ ]*&)?v=|shorts/|live/|embed/|v/))([A-Za-z0-9_-]{11})""",
    RegexOption.IGNORE_CASE,
)

fun youTubeId(url: String): String? = YT_ID.find(url)?.groupValues?.get(1)

fun isYouTube(url: String): Boolean = youTubeId(url) != null

// Scan every <meta> tag ONCE (precompiled patterns, sliced to <head>) and index
// it by its property/name → content, instead of two fresh whole-document regex
// passes per property (the old metaContent recompiled and rescanned for each of
// og:title/description/image/site_name). Attribute order/quote style don't matter
// since we pull both attrs out of the matched tag independently.
private val META_TAG = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
private val META_KEY = Regex("""(?:property|name)\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
private val META_CONTENT = Regex("""content\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

private fun metaMap(html: String): Map<String, String> {
    // OG/meta tags live in <head> — stop there so we don't scan a multi-MB body.
    val head = html.substringBefore("</head>")
    val map = HashMap<String, String>()
    for (m in META_TAG.findAll(head)) {
        val tag = m.value
        val key = META_KEY.find(tag)?.groupValues?.get(1)?.lowercase() ?: continue
        val content = META_CONTENT.find(tag)?.groupValues?.get(1) ?: continue
        val value = htmlUnescape(content).ifBlank { null } ?: continue
        map.putIfAbsent(key, value) // first wins, matching the old first-match semantics
    }
    return map
}

private val TITLE_TAG = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)
private fun titleTag(html: String): String? =
    TITLE_TAG.find(html)?.groupValues?.get(1)?.let { htmlUnescape(it).trim().ifBlank { null } }

// "shortDescription":"...", with \" and \\ inside — grab the escaped run, then
// decode the JSON string escapes ourselves (org.json is a throwing stub under
// plain JVM unit tests, and this keeps test == prod behaviour).
private val SHORT_DESC = Regex(""""shortDescription":"((?:\\.|[^"\\])*)"""")
private fun shortDescription(html: String): String? {
    val raw = SHORT_DESC.find(html)?.groupValues?.get(1) ?: return null
    return jsonUnescape(raw).trim().ifBlank { null }
}

/** Decode the escapes inside one JSON string literal (\n \t \" \\ \/ \uXXXX …). */
internal fun jsonUnescape(s: String): String {
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c != '\\' || i + 1 >= s.length) { sb.append(c); i++; continue }
        when (val n = s[i + 1]) {
            'n' -> { sb.append('\n'); i += 2 }
            't' -> { sb.append('\t'); i += 2 }
            'r' -> { sb.append('\r'); i += 2 }
            'b' -> { sb.append('\b'); i += 2 }
            'f' -> { sb.append('\u000C'); i += 2 }
            '"' -> { sb.append('"'); i += 2 }
            '\\' -> { sb.append('\\'); i += 2 }
            '/' -> { sb.append('/'); i += 2 }
            'u' -> {
                val cp = if (i + 6 <= s.length) s.substring(i + 2, i + 6).toIntOrNull(16) else null
                if (cp != null) { sb.append(cp.toChar()); i += 6 } else { sb.append(c); i++ }
            }
            else -> { sb.append(n); i += 2 }
        }
    }
    return sb.toString()
}

private val ENTITY = Regex("""&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);""")
private val NAMED = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
)

internal fun htmlUnescape(s: String): String = ENTITY.replace(s) { m ->
    val e = m.groupValues[1]
    when {
        e.startsWith("#x") || e.startsWith("#X") ->
            e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        e.startsWith("#") ->
            e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        else -> NAMED[e.lowercase()] ?: m.value
    }
}

private fun hostOf(url: String): String? =
    runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull()

private fun absolutize(base: String, u: String): String =
    runCatching { java.net.URI(base).resolve(u).toString() }.getOrElse { u }

// ---- Compose UI -------------------------------------------------------------

/**
 * Render preview cards for the non-media links in [text], each gated by its own
 * device-local toggle. Dropped in beneath the inline-media embeds in every chat
 * row variant. [onOpen] routes a tap to the same link handler messages use.
 */
@Composable
fun LinkPreviewCards(text: String, onOpen: (String) -> Unit) {
    val links = Ui.linkPreviews
    val yt = Ui.youtubeDescriptions
    if (!links && !yt) return
    val urls = remember(text) { previewUrlsIn(text) }
    if (urls.isEmpty()) return
    urls.forEach { url ->
        val isYt = remember(url) { isYouTube(url) }
        // Each toggle owns exactly one behaviour: YouTube links show only when the
        // YouTube toggle is on; everything else only when Link previews is on.
        if (isYt && !yt) return@forEach
        if (!isYt && !links) return@forEach
        val preview by produceState<LinkPreview?>(LinkPreviews.peek(url), url) {
            value = LinkPreviews.load(url, isYt)
        }
        preview?.let { p ->
            if (p.youtube) YouTubeCard(p, onOpen) else GeneralLinkCard(p, onOpen)
        }
    }
}

@Composable
private fun YouTubeCard(p: LinkPreview, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CanvasBlack)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable { onOpen(p.url) },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.78f), contentAlignment = Alignment.Center) {
            p.imageUrl?.let {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(it).build(),
                    imageLoader = LocalContext.current.imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Color(0xE6FF0000)),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Color.White, fontSize = 20.sp) }
        }
        Column(Modifier.padding(10.dp)) {
            Text("YouTube", color = AccentBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(
                p.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
            p.description?.let {
                Text(
                    it,
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun GeneralLinkCard(p: LinkPreview, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            // Height comes from the text block, and the thumbnail stretches to match
            // it. A fixed square thumb was vertically CENTERED instead, so it floated
            // mid-card with dead gaps above and below it rather than sitting flush in
            // the corner. heightIn keeps a one-line card from collapsing to a sliver.
            .height(IntrinsicSize.Min)
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
            .clickable { onOpen(p.url) },
    ) {
        p.imageUrl?.let {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(it).build(),
                imageLoader = LocalContext.current.imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxHeight().width(92.dp),
            )
        }
        Column(Modifier.weight(1f).padding(10.dp)) {
            (p.siteName)?.let {
                Text(
                    it,
                    color = AccentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                p.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            p.description?.let {
                Text(
                    it,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}
