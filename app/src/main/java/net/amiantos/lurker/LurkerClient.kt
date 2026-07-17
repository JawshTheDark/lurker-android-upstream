// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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

    private var ws: WebSocket? = null
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
    private var connecting = false
    private var backoffMs = INITIAL_BACKOFF

    private fun post(block: () -> Unit) = main.post(block)

    // ---- Session lifecycle ------------------------------------------------

    /**
     * Wire up persistence and, if a saved session exists, connect straight away
     * without a sign-in screen. Called once from the Activity.
     */
    fun start(prefs: Prefs) {
        this.prefs = prefs
        if (prefs.hasSession) {
            token = prefs.token
            baseUrl = prefs.serverUrl!!.trimEnd('/')
            post { status = "Connecting…"; loggedIn = true }
            io.execute {
                fetchNetworkNames()
                openSocket(null)
            }
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
        } catch (e: Exception) {
            post { status = "Sign-in failed: ${e.message}" }
        }
    }

    /** Tear down the session: close the socket, forget the token, reset state. */
    fun signOut() {
        intentionalClose = true
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
            transfers.clear()
            settingsRegistry.clear()
            settingsValues.clear()
            settingsLoaded = false
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

    /** Called from the Activity's ON_RESUME — reconnect immediately if dropped. */
    fun onForeground() {
        if (!loggedIn || connected || connecting || reconnectScheduled || token == null) return
        backoffMs = INITIAL_BACKOFF
        openSocket(maxMessageId.takeIf { it > 0 })
    }

    // ---- REST helpers -----------------------------------------------------

    private fun authed(path: String): Request.Builder =
        Request.Builder().url("$baseUrl$path").header("Authorization", "Bearer ${token!!}")

    private fun fetchNetworkNames() {
        try {
            http.newCall(authed("/api/networks").build()).execute().use { res ->
                if (!res.isSuccessful) return
                val arr = JSONObject(res.body?.string().orEmpty()).optJSONArray("networks") ?: return
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
                }
            }
        } catch (_: Exception) {
            // Names are cosmetic; the buffer list still works without them.
        }
    }

    // ---- WebSocket --------------------------------------------------------

    private fun openSocket(since: Long?) {
        intentionalClose = false
        connecting = true
        ws?.cancel()
        val wsBase = baseUrl.replaceFirst("http", "ws") + "/ws"
        val wsUrl = if (since != null && since > 0) "$wsBase?since=$since" else wsBase
        val req = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer ${token!!}")
            .build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                post {
                    connected = true
                    connecting = false
                    status = null
                    backoffMs = INITIAL_BACKOFF
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    return
                }
                post { handleFrame(frame) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                post {
                    connected = false
                    connecting = false
                    // A 401 means the saved token is dead — don't spin on it.
                    if (code == 401) {
                        status = "Session expired — please sign in again."
                        forceSignOutLocally()
                    } else {
                        status = if (code != null) "Reconnecting… (HTTP $code)" else "Reconnecting…"
                        scheduleReconnect()
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
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
        main.postDelayed({
            reconnectScheduled = false
            if (!intentionalClose && token != null) openSocket(maxMessageId.takeIf { it > 0 })
        }, delay)
    }

    // ---- Frame handling ---------------------------------------------------

    private fun handleFrame(frame: JSONObject) {
        when (frame.optString("kind")) {
            "snapshot" -> {
                bumpCursor(frame.optLong("cursor"))
                frame.optJSONArray("networks")?.let { applyNetworks(it) }
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
            }

            "irc" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.getInt("networkId")
                val target = frame.optString("target")
                if (target.isEmpty()) return
                bumpCursor(frame.optLong("id"))
                val buffer = ensureBuffer(networkId, target)
                // Roster updates ride the same stream; apply before the renderable
                // check (names/channel-parted produce no chat line at all).
                applyMemberEvent(buffer.key, frame)
                val msg = parseEvent(frame) ?: return
                mergeInto(buffer.key, listOf(msg), replace = false)
                countUnread(buffer.key, frame, msg)
            }

            "read-state" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                val target = frame.optString("target")
                val key = "${networkId ?: "sys"}::$target"
                setBadge(unread, key, frame.optInt("unread", 0))
                setBadge(highlights, key, frame.optInt("highlights", 0))
                if (frame.has("lastReadId")) lastRead[key] = frame.optLong("lastReadId")
            }

            "buffer-opened", "buffer-reopened" -> {
                val networkId = if (frame.isNull("networkId")) null else frame.optInt("networkId")
                val target = frame.optString("target")
                if (target.isNotEmpty()) ensureBuffer(networkId, target)
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

            "dcc-transfer" -> frame.optJSONObject("transfer")?.let { applyTransfer(it) }

            "error" -> status = frame.optString("text")
        }
    }

    private fun applyNetworks(arr: JSONArray) {
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            val id = n.optInt("id", -1)
            if (id < 0) continue
            val name = n.optString("name").ifEmpty { networkNames[id] ?: "network" }
            networkNames[id] = name
            networks[id] = Network(
                id = id,
                name = name,
                nick = n.optString("nick").ifEmpty { null },
                connected = n.optBoolean("connected", n.optString("state") == "connected"),
            )
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

    private fun mergeInto(key: String, newMsgs: List<Msg>, replace: Boolean) {
        if (replace) {
            messagesByBuffer[key] = newMsgs
            return
        }
        val existing = messagesByBuffer[key] ?: emptyList()
        if (existing.isEmpty()) {
            messagesByBuffer[key] = newMsgs
            return
        }
        val ids = existing.mapNotNull { if (it.id > 0) it.id else null }.toHashSet()
        val add = newMsgs.filter { it.id <= 0 || ids.add(it.id) }
        if (add.isNotEmpty()) messagesByBuffer[key] = existing + add
    }

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
        if (prev != null && prev != buffer?.key) dividerAfter.remove(prev)
        val key = buffer?.key ?: return
        // Anchor the New-messages divider before the badge and cursor move.
        val cursor = lastRead[key] ?: 0L
        if ((unread[key] ?: 0) > 0 && cursor > 0) dividerAfter[key] = cursor
        unread.remove(key)
        highlights.remove(key)
        markRead(buffer)
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
        val type = e.optString("type")
        val id = e.optLong("id")
        val nick = e.optString("nick", "*")
        val text = e.optString("text")
        return when (type) {
            "message", "action", "notice", "error" -> Msg(
                id = id, type = type, nick = nick, text = text,
                self = e.optBoolean("self", false), time = e.optString("time").ifEmpty { null },
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
    fun execute(buffer: Buffer, ops: List<WireOp>) {
        val networkId = buffer.networkId ?: return
        val socket = ws ?: return
        for (op in ops) {
            val target = op.target ?: buffer.target
            val frame = JSONObject().put("networkId", networkId)
            when (op.type) {
                "send", "action", "notice" ->
                    frame.put("type", op.type).put("target", target).put("text", op.text)
                "raw" -> frame.put("type", "raw").put("line", op.line)
                "join" -> frame.put("type", "join").put("channel", op.channel)
                "part" -> {
                    frame.put("type", "part").put("channel", op.channel)
                    op.reason?.let { frame.put("reason", it) }
                }
                "close" -> frame.put("type", "close-buffer").put("target", target)
                "clear" -> frame.put("type", "clear-buffer").put("target", target)
                else -> continue
            }
            socket.send(frame.toString())
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
    fun dccSendFile(networkId: Int, nick: String, filename: String, bytes: ByteArray) = io.execute {
        try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("networkId", networkId.toString())
                .addFormDataPart("nick", nick)
                .addFormDataPart("file", filename, bytes.toRequestBody("application/octet-stream".toMediaType()))
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
        const val INITIAL_BACKOFF = 1_000L
        const val MAX_BACKOFF = 30_000L
        val COUNTABLE = setOf("message", "action", "notice")
        val SYSTEM_TYPES = setOf("join", "part", "quit", "nick", "kick", "mode", "topic", "invite")
    }
}
