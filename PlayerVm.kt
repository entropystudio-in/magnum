package com.cadence.player

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerVm(app: Application) : AndroidViewModel(app) {

    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    var current by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var quality by mutableStateOf<AudioQuality?>(null)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set

    // EQ UI state mirrors EqSettings (which the audio thread reads).
    var eqEnabled by mutableStateOf(true)
        private set
    var autoEqEnabled by mutableStateOf(true)
        private set
    var autoEqName by mutableStateOf("")
        private set
    var preamp by mutableStateOf(0f)
        private set
    var bands by mutableStateOf(FloatArray(10))
        private set

    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var probeJob: Job? = null
    private val byId = HashMap<Long, Song>()
    private val external = HashMap<Long, Song>()
    private var pendingExternal: Song? = null

    private fun lookup(id: Long?): Song? = if (id == null) null else (byId[id] ?: external[id])

    /** Called when another app (e.g. Files by Google) opens an audio file with magnum. */
    fun openExternal(uri: android.net.Uri) {
        viewModelScope.launch {
            val song = Library.fromUri(getApplication(), uri)
            external[song.id] = song
            if (controller == null) pendingExternal = song else play(listOf(song), 0)
        }
    }

    private fun connect() {
        val app = getApplication<Application>()
        EqSettings.load(app)
        syncEq()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            shuffle = c.shuffleModeEnabled
            repeatMode = c.repeatMode
            onItemChanged(c.currentMediaItem)
            isPlaying = c.isPlaying
            pendingExternal?.let { play(listOf(it), 0); pendingExternal = null }
        }, MoreExecutors.directExecutor())
    }

    fun loadLibrary() {
        viewModelScope.launch {
            loading = true
            val list = try { Library.load(getApplication()) } catch (_: Exception) { emptyList() }
            songs = list
            byId.clear()
            list.forEach { byId[it.id] = it }
            loading = false
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (playing) startTicker() else ticker?.cancel()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = onItemChanged(mediaItem)

        override fun onPlaybackStateChanged(state: Int) {
            controller?.let { durationMs = it.duration.coerceAtLeast(0L) }
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffle = enabled }
        override fun onRepeatModeChanged(mode: Int) { repeatMode = mode }
    }

    init { connect() }

    private fun onItemChanged(item: MediaItem?) {
        val song = lookup(item?.mediaId?.toLongOrNull())
        current = song
        positionMs = 0L
        durationMs = song?.durationMs ?: 0L
        quality = null
        probeJob?.cancel()
        if (song != null) {
            probeJob = viewModelScope.launch {
                quality = AudioQualityProbe.probe(getApplication(), song.uri, song.mime)
            }
        }
        controller?.let { c ->
            queue = (0 until c.mediaItemCount).mapNotNull { lookup(c.getMediaItemAt(it).mediaId.toLongOrNull()) }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                controller?.let {
                    positionMs = it.currentPosition.coerceAtLeast(0L)
                    val d = it.duration
                    if (d > 0) durationMs = d
                }
                delay(250)
            }
        }
    }

    private fun item(s: Song) = MediaItem.Builder()
        .setMediaId(s.id.toString())
        .setUri(s.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title)
                .setArtist(s.artist)
                .setAlbumTitle(s.album)
                .apply { if (s.id > 0) setArtworkUri(s.artUri) }
                .build()
        )
        .build()

    fun play(list: List<Song>, index: Int, shuffled: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { item(it) }, index.coerceIn(0, list.lastIndex), 0L)
        c.shuffleModeEnabled = shuffled
        c.prepare()
        c.play()
    }

    fun shufflePlay(list: List<Song>) {
        if (list.isEmpty()) return
        play(list, (list.indices).random(), shuffled = true)
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        positionMs = ms
    }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun playQueueIndex(i: Int) {
        controller?.let { it.seekTo(i, 0L); it.play() }
    }

    // ---- EQ ----
    private fun syncEq() {
        eqEnabled = EqSettings.enabled
        autoEqEnabled = EqSettings.autoEqEnabled
        autoEqName = EqSettings.autoEqName
        preamp = EqSettings.preampDb
        bands = EqSettings.bandGains.copyOf()
    }

    private fun persist() {
        EqSettings.save(getApplication())
        syncEq()
    }

    fun setEqEnabled(v: Boolean) { EqSettings.enabled = v; EqSettings.touch(); persist() }
    fun setAutoEqEnabled(v: Boolean) { EqSettings.autoEqEnabled = v; EqSettings.touch(); persist() }
    fun setBand(i: Int, gain: Float) { EqSettings.setBand(i, gain); syncEq() }
    fun bandsCommitted() { persist() }
    fun resetBands() { EqSettings.resetBands(); persist() }
    fun setPreamp(v: Float) { EqSettings.preampDb = v; EqSettings.touch(); syncEq() }

    fun importAutoEq(name: String, text: String): Boolean {
        val ok = EqSettings.loadAutoEq(name, text)
        persist()
        return ok
    }

    fun clearAutoEq() { EqSettings.clearAutoEq(); persist() }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
