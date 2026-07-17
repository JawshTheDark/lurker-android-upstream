// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.amiantos.lurker.ui.theme.AccentBlue
import net.amiantos.lurker.ui.theme.AlertRed
import net.amiantos.lurker.ui.theme.CanvasBlack
import net.amiantos.lurker.ui.theme.LurkerTheme
import net.amiantos.lurker.ui.theme.NoticeAmber
import net.amiantos.lurker.ui.theme.OnlineGreen
import net.amiantos.lurker.ui.theme.PillGray
import net.amiantos.lurker.ui.theme.SurfaceDark
import net.amiantos.lurker.ui.theme.SurfaceRaised
import net.amiantos.lurker.ui.theme.TextSecondary
import net.amiantos.lurker.ui.theme.formatTime
import net.amiantos.lurker.ui.theme.nickColor

/** Where the app is: the buffer list, a chat, settings, or the DCC screen. */
private sealed interface Screen {
    data object Buffers : Screen
    data class Chat(val buffer: Buffer) : Screen
    data object Settings : Screen
    data object Dcc : Screen
}

class MainActivity : ComponentActivity() {
    private val client = LurkerClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        client.start(prefs)
        setContent {
            LurkerTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Buffers) }

                if (!client.loggedIn) {
                    LoginScreen(client, prefs)
                    return@LurkerTheme
                }

                when (val s = screen) {
                    Screen.Buffers -> BufferListScreen(
                        client = client,
                        onOpen = { buffer ->
                            client.open(buffer)
                            client.setActive(buffer)
                            screen = Screen.Chat(buffer)
                        },
                        onSettings = { client.loadSettings(); screen = Screen.Settings },
                        onDcc = { client.loadDcc(); screen = Screen.Dcc },
                        onSignOut = { client.signOut() },
                    )
                    is Screen.Chat -> ChatScreen(
                        client = client,
                        buffer = s.buffer,
                        onBack = { client.setActive(null); screen = Screen.Buffers },
                        onOpenBuffer = { buffer ->
                            client.setActive(buffer)
                            screen = Screen.Chat(buffer)
                        },
                    )
                    Screen.Settings -> SettingsScreen(client) { screen = Screen.Buffers }
                    Screen.Dcc -> DccScreen(
                        client = client,
                        onOpenBuffer = { buffer ->
                            client.setActive(buffer)
                            screen = Screen.Chat(buffer)
                        },
                        onBack = { screen = Screen.Buffers },
                    )
                }
            }
        }
    }

    // The bouncer holds our place while we're away; on return, revive a dropped
    // socket and resume from the last message id we saw.
    override fun onResume() {
        super.onResume()
        client.onForeground()
    }
}

@Composable
private fun LoginScreen(client: LurkerClient, prefs: Prefs) {
    // 10.0.2.2 is the emulator's alias for the host's 127.0.0.1; 8010 is the
    // API/WS port. Prefill from the last successful sign-in.
    var server by remember { mutableStateOf(prefs.serverUrl ?: "http://10.0.2.2:8010") }
    var username by remember { mutableStateOf(prefs.username ?: "") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("lurker", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Sign in with your password. The session stays put across launches.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text("Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { client.login(server, username, password) }
                    }
                },
                enabled = username.isNotBlank() && password.isNotBlank(),
            ) { Text("Sign in") }

            client.status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BufferListScreen(
    client: LurkerClient,
    onOpen: (Buffer) -> Unit,
    onSettings: () -> Unit,
    onDcc: () -> Unit,
    onSignOut: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionDot(client.connected)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (client.connected) "Buffers" else "Connecting…",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { menuOpen = true }) {
                        Text("⋯", color = TextSecondary, fontSize = 22.sp)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onSettings() })
                        DropdownMenuItem(text = { Text("DCC transfers") }, onClick = { menuOpen = false; onDcc() })
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = { menuOpen = false; onSignOut() })
                    }
                },
            )
        },
    ) { padding ->
        val rows = remember(
            client.buffers.toList(),
            client.unread.toMap(),
            client.networks.toMap(),
        ) { buildBufferSections(client) }

        if (rows.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (client.connected) "No buffers yet." else "Connecting…", color = TextSecondary)
            }
            return@Scaffold
        }

        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(rows.size) { index ->
                val section = rows[index]
                Text(
                    section.network,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(28.dp, 18.dp, 16.dp, 6.dp),
                )
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column {
                        section.buffers.forEachIndexed { i, buffer ->
                            BufferRow(
                                buffer = buffer,
                                unread = client.unread[buffer.key] ?: 0,
                                highlight = (client.highlights[buffer.key] ?: 0) > 0,
                                onClick = { onOpen(buffer) },
                            )
                            if (i < section.buffers.lastIndex) {
                                HorizontalDivider(
                                    color = SurfaceRaised,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class BufferSection(val network: String, val buffers: List<Buffer>)

/** Group buffers by network, DMs after channels, unread floated to the top. */
private fun buildBufferSections(client: LurkerClient): List<BufferSection> {
    val byNetwork = client.buffers.groupBy { it.networkName }
    return byNetwork.keys.sortedBy { it.lowercase() }.map { network ->
        BufferSection(
            network = network,
            buffers = byNetwork.getValue(network).sortedWith(
                compareByDescending<Buffer> { (client.unread[it.key] ?: 0) > 0 }
                    .thenByDescending { it.isChannel }
                    .thenBy { it.isServerBuffer } // Server row anchors the bottom, like iOS
                    .thenBy { it.target.lowercase() },
            ),
        )
    }
}

@Composable
private fun BufferRow(buffer: Buffer, unread: Int, highlight: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buffer.displayName,
            fontSize = 17.sp,
            fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (unread > 0) {
            UnreadBadge(unread, highlight)
            Spacer(Modifier.width(8.dp))
        }
        Text("›", color = TextSecondary, fontSize = 20.sp)
    }
}

@Composable
private fun UnreadBadge(count: Int, highlight: Boolean) {
    Box(
        Modifier
            .background(if (highlight) AlertRed else PillGray, RoundedCornerShape(11.dp))
            .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(
            if (count > 999) "999+" else "$count",
            color = Color.White,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ConnectionDot(connected: Boolean) {
    Box(Modifier.size(9.dp).background(if (connected) OnlineGreen else AlertRed, CircleShape))
}

// ---- Chat ------------------------------------------------------------------

/** One rendered row in the chat list, after grouping and divider insertion. */
private sealed interface ChatRow {
    data class Bubble(val msg: Msg, val first: Boolean, val last: Boolean) : ChatRow
    data class Action(val msg: Msg) : ChatRow
    data class SystemLine(val msg: Msg) : ChatRow
    data object NewMessages : ChatRow
}

/**
 * Fold messages into iMessage-style groups: consecutive bubbles from the same
 * nick stack (nick label once, tight gaps, timestamp on the last), and the
 * "New messages" divider slots in where the read cursor sat when the buffer
 * was opened. The divider also breaks a group — messages after it are new.
 */
private fun buildChatRows(messages: List<Msg>, dividerAfter: Long?): List<ChatRow> {
    val out = ArrayList<ChatRow>(messages.size + 1)
    var dividerPending = dividerAfter != null && messages.any { it.id > dividerAfter && !it.self }
    var prevBubble: Msg? = null
    for (msg in messages) {
        var groupBroke = false
        if (dividerPending && dividerAfter != null && msg.id > dividerAfter && !msg.self) {
            out.add(ChatRow.NewMessages)
            dividerPending = false
            groupBroke = true
        }
        when {
            msg.system -> { out.add(ChatRow.SystemLine(msg)); prevBubble = null }
            msg.type == "action" -> { out.add(ChatRow.Action(msg)); prevBubble = null }
            else -> {
                val sameGroup = !groupBroke && prevBubble != null &&
                    prevBubble.nick == msg.nick && prevBubble.self == msg.self
                if (sameGroup) {
                    // When grouped, the row just before this one is always the
                    // previous bubble (anything else resets prevBubble): demote it.
                    val prev = out.last() as ChatRow.Bubble
                    out[out.lastIndex] = prev.copy(last = false)
                }
                out.add(ChatRow.Bubble(msg, first = !sameGroup, last = true))
                prevBubble = msg
            }
        }
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    client: LurkerClient,
    buffer: Buffer,
    onBack: () -> Unit,
    onOpenBuffer: (Buffer) -> Unit,
) {
    BackHandler(onBack = onBack)
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var showMembers by remember { mutableStateOf(false) }
    val messages = client.messagesByBuffer[buffer.key] ?: emptyList()
    val divider = client.dividerAfter[buffer.key]
    val rows = remember(messages, divider) { buildChatRows(messages, divider) }
    val listState = rememberLazyListState()

    // DCC send: remember who we're sending to across the system file picker.
    val context = LocalContext.current
    var dccNick by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val nick = dccNick
        dccNick = null
        val networkId = buffer.networkId
        if (uri != null && nick != null && networkId != null) {
            val upload = readUpload(context, uri)
            if (upload == null) {
                client.dccError = "Couldn't read that file (or it's over ${MAX_DCC_UPLOAD_MB}MB)."
            } else {
                client.dccSendFile(networkId, nick, upload.first, upload.second)
            }
        }
    }

    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.scrollToItem(rows.lastIndex)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(SurfaceDark, RoundedCornerShape(18.dp))
                            .clickable(onClick = onBack)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        ConnectionDot(client.connected)
                        Spacer(Modifier.width(7.dp))
                        Text(buffer.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(5.dp))
                        Text("⌄", color = TextSecondary, fontSize = 13.sp)
                    }
                },
                actions = {
                    if (buffer.isChannel) {
                        val count = client.members[buffer.key]?.size ?: 0
                        TextButton(onClick = { showMembers = true }) {
                            Text(
                                if (count > 0) "☰ $count" else "☰",
                                color = TextSecondary,
                                fontSize = 15.sp,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(rows.size) { i ->
                    when (val row = rows[i]) {
                        is ChatRow.Bubble -> MessageBubble(row.msg, row.first, row.last)
                        is ChatRow.Action -> ActionLine(row.msg)
                        is ChatRow.SystemLine -> SystemLine(row.msg)
                        ChatRow.NewMessages -> NewMessagesDivider()
                    }
                }
                item { Spacer(Modifier.height(6.dp)) }
            }

            if (!buffer.isSystem) {
                Composer(draft, { draft = it }, buffer.displayName) {
                    val text = draft.text.trim()
                    if (text.isEmpty()) return@Composer
                    when (val parsed = Commands.parse(text, buffer.target, buffer.networkId != null)) {
                        is ParsedInput.Ops -> {
                            client.execute(buffer, parsed.ops)
                            parsed.openTarget?.let { target ->
                                buffer.networkId?.let { onOpenBuffer(client.focusTarget(it, target)) }
                            }
                        }
                        is ParsedInput.Local -> client.localNotice(buffer, parsed.message)
                    }
                    draft = TextFieldValue("")
                }
            }
        }
    }

    if (showMembers) {
        MemberSheet(
            client = client,
            buffer = buffer,
            onDismiss = { showMembers = false },
            onOpenBuffer = { showMembers = false; onOpenBuffer(it) },
            onPickFileFor = { nick ->
                dccNick = nick
                showMembers = false
                filePicker.launch("*/*")
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberSheet(
    client: LurkerClient,
    buffer: Buffer,
    onDismiss: () -> Unit,
    onOpenBuffer: (Buffer) -> Unit,
    onPickFileFor: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<Member?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
    ) {
        val roster = remember(client.members[buffer.key]) {
            (client.members[buffer.key] ?: emptyList())
                .sortedWith(compareBy({ it.rank }, { it.nick.lowercase() }))
        }
        val sel = selected
        if (sel == null) {
            Text(
                "${buffer.displayName} — ${roster.size} member${if (roster.size == 1) "" else "s"}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
            if (roster.isEmpty()) {
                Text(
                    "No member list yet — the server sends it when the channel is joined.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(roster.size) { i ->
                    val m = roster[i]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = m }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            m.prefix.ifEmpty { " " },
                            color = if (m.canModerate) NoticeAmber else OnlineGreen,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(20.dp),
                        )
                        Text(
                            m.nick,
                            color = nickColor(m.nick).copy(alpha = if (m.away) 0.45f else 1f),
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (m.away) Text("away", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        } else {
            MemberActions(
                client = client,
                buffer = buffer,
                member = sel,
                canModerate = client.myMember(buffer)?.canModerate == true,
                onBack = { selected = null },
                onDone = onDismiss,
                onOpenBuffer = onOpenBuffer,
                onPickFileFor = onPickFileFor,
            )
        }
    }
}

@Composable
private fun MemberActions(
    client: LurkerClient,
    buffer: Buffer,
    member: Member,
    canModerate: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenBuffer: (Buffer) -> Unit,
    onPickFileFor: (String) -> Unit,
) {
    val networkId = buffer.networkId ?: return
    val nick = member.nick
    val chan = buffer.target
    fun raw(line: String) {
        client.execute(buffer, listOf(WireOp("raw", line = line)))
        onDone()
    }

    Column(Modifier.padding(bottom = 24.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 22.sp) }
            Text(
                "${member.prefix}$nick",
                color = nickColor(nick),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
            member.host?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, color = TextSecondary, fontSize = 12.sp)
            }
        }
        SheetAction("Whois") { raw("WHOIS $nick") }
        SheetAction("Send a message") {
            onOpenBuffer(client.focusTarget(networkId, nick))
        }
        SheetAction("DCC: send a file…") { onPickFileFor(nick) }
        SheetAction("DCC: start a chat") {
            client.dccChat(networkId, nick, open = true)
            onOpenBuffer(client.focusTarget(networkId, "=$nick"))
        }
        if (canModerate) {
            HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 6.dp))
            val isOp = "o" in member.modes
            val hasVoice = "v" in member.modes
            SheetAction(if (isOp) "Take op" else "Give op") {
                raw("MODE $chan ${if (isOp) "-o" else "+o"} $nick")
            }
            SheetAction(if (hasVoice) "Remove voice" else "Give voice") {
                raw("MODE $chan ${if (hasVoice) "-v" else "+v"} $nick")
            }
            SheetAction("Kick", danger = true) { raw("KICK $chan $nick") }
            SheetAction("Ban", danger = true) { raw("MODE $chan +b ${member.banMask}") }
            SheetAction("Kick + ban", danger = true) {
                client.execute(
                    buffer,
                    listOf(
                        WireOp("raw", line = "MODE $chan +b ${member.banMask}"),
                        WireOp("raw", line = "KICK $chan $nick"),
                    ),
                )
                onDone()
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (danger) AlertRed else Color.White,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp),
    )
}

/** Max file we'll buffer for a DCC send (the server's own ceiling is higher). */
private const val MAX_DCC_UPLOAD_MB = 100

/** Read a content Uri into (displayName, bytes); null when unreadable/too big. */
private fun readUpload(context: android.content.Context, uri: android.net.Uri): Pair<String, ByteArray>? {
    return try {
        var name = "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > MAX_DCC_UPLOAD_MB * 1024 * 1024) return null
        name to bytes
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun MessageBubble(msg: Msg, first: Boolean, last: Boolean) {
    val self = msg.self
    // Corner tightening on the grouped edge, iMessage style.
    val big = 18.dp
    val tight = 5.dp
    val shape = RoundedCornerShape(
        topStart = if (!self && !first) tight else big,
        topEnd = if (self && !first) tight else big,
        bottomStart = if (!self && !last) tight else big,
        bottomEnd = if (self && !last) tight else big,
    )
    Column(
        horizontalAlignment = if (self) Alignment.End else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (self) 64.dp else 12.dp,
                end = if (self) 12.dp else 64.dp,
                top = if (first) 8.dp else 1.dp,
                bottom = 1.dp,
            ),
    ) {
        if (first && !self) {
            Text(
                msg.nick,
                color = nickColor(msg.nick),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
            )
        }
        Box(
            Modifier
                .background(if (self) AccentBlue else SurfaceRaised, shape)
                .padding(horizontal = 13.dp, vertical = 7.dp),
        ) {
            val body = mircAnnotated(msg.text, if (self) Color.White else AccentBlue)
            Text(
                if (msg.type == "error") buildAnnotatedString {
                    withStyle(SpanStyle(color = AlertRed)) { append(body) }
                } else body,
                color = when {
                    self -> Color.White
                    msg.type == "notice" -> NoticeAmber
                    else -> Color.White
                },
                fontSize = 16.sp,
            )
        }
        if (last) {
            Text(
                formatTime(msg.time) ?: "",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(
                    start = if (self) 0.dp else 14.dp,
                    end = if (self) 6.dp else 0.dp,
                    top = 3.dp,
                    bottom = 5.dp,
                ),
            )
        }
    }
}

@Composable
private fun ActionLine(msg: Msg) {
    val line = buildAnnotatedString {
        withStyle(SpanStyle(color = TextSecondary, fontStyle = FontStyle.Italic)) { append("* ") }
        withStyle(
            SpanStyle(color = nickColor(msg.nick), fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold),
        ) { append(msg.nick) }
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(" ") ; append(mircAnnotated(msg.text, AccentBlue)) }
    }
    Text(
        line,
        fontSize = 14.sp,
        color = Color.White,
        modifier = Modifier.fillMaxWidth().padding(16.dp, 5.dp),
    )
}

@Composable
private fun SystemLine(msg: Msg) {
    if (msg.type == "whois") {
        // WHOIS blocks read like terminal output: left-aligned, monospace.
        Text(
            msg.text,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .padding(12.dp, 8.dp),
        )
        return
    }
    Text(
        msg.text,
        fontSize = 12.sp,
        fontStyle = FontStyle.Italic,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(24.dp, 3.dp),
    )
}

@Composable
private fun NewMessagesDivider() {
    Text(
        "New messages",
        color = AlertRed,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
    )
}

/**
 * Wrap the selection in mIRC control codes, or insert the opening code at the
 * cursor (mIRC toggle semantics make a lone opener valid — it formats what's
 * typed after it).
 */
private fun applyFormat(v: TextFieldValue, code: String, end: String = code): TextFieldValue {
    val sel = v.selection
    return if (sel.collapsed) {
        val at = sel.start.coerceIn(0, v.text.length)
        TextFieldValue(
            v.text.substring(0, at) + code + v.text.substring(at),
            TextRange(at + code.length),
        )
    } else {
        val lo = minOf(sel.start, sel.end)
        val hi = maxOf(sel.start, sel.end)
        TextFieldValue(
            v.text.substring(0, lo) + code + v.text.substring(lo, hi) + end + v.text.substring(hi),
            TextRange(hi + code.length + end.length),
        )
    }
}

@Composable
private fun Composer(draft: TextFieldValue, onChange: (TextFieldValue) -> Unit, target: String, onSend: () -> Unit) {
    var showFormat by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (showFormat) FormatBar(draft, onChange)
        Row(
            Modifier.fillMaxWidth().padding(10.dp, 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showFormat = !showFormat }) {
                Text(
                    "Aa",
                    color = if (showFormat) AccentBlue else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
            TextField(
                value = draft,
                onValueChange = onChange,
                placeholder = { Text("Message $target", color = TextSecondary) },
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = AccentBlue,
                ),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            TextButton(onClick = onSend, enabled = draft.text.isNotBlank()) {
                Text(
                    "Send",
                    color = if (draft.text.isNotBlank()) AccentBlue else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun FormatBar(draft: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
    var showColors by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FormatKey("B", bold = true) { onChange(applyFormat(draft, Fmt.BOLD)) }
        FormatKey("I", italic = true) { onChange(applyFormat(draft, Fmt.ITALIC)) }
        FormatKey("U", underline = true) { onChange(applyFormat(draft, Fmt.UNDERLINE)) }
        FormatKey("S", strike = true) { onChange(applyFormat(draft, Fmt.STRIKE)) }
        FormatKey("M", mono = true) { onChange(applyFormat(draft, Fmt.MONO)) }
        Box {
            // Color entry point: a rainbow chip that opens the classic palette.
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(22.dp)
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFFFF453A), Color(0xFFFFD60A), Color(0xFF30D158),
                                Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF453A),
                            ),
                        ),
                        CircleShape,
                    )
                    .clickable { showColors = true },
            )
            DropdownMenu(expanded = showColors, onDismissRequest = { showColors = false }) {
                Column(Modifier.padding(10.dp)) {
                    for (rowStart in 0 until 16 step 8) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (i in rowStart until rowStart + 8) {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .background(Color(Mirc.color(i) ?: 0), CircleShape)
                                        .border(1.dp, PillGray, CircleShape)
                                        .clickable {
                                            showColors = false
                                            // Selection gets color..clear; bare opener at cursor.
                                            onChange(applyFormat(draft, Fmt.color(i), Fmt.COLOR))
                                        },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    TextButton(onClick = {
                        showColors = false
                        onChange(applyFormat(draft, Fmt.RESET, ""))
                    }) { Text("Clear formatting", color = TextSecondary, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun FormatKey(
    label: String,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    strike: Boolean = false,
    mono: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = Color.White,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontFamily = if (mono) FontFamily.Monospace else null,
            textDecoration = when {
                underline -> TextDecoration.Underline
                strike -> TextDecoration.LineThrough
                else -> null
            },
        )
    }
}

// ---- Settings ------------------------------------------------------------

// Category order + labels mirror shared/settingsRegistry.ts CATEGORIES (only
// the registry-driven ones — bespoke panes like Networks/Account are web-only).
// Categories not listed still render, alphabetically after these.
private val CATEGORY_META = listOf(
    "appearance" to "Appearance",
    "chat" to "Chat",
    "input" to "Input bar",
    "uploads" to "Uploads",
    "notifications" to "Notifications",
    "away" to "Away",
    "fserve" to "File server",
    "dcc" to "DCC transfers",
)

// Group headers, mirroring the GROUPS map in shared/settingsRegistry.ts.
private val GROUP_LABELS = mapOf(
    "fonts" to "Fonts", "palette" to "Colors", "messages" to "Message rows",
    "members" to "Member prefixes", "buffer-list" to "Buffer list",
    "nicks" to "Nick coloring", "layout" to "Layout", "misc" to "Misc",
    "consolidate" to "Join/part consolidation", "routing" to "Message routing",
    "channel-behavior" to "Channels", "composing" to "Composing",
    "smart-filter" to "Smart filter", "viewing" to "Viewing",
    "connection" to "Connection", "ctcp" to "CTCP replies",
    "system_features" to "System text features", "autocomplete" to "Autocomplete",
    "formatting" to "Formatting", "auto-away" to "Auto-away",
    "alerts" to "Alerts", "push_filters" to "Push filters", "pipeline" to "Image pipeline",
    "fserve" to "File server", "fserve-queue" to "Queue & sends",
    "fserve-ads" to "Advertising", "fserve-find" to "Search (@find)",
    "fserve-files" to "File filters",
    "dcc-incoming" to "Incoming", "dcc-outgoing" to "Outgoing", "dcc-notify" to "Notifications",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(client: LurkerClient, onBack: () -> Unit) {
    var category by remember { mutableStateOf<String?>(null) }
    BackHandler { if (category != null) category = null else onBack() }

    val byCategory = remember(client.settingsRegistry.toList()) {
        client.settingsRegistry
            .filter { it.category.isNotEmpty() && it.category != "system" }
            .groupBy { it.category }
    }
    val orderedCategories = remember(byCategory) {
        val known = CATEGORY_META.map { it.first }.filter { it in byCategory }
        known + (byCategory.keys - known.toSet()).sorted()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = {
                    TextButton(onClick = { if (category != null) category = null else onBack() }) {
                        Text("‹", color = AccentBlue, fontSize = 26.sp)
                    }
                },
                title = {
                    Text(
                        category?.let { c -> CATEGORY_META.firstOrNull { it.first == c }?.second ?: c.replaceFirstChar { ch -> ch.uppercase() } }
                            ?: "Settings",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    ) { padding ->
        when {
            !client.settingsLoaded ->
                Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            client.settingsRegistry.isEmpty() -> Column(Modifier.padding(padding).padding(24.dp)) {
                Text(client.settingsError ?: "No settings available on this server.")
            }

            category == null -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                client.settingsError?.let { err ->
                    item { Text(err, color = AlertRed, modifier = Modifier.padding(16.dp)) }
                }
                item { Spacer(Modifier.height(8.dp)) }
                items(orderedCategories.size) { i ->
                    val cat = orderedCategories[i]
                    val label = CATEGORY_META.firstOrNull { it.first == cat }?.second
                        ?: cat.replaceFirstChar { it.uppercase() }
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { category = cat }
                                .padding(16.dp, 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label, fontSize = 17.sp)
                                Text(
                                    "${byCategory.getValue(cat).size} settings",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                            Text("›", color = TextSecondary, fontSize = 20.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }

            else -> {
                // groupBy preserves registry order, which is the designed order.
                val groups = remember(category) {
                    byCategory[category].orEmpty().groupBy { it.group }
                }
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    for ((group, options) in groups) {
                        item {
                            Text(
                                GROUP_LABELS[group] ?: group.replaceFirstChar { it.uppercase() },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                modifier = Modifier.fillMaxWidth().padding(28.dp, 18.dp, 16.dp, 6.dp),
                            )
                        }
                        item {
                            Surface(
                                color = SurfaceDark,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) {
                                Column {
                                    options.forEachIndexed { i, option ->
                                        SettingRow(client, option)
                                        if (i < options.lastIndex) {
                                            HorizontalDivider(
                                                color = SurfaceRaised,
                                                modifier = Modifier.padding(start = 16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(client: LurkerClient, option: SettingOption) {
    val overridden = client.settingsValues.containsKey(option.key)
    // Bootstrap `values` carries only overrides; everything else shows its default.
    val value = if (overridden) client.settingsValues[option.key] else option.default
    Column(Modifier.fillMaxWidth().padding(16.dp, 10.dp)) {
        when (option.type) {
            "bool" -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(option.label, modifier = Modifier.weight(1f))
                Switch(
                    checked = value as? Boolean ?: false,
                    onCheckedChange = { client.patchSetting(option.key, it) },
                )
            }

            "enum" -> EnumSetting(option, value?.toString()) { client.patchSetting(option.key, it) }

            "int" -> EditableSetting(option.label, value?.toString() ?: "", number = true) {
                it.toIntOrNull()?.let { n ->
                    val clamped = n.coerceIn(option.min ?: n, option.max ?: n)
                    client.patchSetting(option.key, clamped)
                }
            }

            "string-list" -> EditableSetting(
                option.label,
                (value as? List<*>)?.joinToString(", ") ?: "",
                secret = false,
            ) { text ->
                client.patchSetting(option.key, text.split(",").map { it.trim() }.filter { it.isNotEmpty() })
            }

            else -> EditableSetting(option.label, value?.toString() ?: "", secret = option.type == "secret") {
                client.patchSetting(option.key, it)
            }
        }
        if (option.description.isNotEmpty()) {
            Text(
                option.description,
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(option.key, color = PillGray, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (overridden) {
                Text(
                    "Reset",
                    color = AccentBlue,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { client.resetSetting(option.key) }
                        .padding(4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumSetting(option: SettingOption, current: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(option.label, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(current ?: "—") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                option.choices.forEach { choice ->
                    DropdownMenuItem(text = { Text(choice) }, onClick = { open = false; onPick(choice) })
                }
            }
        }
    }
}

@Composable
private fun EditableSetting(
    label: String,
    initial: String,
    number: Boolean = false,
    secret: Boolean = false,
    onCommit: (String) -> Unit,
) {
    // Keyed by the initial value so a server-side update re-seeds the field.
    var text by remember(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---- DCC -----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DccScreen(client: LurkerClient, onOpenBuffer: (Buffer) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) }
                },
                title = { Text("DCC transfers", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        if (!client.dccEnabled) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text("DCC is not enabled for this account.", Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        val transfers = remember(client.transfers.toMap()) {
            client.transfers.values.sortedByDescending { it.id }
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            client.dccError?.let { err ->
                item { Text(err, color = AlertRed, modifier = Modifier.padding(16.dp)) }
            }
            item { DccStartCard(client, onOpenBuffer) }
            if (transfers.isEmpty()) {
                item { Text("No transfers.", Modifier.padding(16.dp), color = TextSecondary) }
            }
            items(transfers.size) { i -> TransferRow(client, transfers[i]) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Start an outgoing DCC send or chat: pick a network, type a nick, go. */
@Composable
private fun DccStartCard(client: LurkerClient, onOpenBuffer: (Buffer) -> Unit) {
    val context = LocalContext.current
    var nick by remember { mutableStateOf("") }
    val connected = client.networks.values.filter { it.connected }
    var networkId by remember(connected.map { it.id }) {
        mutableStateOf(connected.firstOrNull()?.id)
    }
    var netMenu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = networkId
        val who = nick.trim()
        if (uri != null && id != null && who.isNotEmpty()) {
            val upload = readUpload(context, uri)
            if (upload == null) {
                client.dccError = "Couldn't read that file (or it's over ${MAX_DCC_UPLOAD_MB}MB)."
            } else {
                client.dccSendFile(id, who, upload.first, upload.second)
            }
        }
    }

    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Start a transfer or chat", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    TextButton(onClick = { netMenu = true }) {
                        Text(
                            connected.firstOrNull { it.id == networkId }?.name ?: "No network",
                            color = AccentBlue,
                        )
                    }
                    DropdownMenu(expanded = netMenu, onDismissRequest = { netMenu = false }) {
                        connected.forEach { n ->
                            DropdownMenuItem(
                                text = { Text(n.name) },
                                onClick = { networkId = n.id; netMenu = false },
                            )
                        }
                    }
                }
                TextField(
                    value = nick,
                    onValueChange = { nick = it },
                    placeholder = { Text("nick", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceRaised,
                        unfocusedContainerColor = SurfaceRaised,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = AccentBlue,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { picker.launch("*/*") },
                    enabled = networkId != null && nick.isNotBlank(),
                ) { Text("Send a file…") }
                TextButton(
                    onClick = {
                        val id = networkId ?: return@TextButton
                        val who = nick.trim()
                        client.dccChat(id, who, open = true)
                        onOpenBuffer(client.focusTarget(id, "=$who"))
                    },
                    enabled = networkId != null && nick.isNotBlank(),
                ) { Text("Start a chat") }
            }
        }
    }
}

@Composable
private fun TransferRow(client: LurkerClient, t: DccTransfer) {
    Column(Modifier.fillMaxWidth().padding(16.dp, 10.dp)) {
        Text(t.filename, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(
            "${if (t.isSend) "to" else "from"} ${t.peerNick} · ${t.state}" + (t.error?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        if (t.isActive && t.total > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth())
            Text(
                "${humanBytes(t.received)} / ${humanBytes(t.total)}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (t.isPending) {
                TextButton(onClick = { client.dccAccept(t.id) }) { Text("Accept") }
                TextButton(onClick = { client.dccReject(t.id) }) { Text("Reject") }
            } else if (t.isActive) {
                TextButton(onClick = { client.dccCancel(t.id) }) { Text("Cancel") }
            }
        }
    }
    HorizontalDivider()
}

private fun humanBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = n.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024; i++
    }
    return String.format("%.1f %s", value, units[i])
}
