// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Who a call belongs to, so the UI can label it and route moderation. */
data class CallTarget(val networkId: Int, val target: String, val displayName: String)

/** One row in the in-call roster. [video] is the participant's first live camera
 *  track, or null for audio-only. */
data class CallParticipant(
    val identity: String,
    val name: String,
    val isLocal: Boolean,
    val speaking: Boolean,
    val video: VideoTrack?,
)

/**
 * Owns the single active LiveKit call (lurker#680). Process-scoped, like the
 * chat client, so a call keeps running while you navigate around the app; a
 * foreground [CallService] keeps the process alive when it's backgrounded.
 *
 * Media flows client<->LiveKit SFU; Lurker only minted the token. All Room work
 * happens on the main thread (LiveKit's default dispatcher), and Compose reads
 * the snapshot-backed fields below.
 */
object CallController {
    var target by mutableStateOf<CallTarget?>(null)
        private set
    var connecting by mutableStateOf(false)
        private set
    var micEnabled by mutableStateOf(true)
        private set
    var cameraEnabled by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    val participants = mutableStateListOf<CallParticipant>()

    /** Whether the call UI should be shown — OBSERVABLE, so the overlay appears the
     *  instant a call starts. (The room handle below is a plain field; gating on it
     *  wouldn't recompose, which left the call screen hidden until you navigated.) */
    var active by mutableStateOf(false)
        private set

    val inCall: Boolean get() = active

    private var room: Room? = null
    private var scope: CoroutineScope? = null
    private val speaking = mutableSetOf<String>()

    /** Show the call UI in a connecting state BEFORE the token round-trip, so
     *  pressing the call button takes you straight to the call area. */
    fun beginConnecting(tgt: CallTarget) {
        target = tgt
        active = true
        connecting = true
        error = null
        micEnabled = true
        cameraEnabled = false
        participants.clear()
    }

    /** Token mint / connect failed before we had media. Keep the sheet up with the
     *  error and a working leave button, rather than vanishing silently. */
    fun failStart(msg: String) {
        error = msg
        connecting = false
    }

    /** Join [tgt]'s call with the server-minted [tok]. No-op if already connected. */
    fun join(context: Context, tok: VoiceToken, tgt: CallTarget, withVideo: Boolean) {
        if (room != null) return
        val app = context.applicationContext
        if (!active) beginConnecting(tgt) else target = tgt
        connecting = true
        error = null
        val r = LiveKit.create(app)
        room = r
        val s = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = s
        CallService.start(app)
        s.launch {
            r.events.collect { ev ->
                if (ev is RoomEvent.ActiveSpeakersChanged) {
                    speaking.clear()
                    ev.speakers.forEach { it.identity?.value?.let(speaking::add) }
                }
                refresh(r)
            }
        }
        s.launch {
            try {
                r.connect(tok.url, tok.token)
                r.localParticipant.setMicrophoneEnabled(true)
                if (withVideo) {
                    r.localParticipant.setCameraEnabled(true)
                    cameraEnabled = true
                }
                connecting = false
                refresh(r)
            } catch (e: Exception) {
                DebugLog.e("call", "join failed: ${e.message}")
                // Tear down the half-open room but KEEP the sheet up showing the
                // error, so the user sees why and can dismiss it themselves.
                room?.disconnect(); room = null
                CallService.stop(app)
                failStart(e.message ?: "couldn't join the call")
            }
        }
    }

    fun toggleMic() {
        val r = room ?: return
        val next = !micEnabled
        micEnabled = next
        scope?.launch { runCatching { r.localParticipant.setMicrophoneEnabled(next) } }
    }

    fun toggleCamera() {
        val r = room ?: return
        val next = !cameraEnabled
        cameraEnabled = next
        scope?.launch { runCatching { r.localParticipant.setCameraEnabled(next); refresh(r) } }
    }

    /** Grant a chance to open the camera: request from the UI first — LiveKit can't
     *  publish video without CAMERA. Returns whether a room is live to publish to. */
    fun canToggleCamera(): Boolean = room != null

    fun leave(context: Context) {
        room?.disconnect()
        room = null
        scope?.let { it.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
        scope = null
        speaking.clear()
        participants.clear()
        target = null
        active = false
        connecting = false
        cameraEnabled = false
        CallService.stop(context.applicationContext)
    }

    /** A renderer needs the room's shared EGL context before it can draw a track. */
    fun initRenderer(view: io.livekit.android.renderer.TextureViewRenderer) {
        room?.initVideoRenderer(view)
    }

    private fun refresh(r: Room) {
        val rows = ArrayList<CallParticipant>()
        rows.add(toRow(r.localParticipant, isLocal = true))
        r.remoteParticipants.values.forEach { rows.add(toRow(it, isLocal = false)) }
        participants.clear()
        participants.addAll(rows)
    }

    private fun toRow(p: Participant, isLocal: Boolean): CallParticipant {
        val id = p.identity?.value.orEmpty()
        val video = p.videoTrackPublications.firstOrNull { it.second is VideoTrack }?.second as? VideoTrack
        return CallParticipant(
            identity = id,
            name = p.name?.takeIf { it.isNotBlank() } ?: id.removePrefix("guest-").ifEmpty { "someone" },
            isLocal = isLocal,
            speaking = id in speaking,
            video = video,
        )
    }
}
