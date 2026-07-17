package br.com.augusto.jmud.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
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

class MudAudioManager(private val context: Context) {
    private var musicPlayer: ExoPlayer? = null
    private var currentMusicFile: String = ""
    private var currentSoundsFolder: String = ""

    private val soundPool: SoundPool
    private val soundCacheLock = Any()
    private val soundCache = LinkedHashMap<String, Int>(16, 0.75f, true)
    private val pendingPlays = ConcurrentHashMap<Int, MspCommand>()
    private val downloading = ConcurrentHashMap<String, MspCommand>()
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var soundGeneration = 0

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
                val volume = (command.volume / 100f).coerceIn(0f, 1f)
                pool.play(sampleId, volume, volume, command.priority, soundLoop(command), 1f)
            }
        }
    }

    private fun soundLoop(command: MspCommand): Int = when {
        command.loops == -1 -> -1
        command.loops > 1 -> command.loops - 1
        else -> 0
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
            musicPlayer?.volume = (command.volume / 100f).coerceIn(0f, 1f)
            return@runCatching
        }

        val mediaItem = resolveMediaItemForExoPlayer(command) ?: return@runCatching
        currentMusicFile = command.fileName

        if (musicPlayer == null) {
            musicPlayer = ExoPlayer.Builder(context).build()
        } else {
            musicPlayer?.stop()
        }

        musicPlayer?.apply {
            setMediaItem(mediaItem)
            volume = (command.volume / 100f).coerceIn(0f, 1f)
            repeatMode = if (command.loops == -1 || command.loops > 1) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            prepare()
            play()
        }
    }.getOrDefault(Unit)

    fun playTriggerSound(fileName: String) {
        playSound(MspCommand(isMusic = false, fileName = fileName, volume = 100, loops = 1, url = ""))
    }

    fun playSound(command: MspCommand) = runCatching {
        if (command.fileName.isEmpty()) return@runCatching

        if (command.fileName.equals("OFF", ignoreCase = true)) {
            stopAll()
            return@runCatching
        }

        val localFile = resolveLocalFile(command)
        if (localFile != null) {
            playFromFile(localFile.absolutePath, command)
            return@runCatching
        }

        if (command.url.isNotEmpty()) {
            val cacheFile = cacheFileFor(command)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                playFromFile(cacheFile.absolutePath, command)
            } else {
                downloadAndPlay(command, cacheFile)
            }
        }
    }.getOrDefault(Unit)

    private fun playFromFile(path: String, command: MspCommand) {
        val volume = (command.volume / 100f).coerceIn(0f, 1f)
        val cachedId = synchronized(soundCacheLock) { soundCache[path] }

        if (cachedId != null) {
            if (pendingPlays.containsKey(cachedId)) {
                pendingPlays[cachedId] = command
            } else {
                soundPool.play(cachedId, volume, volume, command.priority, soundLoop(command), 1f)
            }
        } else {
            val newId = soundPool.load(path, 1)
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
        downloadScope.launch {
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
            if (success && lastCommand != null && generation == soundGeneration) {
                playFromFile(cacheFile.absolutePath, lastCommand)
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
        musicPlayer?.stop()
        currentMusicFile = ""
    }

    fun stopSounds() {
        soundGeneration++
        downloading.clear()
        soundPool.autoPause()
        pendingPlays.clear()
    }

    fun stopAll() {
        stopMusic()
        stopSounds()
    }

    fun releaseAll() {
        soundGeneration++
        downloadScope.cancel()
        downloading.clear()

        musicPlayer?.release()
        musicPlayer = null

        soundPool.release()
        synchronized(soundCacheLock) { soundCache.clear() }
        pendingPlays.clear()
    }

    private companion object {
        const val MAX_CACHED_SOUNDS = 32
    }
}
