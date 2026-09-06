package br.com.augusto.jmud.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import androidx.media3.common.AudioAttributes as MediaAudioAttributes

class MudAudioManager(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var musicPlayer: ExoPlayer? = null
    private var currentMusicFile: String = ""
    private var currentSoundsFolder: String = ""

    private val longSoundPlayers = mutableListOf<ExoPlayer>()

    private val soundPool: SoundPool
    private val soundCacheLock = Any()
    private val soundCache = LinkedHashMap<String, Int>(16, 0.75f, true)
    private val activeStreams = mutableListOf<Int>()
    private val longSoundRoutes = ConcurrentHashMap<String, Boolean>()
    private val pendingPlays = ConcurrentHashMap<Int, MspCommand>()
    private val downloading = ConcurrentHashMap<String, MspCommand>()
    private val audioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var soundGeneration = 0

    @Volatile
    private var released = false

    private val longSoundAttributes = MediaAudioAttributes.Builder()
        .setUsage(C.USAGE_GAME)
        .setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION)
        .build()

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(15)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { pool, sampleId, status ->
            if (status == 0) {
                val command = pendingPlays.remove(sampleId) ?: return@setOnLoadCompleteListener
                val volume = volumeOf(command)
                trackStream(pool.play(sampleId, volume, volume, command.priority, soundLoop(command), 1f))
            }
        }
    }

    private fun volumeOf(command: MspCommand): Float = (command.volume / 100f).coerceIn(0f, 1f)

    private fun soundLoop(command: MspCommand): Int = when {
        command.loops == -1 -> -1
        command.loops > 1 -> command.loops - 1
        else -> 0
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    fun setSoundsFolder(folder: String) {
        currentSoundsFolder = folder
    }

    fun playMusic(command: MspCommand) = runCatching {
        if (command.fileName.equals("OFF", ignoreCase = true)) {
            stopMusic()
            return@runCatching
        }

        if (command.continueMusic &&
            command.fileName.equals(currentMusicFile, ignoreCase = true) &&
            musicPlayer?.isPlaying == true
        ) {
            musicPlayer?.volume = volumeOf(command)
            return@runCatching
        }

        val mediaItem = resolveMediaItemForExoPlayer(command) ?: return@runCatching
        currentMusicFile = command.fileName

        if (musicPlayer == null) {
            musicPlayer = ExoPlayer.Builder(context).build()
        } else {
            musicPlayer?.stop()
        }

        val repeats = if (command.loops > 1) {
            command.loops.coerceAtMost(MAX_MUSIC_REPEATS)
        } else {
            1
        }

        musicPlayer?.apply {
            clearMediaItems()
            setMediaItems(List(repeats) { mediaItem })
            volume = volumeOf(command)
            repeatMode = if (command.loops == -1) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            prepare()
            play()
        }
    }.getOrDefault(Unit)

    fun playTriggerSound(fileName: String) {
        playSound(MspCommand(isMusic = false, fileName = fileName, volume = 100, loops = 1, url = ""))
    }

    fun playSound(command: MspCommand) {
        if (command.fileName.isEmpty()) return

        if (command.fileName.equals("OFF", ignoreCase = true)) {
            stopAll()
            return
        }

        val generation = soundGeneration
        audioScope.launch {
            runCatching {
                val localFile = resolveLocalFile(command)
                if (localFile != null) {
                    playFromFile(localFile.absolutePath, command, generation)
                    return@runCatching
                }

                if (command.url.isNotEmpty()) {
                    val cacheFile = cacheFileFor(command)
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        playFromFile(cacheFile.absolutePath, command, generation)
                    } else {
                        downloadAndPlay(command, cacheFile)
                    }
                }
            }
        }
    }

    private fun playFromFile(path: String, command: MspCommand, generation: Int) {
        val needsLongPlayer = longSoundRoutes.getOrPut(path) { exceedsSoundPoolLimit(path) }
        if (generation != soundGeneration) return
        if (needsLongPlayer) {
            playWithLongPlayer(path, command)
        } else {
            playWithSoundPool(path, command, generation)
        }
    }

    private fun exceedsSoundPoolLimit(path: String): Boolean {
        val fileSize = File(path).length()
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            var decision: Boolean? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue

                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else {
                    0L
                }
                val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else {
                    DEFAULT_SAMPLE_RATE
                }
                val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else {
                    DEFAULT_CHANNELS
                }

                if (durationUs > 0L) {
                    decision = SoundRouting.needsLongPlayer(durationUs, sampleRate, channels)
                }
                break
            }
            decision ?: SoundRouting.needsLongPlayerForFileSize(fileSize)
        } catch (e: Exception) {
            SoundRouting.needsLongPlayerForFileSize(fileSize)
        } finally {
            try {
                extractor.release()
            } catch (e: Exception) {
            }
        }
    }

    private fun playWithSoundPool(path: String, command: MspCommand, generation: Int) {
        if (generation != soundGeneration) return
        val volume = volumeOf(command)
        val cachedId = synchronized(soundCacheLock) { soundCache[path] }

        if (cachedId != null) {
            if (pendingPlays.containsKey(cachedId)) {
                pendingPlays[cachedId] = command
            } else {
                trackStream(soundPool.play(cachedId, volume, volume, command.priority, soundLoop(command), 1f))
            }
        } else {
            val newId = synchronized(soundCacheLock) {
                if (released) return
                soundPool.load(path, 1)
            }
            pendingPlays[newId] = command
            synchronized(soundCacheLock) {
                soundCache[path] = newId
                while (soundCache.size > MAX_CACHED_SOUNDS) {
                    val eldest = soundCache.entries.iterator()
                    val entry = eldest.next()
                    eldest.remove()
                    pendingPlays.remove(entry.value)
                    soundPool.unload(entry.value)
                }
            }
        }
    }

    private fun trackStream(streamId: Int) {
        if (streamId == 0) return
        synchronized(activeStreams) {
            activeStreams.add(streamId)
            while (activeStreams.size > MAX_TRACKED_STREAMS) {
                activeStreams.removeAt(0)
            }
        }
    }

    private fun stopSoundPoolStreams() {
        if (released) return
        val streams = synchronized(activeStreams) {
            val copy = activeStreams.toList()
            activeStreams.clear()
            copy
        }
        for (streamId in streams) {
            try {
                soundPool.stop(streamId)
            } catch (e: Exception) {
            }
        }
    }

    private fun playWithLongPlayer(path: String, command: MspCommand) {
        val volume = volumeOf(command)
        val generation = soundGeneration
        runOnMain {
            if (generation != soundGeneration) return@runOnMain
            try {
                val player = acquireLongSoundPlayer()
                val mediaItem = MediaItem.fromUri(File(path).toURI().toString())
                val repeats = if (command.loops > 1) {
                    command.loops.coerceAtMost(MAX_LONG_SOUND_REPEATS)
                } else {
                    1
                }
                player.stop()
                player.clearMediaItems()
                player.setMediaItems(List(repeats) { mediaItem })
                player.repeatMode = if (command.loops == -1) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                player.volume = volume
                player.prepare()
                player.play()
            } catch (e: Exception) {
            }
        }
    }

    private fun acquireLongSoundPlayer(): ExoPlayer {
        val idle = longSoundPlayers.firstOrNull {
            it.playbackState == Player.STATE_IDLE || it.playbackState == Player.STATE_ENDED
        }
        if (idle != null) return idle

        if (longSoundPlayers.size < MAX_LONG_SOUND_PLAYERS) {
            val player = ExoPlayer.Builder(context)
                .setAudioAttributes(longSoundAttributes, false)
                .build()
            longSoundPlayers.add(player)
            return player
        }

        val recycled = longSoundPlayers.removeAt(0)
        longSoundPlayers.add(recycled)
        return recycled
    }

    private fun sanitizeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun cacheFileFor(command: MspCommand): File {
        val folderName = if (currentSoundsFolder.isNotEmpty()) sanitizeName(currentSoundsFolder) else "default"
        val dir = File(File(context.cacheDir, "msp_sounds"), folderName)
        return File(dir, sanitizeName(command.fileName))
    }

    private fun downloadAndPlay(command: MspCommand, cacheFile: File) {
        val key = cacheFile.absolutePath
        if (downloading.putIfAbsent(key, command) != null) {
            downloading[key] = command
            return
        }

        val generation = soundGeneration
        audioScope.launch {
            val success = runCatching {
                cacheFile.parentFile?.mkdirs()
                val temp = File(cacheFile.parentFile, cacheFile.name + "." + System.nanoTime() + ".part")
                val base = command.url
                val fullUrl = (if (base.endsWith("/")) base + command.fileName else base + "/" + command.fileName)
                    .replace(" ", "%20")
                val connection = URL(fullUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                try {
                    if (connection.responseCode !in 200..299) {
                        throw IOException("HTTP " + connection.responseCode)
                    }
                    connection.inputStream.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (!cacheFile.exists() && !temp.renameTo(cacheFile)) {
                        temp.copyTo(cacheFile, overwrite = true)
                    }
                    temp.delete()
                } finally {
                    connection.disconnect()
                }
            }.isSuccess

            val lastCommand = downloading.remove(key)
            if (success && lastCommand != null) {
                playFromFile(cacheFile.absolutePath, lastCommand, generation)
            }
        }
    }

    private fun resolveLocalFile(command: MspCommand): File? {
        if (currentSoundsFolder.isEmpty()) return null

        val actualFolder = File(AppStorage.baseDir(context), currentSoundsFolder)
        val folders = if (command.type.isNotBlank()) {
            listOf(actualFolder, File(actualFolder, command.type))
        } else {
            listOf(actualFolder)
        }

        val extensions = listOf("", ".wav", ".mp3", ".ogg")
        for (folder in folders) {
            for (ext in extensions) {
                val candidate = File(folder, command.fileName + ext)
                if (candidate.exists() && candidate.isFile) return candidate
            }
        }

        return null
    }

    private fun resolveMediaItemForExoPlayer(command: MspCommand): MediaItem? {
        if (command.url.isNotEmpty()) {
            val fullUrl = if (command.url.endsWith("/")) command.url + command.fileName else command.url + "/" + command.fileName
            return MediaItem.fromUri(fullUrl)
        }

        val localFile = resolveLocalFile(command)
        if (localFile != null) {
            return MediaItem.fromUri(localFile.toURI().toString())
        }

        return null
    }

    fun stopMusic() {
        currentMusicFile = ""
        runOnMain {
            try {
                musicPlayer?.stop()
            } catch (e: Exception) {
            }
        }
    }

    fun stopSounds() {
        soundGeneration++
        downloading.clear()
        stopSoundPoolStreams()
        pendingPlays.clear()
        runOnMain {
            for (player in longSoundPlayers) {
                try {
                    player.stop()
                    player.clearMediaItems()
                } catch (e: Exception) {
                }
            }
        }
    }

    fun stopAll() {
        stopMusic()
        stopSounds()
    }

    fun releaseAll() {
        soundGeneration++
        audioScope.cancel()
        downloading.clear()
        stopSoundPoolStreams()

        synchronized(soundCacheLock) {
            released = true
            soundPool.release()
            soundCache.clear()
        }
        pendingPlays.clear()
        longSoundRoutes.clear()

        runOnMain {
            try {
                musicPlayer?.release()
            } catch (e: Exception) {
            }
            musicPlayer = null
            for (player in longSoundPlayers) {
                try {
                    player.release()
                } catch (e: Exception) {
                }
            }
            longSoundPlayers.clear()
        }
    }

    private companion object {
        const val MAX_CACHED_SOUNDS = 32
        const val MAX_TRACKED_STREAMS = 32
        const val MAX_LONG_SOUND_PLAYERS = 6
        const val MAX_LONG_SOUND_REPEATS = 20
        const val MAX_MUSIC_REPEATS = 50
        const val DEFAULT_SAMPLE_RATE = 44100
        const val DEFAULT_CHANNELS = 2
    }
}
