// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Server-resolved link previews (Lurker 2.1's `/api/link-preview`).
 *
 * WHY this exists, since everything else here is machinery in service of it: a
 * preview card renders ITSELF. The reader never chose to contact that host.
 * Scraping on-device tells every linked site's operator the reader's IP, rough
 * location, device, and that they were reading that channel at that moment — for
 * a link they never tapped, dozens of hosts a minute in a busy channel. Resolving
 * server-side means the only outbound request a reader makes is one they asked
 * for by tapping.
 *
 * Direct-IRC mode has no Lurker server, so it keeps the on-device scraper in
 * [LinkPreviews]. This file is the Lurker-mode path.
 */

/** One descriptor from `POST /api/link-preview/resolve`. */
data class ServerLinkPreview(
    val url: String,
    val status: String,
    val kind: String,
    val title: String? = null,
    val description: String? = null,
    val siteName: String? = null,
    val author: String? = null,
    /** Bytes for an `image`. Opaque — see [previewMediaTarget]. */
    val src: String? = null,
    /** The card's picture, or a decoded poster frame for a clip. Opaque. */
    val thumb: String? = null,
    val thumbWidth: Int? = null,
    val thumbHeight: Int? = null,
    val embedUrl: String? = null,
    val mime: String? = null,
    val expiresAt: String? = null,
) {
    val ok: Boolean get() = status == "ok"

    /** image/video/audio are FILES; page/video-embed are PAGES. The two settings
     *  exist because they're two different appetites. */
    val isMedia: Boolean get() = kind == "image" || kind == "video" || kind == "audio"

    /**
     * Re-check the SERVER's verdict against the settings. `kind` comes from the
     * response Content-Type, not the extension, so our own guess can be wrong —
     * and not re-checking means "previews off" can still be talked into drawing a
     * card by an extensionless URL that turns out to be a PNG, or a `.jpg` that
     * redirects to an HTML login page.
     */
    fun isAllowed(inlineMedia: Boolean, linkPreviews: Boolean): Boolean =
        ok && if (isMedia) inlineMedia else linkPreviews
}

/**
 * Decode one descriptor leniently. The protocol is ADDITIVE-ONLY — new fields
 * appear without a version bump — so unknown keys are ignored rather than fatal,
 * and a descriptor we can't read is dropped individually instead of failing the
 * whole batch.
 */
internal fun parseServerPreview(o: JSONObject): ServerLinkPreview? {
    val url = o.optString("url").ifEmpty { return null }
    fun str(k: String) = o.optString(k).ifEmpty { null }
    fun num(k: String) = if (o.has(k) && !o.isNull(k)) o.optInt(k).takeIf { it > 0 } else null
    return ServerLinkPreview(
        url = url,
        status = o.optString("status").ifEmpty { "unavailable" },
        kind = o.optString("kind").ifEmpty { "page" },
        title = str("title"),
        description = str("description"),
        siteName = str("siteName"),
        author = str("author"),
        src = str("src"),
        thumb = str("thumb"),
        thumbWidth = num("thumbWidth"),
        thumbHeight = num("thumbHeight"),
        embedUrl = str("embedUrl"),
        mime = str("mime"),
        expiresAt = str("expiresAt"),
    )
}

// ---- Byte URLs are OPAQUE ----------------------------------------------------

/** What to do with a server-minted `src`/`thumb`. */
sealed interface MediaTarget {
    /** On the instance itself: send the session token. */
    data class OwnServer(val url: String) : MediaTarget

    /** A CDN or other third-party host: NEVER send the token. */
    data class ThirdParty(val url: String) : MediaTarget
}

/**
 * Resolve an opaque byte URL, deciding whether it may carry the bearer token.
 *
 * `src`/`thumb` are strings the SERVER mints and a client must never construct or
 * parse. A given value is either a root-relative path on this instance, OR an
 * absolute URL on a CDN when the instance has a bucket-backed byte cache — and
 * nothing on the wire distinguishes them, so which one you get can change with
 * the instance's configuration at any time.
 *
 * Two ways to get this wrong, both of which have shipped:
 *  - Concatenating unconditionally turns an absolute URL into
 *    `https://chat.examplehttps://cdn…`, which fails to parse and can latch a
 *    client into "every preview blank" with nothing surfaced.
 *  - Attaching Authorization to an absolute URL puts the user's session token in
 *    a CDN operator's access log, once per image. A global OkHttp interceptor
 *    that adds the header to everything IS this bug.
 *
 * A relative path must also be ROOT-relative: `baseUrl + "api/media/x"` yields
 * `https://chat.exampleapi/media/x` — a domain someone else can register.
 */
fun previewMediaTarget(path: String, baseUrl: String): MediaTarget? {
    val p = path.trim()
    if (p.isEmpty()) return null
    if (p.startsWith("http://") || p.startsWith("https://")) return MediaTarget.ThirdParty(p)
    if (!p.startsWith("/")) return null // not root-relative: would move the host
    return MediaTarget.OwnServer(baseUrl.trimEnd('/') + p)
}

// ---- The re-ask ladder --------------------------------------------------------

/** Successful answers get 7 days, real failures 1 hour, transient ones ~15s
 *  jittered. So only a SHORT expiry is an invitation to come back. */
internal const val REASK_VERDICT_SECONDS = 60.0
internal const val REASK_FLOOR_SECONDS = 15.0
internal const val REASK_CEILING_SECONDS = 300.0
internal const val REASK_MAX_TRIES = 6

/**
 * Delay before asking about a URL again, or null for "this is the answer".
 *
 * [untilExpirySeconds] null means the server stated no expiry (absent, garbled,
 * or already past) — which is a VERDICT, not an invitation. Mapping that to zero
 * sails through the short-TTL test and arms a poller nothing clears; and a device
 * whose clock runs fast would otherwise turn every 1-hour failure TTL into one.
 *
 * [jitter] is 0..1 from the caller so this stays pure and testable. It is NOT
 * cosmetic: the server jitters its transient TTL precisely so the losers of one
 * saturation event don't return as a single wave, and re-synchronising every
 * client onto the same millisecond aims a thundering herd at a server that just
 * said it was overloaded.
 */
fun reaskDelaySeconds(untilExpirySeconds: Double?, tries: Int, jitter: Double): Double? {
    if (untilExpirySeconds == null) return null
    if (untilExpirySeconds > REASK_VERDICT_SECONDS) return null
    if (tries >= REASK_MAX_TRIES) return null // past this a timer isn't recovery
    val doubled = REASK_FLOOR_SECONDS * 2.0.pow(max(0, tries - 1))
    val base = max(untilExpirySeconds, min(REASK_CEILING_SECONDS, doubled))
    return base * (0.75 + jitter.coerceIn(0.0, 0.999) * 0.5) // ±25%
}

// ---- Which URLs to ask about --------------------------------------------------

/** A card costs real vertical space, so this stays tight. */
internal const val MAX_CARDS_PER_MESSAGE = 3

/** Images render as a grid, so a fifth costs half a row — generous on purpose.
 *  Counted SEPARATELY so one class filling up can't consume the other's budget. */
internal const val MAX_MEDIA_PER_MESSAGE = 20

/** Only these event types can carry a preview. Not notice (usually a bot or a
 *  service, and unfurling ChanServ is not a feature), not topic, not part/quit —
 *  those publish their reason as text, so joining a channel whose topic is a URL,
 *  or scrolling past "Quit: HexChat https://…", would otherwise make the server
 *  fetch a page on the reader's behalf that nothing will ever display. */
fun previewableEvent(type: String): Boolean = type == "message" || type == "action"

/**
 * Character ranges of mIRC "spoiler" runs — a run whose foreground and background
 * are the same slot. Unfurling a link inside one renders the target full-size next
 * to the click-to-reveal box, defeating the spoiler entirely.
 *
 * Slots ABOVE 15 paint nothing, so the equality only hides text when both are
 * renderable. Lurker closes a spoiler with `99,99` when a digit follows, which
 * makes the tail of those messages a 99,99 run — without the bound, every URL
 * after a spoiler would silently lose its preview.
 */
fun spoilerRanges(text: String): List<IntRange> {
    val out = mutableListOf<IntRange>()
    var i = 0
    var fg: Int? = null
    var bg: Int? = null
    var runStart = -1
    fun closeRun(end: Int) {
        if (runStart >= 0 && end >= runStart) out.add(runStart..end)
        runStart = -1
    }
    while (i < text.length) {
        val c = text[i]
        if (c == '\u000F') { // reset
            closeRun(i - 1); fg = null; bg = null; i++; continue
        }
        if (c != '\u0003') { i++; continue }
        // U+0003 [fg[,bg]] — no digits means "colour off".
        var j = i + 1
        val f = StringBuilder()
        while (j < text.length && f.length < 2 && text[j].isDigit()) { f.append(text[j]); j++ }
        val b = StringBuilder()
        if (f.isNotEmpty() && j < text.length && text[j] == ',' &&
            j + 1 < text.length && text[j + 1].isDigit()
        ) {
            j++
            while (j < text.length && b.length < 2 && text[j].isDigit()) { b.append(text[j]); j++ }
        }
        closeRun(i - 1)
        fg = f.toString().toIntOrNull()
        bg = b.toString().toIntOrNull() ?: if (f.isEmpty()) null else bg
        // Hidden only when both are set, equal, and actually paint something.
        if (fg != null && bg != null && fg == bg && fg <= 15) runStart = j
        i = j
    }
    closeRun(text.length - 1)
    return out
}

/**
 * The URLs in [text] worth asking the server about, in message order.
 *
 * Unlike the on-device path this does NOT filter media out: both media and pages
 * go through the same resolver, and images come back with a proxied `src`.
 * `mediaKindForUrl` survives only to decide which BUDGET and which SETTING a URL
 * is charged to.
 */
fun serverPreviewUrls(
    text: String,
    inlineMedia: Boolean,
    linkPreviews: Boolean,
    maxCards: Int = MAX_CARDS_PER_MESSAGE,
    maxMedia: Int = MAX_MEDIA_PER_MESSAGE,
): List<String> {
    if (!inlineMedia && !linkPreviews) return emptyList()
    val spoilers = spoilerRanges(text)
    val seen = LinkedHashSet<String>()
    var cards = 0
    var media = 0
    for (range in Mirc.findUrls(text)) {
        val url = text.substring(range)
        if (!url.startsWith("http", ignoreCase = true)) continue
        // <https://…> is the poster explicitly saying "don't unfurl this". Checked
        // BEFORE dedupe, so the same address posted bare earlier still resolves —
        // the brackets speak for the occurrence they wrap, not for the address.
        val bracketed = range.first > 0 && text[range.first - 1] == '<' &&
            range.last + 1 < text.length && text[range.last + 1] == '>'
        if (bracketed) continue
        if (spoilers.any { range.first >= it.first && range.first <= it.last }) continue
        if (url in seen) continue
        val isMedia = mediaKindForUrl(url) != null
        // An EXTENSIONLESS url is charged to the card budget but is wanted when
        // EITHER toggle is on: mediaKindForUrl returns null both for "definitely a
        // page" and for "nothing to judge by", and requiring linkPreviews for the
        // second case means an extensionless image host (imgur, twimg — the common
        // case on IRC) could never render for someone who enabled only inline
        // media. Permanently, since priming is ingest-driven.
        val wanted = if (isMedia) inlineMedia else (linkPreviews || inlineMedia)
        if (!wanted) continue
        if (isMedia) {
            if (media >= maxMedia) continue
            media++
        } else {
            if (cards >= maxCards) continue
            cards++
        }
        seen.add(url)
    }
    return seen.toList()
}

// ---- Reveal gate ---------------------------------------------------------------

/**
 * Whether a message's whole attachment block may be drawn yet.
 *
 * A preview changes a row's height, so revealing three links 200ms apart makes the
 * reader watch the layout rearrange three times. Withhold the block until every
 * URL has SETTLED, then draw it complete.
 *
 * This gate MUST fail open in every branch: it hides a whole message's attachments
 * while it answers "pending", so any "not sure" turns a partial failure into a
 * blank message — strictly worse than the shift it prevents. In particular a URL
 * waiting on a re-ask is SETTLED; counting it pending hid the block for the whole
 * 15s→5min ladder, so one 502 during a deploy blanked every message that shared a
 * batch with it, including the ones that resolved perfectly.
 */
fun attachmentsSettled(urls: List<String>, inFlight: Set<String>): Boolean =
    urls.none { it in inFlight }
