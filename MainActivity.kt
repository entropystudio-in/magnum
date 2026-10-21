package com.cadence.player

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class Tab(val label: String, val icon: ImageVector) {
    Library("Library", Icons.Rounded.LibraryMusic),
    Search("Search", Icons.Rounded.Search),
    Sound("Sound", Icons.Rounded.Tune)
}

class MainActivity : ComponentActivity() {
    private val vm: PlayerVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CadenceTheme {
                Root(vm)
            }
        }
        // Only on a fresh launch, so rotating the screen doesn't restart the song.
        if (savedInstanceState == null) handleOpenWith(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenWith(intent)
    }

    private fun handleOpenWith(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { /* not every provider offers persistable grants; the one-time grant is enough */ }
        vm.openExternal(uri)
    }
}

@Composable
private fun Root(vm: PlayerVm) {
    val c = cadence
    var tab by remember { mutableStateOf(Tab.Library) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    val stack = remember { mutableStateListOf<LibRoute>(LibRoute.Home) }

    val perms = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.loadLibrary()
    }
    LaunchedEffect(Unit) { launcher.launch(perms) }

    BackHandler(enabled = nowPlayingOpen) { nowPlayingOpen = false }
    BackHandler(enabled = !nowPlayingOpen && tab == Tab.Library && stack.size > 1) { stack.removeAt(stack.lastIndex) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                Tab.Library -> LibraryHost(vm, stack)
                Tab.Search -> SearchScreen(vm)
                Tab.Sound -> SoundScreen(vm)
            }
        }

        // Mini player + tab bar
        Column(Modifier.align(Alignment.BottomCenter)) {
            if (vm.current != null) MiniPlayer(vm) { nowPlayingOpen = true }
            TabBar(tab) {
                if (it == tab && it == Tab.Library) {
                    while (stack.size > 1) stack.removeAt(stack.lastIndex)
                }
                tab = it
            }
        }

        AnimatedVisibility(
            visible = nowPlayingOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            NowPlayingScreen(vm) { nowPlayingOpen = false }
        }
    }
}

@Composable
private fun LibraryHost(vm: PlayerVm, stack: MutableList<LibRoute>) {
    fun push(r: LibRoute) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    when (val r = stack.last()) {
        LibRoute.Home -> LibraryHome(vm, ::push)
        LibRoute.Songs -> SongsScreen(vm, ::pop)
        LibRoute.Albums -> AlbumsScreen(vm, ::pop, ::push)
        LibRoute.Artists -> ArtistsScreen(vm, ::pop, ::push)
        is LibRoute.AlbumDetail -> AlbumDetailScreen(vm, r.album, ::pop)
        is LibRoute.ArtistDetail -> ArtistDetailScreen(vm, r.artist, ::pop)
    }
}

@Composable
private fun MiniPlayer(vm: PlayerVm, onOpen: () -> Unit) {
    val c = cadence
    val song = vm.current ?: return
    Row(
        Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.elevated)
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(song, 42.dp, 6.dp)
        Spacer(Modifier.width(12.dp))
        Text(song.title, style = T.body, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(
            if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = c.label,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable { vm.toggle() }.padding(6.dp)
        )
        Icon(
            Icons.Rounded.SkipNext, null, tint = c.label,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable { vm.next() }.padding(6.dp)
        )
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = cadence
    Column(Modifier.fillMaxWidth().background(c.background)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 6.dp, bottom = 4.dp)) {
            for (t in Tab.values()) {
                val on = t == selected
                Column(
                    Modifier.weight(1f).clickable { onSelect(t) }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(t.icon, null, tint = if (on) c.accent else c.secondaryLabel, modifier = Modifier.size(26.dp))
                    Text(t.label, style = T.tabLabel, color = if (on) c.accent else c.secondaryLabel)
                }
            }
        }
    }
}
