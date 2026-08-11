// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

/**
 * Batches, paces and caches server link-preview lookups.
 *
 * The server owns fetching, parsing, caching and the byte proxy. What's left is
 * the thing only a client can do: notice that a screenful of scrollback contains
 * the same eight URLs forty times and turn that into ONE request.
 *
 * Everything here is deliberately free of Compose and of real time — the clock,
 * the jitter and the resolver are injected — because this is where the bugs live
 * and they're only findable in a unit test. Every rule below was a shipped bug in
 * the iOS client before it was a comment here.
 */
class PreviewStore(
    private val maxBatch: Int = 20,
    /** Server allows 120 resolve req/min; 600ms between batches is ~100/min. */
    private val pacingMs: Long = 600,
    /** Long enough that one layout pass batches together, short enough not to
     *  visibly lag the scroll. */
    private val debounceMs: Long = 24,
    private val cacheCap: Int = 256,
    private val now: () -> Long = System::currentTimeMillis,
    private val jitter: () -> Double = { 0.5 },
) {
    private data class Retry(val dueAt: Long, val tries: Int)

    /** Answers, LRU. A cache across TIME: scroll away and back and it's free. */
    private val cache = object : LinkedHashMap<String, ServerLinkPreview>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ServerLinkPreview>?) =
            size > cacheCap
    }

    /** URLs we've already asked about, so a redraw never re-requests one. */
    private val asked = HashSet<String>()

    /** Waiting to be sent. LinkedHashSet, NOT HashSet: batches must go out in
     *  INSERTION order. Priming runs from the frame the reader is looking at
     *  outwards, and a set's iteration order would put the visible buffer behind
     *  twenty paced batches for no reproducible reason. */
    private val queue = LinkedHashSet<String>()

    /** In flight right now — the ONLY thing that counts as pending for the
     *  reveal gate. */
    private val inFlight = HashSet<String>()

    private val retries = HashMap<String, Retry>()

    /** Bumped on sign-out. Checked on BOTH sides of every suspension point, so an
     *  in-air response can't refill the store we just emptied — which otherwise
     *  puts the previous account's metadata back in cache and POSTs account A's
     *  URLs under account B's token. */
    private var generation = 0

    private var lastBatchAt = 0L
    private var debounceUntil = 0L

    // ---- reads ---------------------------------------------------------------

    fun get(url: String): ServerLinkPreview? = cache[url]

    fun pending(): Set<String> = inFlight.toSet()

    /** True when every URL of a message has settled and its attachment block may
     *  be drawn. Waiting on a re-ask counts as SETTLED (see [attachmentsSettled]). */
    fun settled(urls: List<String>): Boolean = attachmentsSettled(urls, inFlight)

    // ---- priming -------------------------------------------------------------

    /** Offer URLs for lookup. Already-known and already-asked ones are dropped;
     *  the rest queue in the order given. */
    fun prime(urls: List<String>) {
        val t = now()
        for (u in urls) {
            if (u in cache || u in asked || u in inFlight) continue
            val r = retries[u]
            if (r != null && t < r.dueAt) continue // ladder hasn't come due
            queue.add(u)
        }
        if (queue.isNotEmpty() && debounceUntil == 0L) debounceUntil = t + debounceMs
    }

    /** The next batch to send, or null if nothing is due yet. Caller sends it and
     *  reports back via [onResponse] / [onFailure]. */
    fun nextBatch(): List<String>? {
        val t = now()
        if (queue.isEmpty()) { debounceUntil = 0L; return null }
        if (debounceUntil != 0L && t < debounceUntil) return null
        // First batch goes immediately so the buffer you're LOOKING at isn't
        // delayed; later ones are paced.
        if (lastBatchAt != 0L && t - lastBatchAt < pacingMs) return null
        val batch = queue.take(maxBatch)
        queue.removeAll(batch.toSet())
        asked.addAll(batch)
        inFlight.addAll(batch)
        lastBatchAt = t
        debounceUntil = if (queue.isEmpty()) 0L else t + debounceMs
        return batch
    }

    // ---- responses -----------------------------------------------------------

    /**
     * Fold a response. Returns the URLs whose state MOVED, for the UI to
     * re-lay-out — every URL that moved, not only the ones that got a value: a
     * URL the server omitted, or one pushed onto the ladder, moves a message's
     * reveal gate exactly as an answer does.
     */
    fun onResponse(
        gen: Int,
        sent: List<String>,
        previews: List<ServerLinkPreview>,
    ): Set<String> {
        if (gen != generation) return emptySet() // signed out mid-flight
        val changed = HashSet<String>()
        val answered = HashSet<String>()
        for (p in previews) {
            answered.add(p.url)
            cache[p.url] = p
            inFlight.remove(p.url)
            changed.add(p.url)
            val secs = secondsUntil(p.expiresAt, now())
            val prev = retries[p.url]?.tries ?: 0
            // Keep `tries` ACROSS priming passes: resetting to zero means the
            // backoff never accumulates, so a bot reposting a failing link turns a
            // "ladder" into a flat 15-second poll.
            val delay = reaskDelaySeconds(secs, prev + 1, jitter())
            if (delay != null) {
                retries[p.url] = Retry(now() + (delay * 1000).toLong(), prev + 1)
                asked.remove(p.url) // eligible again once due
            } else {
                retries.remove(p.url) // a verdict
            }
        }
        // Reconcile: asked about 20, got 19 back? The 20th must go back in play, or
        // it reaches a state nothing can see — "already asked" so priming skips it,
        // and no retry armed so nothing returns for it. Permanently blank, from a
        // response that looked perfectly fine.
        for (u in sent) if (u !in answered) { forgetForRetry(u); changed.add(u) }
        return changed
    }

    /**
     * A transport failure. Returns the URLs that moved.
     *
     * A failure says NOTHING about the URL — so the asked-set entry is removed. Not
     * doing that means any batch that failed (a 429 from the connect burst is
     * enough) is remembered as a permanent answer, and those links stay blank for
     * the rest of the session.
     */
    fun onFailure(gen: Int, sent: List<String>): Set<String> {
        if (gen != generation) return emptySet()
        sent.forEach { forgetForRetry(it) }
        return sent.toSet()
    }

    private fun forgetForRetry(url: String) {
        inFlight.remove(url)
        asked.remove(url)
        val prev = retries[url]?.tries ?: 0
        val tries = prev + 1
        // Never answered at all → it can't reach a verdict on its own, so cap the
        // ladder. Past 6 tries it's already at 5 minutes and a timer stops being
        // recovery; the URL stays eligible for a NEW priming pass, so it recovers
        // when new messages arrive rather than on a clock.
        val delay = reaskDelaySeconds(REASK_FLOOR_SECONDS, tries, jitter())
        if (delay != null) retries[url] = Retry(now() + (delay * 1000).toLong(), tries)
        else retries[url] = Retry(Long.MAX_VALUE, tries)
    }

    // ---- lifecycle -----------------------------------------------------------

    fun currentGeneration(): Int = generation

    /** Wipe everything on sign-out and invalidate any in-air response. */
    fun reset() {
        generation++
        cache.clear()
        asked.clear()
        queue.clear()
        inFlight.clear()
        retries.clear()
        lastBatchAt = 0L
        debounceUntil = 0L
    }
}

/**
 * Seconds from [nowMs] until an ISO-8601 [expiresAt], or null when the server
 * stated no usable expiry.
 *
 * Null for absent, unparseable, AND already-past — all three are verdicts, not
 * invitations. Mapping them to "zero seconds left" sails through the short-TTL
 * test and arms a poller nothing clears; the already-past case matters because a
 * device whose clock runs an hour fast reads every 1-hour failure TTL as lapsed.
 */
internal fun secondsUntil(expiresAt: String?, nowMs: Long): Double? {
    if (expiresAt.isNullOrBlank()) return null
    val ms = runCatching { java.time.Instant.parse(expiresAt).toEpochMilli() }.getOrNull() ?: return null
    val delta = (ms - nowMs) / 1000.0
    return if (delta <= 0.0) null else delta
}
