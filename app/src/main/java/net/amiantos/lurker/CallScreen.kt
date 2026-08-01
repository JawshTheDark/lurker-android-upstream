// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import net.amiantos.lurker.ui.theme.AccentBlue
import net.amiantos.lurker.ui.theme.AlertRed
import net.amiantos.lurker.ui.theme.OnlineGreen
import net.amiantos.lurker.ui.theme.TextPrimary
import net.amiantos.lurker.ui.theme.TextSecondary

/**
 * Full-screen in-call UI (lurker#680). Shown as an overlay whenever
 * [CallController] holds a live (or connecting) call, so it sits above whatever
 * chat screen you were on and the call persists as you navigate.
 */
@Composable
fun CallScreen(onMinimize: () -> Unit, onModerate: (CallParticipant) -> Unit, canModerate: Boolean) {
    val ctl = CallController
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B0B10)).statusBarsPadding(),
    ) {
        // Header: who + a minimize affordance.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ctl.target?.displayName ?: "Call", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        ctl.connecting -> "Connecting…"
                        else -> "${ctl.participants.size} in call"
                    },
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
            CircleControl("▾", TextPrimary, Color(0x22FFFFFF)) { onMinimize() }
        }
        ctl.error?.let {
            Text(it, color = AlertRed, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        // Tiles.
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (ctl.participants.size <= 1) 1 else 2),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ctl.participants, key = { it.identity }) { p ->
                ParticipantTile(p, canModerate && !p.isLocal, onModerate)
            }
        }
        // Controls.
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleControl(if (ctl.micEnabled) "🎤" else "🔇", TextPrimary, if (ctl.micEnabled) Color(0x22FFFFFF) else AlertRed) { ctl.toggleMic() }
            CircleControl(if (ctl.cameraEnabled) "📹" else "📷", TextPrimary, if (ctl.cameraEnabled) OnlineGreen else Color(0x22FFFFFF)) { ctl.toggleCamera() }
            CircleControl("✕", Color.White, AlertRed) { ctl.leave(context) }
        }
    }
}

@Composable
private fun ParticipantTile(p: CallParticipant, canModerate: Boolean, onModerate: (CallParticipant) -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF16161E)),
        contentAlignment = Alignment.Center,
    ) {
        val video = p.video
        if (video != null) {
            AndroidView(
                factory = { ctx ->
                    io.livekit.android.renderer.TextureViewRenderer(ctx).also { r ->
                        CallController.initRenderer(r)
                        video.addRenderer(r)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { r -> video.removeRenderer(r) },
            )
        } else {
            // Audio-only: an initials disc.
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(p.name.take(1).uppercase(), color = AccentBlue, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Name + speaking ring.
        Row(
            Modifier.align(Alignment.BottomStart).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (p.speaking) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(OnlineGreen))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                if (p.isLocal) "You" else p.name,
                color = TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (canModerate) {
            Text(
                "⋯",
                color = TextPrimary,
                fontSize = 20.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clickableNoRipple { onModerate(p) },
            )
        }
    }
}

/** Slim strip shown while a call is minimized, so you can chat and jump back in. */
@Composable
fun ReturnToCallBar(onExpand: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(OnlineGreen)
            .statusBarsPadding()
            .clickable(onClick = onExpand)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "● In call — ${CallController.target?.displayName ?: ""}   ·   tap to return",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CircleControl(glyph: String, fg: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(bg).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = fg, fontSize = 22.sp)
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
