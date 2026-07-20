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
import org.kitteh.irc.client.library.event.client.ClientNegotiationCompleteEvent
import org.kitteh.irc.client.library.event.connection.ClientConnectionClosedEvent
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent
import org.kitteh.irc.client.library.event.user.PrivateNoticeEvent
import org.kitteh.irc.client.library.event.user.UserNickChangeEvent
import org.kitteh.irc.client.library.event.user.UserQuitEvent
import java.util.concurrent.atomic.AtomicLong

/**
 * Direct-IRC / bouncer backend. Subclasses [LurkerClient] so it inherits all the
 * observable Compose state + the buffer/roster/unread helpers verbatim, and only
 * overrides the transport methods to speak raw IRC (via KICL) instead of the
 * Lurker WebSocket. A bouncer (soju/ZNC) is just an IRC server here.
 *
 * PHASE 2: one hardcoded network, channel + private messages in/out, buffer
 * creation, basic system lines. SASL / multi-network / per-server config UI /
 * first-class soju bouncer-networks come in later phases. This is the pragmatic
 * subclass path (see the plan's "residual: promote to a clean interface").
 */
class DirectIrcBackend(private val appContext: Context) : LurkerClient() {

    override val serverFeatures: Boolean get() = false

    private val manager = KiclManager()
    private val nextMsgId = AtomicLong(1)

    // Phase 2: one hardcoded network so we can prove read+send on-device. Phase 4
    // replaces this with the encrypted DirectNetworkStore + NetworkEditScreen.
    private val demoNetworkId = 1

    override fun start(prefs: Prefs) {
        // No account/login concept in direct mode — we're "signed in" as soon as
        // the app opens; connections are per-configured-network.
        loggedIn = true
        status = "Direct IRC mode"
        connectDemoNetwork()
    }

    private fun connectDemoNetwork() {
        val nick = "spooky${System.currentTimeMillis() % 1000}"
        post { networks[demoNetworkId] = Network(demoNetworkId, "Libera.Chat", nick, false) }
        // KICL's build()/connect() does DNS + socket work — never on the main thread.
        io.execute {
            val client = Client.builder()
                .nick(nick)
                .user("spooky")
                .realName("Spooky (direct mode)")
                .server()
                .host("irc.libera.chat")
                .port(6697, Client.Builder.Server.SecurityType.SECURE)
                .then()
                .build()
            client.eventManager.registerEventListener(Listener(demoNetworkId, client))
            manager.put(demoNetworkId, client)
            client.connect()
        }
    }

    /** KICL dispatches on Netty threads; every handler hops to the main thread
     *  before touching Compose snapshot state (mirrors LurkerClient.post{}). */
    private inner class Listener(val networkId: Int, val client: Client) {
        private fun netName() = networks[networkId]?.name ?: "network"
        private fun buf(target: String) = Buffer(networkId, target, netName())
        private fun myNick() = client.nick

        @Handler
        fun onReady(e: ClientNegotiationCompleteEvent) = post {
            networks[networkId] = Network(networkId, netName(), client.nick, true)
            connected = true
            status = "Connected to ${netName()}"
            // Phase 2 demo autojoin.
            client.addChannel("#lurker")
        }

        @Handler
        fun onClosed(e: ClientConnectionClosedEvent) = post {
            networks[networkId]?.let { networks[networkId] = it.copy(connected = false) }
            connected = networks.values.any { it.connected }
        }

        @Handler
        fun onChannelMessage(e: ChannelMessageEvent) =
            addMessage(e.channel.name, "message", e.actor.nick, e.message)

        @Handler
        fun onPrivateMessage(e: PrivateMessageEvent) =
            addMessage(e.actor.nick, "message", e.actor.nick, e.message, dm = true)

        @Handler
        fun onChannelAction(e: ChannelCtcpEvent) {
            val m = e.message
            if (m.startsWith("ACTION ")) addMessage(e.channel.name, "action", e.actor.nick, m.removePrefix("ACTION "))
        }

        @Handler
        fun onChannelNotice(e: ChannelNoticeEvent) =
            addMessage(e.channel.name, "notice", e.actor.nick, e.message)

        @Handler
        fun onPrivateNotice(e: PrivateNoticeEvent) =
            addMessage(e.actor.nick, "notice", e.actor.nick, e.message, dm = true)

        @Handler
        fun onJoin(e: ChannelJoinEvent) = post {
            val b = buf(e.channel.name)
            ensureBuffer(networkId, e.channel.name)
            rebuildRoster(e.channel.name)
            if (e.user.nick.equals(myNick(), true)) {
                noteJoinRequest(networkId, e.channel.name)
                pendingOpen = b
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
            // Post a quit line into every channel we shared with them.
            buffers.filter { it.networkId == networkId && it.isChannel }.forEach { b ->
                rebuildRoster(b.target)
            }
        }

        @Handler
        fun onNick(e: UserNickChangeEvent) = post {
            buffers.filter { it.networkId == networkId && it.isChannel }.forEach { b ->
                rebuildRoster(b.target)
            }
        }

        @Handler
        fun onUsers(e: ChannelUsersUpdatedEvent) = post { rebuildRoster(e.channel.name) }

        @Handler
        fun onTopic(e: ChannelTopicEvent) = post {
            sysLine(e.channel.name, "Topic: ${e.topic.value.orElse("")}")
        }

        private fun rebuildRoster(channel: String) {
            val ch = client.getChannel(channel).orElse(null) ?: return
            val roster = ch.nicknames.map { Member(nick = it, modes = emptyList(), away = false, host = null) }
            members["$networkId::$channel"] = roster.sortedBy { it.nick.lowercase() }
        }

        private fun sysLine(target: String, text: String) = post {
            val b = ensureBuffer(networkId, target)
            mergeInto(b.key, listOf(Msg(0, "system", "*", text, self = false, system = true)), replace = false)
        }
    }

    private fun addMessage(target: String, type: String, nick: String, text: String, dm: Boolean = false) = post {
        val b = ensureBuffer(demoNetworkId, target)
        val myNick = networks[demoNetworkId]?.nick
        val self = nick.equals(myNick, true)
        val msg = Msg(nextMsgId.getAndIncrement(), type, nick, text, self = self)
        if (!mergeInto(b.key, listOf(msg), replace = false)) return@post
        val highlight = dm || (myNick != null && text.contains(myNick, true))
        val frame = JSONObject().put("matched", highlight).put("dm", dm)
        countUnread(b.key, frame, msg)
        if (shouldNotify(true, b.key == activeKey, self, msg.system, msg.id > 0, appForeground)) {
            notificationSink?.invoke(NotifiableEvent(demoNetworkId, target, nick, text, dm))
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
                "join" -> op.channel?.let { client.addChannel(it) }
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

    /** No echo-message cap yet (Phase 3) → optimistically append our own line. */
    private fun echoSelf(buffer: Buffer, type: String, text: String) = post {
        val nick = networks[buffer.networkId]?.nick ?: "me"
        mergeInto(buffer.key, listOf(Msg(nextMsgId.getAndIncrement(), type, nick, text, self = true)), replace = false)
    }

    // Direct mode has no server history to hydrate; the buffer is already local.
    override fun open(buffer: Buffer) {}

    override fun onForeground() { appForeground = true }
    override fun onBackground() { appForeground = false }

    override fun signOut() {
        manager.shutdownAll()
        super.signOut()
    }
}

/** Holds the live KICL clients keyed by local networkId. */
class KiclManager {
    private val clients = mutableMapOf<Int, Client>()
    fun put(id: Int, client: Client) { clients[id] = client }
    fun get(id: Int): Client? = clients[id]
    fun shutdownAll() { clients.values.forEach { runCatching { it.shutdown() } }; clients.clear() }
}
