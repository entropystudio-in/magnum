package com.cadence.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

@Composable
fun Artwork(song: Song?, size: Dp?, corner: Dp = 6.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(corner)
    val c = cadence
    val placeholder: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        if (c.isDark) listOf(Color(0xFF3A3A3C), Color(0xFF2C2C2E))
                        else listOf(Color(0xFFEDEDF0), Color(0xFFD8D8DC))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = c.secondaryLabel, modifier = Modifier.size(if (size != null) size * 0.4f else 72.dp))
        }
    }
    Box((if (size != null) modifier.size(size) else modifier).clip(shape)) {
        val ctx = LocalContext.current
        val bitmap by produceState<ImageBitmap?>(null, song?.id) {
            value = if (song == null) null else ArtLoader.load(ctx, song)
        }
        val bmp = bitmap
        if (bmp == null) placeholder() else {
            Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun QualityBadge(q: AudioQuality?, modifier: Modifier = Modifier) {
    if (q == null) return
    val c = cadence
    Row(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(c.fill)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(q.badge, style = T.tabLabel, color = c.label)
    }
}

@Composable
fun SongRow(
    song: Song,
    playing: Boolean,
    showArt: Boolean = true,
    onClick: () -> Unit
) {
    val c = cadence
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArt) {
            Artwork(song, 48.dp, 5.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (playing) c.accent else c.label
            )
            Text(
                song.artist, style = T.subhead, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = c.secondaryLabel
            )
        }
    }
}

@Composable
fun Divider(startPad: Dp = 20.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startPad)
            .height(0.5.dp)
            .background(cadence.separator)
    )
}

/** Apple-style scrubber: thin rounded track that grows while dragging. */
@Composable
fun Scrubber(
    fraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = cadence.fill,
    fillColor: Color = cadence.secondaryLabel
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val shown = if (dragging) dragFraction else fraction.coerceIn(0f, 1f)
    val thickness by androidx.compose.animation.core.animateDpAsState(if (dragging) 10.dp else 5.dp, label = "thick")

    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    onSeek((off.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { off ->
                        dragging = true
                        dragFraction = (off.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = { dragging = false; onSeek(dragFraction) },
                    onDragCancel = { dragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(thickness)
                .clip(RoundedCornerShape(50))
                .background(trackColor)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .height(thickness)
                    .background(fillColor)
            )
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text, style = T.title2, color = cadence.label,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
fun LargeTitle(text: String) {
    Text(
        text, style = T.largeTitle, color = cadence.label,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 8.dp)
    )
}
