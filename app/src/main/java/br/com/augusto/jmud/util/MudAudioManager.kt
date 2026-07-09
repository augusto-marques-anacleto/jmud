package br.com.augusto.jmud.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MudAudioManager(private val context: Context) {
    private var musicPlayer: ExoPlayer? = null
    private var currentMusicFile: String = ""
    private val dynamicPlayers = mutableListOf<ExoPlayer>()
    private var currentSoundsFolder: String = ""

    private val soundPool: SoundPool
    private val soundCache = ConcurrentHashMap<String, Int>()
    private val pendingPlays = ConcurrentHashMap<Int, MspCommand>()

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

        val volumeFinal = (command.volume / 100f).coerceIn(0f, 1f)

        if (command.url.isNotEmpty()) {
            playDynamicExoPlayer(command, volumeFinal)
            return@runCatching
        }

        val localFile = resolveLocalFile(command)
        if (localFile != null) {
            val path = localFile.absolutePath
            val cachedId = soundCache[path]

            if (cachedId != null) {
                soundPool.play(cachedId, volumeFinal, volumeFinal, command.priority, soundLoop(command), 1f)
            } else {
                val newId = soundPool.load(path, 1)
                soundCache[path] = newId
                pendingPlays[newId] = command
            }
        }
    }.getOrDefault(Unit)

    private fun playDynamicExoPlayer(command: MspCommand, volumeFinal: Float) {
        val mediaItem = resolveMediaItemForExoPlayer(command) ?: return

        dynamicPlayers.removeAll {
            val isFinished = it.playbackState == Player.STATE_ENDED || it.playbackState == Player.STATE_IDLE
            if (isFinished) it.release()
            isFinished
        }

        val player = ExoPlayer.Builder(context).build()
        dynamicPlayers.add(player)

        player.apply {
            setMediaItem(mediaItem)
            volume = volumeFinal
            prepare()
            play()
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
        dynamicPlayers.forEach {
            it.stop()
            it.release()
        }
        dynamicPlayers.clear()
        soundPool.autoPause()
        pendingPlays.clear()
    }

    fun stopAll() {
        stopMusic()
        stopSounds()
    }

    fun releaseAll() {
        musicPlayer?.release()
        musicPlayer = null

        dynamicPlayers.forEach { it.release() }
        dynamicPlayers.clear()

        soundPool.release()
        soundCache.clear()
        pendingPlays.clear()
    }
}