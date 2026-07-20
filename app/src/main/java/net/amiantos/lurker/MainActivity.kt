// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import net.amiantos.lurker.ui.theme.AccentBlue
import net.amiantos.lurker.ui.theme.AlertRed
import net.amiantos.lurker.ui.theme.AppTheme
import net.amiantos.lurker.ui.theme.CanvasBlack
import net.amiantos.lurker.ui.theme.GlassBorder
import net.amiantos.lurker.ui.theme.LurkerTheme
import net.amiantos.lurker.ui.theme.TextPrimary
import net.amiantos.lurker.ui.theme.Ui
import net.amiantos.lurker.ui.theme.NoticeAmber
import net.amiantos.lurker.ui.theme.OnlineGreen
import net.amiantos.lurker.ui.theme.PillGray
import net.amiantos.lurker.ui.theme.SurfaceDark
import net.amiantos.lurker.ui.theme.SurfaceRaised
import net.amiantos.lurker.ui.theme.TextSecondary
import net.amiantos.lurker.ui.theme.formatTime
import net.amiantos.lurker.ui.theme.nickColor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.imageLoader
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** The frosted-pane recipe every glass surface shares. */
@Composable
private fun glassStyle(): HazeStyle = HazeStyle(
    backgroundColor = CanvasBlack,
    tints = listOf(HazeTint(SurfaceDark.copy(alpha = 0.55f))),
    blurRadius = 22.dp,
    noiseFactor = 0.02f,
)

/** Where the app is: the buffer list, a chat, settings, DCC, or networks. */
private sealed interface Screen {
    data object Buffers : Screen
    data class Chat(val buffer: Buffer) : Screen
    data object Settings : Screen
    data object Dcc : Screen
    data object Networks : Screen
    data object Search : Screen
    data object Friends : Screen
    data object Ignores : Screen
    data object ChannelList : Screen
    data class NetworkEdit(val config: NetworkConfig?) : Screen
}

class MainActivity : FragmentActivity() {
    // Process-scoped: the socket + buffer state outlive Activity recreation
    // (rotation, the tablet/phone layout switch) and are shared with the
    // background service, instead of a fresh client per Activity.
    private val client: LurkerClient get() = (application as LurkerApp).client
    private val prefs by lazy { Prefs(this) }

    /** True while the biometric lock is covering the UI (see [promptUnlock]). */
    private var locked by mutableStateOf(false)

    /** A file arriving via the system share sheet, waiting for a buffer pick. */
    private var sharedUri by mutableStateOf<Uri?>(null)

    /** (networkId, target) to open from a tapped notification, once composed. */
    private var pendingDeepLink by mutableStateOf<Pair<Int, String>?>(null)

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* declining is fine — the socket + badges still work, just no notifications */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cover the UI immediately if the lock is on and we haven't unlocked this
        // process; onResume runs the prompt.
        locked = prefs.biometricLock && !(application as LurkerApp).unlocked
        Ui.theme = AppTheme.from(prefs.theme)
        Ui.inlineMedia = prefs.inlineMedia
        Ui.chatTextScale = prefs.chatTextScale
        // Don't start a backend until a mode is settled. Existing users have a
        // saved Lurker session (clientMode still null on first update) — treat them
        // as Lurker mode and start immediately; only a genuinely fresh install
        // (no mode, no session) waits for the ModePicker.
        if (prefs.clientMode != null || prefs.hasSession) client.start(prefs)
        Notifier.ensureChannels(this)
        // Notify only while backgrounded; posts from the WS thread (thread-safe).
        client.notificationSink = { Notifier.post(applicationContext, it) }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        consumeShareIntent(intent)
        consumeNotificationIntent(intent)
        // Opt-in always-on: revive the liveness anchor on launch (from the
        // foreground, so the background-start rule never bites).
        if (prefs.backgroundConnect && prefs.hasSession) LurkerConnectionService.start(this)
        setContent {
            LurkerTheme {
                // Hoisted above the lock gate: a relock returns early before this
                // line, so declaring it here (below the gate) would forget the open
                // chat on every unlock. Keeping it up top preserves it.
                var screen by remember { mutableStateOf<Screen>(Screen.Buffers) }

                // A dropped session (WS 401 → forceSignOutLocally) flips loggedIn
                // false and clears the token without going through the UI sign-out,
                // so stop the background service here too — otherwise its "staying
                // connected" notification lingers over a dead session.
                LaunchedEffect(client.loggedIn) {
                    if (!client.loggedIn && !prefs.hasSession) {
                        LurkerConnectionService.stop(this@MainActivity)
                    }
                }

                if (locked) {
                    LockScreen(onUnlock = { promptUnlock() })
                    return@LurkerTheme
                }

                // First run: choose Lurker-server mode or direct-IRC/bouncer mode.
                // The chosen backend is picked in LurkerApp by clientMode, and the
                // client is created lazily, so applying a choice restarts the app.
                // Only genuinely fresh installs see the picker — an existing Lurker
                // session means clientMode is effectively "lurker".
                if (prefs.clientMode == null && !prefs.hasSession) {
                    ModePickerScreen(onPick = { mode -> prefs.clientMode = mode; restartApp() })
                    return@LurkerTheme
                }

                if (!client.loggedIn) {
                    LoginScreen(client, prefs)
                    return@LurkerTheme
                }

                // A tapped notification opens its buffer once we're logged in.
                LaunchedEffect(pendingDeepLink) {
                    pendingDeepLink?.let { (nid, tgt) ->
                        val buffer = client.focusTarget(nid, tgt)
                        client.open(buffer)
                        client.setActive(buffer)
                        screen = Screen.Chat(buffer)
                        pendingDeepLink = null
                    }
                }

                // The channel we actually joined (channel-joined) — or an
                // already-open channel we asked to /join — opens itself.
                LaunchedEffect(client.pendingOpen) {
                    client.pendingOpen?.let { buffer ->
                        client.open(buffer)
                        client.setActive(buffer)
                        screen = Screen.Chat(buffer)
                        client.pendingOpen = null
                    }
                }

                // Direct mode with nothing configured yet: drop the user on the
                // Networks screen to add their first IRC network / bouncer.
                LaunchedEffect(prefs.clientMode) {
                    if (prefs.clientMode == "direct" && client.networkConfigs.isEmpty()) {
                        client.loadNetworkConfigs()
                        if (client.networkConfigs.isEmpty()) screen = Screen.Networks
                    }
                }

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Width tiers. THREE-pane (>=1100dp, e.g. landscape tablet):
                    // list | chat | roster, all persistent. TWO-pane (840–1100dp,
                    // e.g. tablet portrait): chat | roster — the channel list is
                    // the full-screen master reached with Back (like the phone), so
                    // the chat pairs with the nicklist, not the list. Below 840:
                    // the unchanged phone flow.
                    val threePane = maxWidth >= 1100.dp
                    val twoPane = maxWidth >= 840.dp && !threePane
                    val active = screen
                    val onSelectBuffer: (Buffer) -> Unit = { buffer ->
                        // Materialize a synthesized Friends row before selecting (see the
                        // phone onOpen note); focusTarget returns held buffers unchanged.
                        val b = buffer.networkId?.let { client.focusTarget(it, buffer.target) }
                            ?: buffer.also { client.open(it) }
                        client.setActive(b); screen = Screen.Chat(b)
                    }
                    if (threePane && (active == Screen.Buffers || active is Screen.Chat)) {
                        TabletHome(
                            client = client,
                            selectedBuffer = (active as? Screen.Chat)?.buffer,
                            sharedUri = sharedUri,
                            onShareConsumed = { sharedUri = null },
                            sharePending = sharedUri != null,
                            onSelect = onSelectBuffer,
                            onDeselect = { client.setActive(null); screen = Screen.Buffers },
                            onSettings = { client.loadSettings(); screen = Screen.Settings },
                            onDcc = { client.loadDcc(); screen = Screen.Dcc },
                            onNetworks = { client.loadNetworkConfigs(); screen = Screen.Networks },
                            onSearch = { screen = Screen.Search },
                            onFriends = { screen = Screen.Friends },
                            onIgnores = { screen = Screen.Ignores },
                            onBrowse = { screen = Screen.ChannelList },
                            onSignOut = { LurkerConnectionService.stop(this@MainActivity); client.signOut() },
                            showList = true,
                            showRoster = true,
                        )
                        return@BoxWithConstraints
                    }
                    if (twoPane && active is Screen.Chat) {
                        // Chat + nicklist; Back drops the list back into view.
                        TabletHome(
                            client = client,
                            selectedBuffer = active.buffer,
                            sharedUri = sharedUri,
                            onShareConsumed = { sharedUri = null },
                            sharePending = sharedUri != null,
                            onSelect = onSelectBuffer,
                            onDeselect = { client.setActive(null); screen = Screen.Buffers },
                            onSettings = {}, onDcc = {}, onNetworks = {},
                            onSearch = {}, onFriends = {}, onIgnores = {}, onBrowse = {}, onSignOut = {},
                            showList = false,
                            showRoster = true,
                        )
                        return@BoxWithConstraints
                    }
                when (val s = screen) {
                    Screen.Buffers -> BufferListScreen(
                        client = client,
                        sharePending = sharedUri != null,
                        onOpen = { buffer ->
                            // A synthesized Friends row isn't in client.buffers yet —
                            // materialize it (ensureBuffer + open-buffer) so Chat opens
                            // a real, hydrating buffer instead of an orphan. focusTarget
                            // returns the existing buffer for ones we already hold; it
                            // also sends open-buffer, so no separate open() call here.
                            val b = buffer.networkId?.let { client.focusTarget(it, buffer.target) }
                                ?: buffer.also { client.open(it) }
                            client.setActive(b)
                            screen = Screen.Chat(b)
                        },
                        onSettings = { client.loadSettings(); screen = Screen.Settings },
                        onDcc = { client.loadDcc(); screen = Screen.Dcc },
                        onNetworks = { client.loadNetworkConfigs(); screen = Screen.Networks },
                        onSearch = { screen = Screen.Search },
                        onFriends = { screen = Screen.Friends },
                        onIgnores = { screen = Screen.Ignores },
                        onBrowse = { screen = Screen.ChannelList },
                        onSignOut = { LurkerConnectionService.stop(this@MainActivity); client.signOut() },
                    )
                    is Screen.Chat -> ChatScreen(
                        client = client,
                        buffer = s.buffer,
                        sharedUri = sharedUri,
                        onShareConsumed = { sharedUri = null },
                        onBack = { client.setActive(null); screen = Screen.Buffers },
                        onOpenBuffer = { buffer ->
                            client.setActive(buffer)
                            screen = Screen.Chat(buffer)
                        },
                    )
                    Screen.Settings -> SettingsScreen(client, prefs) { screen = Screen.Buffers }
                    Screen.Dcc -> DccScreen(
                        client = client,
                        onOpenBuffer = { buffer ->
                            client.setActive(buffer)
                            screen = Screen.Chat(buffer)
                        },
                        onBack = { screen = Screen.Buffers },
                    )
                    Screen.Networks -> NetworksScreen(
                        client = client,
                        onEdit = { screen = Screen.NetworkEdit(it) },
                        onAdd = { screen = Screen.NetworkEdit(null) },
                        onBack = { screen = Screen.Buffers },
                    )
                    Screen.Search -> SearchScreen(
                        client = client,
                        onOpenResult = { networkId, target ->
                            val buffer = client.focusTarget(networkId, target)
                            client.setActive(buffer)
                            screen = Screen.Chat(buffer)
                        },
                        onBack = { screen = Screen.Buffers },
                    )
                    Screen.Friends -> FriendsScreen(
                        client = client,
                        onOpenBuffer = { buffer ->
                            client.setActive(buffer)
                            screen = Screen.Chat(buffer)
                        },
                        onBack = { screen = Screen.Buffers },
                    )
                    Screen.Ignores -> IgnoresScreen(client) { screen = Screen.Buffers }
                    Screen.ChannelList -> ChannelListScreen(
                        client = client,
                        onJoin = { networkId, chan ->
                            // Don't optimistically open — channel-joined focuses the
                            // channel we actually land in (avoids the forwarded ghost).
                            client.join(networkId, chan)
                        },
                        onBack = { screen = Screen.Buffers },
                    )
                    is Screen.NetworkEdit -> NetworkEditScreen(
                        client = client,
                        config = s.config,
                        onBack = { screen = Screen.Networks },
                    )
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeShareIntent(intent)
        consumeNotificationIntent(intent)
    }

    /** Relaunch the app in a fresh process so LurkerApp re-creates its lazy
     *  [LurkerClient] for the newly-chosen clientMode (mode switch = restart). */
    private fun restartApp() = restartAppProcess(this)

    /** A tapped notification carries the buffer to open; stash it for the UI. */
    private fun consumeNotificationIntent(intent: Intent?) {
        val target = intent?.getStringExtra(Notifier.EXTRA_TARGET) ?: return
        val nid = intent.getIntExtra(Notifier.EXTRA_NETWORK_ID, -1)
        if (nid >= 0) pendingDeepLink = nid to target
        intent.removeExtra(Notifier.EXTRA_TARGET) // consume so a config change won't replay
    }

    private fun consumeShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) sharedUri = uri
            intent.action = null // consume so a config change doesn't replay it
        }
    }

    // The bouncer holds our place while we're away; on return, revive a dropped
    // socket and resume from the last message id we saw.
    override fun onResume() {
        super.onResume()
        client.onForeground()
        // Re-lock only after a real trip away (>2s), so a rotation/layout switch
        // doesn't re-prompt. First launch: backgroundedAt is 0 → treated as away.
        if (prefs.biometricLock) {
            val app = application as LurkerApp
            val awayMs = if (app.backgroundedAt > 0) SystemClock.elapsedRealtime() - app.backgroundedAt else Long.MAX_VALUE
            if (!app.unlocked || awayMs > RELOCK_GRACE_MS) {
                app.unlocked = false
                locked = true
                promptUnlock()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        client.onBackground()
        (application as LurkerApp).backgroundedAt = SystemClock.elapsedRealtime()
    }

    /** Show the system biometric / device-credential prompt; unlock on success. */
    private fun promptUnlock() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        // If the device can't authenticate at all (no biometric enrolled AND no
        // PIN/pattern/password), don't strand the user behind a prompt that will
        // only ever error — just let them in. (canAuthenticate is also checked
        // before the toggle is even offered.)
        if (BiometricManager.from(this).canAuthenticate(authenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            unlock()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = unlock()
                // Unrecoverable errors (no hardware, nothing enrolled, permanent
                // lockout, user canceled) arrive here ASYNCHRONOUSLY — the old
                // try/catch never saw them, which could hard-lock the app. Fall
                // open on the terminal ones; transient errors leave the lock up so
                // the "Unlock" button can retry.
                override fun onAuthenticationError(code: Int, err: CharSequence) {
                    when (code) {
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        -> unlock()
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Lurker")
            .setSubtitle("Confirm it's you to open your chats")
            .setAllowedAuthenticators(authenticators)
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: Exception) {
            unlock() // no usable authenticator — don't trap the user out
        }
    }

    private fun unlock() {
        (application as LurkerApp).unlocked = true
        locked = false
    }

    private companion object {
        const val RELOCK_GRACE_MS = 2000L
    }
}

/**
 * Tablet / landscape three-pane home (EXPANDED width): a persistent buffer-list
 * rail | the selected buffer's chat | the channel roster. The phone keeps its
 * screen-by-screen flow untouched; this is only reached above the width gate.
 * Settings/Search/Networks/etc. still open as full-screen overlays through the
 * same handlers the rail invokes.
 */
@Composable
private fun TabletHome(
    client: LurkerClient,
    selectedBuffer: Buffer?,
    sharedUri: Uri?,
    onShareConsumed: () -> Unit,
    sharePending: Boolean,
    onSelect: (Buffer) -> Unit,
    onDeselect: () -> Unit,
    onSettings: () -> Unit,
    onDcc: () -> Unit,
    onNetworks: () -> Unit,
    onSearch: () -> Unit,
    onFriends: () -> Unit,
    onIgnores: () -> Unit,
    onBrowse: () -> Unit,
    onSignOut: () -> Unit,
    // Three-pane keeps the buffer-list rail; two-pane drops it (the chat pairs
    // with the roster and Back returns to the full-screen list).
    showList: Boolean,
    showRoster: Boolean,
) {
    val context = LocalContext.current
    // DCC send from the roster pane: hold the target across the system picker.
    var dccNick by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val nick = dccNick
        dccNick = null
        val networkId = selectedBuffer?.networkId
        if (uri != null && nick != null && networkId != null) {
            val upload = readUpload(context, uri)
            if (upload == null) client.dccError = "Couldn't read that file."
            else client.dccSendFile(networkId, nick, upload.first, upload.second)
        }
    }
    Row(Modifier.fillMaxSize().background(CanvasBlack)) {
        // Left rail (three-pane only) — the full buffer list, overflow menu and all.
        if (showList) {
            Box(Modifier.width(340.dp).fillMaxHeight()) {
                BufferListScreen(
                    client = client,
                    sharePending = sharePending,
                    onOpen = onSelect,
                    onSettings = onSettings,
                    onDcc = onDcc,
                    onNetworks = onNetworks,
                    onSearch = onSearch,
                    onFriends = onFriends,
                    onIgnores = onIgnores,
                    onBrowse = onBrowse,
                    onSignOut = onSignOut,
                )
            }
            VerticalDivider(color = GlassBorder, thickness = 0.5.dp)
        }
        // Middle — the selected conversation.
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (selectedBuffer != null) {
                ChatScreen(
                    client = client,
                    buffer = selectedBuffer,
                    sharedUri = sharedUri,
                    onShareConsumed = onShareConsumed,
                    onBack = onDeselect,
                    onOpenBuffer = onSelect,
                    // Three-pane: the rail is the persistent nav, so no back arrow.
                    // Two-pane: show the back arrow (Back returns to the list).
                    embedded = showList,
                    // No room for a roster column → keep the modal-sheet button.
                    showMembersButton = !showRoster,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a conversation", color = TextSecondary, fontSize = 15.sp)
                }
            }
        }
        // Right — the roster, for channels only (DMs have none) when there's room.
        if (showRoster && selectedBuffer?.isChannel == true) {
            VerticalDivider(color = GlassBorder, thickness = 0.5.dp)
            Box(Modifier.width(260.dp).fillMaxHeight()) {
                MemberPane(
                    client = client,
                    buffer = selectedBuffer,
                    onOpenBuffer = onSelect,
                    onPickFileFor = { nick -> dccNick = nick; filePicker.launch("*/*") },
                )
            }
        }
    }
}

/** Full-bleed cover shown while the biometric lock is engaged. */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().background(CanvasBlack), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text("Lurker is locked", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onUnlock) {
                Text("Unlock", color = AccentBlue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
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

@Composable
private fun ModePickerScreen(onPick: (String) -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("How do you want to connect?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "You can change this later in Settings (it restarts the app).",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            ModeCard(
                title = "Lurker server",
                body = "Connect to a Lurker instance (always-on, history & sync, push). Best experience — needs a Lurker account.",
                onClick = { onPick("lurker") },
            )
            ModeCard(
                title = "Direct IRC / bouncer",
                body = "Connect straight to IRC networks, or to a bouncer (soju, ZNC). No Lurker needed. Note: without a bouncer, a phone can't stay connected in the background.",
                onClick = { onPick("direct") },
            )
        }
    }
}

@Composable
private fun ModeCard(title: String, body: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceDark,
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BufferListScreen(
    client: LurkerClient,
    sharePending: Boolean,
    onOpen: (Buffer) -> Unit,
    onSettings: () -> Unit,
    onDcc: () -> Unit,
    onNetworks: () -> Unit,
    onSearch: () -> Unit,
    onFriends: () -> Unit,
    onIgnores: () -> Unit,
    onBrowse: () -> Unit,
    onSignOut: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    val hazeState = remember { HazeState() }
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize().background(CanvasBlack)) {
        AmbientBackground(Modifier.hazeSource(hazeState, zIndex = 0f))
        val rows = remember(
            client.buffers.toList(),
            client.unread.toMap(),
            client.networks.toMap(),
            client.networkConfigs.toList(),
            client.pins.toMap(),
            client.contacts.toList(),
            client.presence.toMap(),
        ) { buildBufferSections(client) }

        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (client.connected) "No buffers yet." else "Connecting…", color = TextSecondary)
            }
        } else {
            BufferListBody(
                client = client,
                rows = rows,
                sharePending = sharePending,
                topPadding = with(density) { topBarHeightPx.toDp() },
                hazeState = hazeState,
                onOpen = onOpen,
            )
        }

        // Frosted top bar the list scrolls beneath.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topBarHeightPx = it.height }
                .hazeEffect(hazeState, style = glassStyle()),
        ) {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionDot(client.connected)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (client.connected) "Buffers" else "Connecting…",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
                    // Search is a server-side feature — hidden in direct IRC mode.
                    if (client.serverFeatures) {
                        TextButton(onClick = onSearch) { Text("🔍", fontSize = 17.sp) }
                    }
                    TextButton(onClick = { menuOpen = true }) {
                        Text("⋯", color = TextSecondary, fontSize = 22.sp)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // Server-only surfaces (server /LIST cache, server-synced
                        // contacts, server DCC) don't apply to a raw IRC/bouncer link.
                        if (client.serverFeatures) {
                            DropdownMenuItem(text = { Text("Browse channels (/LIST)") }, onClick = { menuOpen = false; onBrowse() })
                        }
                        DropdownMenuItem(text = { Text("Mark all read") }, onClick = { menuOpen = false; client.markAllRead() })
                        if (client.serverFeatures) {
                            DropdownMenuItem(text = { Text("Friends") }, onClick = { menuOpen = false; onFriends() })
                        }
                        DropdownMenuItem(text = { Text("Ignore list") }, onClick = { menuOpen = false; onIgnores() })
                        DropdownMenuItem(text = { Text("Networks") }, onClick = { menuOpen = false; onNetworks() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onSettings() })
                        if (client.serverFeatures) {
                            DropdownMenuItem(text = { Text("DCC transfers") }, onClick = { menuOpen = false; onDcc() })
                        }
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = { menuOpen = false; onSignOut() })
                    }
                },
            )
        }
    }
}

@Composable
private fun BufferListBody(
    client: LurkerClient,
    rows: List<BufferSection>,
    sharePending: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    hazeState: HazeState,
    onOpen: (Buffer) -> Unit,
) {
    LazyColumn(
        // Clip the scroll viewport above the nav bar (padding, not just
        // contentPadding, so rows never render under it mid-scroll) while the
        // ambient background behind still fills edge-to-edge.
        Modifier.fillMaxSize().navigationBarsPadding().hazeSource(hazeState, zIndex = 1f),
        contentPadding = PaddingValues(top = topPadding + 4.dp, bottom = 8.dp),
    ) {
            if (sharePending) {
                item {
                    Text(
                        "Sharing a file — pick a conversation to attach it to.",
                        color = AccentBlue,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 8.dp)
                            .background(SurfaceDark, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                    )
                }
            }
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
                    border = BorderStroke(0.5.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column {
                        section.buffers.forEachIndexed { i, buffer ->
                            // Friend rows are identity rows synthesized from contacts:
                            // they carry a presence dot, are tagged by network (they
                            // span networks), and are NOT closeable/pinnable here (a
                            // friend buffer is unclosable — closing would just collapse
                            // it back to this same row).
                            val isFriendRow = section.network == "Friends"
                            BufferRow(
                                buffer = buffer,
                                unread = client.unread[buffer.key] ?: 0,
                                highlight = (client.highlights[buffer.key] ?: 0) > 0,
                                hasDraft = !client.drafts[buffer.key].isNullOrBlank(),
                                pinned = client.isPinned(buffer),
                                notifyAll = client.isNotifyAlways(buffer),
                                showNetwork = section.network == "★ Pinned" || isFriendRow,
                                online = if (isFriendRow) client.isPresent(buffer.networkId, buffer.target) else null,
                                label = if (isFriendRow) client.friendDisplayName(buffer) else null,
                                onTogglePin = if (isFriendRow || buffer.isSystem || buffer.isServerBuffer) null else {
                                    { client.togglePin(buffer) }
                                },
                                onToggleNotify = if (buffer.isChannel) {
                                    { client.setNotifyAlways(buffer, !client.isNotifyAlways(buffer)) }
                                } else null,
                                onClick = { onOpen(buffer) },
                                onClose = if (isFriendRow || buffer.isSystem || buffer.isServerBuffer) null else {
                                    { client.execute(buffer, listOf(WireOp("close"))) }
                                },
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

private data class BufferSection(val network: String, val buffers: List<Buffer>)

/**
 * Group buffers by network. Sections follow the user's chosen network order
 * (the /api/networks sidebar order, rearrangeable on the Networks screen);
 * within a section the Server row leads, then unread, channels, DMs.
 */
private fun buildBufferSections(client: LurkerClient): List<BufferSection> {
    val out = mutableListOf<BufferSection>()
    // A "Pinned" section leads, intersecting pins with actually-open buffers.
    val pinned = client.buffers.filter { client.isPinned(it) }
    if (pinned.isNotEmpty()) out.add(BufferSection("★ Pinned", pinned.sortedBy { it.target.lowercase() }))

    // A "Friends" section: one row per contact's primary DM, synthesized from the
    // contacts store INDEPENDENTLY of whether a buffer is open server-side — per
    // amiantos, close-state is irrelevant to a friend buffer (the web sidebar is
    // f(buffers ∪ friend-primary-DMs ∪ …)). Reuse the open buffer if we hold one
    // (real casing / shared unread key); otherwise synthesize a transient row that
    // materializes on tap via open-buffer. Friend primary DMs are then hidden from
    // the per-network lists below (the web's friends.primaryDmKeys) so they don't
    // show twice.
    val friendRows = client.contacts.mapNotNull { c ->
        val p = c.primary ?: return@mapNotNull null
        val key = "${p.networkId}::${p.nick}"
        client.buffers.firstOrNull { it.key == key }
            ?: Buffer(p.networkId, p.nick, client.networkName(p.networkId))
    }
    val friendKeys = friendRows.map { it.key }.toSet()
    if (friendRows.isNotEmpty()) {
        out.add(
            BufferSection(
                "Friends",
                friendRows.sortedWith(
                    compareByDescending<Buffer> { client.isPresent(it.networkId, it.target) }
                        .thenBy { it.target.lowercase() },
                ),
            ),
        )
    }

    // Pinned + friend-primary buffers live only in their own sections — drop them
    // from the network group so they don't show twice (matches the web client).
    val byNetwork = client.buffers
        .filterNot { client.isPinned(it) }
        .filterNot { it.key in friendKeys }
        .groupBy { it.networkName }
    byNetwork.keys
        .sortedWith(compareBy({ client.networkOrder(it) }, { it.lowercase() }))
        .forEach { network ->
            out.add(
                BufferSection(
                    network = network,
                    buffers = byNetwork.getValue(network).sortedWith(
                        compareByDescending<Buffer> { it.isServerBuffer } // Server row leads
                            .thenByDescending { (client.unread[it.key] ?: 0) > 0 }
                            .thenByDescending { it.isChannel }
                            .thenBy { it.target.lowercase() },
                    ),
                ),
            )
        }
    return out
}

@Composable
private fun BufferRow(
    buffer: Buffer,
    unread: Int,
    highlight: Boolean,
    hasDraft: Boolean = false,
    pinned: Boolean = false,
    notifyAll: Boolean = false,
    showNetwork: Boolean = false,
    online: Boolean? = null,
    label: String? = null,
    onTogglePin: (() -> Unit)? = null,
    onToggleNotify: (() -> Unit)? = null,
    onClick: () -> Unit,
    onClose: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    if (onClose != null || onTogglePin != null) {
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (onTogglePin != null) {
                DropdownMenuItem(
                    text = { Text(if (pinned) "Unpin" else "Pin to top") },
                    onClick = { menu = false; onTogglePin() },
                )
            }
            if (onToggleNotify != null) {
                DropdownMenuItem(
                    text = { Text(if (notifyAll) "Notify: highlights only" else "Notify on every message") },
                    onClick = { menu = false; onToggleNotify() },
                )
            }
            if (onClose != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (buffer.isChannel) "Close (leaves ${buffer.target})" else "Close conversation",
                            color = AlertRed,
                        )
                    },
                    onClick = { menu = false; onClose() },
                )
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { if (onClose != null) menu = true })
            .padding(16.dp, 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Presence dot for Friends rows: green when present, dim when offline.
        if (online != null) {
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(9.dp)
                    .background(if (online) OnlineGreen else TextSecondary.copy(alpha = 0.4f), CircleShape),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                label ?: buffer.displayName,
                // Explicit: the glass screens sit outside a Scaffold, so there is no
                // implicit Material content color to inherit.
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
            )
            // The Pinned section flattens across networks, so a bare "#foo" is
            // ambiguous when the same channel is joined on several networks —
            // tag each pinned row with its network.
            if (showNetwork && buffer.networkName.isNotBlank()) {
                Text(
                    buffer.networkName,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (hasDraft) {
            Text("✎", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
        }
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
            color = if (highlight) Color.White else TextPrimary,
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
 * Group consecutive messages from the same sender iMessage-style: each message
 * keeps ITS OWN bubble (a full merge made message boundaries invisible), but a
 * group stacks tight — nick label once, tightened corners along the shared
 * edge, timestamp on the last. The "New messages" divider breaks a group.
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
                    prevBubble.nick == msg.nick && prevBubble.self == msg.self &&
                    prevBubble.type == msg.type
                if (sameGroup) {
                    // The row just before this one is always the previous bubble
                    // (anything else resets prevBubble): it's no longer last.
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
    sharedUri: Uri? = null,
    onShareConsumed: () -> Unit = {},
    onBack: () -> Unit,
    onOpenBuffer: (Buffer) -> Unit,
    // The middle pane of the tablet layout: the buffer list is a persistent rail,
    // so this hides the back arrow and yields the back gesture to the host.
    embedded: Boolean = false,
    // Whether to show the roster button that opens the modal member sheet. In the
    // full three-pane layout the roster is its own pane, so this is turned off.
    showMembersButton: Boolean = true,
) {
    BackHandler(enabled = !embedded, onBack = onBack)
    // Seed the composer from the server-synced draft when the buffer opens.
    var draft by remember(buffer.key) {
        mutableStateOf(TextFieldValue(client.drafts[buffer.key] ?: ""))
    }
    // Flush the draft when leaving this buffer.
    DisposableEffect(buffer.key) { onDispose { client.flushDraft(buffer) } }
    // If this buffer is closed out from under us (/part, /close, or a close from
    // another device), leave the chat view rather than stranding an empty one.
    val stillOpen = client.buffers.any { it.key == buffer.key }
    LaunchedEffect(stillOpen) { if (!stillOpen && client.buffers.isNotEmpty()) onBack() }
    // Prefix the composer with "nick: " — the shared reply seam for the
    // long-press action sheet and the swipe-to-reply gesture.
    fun replyTo(m: Msg) {
        // "nick, " — the same ping form nick-completion uses at line start, not ":".
        val t = "${m.nick}, " + draft.text
        draft = TextFieldValue(t, TextRange(t.length))
        client.setDraftLocal(buffer, t)
    }
    var showMembers by remember { mutableStateOf(false) }
    var showE2e by remember { mutableStateOf(false) }
    // Message whose long-press action sheet is open, if any.
    var actionMsg by remember(buffer.key) { mutableStateOf<Msg?>(null) }
    val messages = client.messagesByBuffer[buffer.key] ?: emptyList()
    // No "channel is encrypted" frame exists; infer from recent E2E traffic.
    val e2eActive = remember(messages.size) { messages.takeLast(200).any { it.e2e } }
    val divider = client.dividerAfter[buffer.key]
    val rows = remember(messages, divider) { buildChatRows(messages, divider) }
    val listState = rememberLazyListState()

    // Upload → provider URL → splice into the draft (attach button + share sheet).
    val context = LocalContext.current
    fun uploadIntoDraft(uri: Uri) {
        val upload = readUpload(context, uri)
        if (upload == null) {
            client.localNotice(buffer, "Couldn't read that file.")
            return
        }
        client.uploadFile(upload.first, upload.second) { url, err ->
            if (url != null) {
                val sep = if (draft.text.isEmpty() || draft.text.endsWith(" ")) "" else " "
                val text = draft.text + sep + url + " "
                draft = TextFieldValue(text, TextRange(text.length))
            } else {
                client.localNotice(buffer, "Upload failed: ${err ?: "unknown error"}")
            }
        }
    }
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadIntoDraft(uri)
    }
    // A share-sheet file lands here once a buffer is chosen.
    LaunchedEffect(sharedUri) {
        val uri = sharedUri ?: return@LaunchedEffect
        onShareConsumed()
        uploadIntoDraft(uri)
    }

    // DCC send: remember who we're sending to across the system file picker.
    var dccNick by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val nick = dccNick
        dccNick = null
        val networkId = buffer.networkId
        if (uri != null && nick != null && networkId != null) {
            val upload = readUpload(context, uri)
            if (upload == null) {
                client.dccError = "Couldn't read that file."
            } else {
                client.dccSendFile(networkId, nick, upload.first, upload.second)
            }
        }
    }

    val showLoadOlder = client.hasMoreOlder[buffer.key] == true && buffer.networkId != null
    val loadingOlder = client.loadingOlder[buffer.key] == true
    val typers = client.typing[buffer.key]?.keys?.sorted() ?: emptyList()
    var joinPrompt by remember { mutableStateOf<String?>(null) }
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    // Route taps: images open the in-app viewer, av media goes to the system
    // player picker, everything else to the browser.
    val openLink: (String) -> Unit = { url ->
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        if ((IMAGE_EXTS + MEDIA_EXTS).any { clean.endsWith(it) }) {
            viewerUrl = url
        } else {
            uriHandler.openUri(url)
        }
    }

    viewerUrl?.let { url -> MediaViewerDialog(url, onClose = { viewerUrl = null }) }

    // Reveal the ghost bubble only when already reading the tail.
    LaunchedEffect(typers.isNotEmpty()) {
        if (typers.isNotEmpty() && rows.isNotEmpty() && !listState.canScrollForward) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    joinPrompt?.let { chan ->
        AlertDialog(
            onDismissRequest = { joinPrompt = null },
            containerColor = SurfaceRaised,
            title = { Text("Join $chan?", color = TextPrimary) },
            text = { Text("Join the channel on ${buffer.networkName}?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    joinPrompt = null
                    buffer.networkId?.let { networkId -> client.join(networkId, chan) }
                }) { Text("Join", color = AccentBlue, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { joinPrompt = null }) { Text("Cancel", color = TextSecondary) }
            },
        )
    }
    val oldestId = messages.firstOrNull { it.id > 0 }?.id
    // Set when a load-older is requested: the id whose row we re-anchor to once
    // the prepend lands, so the viewport doesn't jump.
    var anchorId by remember(buffer.key) { mutableStateOf<Long?>(null) }
    val headerCount = if (showLoadOlder) 1 else 0
    // Bumped whenever an inline image finishes loading and resizes its card, so
    // the tail-follow effect below can re-pin the bottom instead of drifting up.
    var mediaTick by remember(buffer.key) { mutableStateOf(0) }

    // Reading position is sacred: follow the tail ONLY when the user is already
    // at (or within a couple rows of) the bottom. The -2 slack also absorbs the
    // index shift of the append itself. Scrolled up = stay put, no fighting.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            // -3 (not -2): one extra row of slack absorbs the brief gap between a
            // message appending and the follow-scroll catching up, so a busy
            // channel doesn't flash "N new" while you're actually at the bottom.
            lastVisible >= info.totalItemsCount - 3
        }
    }
    // The jump chip's count is DERIVED, not incremented: snapshot the newest id
    // when the reader leaves the bottom, then count real messages beyond it —
    // exact even when a burst lands in one recomposition.
    var awaySinceId by remember(buffer.key) { mutableStateOf<Long?>(null) }
    LaunchedEffect(atBottom) {
        awaySinceId = if (atBottom) null else (messages.lastOrNull { it.id > 0 }?.id ?: 0L)
    }
    val missed = awaySinceId?.let { since ->
        messages.count { it.id > since && !it.system && !it.self }
    } ?: 0

    // Opening a buffer lands at the newest message ONCE (regardless of scroll
    // position), the moment its rows populate. After that, live messages only
    // follow the tail if you're already at the bottom — so you never fight it.
    var openScrollDone by remember(buffer.key) { mutableStateOf(false) }
    LaunchedEffect(buffer.key, rows.isNotEmpty()) {
        if (!openScrollDone && rows.isNotEmpty()) {
            listState.scrollToItem(rows.lastIndex + headerCount)
            openScrollDone = true
        }
    }
    // Follow the tail only when the tail itself changed — a prepend of older
    // history grows the list without moving the newest message.
    val tailSig = messages.lastOrNull()?.let { it.id to it.text.length }
    LaunchedEffect(tailSig) {
        if (openScrollDone && anchorId == null && atBottom) {
            listState.scrollToItem(rows.lastIndex + headerCount)
        }
    }
    // An image resizing its card mustn't shove the tail out from under a reader
    // who's sitting at the bottom — re-pin when one loads.
    LaunchedEffect(mediaTick) {
        if (mediaTick > 0 && openScrollDone && anchorId == null && atBottom) {
            listState.scrollToItem(rows.lastIndex + headerCount)
        }
    }
    // Older page landed: put the previously-oldest row back under the finger.
    LaunchedEffect(oldestId) {
        val anchor = anchorId ?: return@LaunchedEffect
        if (oldestId != null && oldestId < anchor) {
            val idx = rows.indexOfFirst { r ->
                val id = when (r) {
                    is ChatRow.Bubble -> r.msg.id
                    is ChatRow.Action -> r.msg.id
                    is ChatRow.SystemLine -> r.msg.id
                    ChatRow.NewMessages -> -1L
                }
                id >= anchor
            }
            if (idx >= 0) listState.scrollToItem(idx + headerCount)
            anchorId = null
        }
    }

    // Real glassmorphism: the message list is the haze SOURCE and scrolls
    // edge-to-edge; the top bar and composer are frosted panes that blur
    // whatever passes beneath them.
    val hazeState = remember { HazeState() }
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableStateOf(0) }
    var bottomBarHeightPx by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize().background(CanvasBlack)) {
        // Ambient wash is the base haze source (zIndex 0) so the bars blur it even
        // where the message list is sparse; messages layer on top at zIndex 1.
        AmbientBackground(Modifier.hazeSource(hazeState, zIndex = 0f))
        // Chat text follows the synced look.font.size.mobile setting (web px
        // ~ sp); +2 keeps the historical default (14 -> 16sp). Ui.chatTextScale
        // is the device-local bump (tablets read tiny at the mobile default).
        val baseSize = (client.settingInt("look.font.size.mobile", 14) + 2 + Ui.chatTextScale).coerceIn(11, 30)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState, zIndex = 1f),
            contentPadding = PaddingValues(
                top = with(density) { topBarHeightPx.toDp() } + 4.dp,
                // Composer overlay height when present (it already includes the
                // nav-bar inset); bare nav-bar inset otherwise (:system: has no
                // composer and its lines were sliding under the task bar).
                bottom = maxOf(
                    with(density) { bottomBarHeightPx.toDp() },
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ) + 6.dp,
            ),
        ) {
            if (showLoadOlder) {
                item {
                    TextButton(
                        onClick = { anchorId = oldestId; client.loadOlder(buffer) },
                        enabled = !loadingOlder,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (loadingOlder) "Loading…" else "Load older messages",
                            color = if (loadingOlder) TextSecondary else AccentBlue,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            items(rows.size) { i ->
                when (val row = rows[i]) {
                    is ChatRow.Bubble -> MessageBubble(
                        row.msg, row.first, row.last, baseSize, openLink,
                        onMediaLoaded = { mediaTick++ },
                        onAction = { actionMsg = it },
                        onSwipeReply = { replyTo(it) },
                    )
                    is ChatRow.Action -> ActionLine(row.msg, baseSize, openLink)
                    is ChatRow.SystemLine -> SystemLine(
                        row.msg,
                        onJoin = if (buffer.networkId != null) {
                            { chan -> joinPrompt = chan }
                        } else null,
                    )
                    ChatRow.NewMessages -> NewMessagesDivider()
                }
            }
            if (typers.isNotEmpty()) {
                item { TypingBubble(typers) }
            }
        }

        // Frosted top bar.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topBarHeightPx = it.height }
                .hazeEffect(hazeState, style = glassStyle()),
        ) {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    if (!embedded) {
                        TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) }
                    }
                },
                title = {
                    var titleMenu by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(SurfaceDark, RoundedCornerShape(18.dp))
                                .border(0.5.dp, GlassBorder, RoundedCornerShape(18.dp))
                                .combinedClickable(
                                    onClick = { if (!embedded) onBack() },
                                    onLongClick = {
                                        if (!buffer.isSystem && !buffer.isServerBuffer) titleMenu = true
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            ConnectionDot(client.connected)
                            Spacer(Modifier.width(7.dp))
                            if (e2eActive) {
                                Text("🔒", fontSize = 12.sp)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(buffer.displayName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(5.dp))
                            Text("⌄", color = TextSecondary, fontSize = 13.sp)
                        }
                        DropdownMenu(expanded = titleMenu, onDismissRequest = { titleMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Encryption (E2E)", color = OnlineGreen) },
                                onClick = { titleMenu = false; showE2e = true; client.execute(buffer, listOf(WireOp("e2e", target = buffer.target, line = "status"))) },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (buffer.isChannel) "Close (leaves ${buffer.target})" else "Close conversation",
                                        color = AlertRed,
                                    )
                                },
                                onClick = {
                                    titleMenu = false
                                    client.execute(buffer, listOf(WireOp("close")))
                                    onBack()
                                },
                            )
                        }
                    }
                },
                actions = {
                    if (buffer.isChannel && showMembersButton) {
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
        }

        // Frosted composer, hugging the keyboard.
        if (!buffer.isSystem) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomBarHeightPx = it.height }
                    .hazeEffect(hazeState, style = glassStyle())
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // Completion: the word under the cursor drives a suggestion strip —
                // nicks in channels, /commands at line start. No Tab key on mobile.
                val word = Completion.wordAt(draft.text, draft.selection.start)
                val suggestions = remember(
                    draft.text, draft.selection.start,
                    client.members[buffer.key], client.buffers.size, client.chanlistRows.size,
                ) {
                    when {
                        word.text.startsWith("/") && word.start == 0 ->
                            Completion.commands(word.text, Completion.VERBS)
                        word.text.length >= 2 && (word.text.startsWith("#") || word.text.startsWith("&")) -> {
                            // Channels you're in first, then anything the /LIST browser pulled in.
                            val known = client.buffers.filter { it.isChannel }.map { it.target } +
                                client.chanlistRows.map { it.channel }
                            Completion.channels(word.text, known).take(12)
                        }
                        word.text.length >= 1 && !word.text.startsWith("/") -> {
                            val recents = (client.messagesByBuffer[buffer.key] ?: emptyList())
                                .asReversed().asSequence()
                                .filter { !it.system && it.nick.isNotBlank() }
                                .map { it.nick }.distinct().take(30).toList()
                            val roster = client.members[buffer.key]?.map { it.nick } ?: emptyList()
                            val self = buffer.networkId?.let { client.networks[it]?.nick }
                            Completion.nicks(word.text, recents, roster, self).take(12)
                        }
                        else -> emptyList()
                    }
                }
                fun applySuggestion(pick: String): TextFieldValue {
                    // The "nick, " ping form only applies to a nick opening the line —
                    // not commands or channel names.
                    val isChannel = pick.startsWith("#") || pick.startsWith("&")
                    val atLineStart = word.start == 0 && !pick.startsWith("/") && !isChannel
                    val insert = pick + if (atLineStart) ", " else " "
                    val before = draft.text.substring(0, word.start)
                    val after = draft.text.substring((word.start + word.text.length).coerceAtMost(draft.text.length))
                    val newText = before + insert + after
                    return TextFieldValue(newText, TextRange(before.length + insert.length))
                }
                Composer(
                    draft = draft,
                    onChange = { new ->
                        val hadText = draft.text.isNotBlank()
                        draft = new
                        client.setDraftLocal(buffer, new.text)
                        val hasText = new.text.isNotBlank()
                        // Commands never broadcast composing state.
                        if (hasText && !new.text.startsWith("/")) {
                            client.notifyTyping(buffer, active = true)
                        } else if (hadText && !hasText) {
                            client.notifyTyping(buffer, active = false)
                        }
                    },
                    target = buffer.displayName,
                    uploading = client.uploading,
                    onAttach = { uploadPicker.launch("*/*") },
                    suggestions = suggestions,
                    onPickSuggestion = { draft = applySuggestion(it); client.setDraftLocal(buffer, draft.text) },
                ) {
                    val raw = draft.text.trim()
                    if (raw.isEmpty()) return@Composer
                    client.notifyTyping(buffer, active = false)
                    client.addInputHistory(buffer, raw)
                    val myNick = buffer.networkId?.let { client.networks[it]?.nick }
                    val text = Commands.expandAlias(raw, client.aliases, myNick, buffer.target)
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
                    client.setDraftLocal(buffer, "") // clears + syncs draft-clear
                }
            }
        }

        // Jump back to the tail after reading history.
        if (!atBottom && rows.isNotEmpty()) {
            val scope = rememberCoroutineScope()
            Text(
                if (missed > 0) "↓ $missed new" else "↓ Latest",
                color = if (missed > 0) AccentBlue else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = with(density) { bottomBarHeightPx.toDp() } + 14.dp,
                    )
                    .background(SurfaceRaised, RoundedCornerShape(16.dp))
                    .border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .clickable {
                        scope.launch {
                            listState.scrollToItem(maxOf(0, listState.layoutInfo.totalItemsCount - 1))
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
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

    if (showE2e) {
        E2eSheet(
            client = client,
            buffer = buffer,
            messages = messages,
            onDismiss = { showE2e = false },
        )
    }

    actionMsg?.let { m ->
        MessageActions(
            msg = m,
            client = client,
            onReply = {
                replyTo(m)
                actionMsg = null
            },
            onQuote = {
                val t = "> ${m.text}\n" + draft.text
                draft = TextFieldValue(t, TextRange(t.length))
                client.setDraftLocal(buffer, t)
                actionMsg = null
            },
            onDismiss = { actionMsg = null },
        )
    }
}

/**
 * Encryption panel: the E2E scrollback (status/handshake lines) for this buffer
 * plus quick-action buttons that drive the server's /e2e subcommands. All crypto
 * is server-side; this is a control + status surface only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun E2eSheet(
    client: LurkerClient,
    buffer: Buffer,
    messages: List<Msg>,
    onDismiss: () -> Unit,
) {
    val lines = remember(messages.size) { messages.filter { it.type == "e2e" }.takeLast(60) }
    fun run(args: String) = client.execute(buffer, listOf(WireOp("e2e", target = buffer.target, line = args)))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        // Keep a touch of translucency but not so much the chat bleeds through and
        // hurts readability — only lifts the see-through Dark theme (~0.70), leaves
        // the already-near-opaque Oled/Light surfaces alone.
        containerColor = SurfaceDark.copy(alpha = SurfaceDark.alpha.coerceAtLeast(0.94f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                "🔒 Encryption — ${buffer.displayName}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                "End-to-end encryption is handled by your Lurker server. These are the handshake and status lines for this buffer.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { run("status") }) { Text("Status", fontSize = 13.sp) }
                TextButton(onClick = { run("list") }) { Text("Peers", fontSize = 13.sp) }
                TextButton(onClick = { run("fingerprint") }) { Text("My key", fontSize = 13.sp) }
                TextButton(onClick = { run("help") }) { Text("Help", fontSize = 13.sp) }
            }
            Text(
                "Type /e2e handshake <nick>, /e2e accept <nick>, /e2e verify <nick>, /e2e on in chat.",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            HorizontalDivider(color = SurfaceRaised)
            if (lines.isEmpty()) {
                Text(
                    "No E2E activity yet. Tap Status, or start a handshake from chat.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(lines.size) { i ->
                        val m = lines[i]
                        Text(
                            m.text,
                            color = if (m.level == "warn") AlertRed else TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
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
        // Fully expanded, and no drag-to-dismiss: a fast fling in the roster
        // list used to spill leftover velocity into the sheet gesture and yank
        // it closed. Close via scrim tap or back.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        // Keep a touch of translucency but not so much the chat bleeds through and
        // hurts readability — only lifts the see-through Dark theme (~0.70), leaves
        // the already-near-opaque Oled/Light surfaces alone.
        containerColor = SurfaceDark.copy(alpha = SurfaceDark.alpha.coerceAtLeast(0.94f)),
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
            RosterList(roster, Modifier.heightIn(max = 460.dp)) { selected = it }
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

/** The channel roster rows, shared by the phone member sheet and the tablet
 *  right pane. Rank-prefix glyph, nick (dimmed when away), and an away tag. */
@Composable
private fun RosterList(
    roster: List<Member>,
    modifier: Modifier = Modifier,
    onSelect: (Member) -> Unit,
) {
    LazyColumn(modifier) {
        items(roster.size) { i ->
            val m = roster[i]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(m) }
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
}

/** Persistent right-hand roster for the tablet three-pane layout. Same roster +
 *  nick-actions flow as [MemberSheet], but always-on rather than a modal sheet. */
@Composable
private fun MemberPane(
    client: LurkerClient,
    buffer: Buffer,
    onOpenBuffer: (Buffer) -> Unit,
    onPickFileFor: (String) -> Unit,
) {
    var selected by remember(buffer.key) { mutableStateOf<Member?>(null) }
    val roster = remember(client.members[buffer.key]) {
        (client.members[buffer.key] ?: emptyList())
            .sortedWith(compareBy({ it.rank }, { it.nick.lowercase() }))
    }
    Column(
        Modifier.fillMaxSize()
            .background(SurfaceDark.copy(alpha = SurfaceDark.alpha.coerceAtLeast(0.94f)))
            .navigationBarsPadding()
            .padding(top = 12.dp),
    ) {
        val sel = selected
        if (sel == null) {
            Text(
                "${roster.size} member${if (roster.size == 1) "" else "s"}",
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
            )
            if (roster.isEmpty()) {
                Text(
                    "No member list yet — the server sends it when the channel is joined.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                )
            }
            RosterList(roster, Modifier.fillMaxSize()) { selected = it }
        } else {
            MemberActions(
                client = client,
                buffer = buffer,
                member = sel,
                canModerate = client.myMember(buffer)?.canModerate == true,
                onBack = { selected = null },
                onDone = { selected = null },
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
    var editNote by remember { mutableStateOf(false) }
    var noteText by remember(nick) { mutableStateOf(client.nickNote(networkId, nick) ?: "") }
    fun raw(line: String) {
        client.execute(buffer, listOf(WireOp("raw", line = line)))
        onDone()
    }

    if (editNote) {
        AlertDialog(
            onDismissRequest = { editNote = false },
            containerColor = SurfaceRaised,
            title = { Text("Note on $nick", color = TextPrimary) },
            text = {
                OutlinedTextField(noteText, { noteText = it }, placeholder = { Text("e.g. lives in Berlin") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { client.setNickNote(networkId, nick, noteText); editNote = false }) {
                    Text("Save", color = AccentBlue, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { editNote = false }) { Text("Cancel", color = TextSecondary) } },
        )
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
        client.nickNote(networkId, nick)?.let { note ->
            Text("📝 $note", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 26.dp, vertical = 4.dp))
        }
        SheetAction("Whois") { raw("WHOIS $nick") }
        SheetAction("Send a message") {
            onOpenBuffer(client.focusTarget(networkId, nick))
        }
        SheetAction(if (client.nickNote(networkId, nick) != null) "Edit note" else "Add note") { editNote = true }
        if (client.contacts.none { c -> c.targets.any { it.networkId == networkId && it.nick.equals(nick, true) } }) {
            SheetAction("Add as friend") {
                client.setContact(null, nick, notifyOnline = true, listOf(ContactTarget(networkId, nick, isPrimary = true)))
                onDone()
            }
        }
        // Mute this nick's mentions of you without hiding their messages — handy
        // for a bot that pings your name. A global NOHIGHLIGHT rule on their mask.
        SheetAction("Ignore highlights") {
            client.addIgnore(null, member.banMask, levels = listOf("NOHIGHLIGHT")); onDone()
        }
        SheetAction("Ignore (everything)", danger = true) {
            client.addIgnore(networkId, member.banMask, levels = listOf("ALL")); onDone()
        }
        // FORK-ONLY (outgoing DCC): only when the server supports it.
        if (client.serverExtended) {
            SheetAction("DCC: send a file…") { onPickFileFor(nick) }
            SheetAction("DCC: start a chat") {
                client.dccChat(networkId, nick, open = true)
                onOpenBuffer(client.focusTarget(networkId, "=$nick"))
            }
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
        color = if (danger) AlertRed else TextPrimary,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp),
    )
}

/** Long-press action sheet for a single message: copy / reply / quote / share / bookmark. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActions(
    msg: Msg,
    client: LurkerClient,
    onReply: () -> Unit,
    onQuote: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceDark,
    ) {
        Text(
            msg.text.replace("\n", " ").take(90).ifBlank { msg.nick },
            color = TextSecondary,
            fontSize = 13.sp,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp),
        )
        HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(vertical = 4.dp))
        SheetAction("Copy text") {
            clipboard.setText(AnnotatedString(msg.text))
            onDismiss()
        }
        SheetAction("Reply") { onReply() }
        SheetAction("Quote") { onQuote() }
        SheetAction("Share…") {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, msg.text)
            }
            context.startActivity(android.content.Intent.createChooser(send, null))
            onDismiss()
        }
        if (msg.id > 0) {
            SheetAction(if (client.isBookmarked(msg.id)) "Remove bookmark" else "Bookmark") {
                client.toggleBookmark(msg.id)
                onDismiss()
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Max file we'll buffer for a DCC send (the server's own ceiling is higher). */
/**
 * Resolve a content Uri to (displayName, streaming RequestBody). No size cap
 * and no buffering — the body streams straight from the content provider into
 * the socket, so a multi-GB file costs a few KB of memory.
 */
private fun readUpload(
    context: android.content.Context,
    uri: android.net.Uri,
): Pair<String, RequestBody>? {
    return try {
        var name = "file"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                val sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
        // Probe readability now so failures surface as a message, not mid-stream.
        context.contentResolver.openInputStream(uri)?.close() ?: return null
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    sink.writeAll(input.source())
                } ?: throw java.io.IOException("content no longer readable: $uri")
            }
        }
        name to body
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun MessageBubble(
    msg: Msg,
    first: Boolean,
    last: Boolean,
    baseSize: Int,
    onLink: ((String) -> Unit)? = null,
    onMediaLoaded: () -> Unit = {},
    onAction: ((Msg) -> Unit)? = null,
    onSwipeReply: ((Msg) -> Unit)? = null,
) {
    val self = msg.self
    // Swipe a bubble sideways to reply (reuses the long-press reply seam). The
    // live drag is a plain float (synchronous, no per-delta coroutine that could
    // land after the release); the Animatable only drives the spring back so the
    // bubble always returns to place.
    var dragPx by remember(msg.id) { mutableStateOf(0f) }
    val settleAnim = remember(msg.id) { androidx.compose.animation.core.Animatable(0f) }
    var settling by remember(msg.id) { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val maxDragPx = with(density) { 72.dp.toPx() }
    val triggerPx = with(density) { 56.dp.toPx() }
    var firedThisDrag by remember(msg.id) { mutableStateOf(false) }
    val shownX = if (settling) settleAnim.value else dragPx
    // Grouping reads through the corners: the shared edge between messages of
    // one group is tightened, the outside stays fully rounded.
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
            .then(
                if (onSwipeReply != null) {
                    Modifier
                        .offset { IntOffset(shownX.roundToInt(), 0) }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                val next = (dragPx + delta).coerceIn(0f, maxDragPx)
                                if (next >= triggerPx && !firedThisDrag) {
                                    firedThisDrag = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                dragPx = next
                            },
                            onDragStopped = {
                                val reached = dragPx >= triggerPx
                                settleAnim.snapTo(dragPx)
                                settling = true
                                if (reached) onSwipeReply(msg)
                                settleAnim.animateTo(0f)
                                dragPx = 0f
                                settling = false
                                firedThisDrag = false
                            },
                        )
                } else Modifier,
            )
            .padding(
                start = if (self) 64.dp else 12.dp,
                end = if (self) 12.dp else 64.dp,
                top = if (first) 8.dp else 2.dp,
                bottom = 1.dp,
            ),
    ) {
        if (first && !self) {
            Text(
                msg.nick,
                color = nickColor(msg.nick),
                fontSize = (baseSize - 3).sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
            )
        }
        // A message fully painted with one mIRC background becomes a bubble of
        // that color instead of colored stripes inside a gray bubble.
        val paintedBg = remember(msg.text) { Mirc.wholeMessageBg(msg.text)?.let { Color(it) } }
        Box(
            Modifier
                .clip(shape)
                .background(paintedBg ?: if (self) AccentBlue else SurfaceRaised, shape)
                // Long-press opens the message action sheet. onClick is a no-op so
                // link/media taps inside the text still reach their own handlers.
                .then(
                    if (onAction != null) {
                        Modifier.combinedClickable(onClick = {}, onLongClick = { onAction(msg) })
                    } else Modifier,
                )
                .padding(horizontal = 13.dp, vertical = 7.dp),
        ) {
            val body = mircAnnotated(msg.text, if (self) Color.White else AccentBlue, onLink)
            Text(
                if (msg.type == "error") buildAnnotatedString {
                    withStyle(SpanStyle(color = AlertRed)) { append(body) }
                } else body,
                color = when {
                    self -> Color.White
                    msg.type == "notice" -> NoticeAmber
                    else -> TextPrimary
                },
                fontSize = baseSize.sp,
            )
        }
        // Inline media thumbnails (device-local toggle). Tap → the full viewer via
        // the same onLink route that already handles media URLs.
        if (Ui.inlineMedia && onLink != null) {
            val embeds = remember(msg.text) { mediaUrlsIn(msg.text) }
            embeds.forEach { (url, kind) ->
                when (kind) {
                    MediaKind.AUDIO -> InlineAudioPlayer(url)
                    MediaKind.VIDEO -> InlineVideoPlayer(url)
                    MediaKind.IMAGE -> MediaEmbed(url, onOpen = { onLink(url) }, onLoaded = onMediaLoaded)
                }
            }
        }
        if (last) {
            Text(
                // 🔒 marks an end-to-end-encrypted message (the web client
                // carries the flag but never shows it — we do).
                (if (msg.e2e) "🔒 " else "") + (formatTime(msg.time) ?: ""),
                color = if (msg.e2e) OnlineGreen else TextSecondary,
                fontSize = (baseSize - 5).sp,
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
private fun ActionLine(msg: Msg, baseSize: Int = 16, onLink: ((String) -> Unit)? = null) {
    val line = buildAnnotatedString {
        withStyle(SpanStyle(color = TextSecondary, fontStyle = FontStyle.Italic)) { append("* ") }
        withStyle(
            SpanStyle(color = nickColor(msg.nick), fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold),
        ) { append(msg.nick) }
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(" ") ; append(mircAnnotated(msg.text, AccentBlue, onLink)) }
    }
    Text(
        line,
        fontSize = (baseSize - 2).sp,
        color = TextPrimary,
        modifier = Modifier.fillMaxWidth().padding(16.dp, 5.dp),
    )
}

private val CHANNEL_RE = Regex("""#[^\s,]+""")

// IMAGE_EXTS/VIDEO_EXTS/AUDIO_EXTS live in Media.kt now (shared with embeds).
private val MEDIA_EXTS = VIDEO_EXTS + AUDIO_EXTS

/** One shared animated image loader for all inline embeds (GIF/WebP capable). */

@Composable
private fun rememberEmbedLoader(): coil.ImageLoader {
    // The app-wide loader (LurkerApp.newImageLoader) — one shared 256 MB disk
    // cache so inline media survives scrolls, buffer switches, and relaunches.
    val context = LocalContext.current
    return remember { context.imageLoader }
}

/**
 * An inline image card in a chat bubble. Takes the picture's own aspect ratio
 * (scaled to fit, never cropped), bounded so a tall portrait can't run away;
 * only extreme aspects letterbox. The card resizes when the image loads, so
 * [onLoaded] lets the chat re-pin to the tail — that keeps the tail-follow /
 * load-older anchoring stable despite the size change.
 */
@Composable
private fun MediaEmbed(url: String, onOpen: () -> Unit, onLoaded: () -> Unit = {}) {
    // Real aspect once known; a landscape default reserves sane space first.
    var ratio by remember(url) { mutableStateOf<Float?>(null) }
    BoxWithConstraints(Modifier.padding(top = 6.dp).fillMaxWidth()) {
        val h = (maxWidth / (ratio ?: 1.6f)).coerceIn(80.dp, 360.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(h)
                .clip(RoundedCornerShape(12.dp))
                .background(CanvasBlack)
                .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).build(),
                imageLoader = rememberEmbedLoader(),
                contentDescription = "Shared image",
                contentScale = ContentScale.Fit,
                onSuccess = { st ->
                    val d = st.result.drawable
                    if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                        ratio = d.intrinsicWidth.toFloat() / d.intrinsicHeight
                    }
                    onLoaded()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Inline video player in a chat bubble. Shows a 16:9 poster with a ▶ until
 * tapped, then plays in place with ExoPlayer's standard transport controls —
 * no full-screen hop. Created lazily and released on dispose, so a video link
 * costs nothing until the user actually plays it.
 */
@Composable
private fun InlineVideoPlayer(url: String) {
    var started by remember(url) { mutableStateOf(false) }
    Box(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .aspectRatio(1.78f)
            .heightIn(max = 300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CanvasBlack)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
            .then(if (!started) Modifier.clickable { started = true } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!started) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▶", color = Color.White, fontSize = 40.sp)
                Text(
                    url.substringAfterLast('/').substringBefore('?').take(40),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp),
                )
            }
        } else {
            val context = LocalContext.current
            val player = remember(url) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Compact inline audio player for a chat bubble. Fixed height keeps the chat
 * list's tail-follow / load-older anchoring stable (see [MediaEmbed]). The
 * ExoPlayer is created lazily and only prepared on the first ▶, so audio links
 * don't fetch until the user actually plays them. Tap the bar to seek.
 */
@Composable
private fun InlineAudioPlayer(url: String) {
    val context = LocalContext.current
    val player = remember(url) { ExoPlayer.Builder(context).build() }
    var prepared by remember(url) { mutableStateOf(false) }
    var isPlaying by remember(url) { mutableStateOf(false) }
    var positionMs by remember(url) { mutableStateOf(0L) }
    var durationMs by remember(url) { mutableStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && player.duration > 0) durationMs = player.duration
                if (state == Player.STATE_ENDED) { player.seekTo(0); player.playWhenReady = false }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    // Poll position only while playing — no work when paused/idle.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.currentPosition
            if (durationMs <= 0 && player.duration > 0) durationMs = player.duration
            delay(400)
        }
    }
    fun mmss(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Row(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CanvasBlack)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isPlaying) "⏸" else "▶",
            color = AccentBlue,
            fontSize = 22.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable {
                    if (!prepared) {
                        player.setMediaItem(MediaItem.fromUri(url))
                        player.prepare()
                        prepared = true
                    }
                    if (player.isPlaying) player.pause() else player.play()
                }
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                url.substringAfterLast('/').substringBefore('?').take(40),
                color = TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .pointerInput(durationMs) {
                        detectTapGestures { off ->
                            if (durationMs > 0) {
                                val f = (off.x / size.width).coerceIn(0f, 1f)
                                val target = (f * durationMs).toLong()
                                player.seekTo(target)
                                positionMs = target
                            }
                        }
                    },
            ) {
                LinearProgressIndicator(
                    progress = { frac },
                    color = AccentBlue,
                    trackColor = SurfaceRaised,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
        }
        Text(
            if (durationMs > 0) "${mmss(positionMs)} / ${mmss(durationMs)}" else mmss(positionMs),
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * Full-screen in-app media viewer. Images: pinch-zoom + pan, GIFs animate.
 * Video/audio: embedded ExoPlayer with the standard controls. Escape hatches:
 * open in browser, or ✕ / back to close.
 */
@Composable
private fun MediaViewerDialog(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clean = url.substringBefore('?').substringBefore('#').lowercase()
    val isImage = IMAGE_EXTS.any { clean.endsWith(it) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f)),
        ) {
            if (isImage) {
                val loader = context.imageLoader // shared app-wide disk cache
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                AsyncImage(
                    model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                    imageLoader = loader,
                    contentDescription = "Shared image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            } else {
                // Video and audio share ExoPlayer; audio just shows the controls.
                val player = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(url))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(Unit) {
                    onDispose { player.release() }
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp),
            ) {
                TextButton(onClick = { uriHandler.openUri(url) }) {
                    Text("Browser", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                }
                TextButton(onClick = onClose) {
                    Text("✕", color = Color.White, fontSize = 20.sp)
                }
            }
        }
    }
}

/** iMessage-style "someone is composing" bubble with three breathing dots. */
@Composable
private fun TypingBubble(nicks: List<String>) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 64.dp, top = 8.dp, bottom = 2.dp),
    ) {
        Text(
            nicks.joinToString(", "),
            color = nickColor(nicks.first()),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
        )
        Box(
            Modifier
                .background(SurfaceRaised, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            val transition = rememberInfiniteTransition(label = "typing")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    val alpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(i * 160),
                        ),
                        label = "dot$i",
                    )
                    Box(Modifier.size(8.dp).background(TextSecondary.copy(alpha = alpha), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun SystemLine(msg: Msg, onJoin: ((String) -> Unit)? = null) {
    if (msg.type == "e2e") {
        // Encryption status / handshake line: an "E2E" tag, green for info,
        // red for warn — mirrors the web client's .p-e2e styling.
        val warn = msg.level == "warn"
        val line = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = if (warn) AlertRed else OnlineGreen,
                    fontWeight = FontWeight.Bold,
                ),
            ) { append("🔒 E2E  ") }
            append(msg.text)
        }
        Text(
            line,
            fontSize = 12.sp,
            color = if (warn) AlertRed else TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 4.dp)
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .padding(12.dp, 7.dp),
        )
        return
    }
    if (msg.type == "system") {
        // :system: log lines read like a terminal: left-aligned, level-tinted.
        Text(
            msg.text,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = when (msg.level) {
                "error" -> AlertRed
                "warn" -> NoticeAmber
                else -> TextSecondary
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 3.dp),
        )
        return
    }
    if (msg.type == "send-failed") {
        Text(
            msg.text,
            fontSize = 13.sp,
            color = AlertRed,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(24.dp, 6.dp),
        )
        return
    }
    if (msg.type == "whois") {
        // WHOIS blocks read like terminal output: left-aligned, monospace.
        // Channel names are tappable (join prompt) when a handler is provided.
        val body = if (onJoin != null) {
            buildAnnotatedString {
                append(msg.text)
                for (m in CHANNEL_RE.findAll(msg.text)) {
                    addLink(
                        LinkAnnotation.Clickable(
                            m.value,
                            TextLinkStyles(SpanStyle(color = AccentBlue)),
                        ) { onJoin(m.value) },
                        m.range.first,
                        m.range.last + 1,
                    )
                }
            }
        } else {
            AnnotatedString(msg.text)
        }
        Text(
            body,
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
    // Through the mIRC renderer: topics regularly carry color/format codes,
    // which as plain text leak their digits ("09[Game Online]…").
    Text(
        mircAnnotated(msg.text, AccentBlue),
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
private fun Composer(
    draft: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    target: String,
    uploading: Boolean = false,
    onAttach: (() -> Unit)? = null,
    suggestions: List<String> = emptyList(),
    onPickSuggestion: (String) -> Unit = {},
    onSend: () -> Unit,
) {
    var showFormat by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (suggestions.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(suggestions.size) { i ->
                    val s = suggestions[i]
                    Text(
                        s,
                        color = AccentBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(SurfaceRaised, RoundedCornerShape(14.dp))
                            .border(0.5.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .clickable { onPickSuggestion(s) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (showFormat) FormatBar(draft, onChange)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Compact glyph buttons (not TextButtons) so the input gets the room.
            if (onAttach != null) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .clickable(enabled = !uploading, onClick = onAttach)
                        .semantics { contentDescription = "Attach a file"; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (uploading) "…" else "+",
                        color = if (uploading) TextSecondary else AccentBlue,
                        fontSize = 24.sp,
                    )
                }
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .clickable { showFormat = !showFormat }
                    .semantics { contentDescription = "Text formatting"; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Aa",
                    color = if (showFormat) AccentBlue else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
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
            val canSend = draft.text.isNotBlank()
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .clickable(enabled = canSend, onClick = onSend)
                    .semantics { contentDescription = "Send message"; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "➤",
                    color = if (canSend) AccentBlue else TextSecondary,
                    fontSize = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun FormatBar(draft: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
    var showColors by remember { mutableStateOf(false) }
    // The last text color picked this session: a fill pick pairs with it, since
    // mIRC syntax can't set a background without a foreground.
    var lastFg by remember { mutableStateOf<Int?>(null) }
    var fillMode by remember { mutableStateOf(false) }
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
                    // Text vs Fill: fill inserts a fg,bg pair (fg = last-picked
                    // text color, else a contrast-safe black/white).
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { fillMode = false }) {
                            Text(
                                "Text",
                                color = if (!fillMode) AccentBlue else TextSecondary,
                                fontWeight = if (!fillMode) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 13.sp,
                            )
                        }
                        TextButton(onClick = { fillMode = true }) {
                            Text(
                                "Background",
                                color = if (fillMode) AccentBlue else TextSecondary,
                                fontWeight = if (fillMode) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 13.sp,
                            )
                        }
                    }
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
                                            if (fillMode) {
                                                val fg = lastFg ?: Mirc.contrastIndex(i)
                                                onChange(applyFormat(draft, Fmt.colorPair(fg, i), Fmt.COLOR))
                                            } else {
                                                lastFg = i
                                                // Selection gets color..clear; bare opener at cursor.
                                                onChange(applyFormat(draft, Fmt.color(i), Fmt.COLOR))
                                            }
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
            color = TextPrimary,
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

/**
 * Registry settings that only steer the *web* client and do nothing here are
 * hidden, so the mobile settings screen shows only what actually affects this
 * app or the account server-side. Server behaviors (chat filters, away, ctcp,
 * uploads, dcc, channel, push) still apply to mobile via the server, so they
 * stay. Everything under `look.*` styles the web renderer — mobile has its own
 * themes — except the one mobile font-size key it honors.
 */
private fun settingHiddenOnMobile(key: String): Boolean = when {
    key == "look.font.size.mobile" -> false      // the only look.* the mobile UI reads
    key.startsWith("look.") -> true              // fonts, palette, desktop layout, bars
    key.startsWith("input.") -> true             // web input-box prefs; Android uses the system IME
    key.contains(".sound.") -> true              // web notification audio; Android uses channels
    key == "chat.image_modal.enabled" -> true    // web lightbox; mobile has its own media viewer
    key == "uploads.paste.enabled" -> true       // web clipboard paste
    else -> false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(client: LurkerClient, prefs: Prefs, onBack: () -> Unit) {
    var category by remember { mutableStateOf<String?>(null) }
    BackHandler { if (category != null) category = null else onBack() }

    val byCategory = remember(client.settingsRegistry.toList()) {
        client.settingsRegistry
            .filter { it.category.isNotEmpty() && it.category != "system" && !settingHiddenOnMobile(it.key) }
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
                item { ThemePickerCard(prefs) }
                item { InlineMediaCard(prefs) }
                item { ChatTextSizeCard(prefs) }
                item { BiometricLockCard(prefs) }
                item { BackgroundConnectCard(prefs) }
                item { ConnectionModeCard(prefs) }
                // FORK-ONLY: only shown when the connected server supports it.
                if (client.serverExtended) item { AliasesCard(client) }
                items(orderedCategories.size) { i ->
                    val cat = orderedCategories[i]
                    val label = CATEGORY_META.firstOrNull { it.first == cat }?.second
                        ?: cat.replaceFirstChar { it.uppercase() }
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, GlassBorder),
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
                                border = BorderStroke(0.5.dp, GlassBorder),
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

// FORK-ONLY: custom server-synced aliases (stripped from the public build).
@Composable
private fun AliasesCard(client: LurkerClient) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var expansion by remember { mutableStateOf("") }
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Column(Modifier.padding(16.dp, 12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aliases", fontSize = 17.sp)
                    Text("Custom /commands ($1..$9, \$me, \$chan)", color = TextSecondary, fontSize = 12.sp)
                }
                TextButton(onClick = { adding = !adding }) { Text(if (adding) "Cancel" else "Add", color = AccentBlue) }
            }
            if (adding) {
                OutlinedTextField(name, { name = it.trimStart('/').trim() }, label = { Text("name (e.g. wave)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(expansion, { expansion = it }, label = { Text("expansion (e.g. /me waves at \$1)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank() && expansion.isNotBlank()) {
                            client.addAlias(name.trim(), expansion.trim())
                            name = ""; expansion = ""; adding = false
                        }
                    },
                    enabled = name.isNotBlank() && expansion.isNotBlank(),
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Save alias") }
            }
            client.aliases.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("/${a.name}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(a.expansion, color = TextSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = { client.removeAlias(a.id) }) { Text("Delete", color = AlertRed, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun InlineMediaCard(prefs: Prefs) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Inline media", fontSize = 17.sp)
                Text(
                    "Show image & video links as thumbnails. Off = tap-to-open only (won't fetch link previews).",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = Ui.inlineMedia,
                onCheckedChange = { Ui.inlineMedia = it; prefs.inlineMedia = it },
            )
        }
    }
}

/** A titled on-device toggle row, matching the InlineMedia/theme cards. */
@Composable
private fun PrefToggleCard(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun BiometricLockCard(prefs: Prefs) {
    val context = LocalContext.current
    // Only offer the lock if the device can actually satisfy it (a biometric or a
    // device PIN/pattern/password) — otherwise enabling it would strand the user.
    val canAuth = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    if (!canAuth) return
    var on by remember { mutableStateOf(prefs.biometricLock) }
    PrefToggleCard(
        title = "Require unlock",
        subtitle = "Ask for fingerprint, face, or device PIN each time the app opens.",
        checked = on,
    ) { on = it; prefs.biometricLock = it }
}

@Composable
private fun ChatTextSizeCard(prefs: Prefs) {
    var scale by remember { mutableStateOf(Ui.chatTextScale) }
    fun set(v: Int) { scale = v.coerceIn(-3, 14); Ui.chatTextScale = scale; prefs.chatTextScale = scale }
    val preview = (16 + scale).coerceIn(11, 30)
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Message text size", fontSize = 17.sp)
                Text(
                    if (scale == 0) "Default · great to bump up on a tablet" else "${if (scale > 0) "+" else ""}$scale from default",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { set(scale - 1) }
                        .semantics { contentDescription = "Smaller text"; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Text("A", color = AccentBlue, fontSize = 15.sp) }
                Text(
                    "Aa",
                    fontSize = preview.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(48.dp),
                )
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { set(scale + 1) }
                        .semantics { contentDescription = "Larger text"; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Text("A", color = AccentBlue, fontSize = 22.sp) }
            }
        }
    }
}

@Composable
private fun BackgroundConnectCard(prefs: Prefs) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(prefs.backgroundConnect) }
    PrefToggleCard(
        title = "Stay connected in background",
        subtitle = "Keep a persistent notification so highlights & DMs notify you even when the app is closed.",
        checked = on,
    ) {
        on = it
        prefs.backgroundConnect = it
        if (it) LurkerConnectionService.start(context) else LurkerConnectionService.stop(context)
    }
}

/** Switch between Lurker-server and direct-IRC/bouncer mode. Applying the switch
 *  restarts the app so LurkerApp rebuilds its lazy backend for the new mode. */
@Composable
private fun ConnectionModeCard(prefs: Prefs) {
    val context = LocalContext.current
    val mode = prefs.clientMode ?: "lurker"
    val isDirect = mode == "direct"
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Column(Modifier.padding(16.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Connection mode", fontSize = 17.sp)
            Text(
                if (isDirect) "Direct IRC / bouncer" else "Lurker server",
                color = TextSecondary, fontSize = 13.sp,
            )
            Text(
                "Switch restarts the app. " +
                    if (isDirect) "Switching to Lurker signs you into a Lurker server."
                    else "Switching to Direct connects straight to IRC / a bouncer.",
                color = TextSecondary, fontSize = 12.sp,
            )
            Surface(
                onClick = { prefs.clientMode = if (isDirect) "lurker" else "direct"; restartAppProcess(context) },
                shape = RoundedCornerShape(10.dp),
                color = SurfaceRaised,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    if (isDirect) "Switch to Lurker server" else "Switch to Direct IRC / bouncer",
                    color = AccentBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(14.dp, 10.dp),
                )
            }
        }
    }
}

/** Relaunch the app in a fresh process (mode switch = restart). */
private fun restartAppProcess(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

@Composable
private fun ThemePickerCard(prefs: Prefs) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
    ) {
        Column(Modifier.padding(16.dp, 12.dp)) {
            Text("App theme", fontSize = 17.sp)
            Text("On this device", color = TextSecondary, fontSize = 13.sp)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTheme.entries.forEach { t ->
                    val selected = Ui.theme == t
                    Text(
                        t.label,
                        color = if (selected) AccentBlue else TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .background(
                                if (selected) SurfaceRaised else Color.Transparent,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { Ui.theme = t; prefs.theme = t.id }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
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

// ---- Networks --------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworksScreen(
    client: LurkerClient,
    onEdit: (NetworkConfig) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
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
                title = { Text("Networks", fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(onClick = onAdd) { Text("+", color = AccentBlue, fontSize = 24.sp) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            client.networksError?.let { err ->
                item { Text(err, color = AlertRed, modifier = Modifier.padding(16.dp)) }
            }
            if (client.networkConfigs.isEmpty()) {
                item { Text("No networks yet — add one with +.", Modifier.padding(16.dp), color = TextSecondary) }
            }
            items(client.networkConfigs.size) { i ->
                val cfg = client.networkConfigs[i]
                val live = client.networks[cfg.id]
                val connected = live?.connected == true
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 6.dp),
                ) {
                    Column(Modifier.padding(16.dp, 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ConnectionDot(connected)
                            Spacer(Modifier.width(8.dp))
                            Text(cfg.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            // Arrange: swap with the neighbor and persist the order
                            // (drives the buffer-list section order too).
                            TextButton(
                                onClick = {
                                    val ids = client.networkConfigs.map { it.id }.toMutableList()
                                    val at = ids.indexOf(cfg.id)
                                    if (at > 0) {
                                        ids[at] = ids[at - 1].also { ids[at - 1] = ids[at] }
                                        client.reorderNetworks(ids)
                                    }
                                },
                                enabled = i > 0,
                            ) { Text("↑", color = if (i > 0) TextSecondary else PillGray) }
                            TextButton(
                                onClick = {
                                    val ids = client.networkConfigs.map { it.id }.toMutableList()
                                    val at = ids.indexOf(cfg.id)
                                    if (at >= 0 && at < ids.lastIndex) {
                                        ids[at] = ids[at + 1].also { ids[at + 1] = ids[at] }
                                        client.reorderNetworks(ids)
                                    }
                                },
                                enabled = i < client.networkConfigs.lastIndex,
                            ) { Text("↓", color = if (i < client.networkConfigs.lastIndex) TextSecondary else PillGray) }
                            TextButton(onClick = { onEdit(cfg) }) { Text("Edit", color = AccentBlue) }
                        }
                        Text(
                            "${cfg.host}:${cfg.port}${if (cfg.tls) " (TLS)" else ""} · ${live?.nick ?: cfg.nick}" +
                                if (cfg.blocked) " · blocked by server policy" else "",
                            color = if (cfg.blocked) AlertRed else TextSecondary,
                            fontSize = 13.sp,
                        )
                        Row {
                            if (connected) {
                                TextButton(onClick = { client.networkAction(cfg.id, "disconnect") }) {
                                    Text("Disconnect", color = AlertRed, fontSize = 13.sp)
                                }
                                TextButton(onClick = { client.networkAction(cfg.id, "reconnect") }) {
                                    Text("Reconnect", color = TextSecondary, fontSize = 13.sp)
                                }
                            } else {
                                TextButton(
                                    onClick = { client.networkAction(cfg.id, "connect") },
                                    enabled = !cfg.blocked,
                                ) { Text("Connect", color = if (cfg.blocked) TextSecondary else OnlineGreen, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkEditScreen(client: LurkerClient, config: NetworkConfig?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var name by remember { mutableStateOf(config?.name ?: "") }
    var host by remember { mutableStateOf(config?.host ?: "") }
    var port by remember { mutableStateOf((config?.port ?: 6697).toString()) }
    var tls by remember { mutableStateOf(config?.tls ?: true) }
    var nick by remember { mutableStateOf(config?.nick ?: "") }
    var username by remember { mutableStateOf(config?.username ?: "") }
    var realname by remember { mutableStateOf(config?.realname ?: "") }
    var serverPassword by remember { mutableStateOf("") }
    var saslAccount by remember { mutableStateOf(config?.saslAccount ?: "") }
    var saslPassword by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf("") }
    var autoconnect by remember { mutableStateOf(config?.autoconnect ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) }
                },
                title = { Text(config?.name ?: "Add network", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            error?.let { Text(it, color = AlertRed, fontSize = 13.sp) }
            FormField("Name", name) { name = it }
            FormField("Host", host) { host = it }
            FormField("Port", port, number = true) { port = it }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TLS")
                Switch(checked = tls, onCheckedChange = { tls = it })
            }
            FormField("Nick", nick) { nick = it }
            FormField("Username (ident) — optional", username) { username = it }
            FormField("Real name — optional", realname) { realname = it }
            FormField(
                if (config?.hasPassword == true) "Server password (set — leave blank to keep)"
                else "Server password — optional",
                serverPassword, secret = true,
            ) { serverPassword = it }
            FormField("SASL account — optional", saslAccount) { saslAccount = it }
            FormField(
                if (config?.hasSaslPassword == true) "SASL password (set — leave blank to keep)"
                else "SASL password — optional",
                saslPassword, secret = true,
            ) { saslPassword = it }
            if (config == null) {
                FormField("Channels to join — optional (#a, #b)", channels) { channels = it }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Connect automatically")
                Switch(checked = autoconnect, onCheckedChange = { autoconnect = it })
            }
            Button(
                onClick = {
                    saving = true
                    val fields = buildMap<String, Any?> {
                        put("name", name.trim())
                        put("host", host.trim())
                        put("port", port.toIntOrNull() ?: 6697)
                        put("tls", tls)
                        put("nick", nick.trim())
                        if (username.isNotBlank()) put("username", username.trim())
                        if (realname.isNotBlank()) put("realname", realname.trim())
                        if (serverPassword.isNotEmpty()) put("server_password", serverPassword)
                        if (saslAccount.isNotBlank()) put("sasl_account", saslAccount.trim())
                        if (saslPassword.isNotEmpty()) put("sasl_password", saslPassword)
                        if (config == null && channels.isNotBlank()) put("default_channel", channels)
                        put("autoconnect", autoconnect)
                    }
                    client.saveNetwork(config?.id, fields) { err ->
                        saving = false
                        if (err == null) onBack() else error = err
                    }
                },
                enabled = !saving && name.isNotBlank() && host.isNotBlank() && nick.isNotBlank(),
            ) { Text(if (config == null) "Save & connect" else "Save") }

            if (config != null) {
                TextButton(onClick = {
                    if (!confirmDelete) {
                        confirmDelete = true
                    } else {
                        client.deleteNetwork(config.id) { err ->
                            if (err == null) onBack() else error = err
                        }
                    }
                }) {
                    Text(
                        if (confirmDelete) "Tap again to permanently delete" else "Delete network",
                        color = AlertRed,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    number: Boolean = false,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---- Search + highlights ---------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    client: LurkerClient,
    onOpenResult: (Int, String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf(0) } // 0 = search, 1 = highlights
    var query by remember { mutableStateOf("") }

    // Debounced search: re-run 300ms after typing stops.
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        client.runSearch(query)
    }
    LaunchedEffect(tab) {
        if (tab == 1 && client.highlightItems.isEmpty()) client.loadHighlights(fresh = true)
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
                title = { Text("Search", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchTab("Messages", tab == 0) { tab = 0 }
                SearchTab("Highlights", tab == 1) { tab = 1 }
            }
            if (tab == 0) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search — from:nick in:#chan on:net text", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = AccentBlue,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 6.dp),
                )
                val results = client.searchResults
                if (client.searchLoading && results.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                if (!client.searchLoading && results.isEmpty() && query.isNotBlank()) {
                    Text("No matches.", color = TextSecondary, modifier = Modifier.padding(16.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results.size) { i ->
                        val r = results[i]
                        ResultRow(client, r, onOpenResult)
                        if (i == results.size - 1 && client.searchHasMore) {
                            LaunchedEffect(results.size) { client.searchMore() }
                        }
                    }
                }
            } else {
                val items = client.highlightItems
                if (client.highlightsLoading && items.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                if (!client.highlightsLoading && items.isEmpty()) {
                    Text("No recent highlights.", color = TextSecondary, modifier = Modifier.padding(16.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items.size) { i ->
                        ResultRow(client, items[i], onOpenResult)
                        if (i == items.size - 1 && client.highlightsHasMore) {
                            LaunchedEffect(items.size) { client.loadHighlights(fresh = false) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) AccentBlue else TextSecondary,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 15.sp,
        modifier = Modifier
            .background(if (selected) SurfaceRaised else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun ResultRow(client: LurkerClient, r: SearchResult, onOpen: (Int, String) -> Unit) {
    val net = client.networks[r.networkId]?.name ?: ""
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { if (r.networkId >= 0) onOpen(r.networkId, r.target) }
            .padding(16.dp, 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.nick, color = nickColor(r.nick), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                r.target + (if (net.isNotEmpty()) " · $net" else ""),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            formatTime(r.createdAt)?.let { Text(it, color = TextSecondary, fontSize = 11.sp) }
        }
        Text(mircAnnotated(r.body, AccentBlue), fontSize = 15.sp, color = TextPrimary, modifier = Modifier.padding(top = 2.dp))
    }
    HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(start = 16.dp))
}

// ---- Channel list (/LIST) --------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelListScreen(
    client: LurkerClient,
    onJoin: (Int, String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val connected = remember(client.networks.toMap()) { client.networks.values.filter { it.connected } }
    var networkId by remember(connected.map { it.id }) { mutableStateOf(connected.firstOrNull()?.id) }
    var query by remember { mutableStateOf("") }
    var sortByUsers by remember { mutableStateOf(true) }
    var netMenu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(networkId) { networkId?.let { client.openChannelList(it) } }
    LaunchedEffect(query, sortByUsers) {
        kotlinx.coroutines.delay(300)
        if (networkId != null) client.searchChannelList(query, if (sortByUsers) "users" else "name", "desc", 0)
    }

    confirm?.let { chan ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = SurfaceRaised,
            title = { Text("Join $chan?", color = TextPrimary) },
            confirmButton = {
                TextButton(onClick = { confirm = null; networkId?.let { onJoin(it, chan) } }) {
                    Text("Join", color = AccentBlue, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = TextSecondary) } },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = { TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) } },
                title = { Text("Channels", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { netMenu = true }) {
                        Text(connected.firstOrNull { it.id == networkId }?.name ?: "Network", color = AccentBlue)
                    }
                    DropdownMenu(expanded = netMenu, onDismissRequest = { netMenu = false }) {
                        connected.forEach { n -> DropdownMenuItem(text = { Text(n.name) }, onClick = { networkId = n.id; netMenu = false }) }
                    }
                }
                SearchTab(if (sortByUsers) "By users" else "By name", true) { sortByUsers = !sortByUsers }
                if (client.chanlistInProgress) Text("refreshing… ${client.chanlistTotalCount}", color = TextSecondary, fontSize = 12.sp)
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter channels", color = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = AccentBlue,
                ),
                modifier = Modifier.fillMaxWidth().padding(16.dp, 6.dp),
            )
            val rows = client.chanlistRows
            if (client.chanlistLoading && rows.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows.size) { i ->
                    val r = rows[i]
                    Column(
                        Modifier.fillMaxWidth().clickable { confirm = r.channel }.padding(16.dp, 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.channel, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text("${r.users}", color = TextSecondary, fontSize = 13.sp)
                        }
                        if (r.topic.isNotBlank()) {
                            Text(mircAnnotated(r.topic, AccentBlue), color = TextSecondary, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(start = 16.dp))
                    if (i == rows.size - 1 && rows.size < client.chanlistTotalCount) {
                        LaunchedEffect(rows.size) {
                            client.searchChannelList(query, if (sortByUsers) "users" else "name", "desc", rows.size)
                        }
                    }
                }
            }
        }
    }
}

// ---- DCC -----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IgnoresScreen(client: LurkerClient, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBlack,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CanvasBlack),
                navigationIcon = { TextButton(onClick = onBack) { Text("‹", color = AccentBlue, fontSize = 26.sp) } },
                title = { Text("Ignore list", fontWeight = FontWeight.SemiBold) },
                actions = { TextButton(onClick = { showAdd = true }) { Text("Add", color = AccentBlue) } },
            )
        },
    ) { padding ->
        val rules = remember(client.ignores.toList()) {
            client.ignores.sortedWith(compareBy({ it.networkId ?: -1 }, { it.who.lowercase() }))
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (rules.isEmpty()) {
                item {
                    Text(
                        "Nobody ignored. Tap Add, or long-press a nick in a channel and choose “Ignore highlights.”",
                        color = TextSecondary, modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(rules.size) { i ->
                val r = rules[i]
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.who, fontSize = 16.sp, color = TextPrimary)
                        val scope = if (r.networkId == null) "global" else client.networks[r.networkId]?.name ?: "net ${r.networkId}"
                        val exc = if (r.isExcept) "except " else ""
                        Text("$exc${r.levelsLabel} · $scope", fontSize = 13.sp, color = TextSecondary)
                    }
                    TextButton(onClick = { client.removeIgnore(r.networkId, r.id) }) {
                        Text("Remove", color = AlertRed, fontSize = 13.sp)
                    }
                }
                HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(start = 20.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (showAdd) {
        AddIgnoreDialog(onDismiss = { showAdd = false }) { mask, levels ->
            client.addIgnore(null, mask, levels = levels)
            showAdd = false
        }
    }
}

/** Independent level checkboxes — pick exactly what to ignore (e.g. just
 *  highlights). Unlike the web UI, "Everything" is optional, not forced-on. */
private val IGNORE_LEVELS = listOf(
    "NOHIGHLIGHT" to "Highlights (mentions of your nick)",
    "NONOTIFY" to "Push notifications",
    "ALL" to "Everything (all messages & events)",
    "PUBLIC" to "Channel messages",
    "MSGS" to "Direct messages",
    "NOTICES" to "Notices",
    "ACTIONS" to "Actions (/me)",
    "JOINS" to "Joins",
    "PARTS" to "Parts",
    "QUITS" to "Quits",
    "NICKS" to "Nick changes",
)

@Composable
private fun AddIgnoreDialog(onDismiss: () -> Unit, onAdd: (String, List<String>) -> Unit) {
    var mask by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceRaised,
        title = { Text("Ignore rule", color = TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    mask, { mask = it },
                    label = { Text("Nick or mask (e.g. botnick or *!*@host)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("Ignore which:", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    IGNORE_LEVELS.forEach { (token, label) ->
                        val on = token in selected
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (on) selected.remove(token) else selected.add(token) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = { if (on) selected.remove(token) else selected.add(token) })
                            Text(label, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = mask.isNotBlank() && selected.isNotEmpty(),
                onClick = { onAdd(mask.trim(), selected.toList()) },
            ) { Text("Add", color = AccentBlue, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendsScreen(client: LurkerClient, onOpenBuffer: (Buffer) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    // Refresh each friend's presence on entry.
    LaunchedEffect(client.contacts.size) {
        client.contacts.forEach { c -> c.primary?.let { client.probePresence(it.networkId, it.nick) } }
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
                title = { Text("Friends", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        val friends = remember(client.contacts.toList()) { client.contacts.sortedBy { it.displayName.lowercase() } }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (friends.isEmpty()) {
                item {
                    Text(
                        "No friends yet. Long-press a nick in a channel's member list and choose “Add as friend.”",
                        color = TextSecondary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(friends.size) { i ->
                val c = friends[i]
                val t = c.primary
                val dot = when (t?.let { client.presenceOf(it) }) {
                    "online", "back" -> OnlineGreen
                    "away" -> NoticeAmber
                    else -> TextSecondary
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = t != null) {
                            t?.let { onOpenBuffer(client.focusTarget(it.networkId, it.nick)) }
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(c.displayName, fontSize = 16.sp, color = TextPrimary)
                        val sub = t?.let { "${it.nick} · ${client.networks[it.networkId]?.name ?: "net ${it.networkId}"}" } ?: "no target"
                        Text(sub, fontSize = 13.sp, color = TextSecondary)
                    }
                    TextButton(onClick = { client.deleteContact(c.id) }) {
                        Text("Remove", color = AlertRed, fontSize = 13.sp)
                    }
                }
                HorizontalDivider(color = SurfaceRaised, modifier = Modifier.padding(start = 20.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

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
            // FORK-ONLY (outgoing DCC): receiving transfers still list below.
            if (client.serverExtended) item { DccStartCard(client, onOpenBuffer) }
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
                client.dccError = "Couldn't read that file."
            } else {
                client.dccSendFile(id, who, upload.first, upload.second)
            }
        }
    }

    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, GlassBorder),
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
