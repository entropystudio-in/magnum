package com.cadence.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

@Composable
fun NowPlayingScreen(vm: PlayerVm, onClose: () -> Unit) {
    val c = cadence
    var showQueue by remember { mutableStateOf(false) }
    val song = vm.current
    val q = vm.quality

    val artScale by animateFloatAsState(
        targetValue = if (vm.isPlaying) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "art"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (c.isDark) listOf(androidx.compose.ui.graphics.Color(0xFF2A2A2D), androidx.compose.ui.graphics.Color(0xFF000000))
                    else listOf(androidx.compose.ui.graphics.Color(0xFFF2F2F7), androidx.compose.ui.graphics.Color(0xFFFFFFFF))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
        ) {
            // Grabber / close
            Box(Modifier.fillMaxWidth().height(36.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(5.dp).clip(RoundedCornerShape(50)).background(c.tertiaryLabel))
            }

            if (showQueue) {
                Text("Playing Next", style = T.title2, color = c.label, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(vm.queue) { i, s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.playQueueIndex(i) }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Artwork(s, 44.dp, 5.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (s.id == song?.id) c.accent else c.label)
                                Text(s.artist, style = T.subhead, maxLines = 1, overflow = TextOverflow.Ellipsis, color = c.secondaryLabel)
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(0.5f))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Artwork(
                        song, null, 10.dp,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .scale(artScale)
                            .shadow(if (vm.isPlaying) 24.dp else 6.dp, RoundedCornerShape(10.dp))
                    )
                }
                Spacer(Modifier.weight(0.6f))
            }

            // Title + artist
            Column(Modifier.fillMaxWidth().padding(top = if (showQueue) 8.dp else 0.dp)) {
                Text(song?.title ?: "Not Playing", style = T.title2, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song?.artist ?: "", style = T.title2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(14.dp))
            val frac = if (vm.durationMs > 0) vm.positionMs.toFloat() / vm.durationMs else 0f
            Scrubber(frac, onSeek = { vm.seekTo((it * vm.durationMs).toLong()) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(vm.positionMs), style = T.caption, color = c.secondaryLabel)
                Text("-" + formatTime((vm.durationMs - vm.positionMs).coerceAtLeast(0)), style = T.caption, color = c.secondaryLabel)
            }

            // Audio quality (the Apple Music "Lossless" pill, plus the real numbers)
            Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                QualityBadge(q)
                if (q != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(q.summary, style = T.caption, color = c.secondaryLabel, textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.weight(0.5f))

            // Transport
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = c.label,
                    modifier = Modifier.size(52.dp).clip(CircleShape).clickable { vm.previous() })
                Icon(
                    if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (vm.isPlaying) "Pause" else "Play", tint = c.label,
                    modifier = Modifier.size(76.dp).clip(CircleShape).clickable { vm.toggle() }
                )
                Icon(Icons.Rounded.SkipNext, "Next", tint = c.label,
                    modifier = Modifier.size(52.dp).clip(CircleShape).clickable { vm.next() })
            }

            Spacer(Modifier.weight(0.5f))

            // Shuffle / queue / repeat
            Row(
                Modifier.fillMaxWidth().padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToggleIcon(Icons.Rounded.Shuffle, vm.shuffle) { vm.toggleShuffle() }
                ToggleIcon(Icons.AutoMirrored.Rounded.QueueMusic, showQueue) { showQueue = !showQueue }
                ToggleIcon(
                    if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    vm.repeatMode != Player.REPEAT_MODE_OFF
                ) { vm.cycleRepeat() }
            }
        }
    }
}

@Composable
private fun ToggleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    val c = cadence
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) c.fill else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (on) c.accent else c.secondaryLabel, modifier = Modifier.size(24.dp))
    }
}
