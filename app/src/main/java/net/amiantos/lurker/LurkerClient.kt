// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Transport + observable state for the Lurker WS/REST contract.
 *
 * Grown up from the prototype's single-shot proof into something usable day to
 * day: it restores a saved session and connects silently, keeps the socket alive
 * across drops with backoff + `?since=` resume, tracks unread counts, renders the
 * full spread of IRC event types, and drives the DCC and settings REST surfaces.
 *
 * Still no ViewModel/repository layer — state lives in Compose snapshot state and
 * the class is the single source of truth. That's a deliberate ceiling for a
 * native client this size; lurker#492's transport-adapter seam is the next step
 * if it grows further.
 */
class LurkerClient {
    // ---- Observable state -------------------------------------------------
    var status by mutableStateOf<String?>(null)
        private set
    var loggedIn by mutableStateOf(false)
        private set
    var connected by mutableStateOf(false)
        private set

    val networks = mutableStateMapOf<Int, Network>()
    val buffers = mutableStateListOf<Buffer>()

    /** bufferKey -> messages, in id order. */
    val messagesByBuffer = mutableStateMapOf<String, List<Msg>>()

    /** bufferKey -> unread / highlight counts for badges. */
    val unread = mutableStateMapOf<String, Int>()
    val highlights = mutableStateMapOf<String, Int>()

    /** bufferKey -> channel roster, from names frames + incremental events. */
    val members = mutableStateMapOf<String, List<Member>>()

    /** bufferKey -> (nick -> expiry ms) of people currently typing there. */
    val typing = mutableStateMapOf<String, Map<String, Long>>()

    /** bufferKey -> server-synced draft text (persisted across devices). */
    val drafts = mutableStateMapOf<String, String>()
    private val draftPending = HashSet<String>() // keys with an unflushed local edit
    private val draftFlush = HashMap<String, Runnable>()

    /** bufferKey -> recent submitted lines (server-synced), oldest→newest. */
    val inputHistory = mutableStateMapOf<String, List<String>>()

    /** Server-synced custom aliases (name -> expansion). Fork-only feature. */
    val aliases = mutableStateListOf<AliasEntry>()

    /** Server-synced ignore rules (global + per-network), each carrying its scope. */
    val ignores = mutableStateListOf<IgnoreRule>()

    /**
     * True once a snapshot arrives from a server that supports the fork's
     * extended features (custom aliases, DCC send/chat, fserve). Inferred from
     * the presence of the fork-only `aliases` field — vanilla Lurker never sends
     * it. Gates the fork-only UI at runtime so one build works against any server:
     * the surfaces stay hidden on stock/hosted Lurker and light up on the fork.
     */
    var serverExtended by mutableStateOf(false)
        private set

    /** networkId -> pinned buffer targets, in user order. */
    val pins = mutableStateMapOf<Int, List<String>>()

    /** "networkId::nick" -> free-form nick note. */
    val nickNotes = mutableStateMapOf<String, String>()

    /** "networkId::#target" -> notify-always flag. */
    val notifyAlways = mutableStateMapOf<String, Boolean>()

    /** Message ids the user has bookmarked (saved). Synced from the server. */
    val bookmarkIds = mutableStateSetOf<Long>()

    /** Friends/contacts, synced from contacts-snapshot + contact-updated/deleted. */
    val contacts = mutableStateListOf<Contact>()

    /** "networkId::nickLower" -> presence state (online/offline/away/back). */
    val presence = mutableStateMapOf<String, String>()

    /** One-shot: the UI opens+focuses this buffer. Set when we actually join a
     *  channel (channel-joined) — which a 470 forward can rename vs. the request. */
    var pendingOpen: Buffer? by mutableStateOf(null)

    /** networkId -> (requested channel, requestedAt) for a user join, so the real
     *  channel-joined can retarget focus + drop the ghost requested buffer. */
    private val pendingJoin = mutableMapOf<Int, Pair<String, Long>>()

    /** True while the Activity is in the foreground (badge is enough; no notif). */
    private var appForeground = true

    /** Guards [start] so a recreated Activity doesn't cycle the socket. */
    private var started = false

    /** Set by the app: invoked for a notify-worthy message while backgrounded. */
    var notificationSink: ((NotifiableEvent) -> Unit)? = null

    // Channel-list browser (/LIST) state for the currently-viewed network.
    val chanlistRows = mutableStateListOf<ChannelListing>()
    var chanlistLoading by mutableStateOf(false)
    var chanlistInProgress by mutableStateOf(false)
    var chanlistTotalCount by mutableStateOf(0)
    private var chanlistKey = "" // (query,sortBy,sortDir) guard
    private var chanlistNetworkId = -1

    /** bufferKey -> whether older history exists beyond what's loaded. */
    val hasMoreOlder = mutableStateMapOf<String, Boolean>()

    /** bufferKey -> a history(before) request is in flight. */
    val loadingOlder = mutableStateMapOf<String, Boolean>()

    // Outstanding sends awaiting their send-result ack (clientId -> context).
    // The web client uses the same 8s deadline before declaring a send lost.
    private data class PendingSend(val bufferKey: String, val text: String)
    private val pendingSends = mutableMapOf<String, PendingSend>()
    private var clientIdSeq = 0

    /** bufferKey -> server read cursor (highest id the user has read). */
    private val lastRead = mutableMapOf<String, Long>()

    /**
     * bufferKey -> "New messages" divider anchor. Snapshotted from [lastRead]
     * when a buffer with unread is opened, so the divider stays put while the
     * user reads (the live cursor races to the tail the moment we mark-read).
     * Cleared when the buffer stops being active.
     */
    val dividerAfter = mutableStateMapOf<String, Long>()

    // DCC (receive side; send/chat scaffolded in DccScreen pending server API).
    val transfers = mutableStateMapOf<Int, DccTransfer>()
    var dccError by mutableStateOf<String?>(null)
    var dccEnabled by mutableStateOf(true)
        private set

    // Search (WS) + highlights (REST). One monotonic token drops stale results.
    val searchResults = mutableStateListOf<SearchResult>()
    var searchLoading by mutableStateOf(false)
    var searchHasMore by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)
    private var searchToken = 0
    private var searchNextBefore: Long? = null
    private var lastSearchRaw: String = ""

    val highlightItems = mutableStateListOf<SearchResult>()
    var highlightsLoading by mutableStateOf(false)
    private var highlightsNextBefore: Long? = null
    var highlightsHasMore by mutableStateOf(false)

    // Settings (registry-driven; empty if the server predates the endpoint).
    val settingsRegistry = mutableStateListOf<SettingOption>()
    val settingsValues = mutableStateMapOf<String, Any?>()
    var settingsError by mutableStateOf<String?>(null)
    var settingsLoaded by mutableStateOf(false)
        private set

    // ---- Internals --------------------------------------------------------
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val json = "application/json; charset=utf-8".toMediaType()

    @Volatile private var ws: WebSocket? = null
    private var token: String? = null
    private var baseUrl: String = ""
    private val networkNames = mutableMapOf<Int, String>()

    private var prefs: Prefs? = null
    private var activeKey: String? = null

    /** Highest server message id seen — the `?since=` cursor on reconnect. */
    private var maxMessageId = 0L

    // Reconnect bookkeeping.
    private var intentionalClose = false
    private var reconnectScheduled = false
    private var reconnectRunnable: Runnable? = null
    private var connecting = false
    private var backoffMs = INITIAL_BACKOFF

    /** Last time any frame arrived — the only honest socket-liveness signal. */
    private var lastFrameAt = 0L

    /** When the app went to background (0 while foregrounded). */
    private var wentBackgroundAt = 0L

    private fun post(block: () -> Unit) = main.post(block)

    // ---- Session lifecycle ------------------------------------------------

    /**
     * Wire up persistence and, if a saved session exists, connect straight away
     * without a sign-in screen. Called once from the Activity.
     */
    fun start(prefs: Prefs) {
        // Idempotent: the client is process-scoped now, so a recreated Activity
        // (rotation, layout switch) calls start() again — don't cycle the socket.
        if (started) { this.prefs = prefs; return }
        started = true
        this.prefs = prefs
        if (prefs.hasSession) {
            token = prefs.token
            baseUrl = prefs.serverUrl!!.trimEnd('/')
            post { status = "Connecting…"; loggedIn = true }
            io.execute {
                fetchNetworkNames()
                openSocket(null)
            }
            loadSettings() // chat rendering (font size) reads the registry
        }
    }

    /**
     * POST /api/auth/login/token — password in, bearer session token out. Blocking;
     * the caller runs it off the main thread.
     */
    fun login(rawBase: String, username: String, password: String) {
        val base = rawBase.trim().trimEnd('/')
        baseUrl = base
        post { status = "Signing in…" }
        try {
            val body = JSONObject()
                .put("username", username)
                .put("password", password)
                .toString()
                .toRequestBody(json)
            val req = Request.Builder().url("$base/api/auth/login/token").post(body).build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    post { status = "Sign-in failed (HTTP ${res.code})" }
                    return
                }
                token = JSONObject(res.body?.string().orEmpty()).getString("token")
            }
            prefs?.saveSession(base, username, token!!)
            fetchNetworkNames()
            post {
                status = "Connecting…"
                loggedIn = true
            }
            openSocket(null)
            loadSettings() // chat rendering (font size) reads the registry
        } catch (e: Exception) {
            post { status = "Sign-in failed: ${e.message}" }
        }
    }

    /** Tear down the session: close the socket, forget the token, reset state. */
    fun signOut() {
        intentionalClose = true
        started = false
        ws?.close(1000, "sign out")
        ws = null
        io.execute { revokeSession() }
        prefs?.clearSession()
        token = null
        maxMessageId = 0
        post {
            loggedIn = false
            connected = false
            status = null
            networks.clear()
            buffers.clear()
            messagesByBuffer.clear()
            unread.clear()
            highlights.clear()
            lastRead.clear()
            dividerAfter.clear()
            members.clear()
            typing.clear()
            hasMoreOlder.clear()
            loadingOlder.clear()
            pendingSends.clear()
            transfers.clear()
            drafts.clear()
            inputHistory.clear()
            aliases.clear()
            ignores.clear()
            pendingOpen = null
            pendingJoin.clear()
            serverExtended = false
            bookmarkIds.clear()
            contacts.clear()
            presence.clear()
            pins.clear()
            nickNotes.clear()
            notifyAlways.clear()
            chanlistRows.clear()
            searchResults.clear()
            highlightItems.clear()
            settingsRegistry.clear()
            settingsValues.clear()
            settingsLoaded = false
            // The client is process-scoped, so sign-out→sign-in happens without
            // process death — none of this transient state may leak into the next
            // account. Cancel the reconnect timer so it can't fire against a fresh
            // login, and reset the connection/paging/UI flags to their defaults.
            cancelScheduledReconnect()
            connecting = false
            activeKey = null
            activeBuffer = null
            markReadQueued = false
            chanlistLoading = false
            chanlistInProgress = false
            chanlistTotalCount = 0
            chanlistKey = ""
            chanlistNetworkId = -1
            dccEnabled = true
            dccError = null
            networkConfigs.clear()
            networksError = null
            networkNames.clear()
            searchLoading = false
            searchHasMore = false
            searchError = null
            searchNextBefore = null
            lastSearchRaw = ""
            highlightsLoading = false
            highlightsNextBefore = null
            highlightsHasMore = false
            settingsError = null
            draftPending.clear()
            draftFlush.values.forEach(main::removeCallbacks)
            draftFlush.clear()
        }
    }

    private fun revokeSession() {
        val t = token ?: return
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/auth/logout")
                .header("Authorization", "Bearer $t")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            http.newCall(req).execute().close()
        } catch (_: Exception) {
            // Best-effort; the local token is already gone either way.
        }
    }

    /**
     * Called from the Activity's ON_RESUME. The hard part: after a long
     * background, the socket is often DEAD but nothing has told us yet — the
     * process was frozen when the NAT mapping / server reaped it, so `connected`
     * still reads true and no callback ever fired. Waiting for the next ping
     * cycle to notice reads as "spotty for a minute after opening the app".
     *
     * So: reconnect NOW (reset backoff, cancel any pending timer) whenever we're
     * not connected, and *cycle proactively* even when we look connected if the
     * app was backgrounded for a while and the socket has been silent — a
     * `?since=` resume is cheap, a zombie socket is not. A healthy socket just
     * gets a presence nudge.
     */
    fun onForeground() {
        appForeground = true
        if (!loggedIn || token == null) return
        val now = System.currentTimeMillis()
        // Deterministic by design: any real background trip cycles the socket —
        // no liveness guessing, no zombie-socket class of bugs. ?since= resume
        // makes it cheap, and superseded-socket guards keep the UI steady. Only
        // sub-3s task-switcher hops skip the churn (if their socket died, the
        // normal failure path still reconnects instantly).
        val quickHop = wentBackgroundAt > 0 && now - wentBackgroundAt < BACKGROUND_CYCLE_MS
        wentBackgroundAt = 0
        if (!connected || !quickHop) {
            backoffMs = INITIAL_BACKOFF
            cancelScheduledReconnect()
            connecting = false
            openSocket(maxMessageId.takeIf { it > 0 })
        } else {
            // send() returning false = the socket is dead after all; onFailure
            // follows and the normal reconnect path takes over immediately.
            ws?.send(presenceFrame(true))
        }
    }

    /** Called from the Activity's ON_STOP — remember when we left. */
    fun onBackground() {
        appForeground = false
        wentBackgroundAt = System.currentTimeMillis()
        activeBuffer?.let { flushDraft(it) } // don't lose an unsynced draft
        ws?.send(presenceFrame(false))
    }

    private fun presenceFrame(visible: Boolean): String =
        JSONObject().put("type", "presence").put("visible", visible).toString()

    // ---- REST helpers -----------------------------------------------------

    private fun authed(path: String): Request.Builder =
        Request.Builder().url("$baseUrl$path").header("Authorization", "Bearer ${token!!}")

    private fun fetchNetworkNames() {
        try {
            http.newCall(authed("/api/networks").build()).execute().use { res ->
                if (!res.isSuccessful) return
                val arr = JSONObject(res.body?.string().orEmpty()).optJSONArray("networks") ?: return
                val configs = mutableListOf<NetworkConfig>()
                for (i in 0 until arr.length()) {
                    val n = arr.getJSONObject(i)
                    val id = n.getInt("id")
                    val name = n.optString("name", "network")
                    networkNames[id] = name
                    val net = Network(
                        id = id,
                        name = name,
                        nick = n.optString("nick").ifEmpty { null },
                        connected = n.optBoolean("connected", n.optString("state") == "connected"),
                    )
                    post { networks[id] = net }
                    parseNetworkConfig(n)?.let(configs::add)
                }
                // The REST order IS the user's chosen sidebar order — the buffer
                // list sections follow it.
                post {
                    networkConfigs.clear()
                    networkConfigs.addAll(configs)
                }
            }
        } catch (_: Exception) {
            // Names are cosmetic; the buffer list still works without them.
        }
    }

    /** Position of a network (by display name) in the user's chosen order. */
    fun networkOrder(name: String): Int {
        val i = networkConfigs.indexOfFirst { it.name.equals(name, true) }
        return if (i >= 0) i else Int.MAX_VALUE
    }

    /** Persist a full network ordering (must contain every network id). */
    fun reorderNetworks(ids: List<Int>) = io.execute {
        try {
            val body = JSONObject().put("ids", org.json.JSONArray(ids)).toString().toRequestBody(json)
            http.newCall(authed("/api/networks/reorder").post(body).build()).execute().use { res ->
                if (!res.isSuccessful) {
                    post { networksError = "reorder failed (HTTP ${res.code})" }
                }
                // Already on the io thread; refreshes names, states, and order.
                fetchNetworkNames()
            }
        } catch (e: Exception) {
            post { networksError = "reorder failed: ${e.message}" }
        }
    }

    // ---- WebSocket --------------------------------------------------------

    private fun openSocket(since: Long?) {
        intentionalClose = false
        connecting = true
        ws?.cancel()
        // ?v= announces the protocol we speak (#569); the server 426s a client
        // older than its minimum instead of feeding it frames it would mis-render.
        val wsBase = baseUrl.replaceFirst("http", "ws") + "/ws?v=$PROTOCOL_VERSION"
        val wsUrl = if (since != null && since > 0) "$wsBase&since=$since" else wsBase
        val req = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer ${token!!}")
            .build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== ws) return // superseded socket — ignore
                android.util.Log.i("LurkerWS", "open")
                lastFrameAt = System.currentTimeMillis()
                webSocket.send(presenceFrame(true))
                post {
                    connected = true
                    connecting = false
                    status = null
                    backoffMs = INITIAL_BACKOFF
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== ws) return // superseded socket — ignore
                lastFrameAt = System.currentTimeMillis()
                val frame = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    return
                }
                post { handleFrame(frame) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // The cancel() from a cycle fires this on the OLD socket, often
                // AFTER the new one opened — acting on it flipped `connected`
                // false and double-booked reconnects (the reopen "flutter").
                if (webSocket !== ws) return
                android.util.Log.w("LurkerWS", "failure http=${response?.code} ${t.javaClass.simpleName}: ${t.message}")
                val code = response?.code
                post {
                    connected = false
                    connecting = false
                    // A 401 means the saved token is dead — don't spin on it.
                    if (code == 401) {
                        status = "Session expired — please sign in again."
                        forceSignOutLocally()
                    } else if (code == 426) {
                        // Protocol handshake refused: this build is older than the
                        // server's minimum. Retrying can't help — say so legibly.
                        status = "This app is too old for the server — please update the app."
                    } else {
                        status = if (code != null) "Reconnecting… (HTTP $code)" else "Reconnecting…"
                        scheduleReconnect()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== ws) return // superseded socket — ignore
                android.util.Log.w("LurkerWS", "closed code=$code reason=$reason")
                post {
                    connected = false
                    connecting = false
                    if (!intentionalClose) scheduleReconnect()
                }
            }
        })
    }

    private fun forceSignOutLocally() {
        prefs?.clearSession()
        token = null
        loggedIn = false
    }

    private fun scheduleReconnect() {
        if (intentionalClose || reconnectScheduled || token == null) return
        reconnectScheduled = true
        val delay = backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF)
        val runnable = Runnable {
            reconnectScheduled = false
            reconnectRunnable = null
            if (!intentionalClose && token != null) openSocket(maxMessageId.takeIf { it > 0 })
        }
        reconnectRunnable = runnable
        main.postDelayed(runnable, delay)
    }

    /** Drop a pending backoff timer so a foreground revival can connect NOW. */
    private fun cancelScheduledReconnect() {
        reconnectRunnable?.let(main::removeCallbacks)
        reconnectRunnable = null
        reconnectScheduled = false
    }

    // ---- Frame handling ---------------------------------------------------

    private fun handleFrame(frame: JSONObject) {
        when (frame.optString("kind")) {
            "snapshot" -> {
                bumpCursor(frame.optLong("cursor"))
                frame.optJSONArray("networks")?.let { applyNetworks(it) }
                if (frame.has("aliases")) {
                    serverExtended = true
                    applyAliases(frame.optJSONArray("aliases"))
                }
                if (frame.has("globalIgnores")) applyIgnoreScope(null, frame.optJSONArray("globalIgnores"))
            }

            "ignore-list-updated" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                applyIgnoreScope(networkId, frame.optJSONArray("masks"))
            }

            "backlog" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.getInt("networkId")
                val target = frame.optString("target")
                if (target.isEmpty()) return
                val buffer = ensureBuffer(networkId, target)
                val events = frame.optJSONArray("events")
                bumpCursorFromEvents(events)
                val reset = frame.optBoolean("reset", false)
                val parsed = parseEvents(events)
                mergeInto(buffer.key, parsed, replace = reset || messagesByBuffer[buffer.key] == null)
                seedUnread(buffer.key, frame)
                if (frame.has("lastReadId")) lastRead[buffer.key] = frame.optLong("lastReadId")
                if (frame.has("hasMoreOlder")) hasMoreOlder[buffer.key] = frame.optBoolean("hasMoreOlder")
                frame.optJSONArray("inputHistory")?.let { h ->
                    inputHistory[buffer.key] = (0 until h.length()).map { h.optString(it) }
                }
                // Reopening a buffer: once the fresh backlog hydrates, tell the
                // server we're caught up to what it just showed us.
                scheduleMarkRead(buffer.key)
            }

            "irc" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.getInt("networkId")
                // WHOIS replies are ephemeral and targetless (the web renders a
                // modal); we print them into the buffer they were issued from.
                if (frame.optString("type") == "whois_result") {
                    handleWhoisResult(frame)
                    return
                }
                val target = frame.optString("target")
                if (target.isEmpty()) return
                // Typing is ephemeral and must not create buffers — handle it
                // before ensureBuffer and bail.
                if (frame.optString("type") == "typing") {
                    applyTyping("${networkId ?: "sys"}::$target", networkId, frame)
                    return
                }
                // Friend presence is ephemeral (server pseudo-target) — record it
                // and bail before ensureBuffer so it never spawns a buffer.
                if (frame.optString("type") == "peer-presence") {
                    val nick = frame.optString("nick")
                    if (networkId != null && nick.isNotEmpty()) {
                        val k = "$networkId::${nick.lowercase()}"
                        if (frame.isNull("state")) presence.remove(k) else presence[k] = frame.optString("state")
                    }
                    return
                }
                // The authoritative "you actually joined this channel" signal (per
                // amiantos). Create the buffer here — NOT on buffer-opened — and, if
                // it fulfils a join the user just asked for, focus it. A 470 forward
                // makes the joined channel differ from the request, so drop the ghost
                // requested buffer. Reconnect re-joins (no recent pendingJoin) just
                // ensure the buffer exists without stealing focus.
                if (frame.optString("type") == "channel-joined") {
                    val joined = ensureBuffer(networkId, target)
                    val pend = networkId?.let { pendingJoin[it] }
                    if (networkId != null && pend != null &&
                        System.currentTimeMillis() - pend.second < JOIN_FOCUS_WINDOW_MS
                    ) {
                        pendingJoin.remove(networkId)
                        if (!target.equals(pend.first, true)) {
                            val ghost = "$networkId::${pend.first}"
                            buffers.removeAll { it.key == ghost }
                            messagesByBuffer.remove(ghost)
                            unread.remove(ghost)
                            highlights.remove(ghost)
                        }
                        pendingOpen = joined
                    }
                    return
                }
                // A part/channel-parted for a channel we don't hold an open buffer
                // for (a FAILED join, or the leg of a forward we bailed on) must NOT
                // spawn a ghost buffer via ensureBuffer below (amiantos). Drop it —
                // you can't leave a channel you were never in. (pendingJoin is left
                // to expire; a real channel-joined still focuses where we land.)
                val partType = frame.optString("type")
                if ((partType == "channel-parted" || partType == "part") &&
                    buffers.none { it.key == "${networkId ?: "sys"}::$target" }
                ) {
                    return
                }
                bumpCursor(frame.optLong("id"))
                val buffer = ensureBuffer(networkId, target)
                // Roster updates ride the same stream; apply before the renderable
                // check (names/channel-parted produce no chat line at all).
                applyMemberEvent(buffer.key, frame)
                val msg = parseEvent(frame) ?: return
                // A re-delivered message (e.g. the boundary id on a ?since= resume)
                // is deduped by mergeInto — don't re-count its badge or re-notify.
                if (!mergeInto(buffer.key, listOf(msg), replace = false)) return
                countUnread(buffer.key, frame, msg)
                if (msg.id > 0) scheduleMarkRead(buffer.key)
                if (shouldNotify(frame.optBoolean("notify", false), buffer.key == activeKey, msg.self, msg.system, msg.id > 0, appForeground)) {
                    notificationSink?.invoke(
                        NotifiableEvent(buffer.networkId, buffer.target, msg.nick, msg.text, frame.optBoolean("dm")),
                    )
                }
            }

            "read-state" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                val target = frame.optString("target")
                val key = "${networkId ?: "sys"}::$target"
                setBadge(unread, key, frame.optInt("unread", 0))
                setBadge(highlights, key, frame.optInt("highlights", 0))
                if (frame.has("lastReadId")) lastRead[key] = frame.optLong("lastReadId")
            }

            "buffer-reopened" -> {
                // A reopen of a previously-closed buffer is authoritative — recreate it.
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                val target = frame.optString("target")
                if (target.isNotEmpty()) ensureBuffer(networkId, target)
            }

            "buffer-opened" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                val target = frame.optString("target")
                if (target.isEmpty()) return
                // Per amiantos: buffer-opened is only a FOCUS HINT — sufficient where
                // nothing can fail (DMs, or a buffer we already have). A *new* channel
                // must NOT be created here: a 470 forward/redirect makes the joined
                // channel differ from the requested one, which is exactly what left the
                // ghost "#apple" buffer. New channels are created on channel-joined
                // (fresh join) or from the snapshot (already joined).
                val key = "${networkId ?: "sys"}::$target"
                if (buffers.any { it.key == key } || !Commands.isChannel(target)) {
                    ensureBuffer(networkId, target)
                }
            }

            "buffer-closed" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                val target = frame.optString("target")
                val key = "${networkId ?: "sys"}::$target"
                buffers.removeAll { it.key == key }
                messagesByBuffer.remove(key)
                unread.remove(key)
                highlights.remove(key)
            }

            "search-result" -> {
                if (frame.optInt("token", -1) != searchToken) return // superseded
                searchLoading = false
                val arr = frame.optJSONArray("results")
                val rows = ArrayList<SearchResult>()
                if (arr != null) for (i in 0 until arr.length()) {
                    parseResultRow(arr.optJSONObject(i) ?: continue)?.let(rows::add)
                }
                if (frame.isNull("before")) searchResults.clear()
                val known = searchResults.mapTo(HashSet()) { it.id }
                searchResults.addAll(rows.filter { it.id !in known })
                searchHasMore = frame.optBoolean("hasMore", false)
                searchNextBefore = if (searchHasMore) searchResults.lastOrNull()?.id else null
            }

            "draft-snapshot" -> {
                drafts.clear()
                frame.optJSONArray("drafts")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val d = arr.optJSONObject(i) ?: continue
                        val key = "${d.optInt("networkId")}::${d.optString("target")}"
                        val body = d.optString("body")
                        if (body.isNotEmpty()) drafts[key] = body
                    }
                }
            }
            "draft-updated" -> {
                val key = "${frame.optInt("networkId")}::${frame.optString("target")}"
                // Don't clobber an in-progress local edit with a remote echo.
                if (key !in draftPending) {
                    val body = frame.optString("body")
                    if (body.isEmpty()) drafts.remove(key) else drafts[key] = body
                }
            }
            "input-history-added" -> {
                val key = "${frame.optInt("networkId")}::${frame.optString("target")}"
                val text = frame.optString("text")
                if (text.isNotEmpty()) inputHistory[key] = (inputHistory[key] ?: emptyList()) + text
            }
            "alias-list-updated" -> applyAliases(frame.optJSONArray("aliases"))

            "pins-changed" -> {
                val networkId = frame.optInt("networkId", -1)
                if (networkId >= 0) {
                    val p = frame.optJSONArray("pinned")
                    pins[networkId] = if (p == null) emptyList() else (0 until p.length()).map { p.optString(it) }
                }
            }
            "channel-notify-changed" -> {
                val key = "${frame.optInt("networkId")}::${frame.optString("target")}"
                if (frame.optBoolean("notifyAlways")) notifyAlways[key] = true else notifyAlways.remove(key)
            }
            "nick-note-updated" -> {
                val key = "${frame.optInt("networkId")}::${frame.optString("nick").lowercase()}"
                val note = frame.optString("note")
                if (note.isEmpty()) nickNotes.remove(key) else nickNotes[key] = note
            }

            "bookmark-ids-snapshot" -> {
                bookmarkIds.clear()
                frame.optJSONArray("ids")?.let { a ->
                    for (i in 0 until a.length()) bookmarkIds.add(a.optLong(i))
                }
            }
            "bookmark-updated" -> {
                val id = frame.optLong("messageId")
                if (frame.optBoolean("saved")) bookmarkIds.add(id) else bookmarkIds.remove(id)
            }

            "contacts-snapshot" -> {
                val arr = frame.optJSONArray("contacts")
                contacts.clear()
                if (arr != null) for (i in 0 until arr.length()) {
                    parseContact(arr.optJSONObject(i))?.let(contacts::add)
                }
            }
            "contact-updated" -> parseContact(frame.optJSONObject("contact"))?.let { c ->
                val i = contacts.indexOfFirst { it.id == c.id }
                if (i >= 0) contacts[i] = c else contacts.add(c)
            }
            "contact-deleted" -> {
                val id = frame.optInt("contactId", -1)
                contacts.removeAll { it.id == id }
            }

            "chanlist-result" -> {
                val networkId = frame.optInt("networkId", -1)
                val key = "${frame.optString("query")}|${frame.optString("sortBy")}|${frame.optString("sortDir")}"
                if (networkId != chanlistNetworkId || key != chanlistKey) return // superseded
                chanlistLoading = false
                chanlistInProgress = frame.optBoolean("inProgress", false)
                chanlistTotalCount = frame.optInt("totalCount", 0)
                val rows = frame.optJSONArray("rows")
                val parsed = ArrayList<ChannelListing>()
                if (rows != null) for (i in 0 until rows.length()) {
                    val r = rows.optJSONObject(i) ?: continue
                    parsed.add(ChannelListing(r.optString("channel"), r.optInt("num_users"), r.optString("topic")))
                }
                if (frame.optInt("offset", 0) == 0) chanlistRows.clear()
                val known = chanlistRows.mapTo(HashSet()) { it.channel }
                chanlistRows.addAll(parsed.filter { it.channel !in known })
            }

            "dcc-transfer" -> frame.optJSONObject("transfer")?.let { applyTransfer(it) }

            "send-result" -> {
                val clientId = frame.optString("clientId")
                if (frame.optBoolean("ok", false)) {
                    pendingSends.remove(clientId)
                } else {
                    failSend(clientId, frame.optString("error").ifEmpty { "rejected by server" })
                }
            }

            // Reply to a history(before) page: prepend older events, dedup by id.
            "history" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                val target = frame.optString("target")
                if (target.isEmpty()) return
                val key = "${networkId ?: "sys"}::$target"
                loadingOlder.remove(key)
                if (frame.has("hasMoreOlder")) hasMoreOlder[key] = frame.optBoolean("hasMoreOlder")
                val parsed = parseEvents(frame.optJSONArray("events"))
                if (parsed.isEmpty()) return
                val existing = messagesByBuffer[key] ?: emptyList()
                val known = existing.mapNotNull { m -> m.id.takeIf { it > 0 } }.toHashSet()
                val older = parsed.filter { it.id <= 0 || it.id !in known }
                if (older.isNotEmpty()) messagesByBuffer[key] = ensureOrdered(older + existing)
            }

            "error" -> status = frame.optString("text")
        }
    }

    /** Ask for the page of history older than what's currently loaded. */
    fun loadOlder(buffer: Buffer) {
        val networkId = buffer.networkId ?: return
        val key = buffer.key
        if (loadingOlder[key] == true) return
        val oldest = messagesByBuffer[key]?.firstOrNull { it.id > 0 }?.id ?: return
        loadingOlder[key] = true
        ws?.send(
            JSONObject()
                .put("type", "history")
                .put("networkId", networkId)
                .put("target", buffer.target)
                .put("mode", "before")
                .put("before", oldest)
                .put("limit", 100)
                .toString(),
        )
    }

    private fun failSend(clientId: String, reason: String) {
        val pending = pendingSends.remove(clientId) ?: return
        val preview = pending.text.let { if (it.length > 80) it.take(77) + "…" else it }
        mergeInto(
            pending.bufferKey,
            listOf(
                Msg(
                    id = 0, type = "send-failed", nick = "",
                    text = "⚠ Not delivered ($reason): “$preview”",
                    self = false, system = true,
                ),
            ),
            replace = false,
        )
    }

    private fun applyNetworks(arr: JSONArray) {
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            // The snapshot blob keys the network as `networkId` (REST uses `id`).
            val id = n.optInt("networkId", n.optInt("id", -1))
            if (id < 0) continue
            val name = n.optString("name").ifEmpty { networkNames[id] ?: "network" }
            networkNames[id] = name
            networks[id] = Network(
                id = id,
                name = name,
                nick = n.optString("nick").ifEmpty { null },
                connected = n.optBoolean("connected", n.optString("state") == "connected"),
            )
            // Per-network pinned buffers, in the user's chosen order.
            n.optJSONArray("pinned")?.let { p ->
                pins[id] = (0 until p.length()).map { p.optString(it) }
            }
            // Nick notes: {nick, note} rows keyed by (networkId, nick).
            n.optJSONArray("nickNotes")?.let { notes ->
                for (i in 0 until notes.length()) {
                    val nt = notes.optJSONObject(i) ?: continue
                    val nk = nt.optString("nick"); val body = nt.optString("note")
                    if (nk.isNotEmpty() && body.isNotEmpty()) nickNotes["$id::${nk.lowercase()}"] = body
                }
            }
            // channelNotify: { "#target": { notifyAlways } }.
            n.optJSONObject("channelNotify")?.let { cn ->
                for (t in cn.keys()) {
                    if (cn.optJSONObject(t)?.optBoolean("notifyAlways") == true) notifyAlways["$id::$t"] = true
                }
            }
            // The snapshot carries each channel's member roster inline.
            n.optJSONArray("channels")?.let { chans ->
                for (c in 0 until chans.length()) {
                    val ch = chans.optJSONObject(c) ?: continue
                    val chName = ch.optString("name")
                    val roster = ch.optJSONArray("members") ?: continue
                    if (chName.isNotEmpty()) members["$id::$chName"] = parseMembers(roster)
                }
            }
        }
    }

    // ---- Channel members ----------------------------------------------------

    private fun parseMembers(arr: JSONArray): List<Member> =
        (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val nick = m.optString("nick")
            if (nick.isEmpty()) return@mapNotNull null
            Member(
                nick = nick,
                modes = m.optJSONArray("modes")?.let { md ->
                    (0 until md.length()).map { md.optString(it) }
                } ?: emptyList(),
                away = m.optBoolean("away", false),
                host = m.optString("host").ifEmpty { null },
            )
        }

    /**
     * Keep per-buffer rosters current. `names` frames replace wholesale; the
     * join/part/quit/kick/nick stream is applied incrementally (the server does
     * NOT re-send names for those — mirrors the web client's store).
     */
    private fun applyMemberEvent(key: String, frame: JSONObject) {
        when (frame.optString("type")) {
            "names" -> frame.optJSONArray("members")?.let { members[key] = parseMembers(it) }
            "join" -> {
                val nick = frame.optString("nick")
                val cur = members[key] ?: return
                if (nick.isNotEmpty() && cur.none { it.nick.equals(nick, true) }) {
                    members[key] = cur + Member(nick)
                }
            }
            "part", "quit" -> removeMember(key, frame.optString("nick"))
            "kick" -> removeMember(key, frame.optString("kicked"))
            "nick" -> {
                val to = frame.optString("newNick")
                val from = frame.optString("nick")
                val cur = members[key] ?: return
                if (to.isEmpty()) return
                members[key] = cur.map { if (it.nick.equals(from, true)) it.copy(nick = to) else it }
            }
            "channel-parted" -> members.remove(key)
        }
    }

    // ---- Typing indicators --------------------------------------------------

    /** Apply an inbound typing event: active adds (with TTL), paused/done remove. */
    private fun applyTyping(key: String, networkId: Int?, frame: JSONObject) {
        val nick = frame.optString("nick")
        if (nick.isEmpty()) return
        // Never show our own echo.
        if (networkId != null && networks[networkId]?.nick?.equals(nick, true) == true) return
        val cur = typing[key].orEmpty().toMutableMap()
        if (frame.optString("state") == "active") {
            cur[nick] = System.currentTimeMillis() + TYPING_TTL
            main.postDelayed({ sweepTyping(key) }, TYPING_TTL + 250)
        } else {
            cur.remove(nick)
        }
        if (cur.isEmpty()) typing.remove(key) else typing[key] = cur
    }

    /** Drop typers whose `active` refresh stopped arriving (they went quiet). */
    private fun sweepTyping(key: String) {
        val now = System.currentTimeMillis()
        val cur = typing[key] ?: return
        val alive = cur.filterValues { it > now }
        if (alive.size != cur.size) {
            if (alive.isEmpty()) typing.remove(key) else typing[key] = alive
        }
    }

    private val typingSentAt = mutableMapOf<String, Long>()

    /**
     * Broadcast that we're composing in [buffer] (throttled to every 3s, the
     * IRCv3 cadence). Gated by chat.send_typing_notifications; commands are
     * never broadcast.
     */
    fun notifyTyping(buffer: Buffer, active: Boolean) {
        val networkId = buffer.networkId ?: return
        if (!settingBool("chat.send_typing_notifications", true)) return
        val now = System.currentTimeMillis()
        if (active && now - (typingSentAt[buffer.key] ?: 0) < 3_000) return
        typingSentAt[buffer.key] = if (active) now else 0
        ws?.send(
            JSONObject()
                .put("type", "typing")
                .put("networkId", networkId)
                .put("target", buffer.target)
                .put("state", if (active) "active" else "done")
                .toString(),
        )
    }

    /** Effective bool value of a registry setting: override ?? default ?? fallback. */
    fun settingBool(key: String, fallback: Boolean): Boolean {
        (settingsValues[key] as? Boolean)?.let { return it }
        (settingsRegistry.firstOrNull { it.key == key }?.default as? Boolean)?.let { return it }
        return fallback
    }

    /** Render a whois_result into the currently focused buffer as a block. */
    private fun handleWhoisResult(frame: JSONObject) {
        val key = activeKey ?: return
        val w = frame.optJSONObject("whois") ?: return
        val msg = Msg(
            id = 0,
            type = "whois",
            nick = "",
            text = formatWhois(w),
            self = false,
            time = frame.optString("time").ifEmpty { null },
            system = true,
        )
        mergeInto(key, listOf(msg), replace = false)
    }

    private fun formatWhois(w: JSONObject): String = buildString {
        val nick = w.optString("nick", "?")
        if (w.optString("error") == "not_found") {
            append("WHOIS $nick — no such nick")
            return@buildString
        }
        append("WHOIS $nick")
        val ident = w.optString("ident")
        val host = w.optString("hostname")
        if (ident.isNotEmpty() || host.isNotEmpty()) append(" ($ident@$host)")
        w.optString("real_name").takeIf { it.isNotEmpty() }?.let { append("\nname      $it") }
        w.optString("account").takeIf { it.isNotEmpty() }?.let { append("\naccount   $it") }
        w.optString("server").takeIf { it.isNotEmpty() }?.let {
            append("\nserver    $it")
            w.optString("server_info").takeIf { s -> s.isNotEmpty() }?.let { s -> append(" ($s)") }
        }
        w.optString("channels").takeIf { it.isNotEmpty() }?.let { append("\nchannels  $it") }
        val idle = w.optLong("idle", -1)
        if (idle >= 0) append("\nidle      ${formatIdle(idle)}")
        w.optString("away").takeIf { it.isNotEmpty() }?.let { append("\naway      $it") }
        val actual = listOf(w.optString("actual_hostname"), w.optString("actual_ip"))
            .filter { it.isNotEmpty() }.distinct()
        if (actual.isNotEmpty()) append("\nactually  ${actual.joinToString(" ")}")
        if (w.optBoolean("secure")) append("\nsecure connection")
        if (w.optBoolean("operator")) append("\nIRC operator")
    }

    private fun formatIdle(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    private fun removeMember(key: String, nick: String) {
        if (nick.isEmpty()) return
        members[key]?.let { cur -> members[key] = cur.filterNot { it.nick.equals(nick, true) } }
    }

    /** The signed-in user's own membership row in [buffer], for op-gating menus. */
    fun myMember(buffer: Buffer): Member? {
        val nick = buffer.networkId?.let { networks[it]?.nick } ?: return null
        return members[buffer.key]?.firstOrNull { it.nick.equals(nick, true) }
    }

    private fun bumpCursor(id: Long) {
        if (id > maxMessageId) maxMessageId = id
    }

    private fun bumpCursorFromEvents(arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            bumpCursor(arr.optJSONObject(i)?.optLong("id") ?: 0L)
        }
    }

    private fun ensureBuffer(networkId: Int?, target: String): Buffer {
        val key = "${networkId ?: "sys"}::$target"
        buffers.firstOrNull { it.key == key }?.let { return it }
        val buffer = Buffer(
            networkId = networkId,
            target = target,
            networkName = networkId?.let { networkNames[it] } ?: "system",
        )
        buffers.add(buffer)
        return buffer
    }

    /** Returns whether anything was actually added (false = every id was a dup),
     *  so the live path can avoid double-counting a re-delivered message. */
    private fun mergeInto(key: String, newMsgs: List<Msg>, replace: Boolean): Boolean {
        if (replace) {
            messagesByBuffer[key] = newMsgs
            return true
        }
        val existing = messagesByBuffer[key] ?: emptyList()
        if (existing.isEmpty()) {
            messagesByBuffer[key] = newMsgs
            return true
        }
        val ids = existing.mapNotNull { if (it.id > 0) it.id else null }.toHashSet()
        val add = newMsgs.filter { it.id <= 0 || ids.add(it.id) }
        if (add.isEmpty()) return false
        messagesByBuffer[key] = ensureOrdered(existing + add)
        return true
    }

    private fun ensureOrdered(msgs: List<Msg>): List<Msg> = orderMessagesById(msgs)

    // ---- Unread accounting ------------------------------------------------

    private fun seedUnread(key: String, frame: JSONObject) {
        if (key == activeKey) return
        if (frame.has("unread")) setBadge(unread, key, frame.optInt("unread", 0))
        if (frame.has("highlights")) setBadge(highlights, key, frame.optInt("highlights", 0))
    }

    private fun countUnread(key: String, frame: JSONObject, msg: Msg) {
        if (key == activeKey || msg.self || msg.system) return
        if (msg.type !in COUNTABLE) return
        unread[key] = (unread[key] ?: 0) + 1
        if (frame.optBoolean("matched", false) || frame.optBoolean("dm", false)) {
            highlights[key] = (highlights[key] ?: 0) + 1
        }
    }

    private fun setBadge(map: MutableMap<String, Int>, key: String, value: Int) {
        if (value <= 0) map.remove(key) else map[key] = value
    }

    /** Mark a buffer focused: clears its badge and tells the server we've read it. */
    fun setActive(buffer: Buffer?) {
        val prev = activeKey
        activeKey = buffer?.key
        activeBuffer = buffer
        if (prev != null && prev != buffer?.key) dividerAfter.remove(prev)
        val key = buffer?.key ?: return
        // Anchor the New-messages divider before the badge and cursor move.
        val cursor = lastRead[key] ?: 0L
        if ((unread[key] ?: 0) > 0 && cursor > 0) dividerAfter[key] = cursor
        unread.remove(key)
        highlights.remove(key)
        markRead(buffer)
    }

    private var activeBuffer: Buffer? = null
    private var markReadQueued = false

    /**
     * Keep the server's read cursor at our heels while a buffer is focused:
     * every persisted event that lands in the active buffer (fresh backlog on
     * reopen included) queues a mark-read, batched to at most one every 1.5s so
     * a busy channel doesn't chatter the socket. Without this, only the moment
     * of opening was marked — everything watched afterwards stayed "unread" on
     * the server and every other device.
     */
    private fun scheduleMarkRead(key: String) {
        if (key != activeKey || markReadQueued) return
        markReadQueued = true
        main.postDelayed({
            markReadQueued = false
            activeBuffer?.takeIf { it.key == activeKey }?.let(::markRead)
        }, 1_500)
    }

    // ---- Drafts + input history --------------------------------------------

    /** Update a buffer's draft locally and debounce-sync it (500ms, like web). */
    fun setDraftLocal(buffer: Buffer, text: String) {
        val networkId = buffer.networkId ?: return
        val key = buffer.key
        if (text.isEmpty()) drafts.remove(key) else drafts[key] = text
        draftPending.add(key)
        draftFlush.remove(key)?.let(main::removeCallbacks)
        val r = Runnable { flushDraft(buffer) }
        draftFlush[key] = r
        main.postDelayed(r, 500)
    }

    /** Send the pending draft now (called on buffer switch, background, send). */
    fun flushDraft(buffer: Buffer) {
        val networkId = buffer.networkId ?: return
        val key = buffer.key
        draftFlush.remove(key)?.let(main::removeCallbacks)
        if (key !in draftPending) return
        draftPending.remove(key)
        val body = drafts[key].orEmpty()
        ws?.send(
            JSONObject().put("networkId", networkId).put("target", buffer.target).apply {
                if (body.isEmpty()) put("type", "draft-clear")
                else put("type", "draft-set").put("body", body)
            }.toString(),
        )
    }

    /** Record a submitted line in the per-buffer history (server-synced + local). */
    fun addInputHistory(buffer: Buffer, text: String) {
        val networkId = buffer.networkId ?: return
        if (text.isBlank()) return
        inputHistory[buffer.key] = (inputHistory[buffer.key] ?: emptyList()) + text
        ws?.send(
            JSONObject().put("type", "input-history-add")
                .put("networkId", networkId).put("target", buffer.target).put("text", text)
                .toString(),
        )
    }

    // ---- Aliases (fork-only) ------------------------------------------------

    private fun applyAliases(arr: JSONArray?) {
        if (arr == null) return
        val list = (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            AliasEntry(a.optInt("id"), a.optString("name"), a.optString("expansion"))
        }
        aliases.clear(); aliases.addAll(list)
    }

    fun addAlias(name: String, expansion: String) {
        ws?.send(JSONObject().put("type", "add-alias").put("name", name).put("expansion", expansion).toString())
    }

    private fun parseContact(o: JSONObject?): Contact? {
        if (o == null) return null
        val targetsArr = o.optJSONArray("targets")
        val targets = if (targetsArr == null) emptyList() else (0 until targetsArr.length()).mapNotNull { i ->
            val t = targetsArr.optJSONObject(i) ?: return@mapNotNull null
            ContactTarget(t.optInt("networkId"), t.optString("nick"), t.optBoolean("isPrimary"))
        }
        return Contact(o.optInt("id"), o.optString("displayName"), o.optBoolean("notifyOnline", true), targets)
    }

    /** Presence state for a contact target, or null if unknown. */
    fun presenceOf(t: ContactTarget): String? = presence["${t.networkId}::${t.nick.lowercase()}"]

    /** Create (contactId=null) or update a contact, then let the server echo it back. */
    fun setContact(contactId: Int?, displayName: String, notifyOnline: Boolean, targets: List<ContactTarget>) {
        val arr = JSONArray()
        targets.forEach { arr.put(JSONObject().put("networkId", it.networkId).put("nick", it.nick).put("isPrimary", it.isPrimary)) }
        val msg = JSONObject().put("type", "set-contact").put("displayName", displayName)
            .put("notifyOnline", notifyOnline).put("targets", arr)
        if (contactId != null) msg.put("contactId", contactId)
        ws?.send(msg.toString())
    }

    fun deleteContact(contactId: Int) {
        contacts.removeAll { it.id == contactId }
        ws?.send(JSONObject().put("type", "delete-contact").put("contactId", contactId).toString())
    }

    /** Ask the server to refresh a nick's presence (e.g. when opening their DM). */
    fun probePresence(networkId: Int, nick: String) {
        ws?.send(JSONObject().put("type", "probe-presence").put("networkId", networkId).put("nick", nick).toString())
    }

    fun removeAlias(id: Int) {
        ws?.send(JSONObject().put("type", "remove-alias").put("id", id).toString())
    }

    private fun markRead(buffer: Buffer) {
        val networkId = buffer.networkId ?: return
        val lastId = messagesByBuffer[buffer.key]?.lastOrNull { it.id > 0 }?.id ?: return
        ws?.send(
            JSONObject()
                .put("type", "mark-read")
                .put("networkId", networkId)
                .put("target", buffer.target)
                .put("messageId", lastId)
                .toString(),
        )
    }

    // ---- Event parsing ----------------------------------------------------

    private fun parseEvents(arr: JSONArray?): List<Msg> {
        if (arr == null) return emptyList()
        val out = ArrayList<Msg>(arr.length())
        for (i in 0 until arr.length()) {
            parseEvent(arr.getJSONObject(i))?.let(out::add)
        }
        return out
    }

    private fun parseEvent(e: JSONObject): Msg? {
        // The server flags ignored-sender lines; we drop them (client-filtered).
        if (e.optBoolean("fromIgnored", false)) return null
        val type = e.optString("type")
        val id = e.optLong("id")
        val nick = e.optString("nick", "*")
        val text = e.optString("text")
        // Automated TAGMSG/BATCH "unknown command" bounces: a typing notification
        // (client-only tag carried over TAGMSG) that a network without the
        // message-tags cap rejects with 421. The user never typed it, so drop the
        // bounce instead of showing a red error line. Structured field first, with
        // a text fallback for servers that don't tag it ("unknown_command TAGMSG …").
        val unknownCmd = e.optString("unknownCommand")
        if (unknownCmd == "TAGMSG" || unknownCmd == "BATCH") return null
        if (type == "error" && text.startsWith("unknown_command ") &&
            (text.contains("TAGMSG") || text.contains("BATCH"))
        ) {
            return null
        }
        return when (type) {
            "message", "action", "notice", "error" -> Msg(
                id = id, type = type, nick = nick, text = text,
                self = e.optBoolean("self", false), time = e.optString("time").ifEmpty { null },
                // Persisted flag: the message rode E2E (server already decrypted it).
                e2e = e.optBoolean("e2e", false),
            )
            // Ephemeral E2E status/handshake line: rendered as a tagged system
            // line (green info / red warn), never persisted. No crypto involved.
            "e2e" -> Msg(
                id = id, type = "e2e", nick = "", text = text,
                self = false, time = e.optString("time").ifEmpty { null },
                system = true,
                level = e.optString("level").ifEmpty { "info" },
            )
            // The :system: buffer's log lines (#355): type 'system', nick null,
            // with level/scope/source riding alongside.
            "system" -> Msg(
                id = id, type = "system", nick = "",
                text = buildString {
                    val source = e.optString("source")
                    if (source.isNotEmpty()) append("[").append(source).append("] ")
                    append(text)
                },
                self = false, time = e.optString("time").ifEmpty { null },
                system = true,
                level = e.optString("level").ifEmpty { null },
            )
            in SYSTEM_TYPES -> Msg(
                id = id, type = type, nick = "", text = systemLine(type, nick, text, e),
                self = e.optBoolean("self", false), time = e.optString("time").ifEmpty { null },
                system = true,
            )
            else -> null // ephemeral (names/typing/state/…) — nothing to render inline
        }
    }

    /** Best-effort one-line rendering of a structural IRC event. */
    private fun systemLine(type: String, nick: String, text: String, e: JSONObject): String {
        val reason = text.takeIf { it.isNotBlank() }
        return when (type) {
            "join" -> "→ $nick joined"
            "part" -> "← $nick left" + (reason?.let { " ($it)" } ?: "")
            "quit" -> "⇐ $nick quit" + (reason?.let { " ($it)" } ?: "")
            "kick" -> {
                val who = e.optString("target").ifEmpty { e.optString("victim") }
                "$nick kicked ${who.ifEmpty { "someone" }}" + (reason?.let { " ($it)" } ?: "")
            }
            "nick" -> {
                val to = e.optString("newNick").ifEmpty { text }
                "$nick is now known as $to"
            }
            "mode" -> "$nick set mode" + (reason?.let { " $it" } ?: "")
            "topic" -> if (reason != null) "$nick set the topic: $reason" else "$nick cleared the topic"
            "invite" -> "$nick invited you" + (reason?.let { " to $it" } ?: "")
            else -> reason ?: type
        }
    }

    // ---- Sending ----------------------------------------------------------

    /** Hydrate a buffer's history on open (shells arrive empty). */
    fun open(buffer: Buffer) {
        val networkId = buffer.networkId ?: return
        ws?.send(
            JSONObject()
                .put("type", "open-buffer")
                .put("networkId", networkId)
                .put("target", buffer.target)
                .toString(),
        )
        // Self-healing roster: the server only pushes `names` on join/NAMES/WHO,
        // so if we somehow have no roster for a joined channel, ask for one.
        if (buffer.isChannel && members[buffer.key].isNullOrEmpty()) {
            ws?.send(
                JSONObject()
                    .put("type", "raw")
                    .put("networkId", networkId)
                    .put("line", "NAMES ${buffer.target}")
                    .toString(),
            )
        }
    }

    /** Ensure a DM/channel buffer exists, hydrate it, and return it for focusing. */
    fun focusTarget(networkId: Int, target: String): Buffer {
        val buffer = ensureBuffer(networkId, target)
        open(buffer)
        return buffer
    }

    /** Append a locally-generated line (command help / usage errors) to a buffer. */
    fun localNotice(buffer: Buffer, text: String) {
        mergeInto(
            buffer.key,
            listOf(Msg(id = 0, type = "notice", nick = "*", text = text, self = false, system = true)),
            replace = false,
        )
    }

    /** Execute the parsed composer input against [buffer]. */
    /** Record a user-initiated join so channel-joined can focus the channel we
     *  actually land in (a 470 forward can rename it) and drop the ghost request. */
    private fun noteJoinRequest(networkId: Int, channel: String) {
        pendingJoin[networkId] = channel to System.currentTimeMillis()
    }

    /** Join a channel WITHOUT optimistically creating its buffer (per amiantos —
     *  channels are pending until channel-joined). The transient Buffer only
     *  carries the networkId to execute; it's never added to the buffer list. */
    fun join(networkId: Int, channel: String) {
        execute(
            Buffer(networkId, channel, networks[networkId]?.name ?: ""),
            listOf(WireOp("join", channel = channel)),
        )
    }

    fun execute(buffer: Buffer, ops: List<WireOp>) {
        val networkId = buffer.networkId ?: return
        val socket = ws ?: return
        for (op in ops) {
            val target = op.target ?: buffer.target
            val frame = JSONObject().put("networkId", networkId)
            when (op.type) {
                "send", "action", "notice" -> {
                    frame.put("type", op.type).put("target", target).put("text", op.text)
                    // Correlate with the send-result ack so a rejected or lost
                    // message is surfaced instead of silently vanishing.
                    val clientId = "a${++clientIdSeq}"
                    frame.put("clientId", clientId)
                    val key = "${networkId}::$target"
                    pendingSends[clientId] = PendingSend(key, op.text.orEmpty())
                    main.postDelayed({ failSend(clientId, "no acknowledgement (timeout)") }, SEND_ACK_TIMEOUT)
                }
                "raw" -> frame.put("type", "raw").put("line", op.line)
                // {type:'e2e', networkId, target, args} — server runs the subcommand.
                "e2e" -> frame.put("type", "e2e").put("target", target).put("args", op.line.orEmpty())
                "join" -> {
                    frame.put("type", "join").put("channel", op.channel)
                    op.channel?.let { ch ->
                        val existing = buffers.firstOrNull { it.key == "$networkId::$ch" }
                        // Already joined: just focus it. Otherwise note the request so
                        // channel-joined focuses the channel we actually land in (a 470
                        // forward can rename it) — the buffer isn't created until then.
                        if (existing != null) pendingOpen = existing else noteJoinRequest(networkId, ch)
                    }
                }
                "part" -> {
                    frame.put("type", "part").put("channel", op.channel)
                    op.reason?.let { frame.put("reason", it) }
                }
                "close" -> {
                    frame.put("type", "close-buffer").put("target", target)
                    op.reason?.let { frame.put("reason", it) } // /part's leave message
                }
                "clear" -> frame.put("type", "clear-buffer").put("target", target)
                else -> continue
            }
            socket.send(frame.toString())
        }
    }

    // ---- Uploads ------------------------------------------------------------

    var uploading by mutableStateOf(false)

    /**
     * Upload a file through the account's configured provider (Zipline/Chibisafe/
     * local/…) and hand back its public URL. Multipart field is `image` for
     * historical reasons — any file type goes through it.
     */
    fun uploadFile(filename: String, fileBody: okhttp3.RequestBody, onDone: (String?, String?) -> Unit) {
        post { uploading = true }
        io.execute {
            try {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("image", filename, fileBody)
                    .build()
                http.newCall(authed("/api/uploads").post(body).build()).execute().use { res ->
                    val text = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        val err = runCatching { JSONObject(text).optString("error") }.getOrNull()
                        post { uploading = false; onDone(null, err?.ifEmpty { null } ?: "upload failed (HTTP ${res.code})") }
                        return@execute
                    }
                    val url = JSONObject(text).optString("url")
                    post { uploading = false; onDone(url.ifEmpty { null }, null) }
                }
            } catch (e: Exception) {
                post { uploading = false; onDone(null, "upload failed: ${e.message}") }
            }
        }
    }

    // ---- Network management (REST) ------------------------------------------

    val networkConfigs = mutableStateListOf<NetworkConfig>()
    var networksError by mutableStateOf<String?>(null)

    fun loadNetworkConfigs() = io.execute {
        try {
            http.newCall(authed("/api/networks").build()).execute().use { res ->
                if (!res.isSuccessful) {
                    post { networksError = "Failed to load networks (HTTP ${res.code})" }
                    return@execute
                }
                val arr = JSONObject(res.body?.string().orEmpty()).optJSONArray("networks")
                val parsed = mutableListOf<NetworkConfig>()
                if (arr != null) for (i in 0 until arr.length()) {
                    parseNetworkConfig(arr.optJSONObject(i) ?: continue)?.let(parsed::add)
                }
                post {
                    networksError = null
                    networkConfigs.clear()
                    networkConfigs.addAll(parsed)
                }
            }
        } catch (e: Exception) {
            post { networksError = "Failed to load networks: ${e.message}" }
        }
    }

    private fun parseNetworkConfig(o: JSONObject): NetworkConfig? {
        val id = o.optInt("id", -1)
        if (id < 0) return null
        return NetworkConfig(
            id = id,
            name = o.optString("name"),
            host = o.optString("host"),
            port = o.optInt("port", 6697),
            tls = o.optBoolean("tls", true),
            nick = o.optString("nick"),
            username = o.optString("username").ifEmpty { null },
            realname = o.optString("realname").ifEmpty { null },
            autoconnect = o.optBoolean("autoconnect", false),
            hasPassword = o.optBoolean("has_password", false),
            hasSaslPassword = o.optBoolean("has_sasl_password", false),
            saslAccount = o.optString("sasl_account").ifEmpty { null },
            blocked = o.optBoolean("blocked", false),
        )
    }

    /** Create (id == null) or update (id != null) a network; refreshes the list. */
    fun saveNetwork(id: Int?, fields: Map<String, Any?>, onDone: (String?) -> Unit) = io.execute {
        try {
            val payload = JSONObject()
            for ((k, v) in fields) if (v != null) payload.put(k, v)
            val body = payload.toString().toRequestBody(json)
            val req = if (id == null) {
                authed("/api/networks").post(body)
            } else {
                authed("/api/networks/$id").patch(body)
            }.build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    val err = runCatching {
                        JSONObject(res.body?.string().orEmpty()).optString("error")
                    }.getOrNull()
                    post { onDone(err?.ifEmpty { null } ?: "save failed (HTTP ${res.code})") }
                    return@execute
                }
                loadNetworkConfigs()
                post { onDone(null) }
            }
        } catch (e: Exception) {
            post { onDone("save failed: ${e.message}") }
        }
    }

    fun deleteNetwork(id: Int, onDone: (String?) -> Unit) = io.execute {
        try {
            http.newCall(authed("/api/networks/$id").delete().build()).execute().use { res ->
                if (!res.isSuccessful) {
                    post { onDone("delete failed (HTTP ${res.code})") }
                    return@execute
                }
                loadNetworkConfigs()
                post { onDone(null) }
            }
        } catch (e: Exception) {
            post { onDone("delete failed: ${e.message}") }
        }
    }

    /** connect | disconnect | reconnect. Live state lands via the WS snapshot. */
    fun networkAction(id: Int, action: String) = io.execute {
        try {
            http.newCall(
                authed("/api/networks/$id/$action").post(ByteArray(0).toRequestBody(null)).build(),
            ).execute().use { res ->
                if (!res.isSuccessful) {
                    post { networksError = "$action failed (HTTP ${res.code})" }
                }
            }
        } catch (e: Exception) {
            post { networksError = "$action failed: ${e.message}" }
        }
    }

    // ---- DCC (receive) ----------------------------------------------------

    fun loadDcc() = io.execute {
        try {
            http.newCall(authed("/api/dcc?limit=100").build()).execute().use { res ->
                if (res.code == 403) {
                    post { dccEnabled = false; dccError = null }
                    return@execute
                }
                if (!res.isSuccessful) {
                    post { dccError = "Failed to load transfers (HTTP ${res.code})" }
                    return@execute
                }
                val arr = JSONObject(res.body?.string().orEmpty()).optJSONArray("transfers")
                post {
                    dccEnabled = true
                    dccError = null
                    if (arr != null) for (i in 0 until arr.length()) applyTransfer(arr.getJSONObject(i))
                }
            }
        } catch (e: Exception) {
            post { dccError = "Failed to load transfers: ${e.message}" }
        }
    }

    fun dccAccept(id: Int) = dccAction(id, "accept")
    fun dccReject(id: Int) = dccAction(id, "reject")
    fun dccCancel(id: Int) = dccAction(id, "cancel")

    private fun dccAction(id: Int, action: String) = io.execute {
        try {
            val req = authed("/api/dcc/$id/$action")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    post { dccError = "DCC $action failed (HTTP ${res.code})" }
                    return@execute
                }
                JSONObject(res.body?.string().orEmpty()).optJSONObject("transfer")?.let {
                    post { applyTransfer(it) }
                }
            }
        } catch (e: Exception) {
            post { dccError = "DCC $action failed: ${e.message}" }
        }
    }

    /**
     * Offer a file to [nick] over DCC SEND. Bytes go up as multipart; the server
     * stages them in its DCC dir and CTCP-offers the peer (active or passive per
     * server config + dcc.prefer_passive). Progress arrives as dcc-transfer frames.
     */
    fun dccSendFile(networkId: Int, nick: String, filename: String, fileBody: okhttp3.RequestBody) = io.execute {
        try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("networkId", networkId.toString())
                .addFormDataPart("nick", nick)
                .addFormDataPart("file", filename, fileBody)
                .build()
            http.newCall(authed("/api/dcc/send").post(body).build()).execute().use { res ->
                val bodyText = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val err = runCatching { JSONObject(bodyText).optString("error") }.getOrNull()
                    post { dccError = err?.ifEmpty { null } ?: "DCC send failed (HTTP ${res.code})" }
                    return@execute
                }
                JSONObject(bodyText).optJSONObject("transfer")?.let {
                    post { dccEnabled = true; dccError = null; applyTransfer(it) }
                }
            }
        } catch (e: Exception) {
            post { dccError = "DCC send failed: ${e.message}" }
        }
    }

    /** Open (or close) a DCC chat with [nick]. The chat lives in the "=nick" buffer. */
    fun dccChat(networkId: Int, nick: String, open: Boolean) = io.execute {
        try {
            val path = if (open) "/api/dcc/chat" else "/api/dcc/chat/close"
            val body = JSONObject().put("networkId", networkId).put("nick", nick)
                .toString().toRequestBody(json)
            http.newCall(authed(path).post(body).build()).execute().use { res ->
                if (!res.isSuccessful) {
                    val err = runCatching {
                        JSONObject(res.body?.string().orEmpty()).optString("error")
                    }.getOrNull()
                    post { dccError = err?.ifEmpty { null } ?: "DCC chat failed (HTTP ${res.code})" }
                }
            }
        } catch (e: Exception) {
            post { dccError = "DCC chat failed: ${e.message}" }
        }
    }

    // ---- Search + highlights ------------------------------------------------

    /** Run a fresh search from raw `from:/in:/on: text` input. */
    fun runSearch(raw: String) {
        lastSearchRaw = raw
        val q = Search.parse(raw)
        // A search needs at least free text OR a structured filter.
        if (q.text.isBlank() && q.from.isEmpty() && q.inTarget.isBlank()) {
            searchResults.clear(); searchHasMore = false; searchLoading = false
            return
        }
        val token = ++searchToken
        searchNextBefore = null
        searchLoading = true
        searchError = null
        searchResults.clear()
        sendSearch(q, token, before = null)
    }

    /** Fetch the next page of the current search. */
    fun searchMore() {
        val before = searchNextBefore ?: return
        if (searchLoading) return
        searchLoading = true
        sendSearch(Search.parse(lastSearchRaw), searchToken, before)
    }

    private fun sendSearch(q: Search.Query, token: Int, before: Long?) {
        val networkId = if (q.onNetwork.isNotBlank()) {
            networks.values.firstOrNull { it.name.equals(q.onNetwork, true) }?.id
        } else null
        val frame = JSONObject()
            .put("type", "search")
            .put("token", token)
            .put("limit", 50)
        if (q.text.isNotBlank()) frame.put("query", q.text)
        if (q.from.isNotEmpty()) frame.put("nicks", JSONArray(q.from))
        if (q.inTarget.isNotBlank()) frame.put("target", q.inTarget)
        if (networkId != null) frame.put("networkId", networkId)
        if (before != null) frame.put("before", before)
        ws?.send(frame.toString())
    }

    private fun parseResultRow(o: JSONObject): SearchResult? {
        val id = o.optLong("id", -1)
        if (id < 0) return null
        return SearchResult(
            id = id,
            networkId = o.optInt("networkId", -1),
            target = o.optString("target"),
            nick = o.optString("nick", "*"),
            // Search rows use DB columns (body/createdAt); fall back to live-frame names.
            body = o.optString("body").ifEmpty { o.optString("text") },
            createdAt = o.optString("createdAt").ifEmpty { o.optString("time").ifEmpty { null } },
        )
    }

    /** GET /api/highlights — paginated recent highlight messages (REST). */
    fun loadHighlights(fresh: Boolean) {
        if (highlightsLoading) return
        if (fresh) { highlightItems.clear(); highlightsNextBefore = null }
        val before = highlightsNextBefore
        post { highlightsLoading = true }
        io.execute {
            try {
                val path = "/api/highlights?limit=50" + (before?.let { "&before=$it" } ?: "")
                http.newCall(authed(path).build()).execute().use { res ->
                    if (!res.isSuccessful) {
                        post { highlightsLoading = false }
                        return@execute
                    }
                    val obj = JSONObject(res.body?.string().orEmpty())
                    val arr = obj.optJSONArray("items")
                    val next = if (obj.isNull("nextBefore")) null else obj.optLong("nextBefore")
                    val rows = ArrayList<SearchResult>()
                    if (arr != null) for (i in 0 until arr.length()) {
                        parseResultRow(arr.optJSONObject(i) ?: continue)?.let(rows::add)
                    }
                    post {
                        highlightItems.addAll(rows)
                        highlightsNextBefore = next
                        highlightsHasMore = next != null
                        highlightsLoading = false
                    }
                }
            } catch (_: Exception) {
                post { highlightsLoading = false }
            }
        }
    }

    // ---- Pins, mark-all-read, channel list ---------------------------------

    fun isPinned(buffer: Buffer): Boolean =
        buffer.networkId?.let { pins[it]?.any { t -> t.equals(buffer.target, true) } } == true

    fun togglePin(buffer: Buffer) {
        val networkId = buffer.networkId ?: return
        val type = if (isPinned(buffer)) "unpin-buffer" else "pin-buffer"
        ws?.send(JSONObject().put("type", type).put("networkId", networkId).put("target", buffer.target).toString())
    }

    fun markAllRead() {
        ws?.send(JSONObject().put("type", "mark-all-read").toString())
    }

    // ---- Notify, notes, ignore ---------------------------------------------

    fun nickNote(networkId: Int?, nick: String): String? =
        networkId?.let { nickNotes["$it::${nick.lowercase()}"] }

    fun setNickNote(networkId: Int, nick: String, note: String) {
        val key = "$networkId::${nick.lowercase()}"
        if (note.isBlank()) nickNotes.remove(key) else nickNotes[key] = note
        ws?.send(JSONObject().put("type", "set-nick-note").put("networkId", networkId).put("nick", nick).put("note", note).toString())
    }

    fun isNotifyAlways(buffer: Buffer): Boolean =
        buffer.networkId?.let { notifyAlways["$it::${buffer.target}"] } == true

    fun setNotifyAlways(buffer: Buffer, on: Boolean) {
        val networkId = buffer.networkId ?: return
        if (on) notifyAlways["$networkId::${buffer.target}"] = true else notifyAlways.remove("$networkId::${buffer.target}")
        ws?.send(JSONObject().put("type", "set-channel-notify-always").put("networkId", networkId).put("target", buffer.target).put("notifyAlways", on).toString())
    }

    fun isBookmarked(id: Long): Boolean = id in bookmarkIds

    /** Save/unsave a message. Optimistic; the server echoes `bookmark-updated`. */
    fun toggleBookmark(id: Long) {
        if (id <= 0) return
        val save = id !in bookmarkIds
        if (save) bookmarkIds.add(id) else bookmarkIds.remove(id)
        ws?.send(JSONObject().put("type", if (save) "set-bookmark" else "unset-bookmark").put("messageId", id).toString())
    }

    /** Add a simple ALL-level ignore for [mask] (bare-mask rule the server accepts). */
    /** Add an ignore rule. [networkId] null = global (every network); empty
     *  [levels] means ALL. e.g. levels=["NOHIGHLIGHT"] ignores only a sender's
     *  highlights without hiding their messages. */
    fun addIgnore(
        networkId: Int?,
        mask: String?,
        levels: List<String> = listOf("ALL"),
        isExcept: Boolean = false,
        pattern: String? = null,
        channels: List<String>? = null,
    ) {
        val rule = JSONObject()
            .put("mask", mask ?: JSONObject.NULL)
            .put("channels", channels?.let { JSONArray(it) } ?: JSONObject.NULL)
            .put("pattern", pattern ?: JSONObject.NULL)
            .put("patternKind", "substr")
            .put("levels", JSONArray(levels))
            .put("isExcept", isExcept)
        ws?.send(
            JSONObject().put("type", "add-ignore")
                .put("networkId", networkId ?: JSONObject.NULL)
                .put("rule", rule)
                .toString(),
        )
    }

    fun removeIgnore(networkId: Int?, id: Long) {
        ws?.send(
            JSONObject().put("type", "remove-ignore")
                .put("networkId", networkId ?: JSONObject.NULL)
                .put("id", id)
                .toString(),
        )
    }

    private fun applyIgnoreScope(networkId: Int?, arr: JSONArray?) {
        ignores.removeAll { it.networkId == networkId }
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val channels = o.optJSONArray("channels")?.let { a -> (0 until a.length()).map { a.optString(it) } }
            val levels = o.optJSONArray("levels")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            ignores.add(
                IgnoreRule(
                    id = o.optLong("id"),
                    networkId = networkId,
                    mask = if (o.isNull("mask")) null else o.optString("mask").takeIf { it.isNotEmpty() },
                    channels = channels,
                    pattern = if (o.isNull("pattern")) null else o.optString("pattern").takeIf { it.isNotEmpty() },
                    patternKind = o.optString("patternKind").ifEmpty { "substr" },
                    levels = levels,
                    isExcept = o.optBoolean("isExcept", false),
                ),
            )
        }
    }

    /** Kick a fresh /LIST refresh on a network and load its cached rows. */
    fun openChannelList(networkId: Int) {
        chanlistNetworkId = networkId
        chanlistRows.clear()
        ws?.send(JSONObject().put("type", "list-channels").put("networkId", networkId).toString())
        searchChannelList("", "users", "desc", 0)
    }

    fun searchChannelList(query: String, sortBy: String, sortDir: String, offset: Int) {
        val networkId = chanlistNetworkId
        if (networkId < 0) return
        chanlistKey = "$query|$sortBy|$sortDir"
        if (offset == 0) chanlistLoading = true
        ws?.send(
            JSONObject().put("type", "chanlist-search").put("networkId", networkId)
                .put("query", query).put("sortBy", sortBy).put("sortDir", sortDir)
                .put("offset", offset).put("limit", 200).toString(),
        )
    }

    private fun applyTransfer(o: JSONObject) {
        val id = o.optInt("id", -1)
        if (id < 0) return
        transfers[id] = DccTransfer(
            id = id,
            peerNick = o.optString("peer_nick", "?"),
            filename = o.optString("filename", "file"),
            state = o.optString("state", "unknown"),
            received = o.optLong("received_bytes", 0),
            total = o.optLong("advertised_size", 0),
            direction = o.optString("direction", "recv"),
            error = o.optString("error").ifEmpty { null },
        )
    }

    // ---- Settings ---------------------------------------------------------

    fun loadSettings() = io.execute {
        try {
            http.newCall(authed("/api/settings/bootstrap").build()).execute().use { res ->
                if (!res.isSuccessful) {
                    post { settingsError = "Settings unavailable (HTTP ${res.code})"; settingsLoaded = true }
                    return@execute
                }
                val obj = JSONObject(res.body?.string().orEmpty())
                val reg = obj.optJSONArray("registry")
                val vals = obj.optJSONObject("values")
                post {
                    settingsRegistry.clear()
                    settingsValues.clear()
                    if (reg != null) for (i in 0 until reg.length()) {
                        parseOption(reg.getJSONObject(i))?.let(settingsRegistry::add)
                    }
                    if (vals != null) for (k in vals.keys()) {
                        settingsValues[k] = jsonToValue(vals.get(k))
                    }
                    settingsError = null
                    settingsLoaded = true
                }
            }
        } catch (e: Exception) {
            post { settingsError = "Settings unavailable: ${e.message}"; settingsLoaded = true }
        }
    }

    fun patchSetting(key: String, value: Any?) = io.execute {
        try {
            val changes = JSONObject().put(key, toJson(value))
            val body = JSONObject().put("changes", changes).toString().toRequestBody(json)
            val req = authed("/api/settings/").patch(body).build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    post { settingsError = "Couldn't save $key (HTTP ${res.code})" }
                    return@execute
                }
                applyValues(res)
            }
        } catch (e: Exception) {
            post { settingsError = "Couldn't save $key: ${e.message}" }
        }
    }

    fun resetSetting(key: String) = io.execute {
        try {
            http.newCall(authed("/api/settings/$key").delete().build()).execute().use { res ->
                if (res.isSuccessful) applyValues(res)
            }
        } catch (_: Exception) {
        }
    }

    /** Effective int value of a registry setting: override ?? default ?? fallback. */
    fun settingInt(key: String, fallback: Int): Int {
        (settingsValues[key] as? Number)?.let { return it.toInt() }
        (settingsValues[key] as? String)?.toIntOrNull()?.let { return it }
        (settingsRegistry.firstOrNull { it.key == key }?.default as? Number)?.let { return it.toInt() }
        return fallback
    }

    private fun applyValues(res: Response) {
        val vals = JSONObject(res.body?.string().orEmpty()).optJSONObject("values") ?: return
        post {
            // The response is the FULL override map — replace, don't merge, so a
            // reset (key removed server-side) also disappears locally.
            settingsValues.clear()
            for (k in vals.keys()) settingsValues[k] = jsonToValue(vals.get(k))
            settingsError = null
        }
    }

    private fun parseOption(o: JSONObject): SettingOption? {
        val key = o.optString("key").ifEmpty { return null }
        val choices = o.optJSONArray("choices")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()
        return SettingOption(
            key = key,
            label = o.optString("label").ifEmpty { key.substringAfterLast('.') },
            type = o.optString("type", "string"),
            group = o.optString("group").ifEmpty { key.substringBefore('.') },
            category = o.optString("category"),
            description = o.optString("description"),
            default = jsonToValue(o.opt("default")),
            choices = choices,
            min = if (o.has("min")) o.optInt("min") else null,
            max = if (o.has("max")) o.optInt("max") else null,
        )
    }

    private fun jsonToValue(v: Any?): Any? = when {
        v == null || v == JSONObject.NULL -> null
        v is JSONArray -> (0 until v.length()).map { v.optString(it) }
        else -> v
    }

    private fun toJson(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is List<*> -> JSONArray(value)
        else -> value
    }

    private companion object {
        const val PROTOCOL_VERSION = 1 // mirror server/protocol.ts
        const val INITIAL_BACKOFF = 1_000L
        const val MAX_BACKOFF = 30_000L
        const val SEND_ACK_TIMEOUT = 8_000L // matches the web client's deadline

        /** How long an `active` typing signal lives without a refresh. */
        const val TYPING_TTL = 6_000L

        /** Backgrounds shorter than this (task-switcher hops) skip the cycle. */
        const val BACKGROUND_CYCLE_MS = 3_000L

        /** A channel-joined within this of the join request focuses that channel. */
        const val JOIN_FOCUS_WINDOW_MS = 15_000L
        val COUNTABLE = setOf("message", "action", "notice")
        val SYSTEM_TYPES = setOf("join", "part", "quit", "nick", "kick", "mode", "topic", "invite")
    }
}

/**
 * Pure decision for whether an incoming message should raise a system
 * notification. Plain booleans (no Android/JSON deps) so the truth table is
 * unit-testable. Only notify while backgrounded, for a real inbound message the
 * server flagged `notify` (its union of highlight / DM / notify-always) that
 * isn't in the currently-open buffer.
 */
internal fun shouldNotify(
    notify: Boolean,
    isActiveBuffer: Boolean,
    self: Boolean,
    system: Boolean,
    hasId: Boolean,
    foreground: Boolean,
): Boolean =
    notify && !foreground && !self && !system && hasId && !isActiveBuffer

/**
 * Order chat rows by server id. Out-of-order live delivery (two sources racing —
 * e.g. an MCP post + an app send — or a delayed E2E decrypt) would otherwise
 * strand a lower-id message at the tail. Fast-paths the already-sorted common
 * case (no allocation). When a reorder IS needed, client-injected rows (id<=0:
 * local notices, whois blocks, E2E status) stay anchored right after the real
 * message they were injected below, rather than jumping to the end.
 */
internal fun orderMessagesById(msgs: List<Msg>): List<Msg> {
    var last = 0L
    for (m in msgs) if (m.id > 0) { if (m.id < last) { last = -1; break }; last = m.id }
    if (last != -1L) return msgs // already in order
    var anchor = 0L
    return msgs.map { m ->
        val sortKey = if (m.id > 0) { anchor = m.id; m.id * 2 } else anchor * 2 + 1
        sortKey to m
    }.sortedBy { it.first }.map { it.second }
}
