package com.cadence.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

sealed class LibRoute {
    data object Home : LibRoute()
    data object Artists : LibRoute()
    data object Albums : LibRoute()
    data object Songs : LibRoute()
    data class AlbumDetail(val album: Album) : LibRoute()
    data class ArtistDetail(val artist: Artist) : LibRoute()
}

private val BOTTOM_PAD = 150.dp

@Composable
fun LibraryHome(vm: PlayerVm, push: (LibRoute) -> Unit) {
    val c = cadence
    val albums = remember(vm.songs) { Library.albums(vm.songs) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = BOTTOM_PAD),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding()) {
                Text("Library", style = T.largeTitle, color = c.label, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                NavRow(Icons.Rounded.Person, "Artists") { push(LibRoute.Artists) }
                Divider(44.dp)
                NavRow(Icons.Rounded.Album, "Albums") { push(LibRoute.Albums) }
                Divider(44.dp)
                NavRow(Icons.Rounded.MusicNote, "Songs") { push(LibRoute.Songs) }
                Divider(44.dp)
                Text("Albums", style = T.title2, color = c.label, modifier = Modifier.padding(top = 26.dp, bottom = 2.dp))
            }
        }
        if (vm.songs.isEmpty() && !vm.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No music found on this device.", style = T.body, color = c.secondaryLabel)
            }
        }
        gridItems(albums) { a ->
            Column(Modifier.clickable { push(LibRoute.AlbumDetail(a)) }) {
                Artwork(a.songs.first(), null, 8.dp, Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(6.dp))
                Text(a.name, style = T.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = c.label,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.artist, style = T.subhead, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = cadence
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = T.title3.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.label, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.tertiaryLabel)
    }
}

@Composable
fun SubScreen(title: String, parent: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val c = cadence
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onBack).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.KeyboardArrowLeft, null, tint = c.accent, modifier = Modifier.size(32.dp))
            Text(parent, style = T.body, color = c.accent)
        }
        Text(title, style = T.largeTitle, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        content()
    }
}

@Composable
fun SongList(vm: PlayerVm, songs: List<Song>, showArt: Boolean = true) {
    LazyColumn(contentPadding = PaddingValues(bottom = BOTTOM_PAD)) {
        items(songs, key = { it.id }) { s ->
            SongRow(s, playing = vm.current?.id == s.id, showArt = showArt) {
                vm.play(songs, songs.indexOf(s))
            }
            Divider(if (showArt) 80.dp else 20.dp)
        }
    }
}

@Composable
fun SongsScreen(vm: PlayerVm, onBack: () -> Unit) =
    SubScreen("Songs", "Library", onBack) { SongList(vm, vm.songs) }

@Composable
fun AlbumsScreen(vm: PlayerVm, onBack: () -> Unit, push: (LibRoute) -> Unit) {
    val c = cadence
    val albums = remember(vm.songs) { Library.albums(vm.songs) }
    SubScreen("Albums", "Library", onBack) {
        LazyColumn(contentPadding = PaddingValues(bottom = BOTTOM_PAD)) {
            items(albums, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { push(LibRoute.AlbumDetail(a)) }.padding(horizontal = 20.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(a.songs.first(), 56.dp, 6.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = T.body, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.artist, style = T.subhead, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Divider(90.dp)
            }
        }
    }
}

@Composable
fun ArtistsScreen(vm: PlayerVm, onBack: () -> Unit, push: (LibRoute) -> Unit) {
    val c = cadence
    val artists = remember(vm.songs) { Library.artists(vm.songs) }
    SubScreen("Artists", "Library", onBack) {
        LazyColumn(contentPadding = PaddingValues(bottom = BOTTOM_PAD)) {
            items(artists, key = { it.name }) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { push(LibRoute.ArtistDetail(a)) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(a.name, style = T.body, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.tertiaryLabel)
                }
                Divider()
            }
        }
    }
}

@Composable
private fun PlayShuffleButtons(vm: PlayerVm, songs: List<Song>) {
    val c = cadence
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton(Icons.Rounded.PlayArrow, "Play", Modifier.weight(1f)) { vm.play(songs, 0) }
        PillButton(Icons.Rounded.Shuffle, "Shuffle", Modifier.weight(1f)) { vm.shufflePlay(songs) }
    }
}

@Composable
private fun PillButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = cadence
    Row(
        modifier.height(50.dp).clip(RoundedCornerShape(10.dp)).background(c.fill).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = T.headline, color = c.accent)
    }
}

@Composable
fun AlbumDetailScreen(vm: PlayerVm, album: Album, onBack: () -> Unit) {
    val c = cadence
    SubScreen(album.name, "Albums", onBack) {
        LazyColumn(contentPadding = PaddingValues(bottom = BOTTOM_PAD)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(album.songs.first(), 240.dp, 10.dp)
                    Text(album.artist, style = T.title3, color = c.accent, modifier = Modifier.padding(top = 12.dp))
                }
                PlayShuffleButtons(vm, album.songs)
            }
            items(album.songs, key = { it.id }) { s ->
                SongRow(s, vm.current?.id == s.id, onClick = { vm.play(album.songs, album.songs.indexOf(s)) }, showArt = false)
                Divider()
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(vm: PlayerVm, artist: Artist, onBack: () -> Unit) {
    SubScreen(artist.name, "Artists", onBack) {
        Column {
            PlayShuffleButtons(vm, artist.songs)
            SongList(vm, artist.songs)
        }
    }
}

@Composable
fun SearchScreen(vm: PlayerVm) {
    val c = cadence
    var query by remember { mutableStateOf("") }
    val results = remember(query, vm.songs) {
        if (query.isBlank()) emptyList()
        else vm.songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Search", style = T.largeTitle, color = c.label, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp))
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(c.fill)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Artists, Songs, Albums", style = T.body, color = c.secondaryLabel)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = T.body.copy(color = c.label),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SongList(vm, results)
    }
}
