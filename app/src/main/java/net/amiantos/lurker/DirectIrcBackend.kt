// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0
package net.amiantos.lurker

import android.content.Context
import net.engio.mbassy.listener.Handler
import org.json.JSONObject
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.event.channel.ChannelCtcpEvent
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.event.channel.ChannelNoticeEvent
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent
import org.kitteh.irc.client.library.event.channel.ChannelTopicEvent
import org.kitteh.irc.client.library.event.channel.ChannelUsersUpdatedEvent
import org.kitteh.irc.client.library.event.capabilities.CapabilitiesSupportedListEvent
import org.kitteh.irc.client.library.event.client.ClientNegotiationCompleteEvent
import org.kitteh.irc.client.library.event.client.ClientReceiveCommandEvent
import org.kitteh.irc.client.library.event.connection.ClientConnectionClosedEvent
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent
import org.kitteh.irc.client.library.event.user.PrivateNoticeEvent
import org.kitteh.irc.client.library.event.user.UserNickChangeEvent
import org.kitteh.irc.client.library.event.user.UserQuitEvent
import org.kitteh.irc.client.library.feature.auth.SaslPlain
import java.util.concurrent.atomic.AtomicLong

/**
 * Direct-IRC / bouncer backend. Subclasses [LurkerClient] to inherit all the
 * observable Compose state + buffer/roster/unread helpers, overriding only the
 * transport methods to speak raw IRC (via KICL). A bouncer (soju/ZNC) is just an
 * IRC server here — bind one upstream network with `user/network` in the
 * username/SASL field.
 */
class DirectIrcBackend(appContext: Context) : LurkerClient() {

    override val serverFeatures: Boolean get() = false

    private val store = DirectNetworkStore(appContext)
    private val manager = KiclManager()
    private val nextMsgId = AtomicLong(1)
    private val nextIgnoreId = AtomicLong(1)

    override fun start(prefs: Prefs) {
        loggedIn = true
        status = "Direct IRC mode"
        ignores.clear()
        ignores.addAll(store.loadIgnores())
        nextIgnoreId.set((ignores.maxOfOrNull { it.id } ?: 0L) + 1L)
        refreshConfigs()
        store.list().filter { it.autoconnect }.forEach { connectNetwork(it) }
    }

    // Ignores are server-synced in Lurker mode; here they're local + persisted.
    override fun addIgnore(
        networkId: Int?, mask: String?, levels: List<String>, isExcept: Boolean,
        pattern: String?, channels: List<String>?,
    ) {
        ignores.add(IgnoreRule(nextIgnoreId.getAndIncrement(), networkId, mask, channels, pattern, "substr", levels, isExcept))
        store.saveIgnores(ignores.toList())
    }

    override fun removeIgnore(networkId: Int?, id: Long) {
        ignores.removeAll { it.id == id }
        store.saveIgnores(ignores.toList())
    }

    private fun refreshConfigs() = post {
        networkConfigs.clear()
        networkConfigs.addAll(store.list().map { it.toConfig() })
        // Seed a network row (disconnected) for each config so the sidebar shows it
        // even before it connects.
        store.list().forEach { n ->
            if (networks[n.id] == null) networks[n.id] = Network(n.id, n.name, null, false)
        }
    }

    private fun buildClient(net: StoredNet): Client {
        val server = Client.builder()
            .nick(net.nick)
            .user(net.username?.ifBlank { null } ?: net.nick)
            .realName(net.realname ?: net.nick)
            .server()
            .host(net.host)
            .port(
                net.port,
                if (net.tls) Client.Builder.Server.SecurityType.SECURE
                else Client.Builder.Server.SecurityType.INSECURE,
            )
        net.serverPassword?.let { server.password(it) }
        val client = server.then().build()
        if (!net.saslAccount.isNullOrEmpty() && !net.saslPassword.isNullOrEmpty()) {
            client.authManager.addProtocol(SaslPlain(client, net.saslAccount, net.saslPassword))
        }
        return client
    }

    // Stable child networkId per (soju control id, upstream network name).
    private val sojuChildIds = mutableMapOf<String, Int>()
    private var nextChildId = 100_000

    private fun connectNetwork(net: StoredNet) {
        post {
            networks[net.id] = Network(net.id, net.name, null, false)
            status = "Connecting to ${net.name}…"
        }
        io.execute {
            runCatching {
                val client = buildClient(net)
                // A type="soju" config with no /network in its username is a soju
                // control connection — it discovers upstream networks. Children
                // (type="direct", username user/network) are normal connections.
                client.eventManager.registerEventListener(Listener(net.id, client, isSojuControl = net.type == "soju"))
                manager.put(net.id, client)
                client.connect()
            }.onFailure { e ->
                post { status = "Couldn't connect to ${net.name}: ${e.message}" }
            }
        }
    }

    /** Open a per-upstream-network binding on a soju bouncer (username user/network).
     *  NOTE: soju.im/bouncer-networks discovery is implemented from spec and needs
     *  verification against a real soju instance. */
    private fun connectSojuChild(control: StoredNet, soju: SojuNetwork) {
        val key = "${control.id}/${soju.name}"
        val childId = sojuChildIds.getOrPut(key) { nextChildId++ }
        if (manager.get(childId) != null) return // already connected
        val baseUser = control.username?.ifBlank { null } ?: control.nick
        val child = control.copy(id = childId, name = soju.name, username = "$baseUser/${soju.name}", type = "direct")
        post {
            networks[childId] = Network(childId, soju.name, null, false)
            if (networkConfigs.none { it.id == childId }) networkConfigs.add(child.toConfig())
        }
        connectNetwork(child)
    }

    /** KICL dispatches on Netty threads; every handler hops to the main thread
     *  before touching Compose snapshot state (mirrors LurkerClient.post{}). */
    private inner class Listener(
        val networkId: Int,
        val client: Client,
        val isSojuControl: Boolean = false,
    ) {
        private fun netName() = networks[networkId]?.name ?: "network"
        private fun buf(target: String) = Buffer(networkId, target, netName())
        private fun myNick() = client.nick

        // soju.im/bouncer-networks: request the cap when the bouncer offers it.
        @Handler
        fun onCaps(e: CapabilitiesSupportedListEvent) {
            if (isSojuControl && e.supportedCapabilities.any { it.name == "soju.im/bouncer-networks" }) {
                e.addRequest("soju.im/bouncer-networks")
            }
        }

        // BOUNCER NETWORK <id> <attrs> — one per upstream network on the bouncer.
        @Handler
        fun onBouncer(e: ClientReceiveCommandEvent) {
            if (!isSojuControl || !e.command.equals("BOUNCER", ignoreCase = true)) return
            val soju = SojuBouncer.parseNetworkLine(e.parameters) ?: return
            post {
                val control = store.get(networkId) ?: return@post
                if (soju.removed) return@post
                connectSojuChild(control, soju)
            }
        }

        @Handler
        fun onReady(e: ClientNegotiationCompleteEvent) = post {
            networks[networkId] = Network(networkId, netName(), client.nick, true)
            connected = networks.values.any { it.connected }
            status = "Connected to ${netName()}"
            if (isSojuControl) {
                // Discover the bouncer's upstream networks; each becomes its own row.
                io.execute { runCatching { client.sendRawLine("BOUNCER LISTNETWORKS") } }
                return@post
            }
            // Always give the network a server buffer — it's the place with a
            // composer where the user can /join a channel (and it holds server
            // notices). Without it a fresh network with no channels is a dead end.
            val server = ensureBuffer(networkId, ":server:$networkId")
            mergeInto(
                server.key,
                listOf(Msg(0, "notice", "*", "Connected to ${netName()} as ${client.nick}. Type /join #channel to join a channel.", self = false, system = true)),
                replace = false,
            )
            store.get(networkId)?.channels
                ?.split(',', ' ')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.forEach { io.execute { runCatching { client.addChannel(it) } } }
        }

        @Handler
        fun onClosed(e: ClientConnectionClosedEvent) = post {
            networks[networkId]?.let { networks[networkId] = it.copy(connected = false) }
            connected = networks.values.any { it.connected }
        }

        @Handler
        fun onChannelMessage(e: ChannelMessageEvent) =
            addMessage(networkId, e.channel.name, "message", e.actor.nick, e.message, mask = e.actor.name)

        @Handler
        fun onPrivateMessage(e: PrivateMessageEvent) =
            addMessage(networkId, e.actor.nick, "message", e.actor.nick, e.message, dm = true, mask = e.actor.name)

        @Handler
        fun onChannelAction(e: ChannelCtcpEvent) {
            val m = e.message
            if (m.startsWith("ACTION ")) {
                addMessage(networkId, e.channel.name, "action", e.actor.nick, m.removePrefix("ACTION "), mask = e.actor.name)
            }
        }

        @Handler
        fun onChannelNotice(e: ChannelNoticeEvent) =
            addMessage(networkId, e.channel.name, "notice", e.actor.nick, e.message, mask = e.actor.name)

        @Handler
        fun onPrivateNotice(e: PrivateNoticeEvent) =
            addMessage(networkId, e.actor.nick, "notice", e.actor.nick, e.message, dm = true, mask = e.actor.name)

        @Handler
        fun onJoin(e: ChannelJoinEvent) = post {
            ensureBuffer(networkId, e.channel.name)
            rebuildRoster(e.channel.name)
            if (e.user.nick.equals(myNick(), true)) {
                noteJoinRequest(networkId, e.channel.name)
                pendingOpen = buf(e.channel.name)
            } else {
                sysLine(e.channel.name, "${e.user.nick} joined")
            }
        }

        @Handler
        fun onPart(e: ChannelPartEvent) = post {
            rebuildRoster(e.channel.name)
            if (!e.user.nick.equals(myNick(), true)) sysLine(e.channel.name, "${e.user.nick} left")
        }

        @Handler
        fun onQuit(e: UserQuitEvent) = post {
            buffers.filter { it.networkId == networkId && it.isChannel }.forEach { rebuildRoster(it.target) }
        }

        @Handler
        fun onNick(e: UserNickChangeEvent) = post {
            buffers.filter { it.networkId == networkId && it.isChannel }.forEach { rebuildRoster(it.target) }
        }

        @Handler
        fun onUsers(e: ChannelUsersUpdatedEvent) = post { rebuildRoster(e.channel.name) }

        @Handler
        fun onTopic(e: ChannelTopicEvent) = post {
            sysLine(e.channel.name, "Topic: ${e.topic.value.orElse("")}")
        }

        private fun rebuildRoster(channel: String) {
            val ch = client.getChannel(channel).orElse(null) ?: return
            val roster = ch.users.map { u ->
                val modes = ch.getUserModes(u).orElse(null)?.map { it.char.toString() } ?: emptyList()
                Member(u.nick, modes, away = false, host = u.host)
            }
            // Op/voice first (Member.rank), then alphabetical — same as Lurker mode.
            members["$networkId::$channel"] = roster.sortedWith(compareBy({ it.rank }, { it.nick.lowercase() }))
        }

        private fun sysLine(target: String, text: String) {
            val b = ensureBuffer(networkId, target)
            mergeInto(b.key, listOf(Msg(0, "system", "*", text, self = false, system = true)), replace = false)
        }
    }

    private fun addMessage(
        networkId: Int, target: String, type: String, nick: String, text: String,
        dm: Boolean = false, mask: String = nick,
    ) = post {
        val myNick = networks[networkId]?.nick
        val self = nick.equals(myNick, true)
        // Client-side ignore filtering (Lurker does this server-side). Never drop
        // our own lines.
        val ignore = if (self) IgnoreOutcome()
        else IgnoreMatch.evaluate(ignores, networkId, if (dm) null else target, dm, mask, type, text)
        if (ignore.drop) return@post
        val b = ensureBuffer(networkId, target)
        val msg = Msg(nextMsgId.getAndIncrement(), type, nick, text, self = self)
        if (!mergeInto(b.key, listOf(msg), replace = false)) return@post
        val highlight = !ignore.suppressHighlight && (dm || (myNick != null && text.contains(myNick, true)))
        if (!ignore.suppressUnread) {
            countUnread(b.key, JSONObject().put("matched", highlight).put("dm", dm), msg)
        }
        if (!ignore.suppressNotify &&
            shouldNotify(true, b.key == activeKey, self, msg.system, msg.id > 0, appForeground)
        ) {
            notificationSink?.invoke(NotifiableEvent(networkId, target, nick, text, dm))
        }
    }

    // ---- Sending: WireOps -> KICL -----------------------------------------
    override fun execute(buffer: Buffer, ops: List<WireOp>) {
        val client = manager.get(buffer.networkId ?: return) ?: return
        for (op in ops) {
            when (op.type) {
                "send" -> {
                    val target = op.target ?: buffer.target
                    client.sendMessage(target, op.text ?: "")
                    if (op.target == null) echoSelf(buffer, "message", op.text ?: "")
                }
                "action" -> {
                    client.sendCtcpMessage(buffer.target, "ACTION ${op.text ?: ""}")
                    echoSelf(buffer, "action", op.text ?: "")
                }
                "notice" -> client.sendNotice(op.target ?: buffer.target, op.text ?: "")
                "raw" -> client.sendRawLine(op.line ?: "")
                "join" -> op.channel?.let { ch -> io.execute { runCatching { client.addChannel(ch) } } }
                "part" -> client.getChannel(op.target ?: buffer.target).ifPresent { it.part(op.reason ?: "") }
                "close" -> {
                    client.getChannel(buffer.target).ifPresent { it.part("") }
                    post { buffers.removeAll { it.key == buffer.key }; messagesByBuffer.remove(buffer.key) }
                }
                "clear" -> post { messagesByBuffer[buffer.key] = emptyList() }
                "e2e" -> localNotice(buffer, "End-to-end encryption isn't available in direct IRC mode.")
            }
        }
    }

    /** No echo-message cap yet → optimistically append our own line. */
    private fun echoSelf(buffer: Buffer, type: String, text: String) = post {
        val nick = networks[buffer.networkId]?.nick ?: "me"
        mergeInto(buffer.key, listOf(Msg(nextMsgId.getAndIncrement(), type, nick, text, self = true)), replace = false)
    }

    override fun open(buffer: Buffer) {} // no server history to hydrate; buffer is local

    override fun onForeground() {
        appForeground = true
        // Recover any autoconnect network whose connection was lost (e.g. dropped
        // while backgrounded, or a Wi-Fi↔cellular switch). Only where we hold no
        // live client, to avoid racing KICL's own reconnection.
        store.list().filter { it.autoconnect && manager.get(it.id) == null }.forEach { connectNetwork(it) }
    }

    override fun onBackground() { appForeground = false }

    override fun signOut() {
        manager.shutdownAll()
        super.signOut()
    }

    // ---- Network CRUD -> local store + KiclManager ------------------------
    override fun loadNetworkConfigs() { refreshConfigs() }

    override fun saveNetwork(id: Int?, fields: Map<String, Any?>, onDone: (String?) -> Unit) {
        val newId = store.upsert(id, fields)
        refreshConfigs()
        // (Re)connect if it should be live.
        store.get(newId)?.let { net ->
            if (net.autoconnect || manager.get(newId) != null) {
                manager.shutdown(newId)
                connectNetwork(net)
            }
        }
        post { onDone(null) }
    }

    override fun deleteNetwork(id: Int, onDone: (String?) -> Unit) {
        manager.shutdown(id)
        store.delete(id)
        post {
            networks.remove(id)
            buffers.removeAll { it.networkId == id }
            refreshConfigs()
            onDone(null)
        }
    }

    override fun networkAction(id: Int, action: String) {
        val net = store.get(id) ?: return
        when (action) {
            "connect" -> connectNetwork(net)
            "disconnect" -> {
                manager.shutdown(id)
                post { networks[id]?.let { networks[id] = it.copy(connected = false) } }
            }
            "reconnect" -> { manager.shutdown(id); connectNetwork(net) }
        }
    }

    override fun reorderNetworks(ids: List<Int>) {
        store.reorder(ids)
        refreshConfigs()
    }
}

/** Holds the live KICL clients keyed by local networkId. */
class KiclManager {
    private val clients = mutableMapOf<Int, Client>()
    fun put(id: Int, client: Client) { clients[id] = client }
    fun get(id: Int): Client? = clients[id]
    fun shutdown(id: Int) { clients.remove(id)?.let { runCatching { it.shutdown() } } }
    fun shutdownAll() { clients.values.forEach { runCatching { it.shutdown() } }; clients.clear() }
}
