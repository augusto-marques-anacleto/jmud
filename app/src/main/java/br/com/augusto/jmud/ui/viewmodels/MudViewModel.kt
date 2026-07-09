package br.com.augusto.jmud.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.augusto.jmud.R
import br.com.augusto.jmud.data.local.CharacterRepository
import br.com.augusto.jmud.data.local.SettingsRepository
import br.com.augusto.jmud.data.local.TimerRepository
import br.com.augusto.jmud.data.local.TriggerRepository
import br.com.augusto.jmud.data.network.MudConnectionManager
import br.com.augusto.jmud.data.network.MudEvent
import br.com.augusto.jmud.domain.MudCharacter
import br.com.augusto.jmud.domain.MudTimer
import br.com.augusto.jmud.domain.MudTrigger
import br.com.augusto.jmud.domain.Scope
import br.com.augusto.jmud.util.AppStorage
import br.com.augusto.jmud.util.BackupManager
import br.com.augusto.jmud.util.LogManager
import br.com.augusto.jmud.util.MspParser
import br.com.augusto.jmud.util.MudAudioManager
import br.com.augusto.jmud.util.SoundPackInstaller
import br.com.augusto.jmud.util.SoundPackNotifier
import br.com.augusto.jmud.util.SoundPackProgress
import br.com.augusto.jmud.util.SoundPackStep
import br.com.augusto.jmud.util.TTSManager
import br.com.augusto.jmud.util.TriggerEngine
import br.com.augusto.jmud.util.TriggerMatch
import br.com.augusto.jmud.util.UpdateInfo
import br.com.augusto.jmud.util.UpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class AppScreen { MAIN, GAME }

class MudViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CharacterRepository(application)
    private val timerRepository = TimerRepository(application)
    private val triggerRepository = TriggerRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val backupManager = BackupManager(application)
    private val updateManager = UpdateManager(application)
    private val ttsManager = TTSManager(application)
    private val audioManager = MudAudioManager(application)
    private val logManager = LogManager(application)

    private val timerJobs = mutableListOf<Job>()

    val characters = mutableStateListOf<MudCharacter>()
    val timers = mutableStateListOf<MudTimer>()
    val triggers = mutableStateListOf<MudTrigger>()
    val gameMessages = mutableStateListOf<String>()
    val commandHistory = mutableStateListOf<String>()
    val namedHistories = mutableStateMapOf<String, SnapshotStateList<String>>()
    val announcements = MutableSharedFlow<String>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    var currentScreen = mutableStateOf(AppScreen.MAIN)
    var activeCharacter = mutableStateOf<MudCharacter?>(null)
    var isConnected = mutableStateOf(false)
    var lastSentCommand = mutableStateOf("")
    var userJustSentCommand = mutableStateOf(false)
    var flushNextTTS = mutableStateOf(false)

    var currentGameUseTTS = mutableStateOf(true)
    var currentGamePlaySounds = mutableStateOf(true)

    var manualHost = mutableStateOf(repository.getManualHost())
    var manualPort = mutableStateOf(repository.getManualPort())
    var manualUseTTS = mutableStateOf(repository.getManualUseTTS())
    var manualPlaySounds = mutableStateOf(repository.getManualPlaySounds())

    var soundPackProgress = mutableStateOf<SoundPackProgress?>(null)
    var soundPackDialogVisible = mutableStateOf(false)
    private var soundPackJob: Job? = null
    private val soundPackInstaller = SoundPackInstaller()
    private val soundPackNotifier = SoundPackNotifier(application)

    var encoding = mutableStateOf(settingsRepository.getEncoding())
    var ttsEngine = mutableStateOf(settingsRepository.getTtsEngine())
    var ttsVoice = mutableStateOf(settingsRepository.getTtsVoice())
    var ttsRate = mutableStateOf(settingsRepository.getTtsRate())
    var ttsPitch = mutableStateOf(settingsRepository.getTtsPitch())
    var ttsVolume = mutableStateOf(settingsRepository.getTtsVolume())
    var logsEnabled = mutableStateOf(settingsRepository.getLogsEnabled())
    var logRetentionDays = mutableStateOf(settingsRepository.getLogRetentionDays())
    var triggersEnabled = mutableStateOf(settingsRepository.getTriggersEnabled())
    var timersEnabled = mutableStateOf(settingsRepository.getTimersEnabled())
    var backupMessage = mutableStateOf<String?>(null)
    var logsMessage = mutableStateOf<String?>(null)

    var availableUpdate = mutableStateOf<UpdateInfo?>(null)
    var updateDialogVisible = mutableStateOf(false)
    var updateDownloadPercent = mutableStateOf<Int?>(null)
    var updateMessage = mutableStateOf<String?>(null)
    private var updateJob: Job? = null

    init {
        characters.addAll(repository.loadCharacters())
        timers.addAll(timerRepository.loadTimers())
        triggers.addAll(triggerRepository.loadTriggers())

        MudConnectionManager.setEncoding(encoding.value)
        if (ttsEngine.value.isNotBlank()) {
            ttsManager.setEngine(ttsEngine.value)
        }
        ttsManager.configure(ttsRate.value, ttsPitch.value, ttsVolume.value, ttsVoice.value)
        logManager.cleanupOldLogs(logRetentionDays.value)

        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { updateManager.checkForUpdate() }
            if (info != null) {
                availableUpdate.value = info
                updateDialogVisible.value = true
            }
        }

        viewModelScope.launch {
            MudConnectionManager.events.collect { event ->
                when (event) {
                    is MudEvent.LineReceived -> onLineReceived(event.text)
                    is MudEvent.ConnectionFailed -> {
                        postStatusMessage(getString(R.string.connection_error, event.detail ?: ""))
                        onDisconnected()
                    }
                    MudEvent.Disconnected -> {
                        postStatusMessage(getString(R.string.server_closed_connection))
                        onDisconnected()
                    }
                    MudEvent.SendFailed -> {
                        postStatusMessage(getString(R.string.send_failed))
                        onDisconnected()
                    }
                }
            }
        }
    }

    private fun getString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    private fun onLineReceived(rawMessage: String) {
        val parsedMsp = MspParser.parse(rawMessage)
        val finalMessage = parsedMsp.cleanText

        val matches = collectTriggerMatches(finalMessage)
        val ignored = matches.any { (trigger, _) -> trigger.ignoreLine }

        if (currentGamePlaySounds.value && !ignored) {
            parsedMsp.commands.forEach { command ->
                if (command.isMusic) {
                    audioManager.playMusic(command)
                } else {
                    audioManager.playSound(command)
                }
            }
        }

        if (finalMessage.isNotBlank()) {
            if (!ignored) {
                postStatusMessage(finalMessage)
            }
            executeTriggerActions(matches, finalMessage)
        }
    }

    private fun collectTriggerMatches(line: String): List<Pair<MudTrigger, TriggerMatch>> {
        if (line.isBlank()) return emptyList()
        if (!triggersEnabled.value) return emptyList()
        if (!isConnected.value) return emptyList()
        val character = activeCharacter.value ?: return emptyList()
        return triggers.mapNotNull { trigger ->
            if (!trigger.enabled) return@mapNotNull null
            if (!scopeMatches(trigger.scope, trigger.scopeValue, character)) return@mapNotNull null
            TriggerEngine.match(trigger, line)?.let { trigger to it }
        }
    }

    private fun executeTriggerActions(
        matches: List<Pair<MudTrigger, TriggerMatch>>,
        line: String
    ) {
        for ((trigger, match) in matches) {
            if (trigger.historyName.isNotBlank()) {
                addToNamedHistory(trigger.historyName.trim(), line)
            }
            if (trigger.soundName.isNotBlank() && currentGamePlaySounds.value) {
                audioManager.playTriggerSound(trigger.soundName.trim())
            }
            if (trigger.commands.isNotBlank()) {
                val commands = TriggerEngine.expandCommands(trigger.commands, match)
                if (commands.isNotEmpty()) {
                    viewModelScope.launch {
                        for (cmd in commands) {
                            transmit(cmd)
                            delay(300)
                        }
                    }
                }
            }
        }
    }

    private fun addToNamedHistory(name: String, line: String) {
        val list = namedHistories.getOrPut(name) { mutableStateListOf() }
        list.add(line)
        if (list.size > 500) {
            list.removeAt(0)
        }
    }

    private fun scopeMatches(scope: String, scopeValue: String, character: MudCharacter): Boolean =
        when (scope) {
            Scope.MUD -> scopeValue.equals(character.host, ignoreCase = true)
            Scope.CHARACTER -> scopeValue == character.id
            else -> true
        }

    private fun postStatusMessage(message: String) {
        if (gameMessages.size > 550) {
            gameMessages.removeRange(0, gameMessages.size - 500)
        }
        gameMessages.add(message)

        if (logsEnabled.value) {
            logManager.logIncoming(message)
        }

        if (currentScreen.value == AppScreen.GAME) {
            if (currentGameUseTTS.value) {
                ttsManager.speak(message, flushNextTTS.value)
                flushNextTTS.value = false
            } else {
                announcements.tryEmit(message)
            }
        }
    }

    private fun onDisconnected() {
        if (!isConnected.value) return
        isConnected.value = false
        stopTimers()
        audioManager.stopAll()
        logManager.endSession()
        MudConnectionManager.disconnect(getApplication())
    }

    fun addCharacter(name: String, host: String, port: Int, password: String, autoLogin: Boolean, commands: String, useTTS: Boolean, playSounds: Boolean, soundsFolder: String) {
        val newCharacter = MudCharacter(
            id = UUID.randomUUID().toString(),
            name = name,
            host = host,
            port = port,
            password = password,
            autoLogin = autoLogin,
            postConnectCommands = commands,
            useTTS = useTTS,
            playSounds = playSounds,
            soundsFolder = soundsFolder
        )
        characters.add(newCharacter)
        repository.saveCharacters(characters)
    }

    fun updateCharacter(updatedCharacter: MudCharacter) {
        val index = characters.indexOfFirst { it.id == updatedCharacter.id }
        if (index != -1) {
            characters[index] = updatedCharacter
            repository.saveCharacters(characters)
            if (activeCharacter.value?.id == updatedCharacter.id) {
                activeCharacter.value = updatedCharacter
            }
        }
    }

    fun removeCharacter(character: MudCharacter) {
        characters.remove(character)
        repository.saveCharacters(characters)
    }

    fun saveTrigger(trigger: MudTrigger) {
        val index = triggers.indexOfFirst { it.id == trigger.id }
        if (index != -1) {
            triggers[index] = trigger
        } else {
            triggers.add(trigger)
        }
        triggerRepository.saveTriggers(triggers)
    }

    fun removeTrigger(trigger: MudTrigger) {
        triggers.remove(trigger)
        triggerRepository.saveTriggers(triggers)
    }

    fun addTimer(seconds: Int, commands: String, scope: String, scopeValue: String, enabled: Boolean) {
        val newTimer = MudTimer(
            id = UUID.randomUUID().toString(),
            seconds = seconds.coerceAtLeast(1),
            commands = commands,
            scope = scope,
            scopeValue = scopeValue,
            enabled = enabled
        )
        timers.add(newTimer)
        timerRepository.saveTimers(timers)
        restartTimersIfConnected()
    }

    fun updateTimer(updatedTimer: MudTimer) {
        val index = timers.indexOfFirst { it.id == updatedTimer.id }
        if (index != -1) {
            timers[index] = updatedTimer.copy(seconds = updatedTimer.seconds.coerceAtLeast(1))
            timerRepository.saveTimers(timers)
            restartTimersIfConnected()
        }
    }

    fun removeTimer(timer: MudTimer) {
        timers.remove(timer)
        timerRepository.saveTimers(timers)
        restartTimersIfConnected()
    }

    private fun restartTimersIfConnected() {
        val character = activeCharacter.value
        if (isConnected.value && character != null) {
            startTimers(character)
        }
    }

    private fun startTimers(character: MudCharacter) {
        stopTimers()
        if (!timersEnabled.value) return
        for (timer in timers) {
            if (!timer.enabled) continue
            if (!scopeMatches(timer.scope, timer.scopeValue, character)) continue
            timerJobs.add(viewModelScope.launch {
                while (isConnected.value) {
                    delay(timer.seconds.coerceAtLeast(1) * 1000L)
                    if (!isConnected.value) break
                    for (cmd in timer.commands.split("\n")) {
                        if (cmd.isNotBlank()) {
                            transmit(cmd.trim())
                            delay(300)
                        }
                    }
                }
            })
        }
    }

    private fun stopTimers() {
        timerJobs.forEach { it.cancel() }
        timerJobs.clear()
    }

    fun setEncodingSetting(value: String) {
        encoding.value = value
        settingsRepository.saveEncoding(value)
        MudConnectionManager.setEncoding(value)
    }

    fun setTtsEngineSetting(value: String) {
        ttsEngine.value = value
        ttsVoice.value = ""
        settingsRepository.saveTtsEngine(value)
        settingsRepository.saveTtsVoice("")
        ttsManager.setEngine(value)
        ttsManager.configure(ttsRate.value, ttsPitch.value, ttsVolume.value, "")
    }

    fun setTtsVoiceSetting(value: String) {
        ttsVoice.value = value
        settingsRepository.saveTtsVoice(value)
        ttsManager.configure(ttsRate.value, ttsPitch.value, ttsVolume.value, value)
    }

    fun setTtsRateSetting(value: Float) {
        ttsRate.value = value
        settingsRepository.saveTtsRate(value)
        ttsManager.configure(value, ttsPitch.value, ttsVolume.value, ttsVoice.value)
    }

    fun setTtsPitchSetting(value: Float) {
        ttsPitch.value = value
        settingsRepository.saveTtsPitch(value)
        ttsManager.configure(ttsRate.value, value, ttsVolume.value, ttsVoice.value)
    }

    fun setTtsVolumeSetting(value: Float) {
        ttsVolume.value = value
        settingsRepository.saveTtsVolume(value)
        ttsManager.configure(ttsRate.value, ttsPitch.value, value, ttsVoice.value)
    }

    fun resetTtsRateAndPitchToSystem() {
        settingsRepository.clearTtsRateAndPitch()
        ttsRate.value = settingsRepository.getSystemTtsRate()
        ttsPitch.value = settingsRepository.getSystemTtsPitch()
        ttsManager.configure(ttsRate.value, ttsPitch.value, ttsVolume.value, ttsVoice.value)
    }

    fun availableEngines(): List<Pair<String, String>> = ttsManager.getEngines()

    fun availableVoices(): List<String> = ttsManager.getVoices()

    fun testVoice() {
        ttsManager.speak(getString(R.string.test_voice_message, getString(R.string.app_name)), true)
    }

    fun setLogsEnabledSetting(value: Boolean) {
        logsEnabled.value = value
        settingsRepository.saveLogsEnabled(value)
        if (!value) {
            logManager.endSession()
        } else if (isConnected.value) {
            activeCharacter.value?.let { logManager.startSession(it.name, it.host) }
        }
    }

    fun setLogRetentionSetting(days: Int) {
        logRetentionDays.value = days
        settingsRepository.saveLogRetentionDays(days)
        logManager.cleanupOldLogs(days)
    }

    fun deleteAllLogs() {
        val wasLogging = logsEnabled.value && isConnected.value
        if (wasLogging) {
            logManager.endSession()
        }
        logManager.deleteAllLogs()
        if (wasLogging) {
            activeCharacter.value?.let { logManager.startSession(it.name, it.host) }
        }
        logsMessage.value = getString(R.string.logs_deleted)
    }

    fun clearLogsMessage() {
        logsMessage.value = null
    }

    fun setTriggersEnabledSetting(value: Boolean) {
        triggersEnabled.value = value
        settingsRepository.saveTriggersEnabled(value)
    }

    fun setTimersEnabledSetting(value: Boolean) {
        timersEnabled.value = value
        settingsRepository.saveTimersEnabled(value)
        if (value) {
            restartTimersIfConnected()
        } else {
            stopTimers()
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { updateManager.checkForUpdate() }
            if (info != null) {
                availableUpdate.value = info
                updateDialogVisible.value = true
            } else {
                updateMessage.value = getString(R.string.update_none)
            }
        }
    }

    fun downloadUpdate() {
        val info = availableUpdate.value ?: return
        if (updateJob?.isActive == true) return
        updateDialogVisible.value = false
        updateDownloadPercent.value = 0
        updateJob = viewModelScope.launch {
            var failed = false
            try {
                val file = withContext(Dispatchers.IO) {
                    updateManager.downloadApk(info.url) { percent ->
                        updateDownloadPercent.value = percent
                    }
                }
                failed = file == null || !updateManager.installApk(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            } finally {
                withContext(NonCancellable) {
                    updateDownloadPercent.value = null
                }
            }
            if (failed) {
                updateMessage.value = getString(R.string.update_failed)
            }
        }
    }

    fun cancelUpdateDownload() {
        updateJob?.cancel()
    }

    fun dismissUpdateDialog() {
        updateDialogVisible.value = false
    }

    fun clearUpdateMessage() {
        updateMessage.value = null
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        output.write(backupManager.exportToJson().toByteArray(Charsets.UTF_8))
                        true
                    } ?: false
                } catch (e: Exception) {
                    false
                }
            }
            backupMessage.value = getString(
                if (ok) R.string.backup_export_ok else R.string.backup_export_failed
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val json = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    json != null && backupManager.importFromJson(json)
                } catch (e: Exception) {
                    false
                }
            }
            if (ok) {
                reloadFromStorage()
            }
            backupMessage.value = getString(
                if (ok) R.string.backup_import_ok else R.string.backup_import_failed
            )
        }
    }

    fun clearBackupMessage() {
        backupMessage.value = null
    }

    private fun reloadFromStorage() {
        characters.clear()
        characters.addAll(repository.loadCharacters())
        timers.clear()
        timers.addAll(timerRepository.loadTimers())
        triggers.clear()
        triggers.addAll(triggerRepository.loadTriggers())

        manualHost.value = repository.getManualHost()
        manualPort.value = repository.getManualPort()
        manualUseTTS.value = repository.getManualUseTTS()
        manualPlaySounds.value = repository.getManualPlaySounds()

        encoding.value = settingsRepository.getEncoding()
        MudConnectionManager.setEncoding(encoding.value)

        ttsEngine.value = settingsRepository.getTtsEngine()
        ttsVoice.value = settingsRepository.getTtsVoice()
        ttsRate.value = settingsRepository.getTtsRate()
        ttsPitch.value = settingsRepository.getTtsPitch()
        ttsVolume.value = settingsRepository.getTtsVolume()
        ttsManager.setEngine(ttsEngine.value)
        ttsManager.configure(ttsRate.value, ttsPitch.value, ttsVolume.value, ttsVoice.value)

        logsEnabled.value = settingsRepository.getLogsEnabled()
        logRetentionDays.value = settingsRepository.getLogRetentionDays()
        triggersEnabled.value = settingsRepository.getTriggersEnabled()
        timersEnabled.value = settingsRepository.getTimersEnabled()
        logManager.cleanupOldLogs(logRetentionDays.value)
        restartTimersIfConnected()
    }

    fun downloadSoundPack(rawUrl: String, folder: String) {
        if (soundPackJob?.isActive == true) return
        if (soundPackInstaller.cleanUrl(rawUrl).isEmpty()) {
            postStatusMessage(getString(R.string.invalid_link))
            return
        }
        runSoundPackJob(folder) { tempZip, targetDir ->
            soundPackInstaller.download(rawUrl, tempZip) { publishSoundPackProgress(it) } &&
                soundPackInstaller.extractAndCopy(tempZip, targetDir) { publishSoundPackProgress(it) }
        }
    }

    fun importSoundPack(uri: Uri, folder: String) {
        if (soundPackJob?.isActive == true) return
        runSoundPackJob(folder) { tempZip, targetDir ->
            val copied = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                tempZip.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            copied && soundPackInstaller.extractAndCopy(tempZip, targetDir) { publishSoundPackProgress(it) }
        }
    }

    private fun publishSoundPackProgress(progress: SoundPackProgress) {
        soundPackProgress.value = progress
        soundPackNotifier.update(progress)
    }

    fun isSoundPackRunning(): Boolean = soundPackJob?.isActive == true

    private fun runSoundPackJob(
        folder: String,
        work: suspend (tempZip: File, targetDir: File) -> Boolean
    ) {
        val app = getApplication<Application>()
        soundPackDialogVisible.value = true
        soundPackProgress.value = SoundPackProgress(SoundPackStep.DOWNLOADING, 0, totalKnown = false)
        soundPackJob = viewModelScope.launch {
            var resultMessage = getString(R.string.sound_pack_failed)
            val tempRoot = app.externalCacheDir ?: app.cacheDir
            val tempZip = File(tempRoot, "soundpack.zip")
            try {
                val ok = withContext(Dispatchers.IO) {
                    work(tempZip, File(AppStorage.baseDir(app), folder))
                }
                if (ok) {
                    resultMessage = getString(R.string.sound_pack_installed, folder)
                }
            } catch (e: CancellationException) {
                resultMessage = getString(R.string.sound_pack_cancelled)
            } catch (e: Exception) {
            } finally {
                withContext(NonCancellable) {
                    tempZip.delete()
                    soundPackNotifier.cancel()
                    soundPackProgress.value = null
                    soundPackDialogVisible.value = false
                    postStatusMessage(resultMessage)
                }
            }
        }
    }

    fun cancelSoundPack() {
        soundPackJob?.cancel()
    }

    fun hideSoundPackDialog() {
        soundPackDialogVisible.value = false
    }

    fun showSoundPackDialog() {
        soundPackDialogVisible.value = true
    }

    fun openLink(url: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
        }
    }

    fun setGameUseTTS(value: Boolean) {
        currentGameUseTTS.value = value
        if (!value) {
            ttsManager.stop()
        }
    }

    fun setGamePlaySounds(value: Boolean) {
        currentGamePlaySounds.value = value
        if (!value) {
            audioManager.stopAll()
        }
    }

    private fun transmit(command: String) {
        if (logsEnabled.value) {
            logManager.logOutgoing(command)
        }
        MudConnectionManager.sendMessage(command)
    }

    fun connectManual() {
        val portInt = manualPort.value.toIntOrNull() ?: 4000
        repository.saveManualConnection(manualHost.value, manualPort.value, manualUseTTS.value, manualPlaySounds.value)

        val sonsDir = File(AppStorage.baseDir(getApplication()), "Sons")
        if (!sonsDir.exists()) {
            sonsDir.mkdirs()
        }

        val manualCharacter = MudCharacter(
            id = "MANUAL_CONNECTION",
            name = getString(R.string.manual_connection),
            host = manualHost.value,
            port = portInt,
            password = "",
            autoLogin = false,
            postConnectCommands = "",
            useTTS = manualUseTTS.value,
            playSounds = manualPlaySounds.value,
            soundsFolder = "Sons"
        )

        connect(manualCharacter)
    }

    fun connect(character: MudCharacter) {
        activeCharacter.value = character
        currentGameUseTTS.value = character.useTTS
        currentGamePlaySounds.value = character.playSounds
        audioManager.setSoundsFolder(character.soundsFolder)

        gameMessages.clear()
        namedHistories.clear()
        lastSentCommand.value = ""
        userJustSentCommand.value = false
        flushNextTTS.value = false
        isConnected.value = true
        currentScreen.value = AppScreen.GAME

        if (logsEnabled.value) {
            logManager.startSession(character.name, character.host)
        }

        val startMessage = getString(R.string.connecting_to, character.host, character.port)
        gameMessages.add(startMessage)
        if (logsEnabled.value) logManager.logIncoming(startMessage)
        if (currentGameUseTTS.value) {
            ttsManager.speak(startMessage, true)
        } else {
            announcements.tryEmit(startMessage)
        }

        MudConnectionManager.connect(getApplication(), character.host, character.port)

        viewModelScope.launch {
            delay(1000)
            if (character.autoLogin && character.name.isNotBlank() && character.password.isNotBlank()) {
                transmit(character.name.trim())
                delay(500)
                transmit(character.password)
                delay(500)
            }
            for (cmd in character.postConnectCommands.split("\n")) {
                if (cmd.isNotBlank()) {
                    transmit(cmd.trim())
                    delay(300)
                }
            }
        }

        startTimers(character)
    }

    fun sendMessage(message: String) {
        if (message.isNotBlank()) {
            lastSentCommand.value = message
            if (commandHistory.isEmpty() || commandHistory.last() != message) {
                commandHistory.add(message)
                if (commandHistory.size > 100) commandHistory.removeAt(0)
            }
        }
        val finalCommand = if (message.isNotBlank()) message else lastSentCommand.value

        if (finalCommand.isNotBlank()) {
            userJustSentCommand.value = true
            flushNextTTS.value = true
            transmit(finalCommand)
        }
    }

    fun reconnect() {
        val character = activeCharacter.value ?: return
        connect(character)
    }

    fun clearHistory() {
        gameMessages.clear()
    }

    fun leaveGame() {
        stopTimers()
        ttsManager.stop()
        audioManager.stopAll()
        logManager.endSession()
        MudConnectionManager.disconnect(getApplication())
        isConnected.value = false
        commandHistory.clear()
        lastSentCommand.value = ""
        userJustSentCommand.value = false
        currentScreen.value = AppScreen.MAIN
        activeCharacter.value = null
    }

    override fun onCleared() {
        super.onCleared()
        MudConnectionManager.disconnect(getApplication())
        ttsManager.shutdown()
        audioManager.releaseAll()
        logManager.endSession()
        logManager.shutdown()
    }
}
