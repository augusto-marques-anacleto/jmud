package br.com.augusto.jmud.ui.viewmodels

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.augusto.jmud.R
import br.com.augusto.jmud.data.local.CharacterRepository
import br.com.augusto.jmud.data.local.MacroRepository
import br.com.augusto.jmud.data.local.SettingsRepository
import br.com.augusto.jmud.data.local.ShortcutRepository
import br.com.augusto.jmud.data.local.TimerRepository
import br.com.augusto.jmud.data.local.TriggerRepository
import br.com.augusto.jmud.data.network.ConnectionFailureReason
import br.com.augusto.jmud.data.network.MudConnectionManager
import br.com.augusto.jmud.data.network.MudEvent
import br.com.augusto.jmud.domain.MudCharacter
import br.com.augusto.jmud.domain.MudMacro
import br.com.augusto.jmud.domain.MudShortcut
import br.com.augusto.jmud.domain.MudTimer
import br.com.augusto.jmud.domain.MudTrigger
import br.com.augusto.jmud.domain.ShortcutAction
import br.com.augusto.jmud.domain.ShortcutDirection
import br.com.augusto.jmud.domain.Scope
import br.com.augusto.jmud.domain.ShareBundle
import br.com.augusto.jmud.domain.ShareKind
import br.com.augusto.jmud.domain.ShareSection
import br.com.augusto.jmud.util.AppStorage
import br.com.augusto.jmud.util.BackupManager
import br.com.augusto.jmud.util.ExtractResult
import br.com.augusto.jmud.util.IntervalFormat
import br.com.augusto.jmud.util.LogManager
import br.com.augusto.jmud.util.MacroEngine
import br.com.augusto.jmud.util.MigrationProgress
import br.com.augusto.jmud.util.MigrationResult
import br.com.augusto.jmud.util.MspParser
import br.com.augusto.jmud.util.MudAudioManager
import br.com.augusto.jmud.util.QuitCommands
import br.com.augusto.jmud.util.ShareFormat
import br.com.augusto.jmud.util.ShareManager
import br.com.augusto.jmud.util.StorageMigrator
import br.com.augusto.jmud.util.StorageOption
import br.com.augusto.jmud.util.StorageType
import br.com.augusto.jmud.util.ToneFeedback
import br.com.augusto.jmud.util.TiltSensorController
import br.com.augusto.jmud.util.TiltZone
import br.com.augusto.jmud.util.TiltZones
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class AppScreen { MAIN, GAME }

enum class MacroRecordingState { NONE, RECORDING, PAUSED }

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

class MudViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CharacterRepository(application)
    private val timerRepository = TimerRepository(application)
    private val triggerRepository = TriggerRepository(application)
    private val macroRepository = MacroRepository(application)
    private val shortcutRepository = ShortcutRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val backupManager = BackupManager(application)
    private val updateManager = UpdateManager(application)
    private val ttsManager = TTSManager(application)
    private val audioManager = MudAudioManager(application)
    private val logManager = LogManager(application)
    private val shareManager = ShareManager(application)
    private val storageMigrator = StorageMigrator()
    private val toneFeedback = ToneFeedback()

    private val timerJobs = mutableListOf<Job>()

    private val outboundMutex = Mutex()
    private val outboundJobs = mutableListOf<Job>()
    private var lastTransmitAt = 0L

    val characters = mutableStateListOf<MudCharacter>()
    val timers = mutableStateListOf<MudTimer>()
    val triggers = mutableStateListOf<MudTrigger>()
    val macros = mutableStateListOf<MudMacro>()
    val shortcuts = mutableStateListOf<MudShortcut>()
    var macroRecordingState = mutableStateOf(MacroRecordingState.NONE)
    val recordedMacroCommands = mutableStateListOf<String>()
    val gameMessages = mutableStateListOf<String>()
    val namedHistories = mutableStateMapOf<String, SnapshotStateList<String>>()
    val announcements = MutableSharedFlow<String>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    var currentScreen = mutableStateOf(AppScreen.MAIN)
    var activeCharacter = mutableStateOf<MudCharacter?>(null)
    var isConnected = mutableStateOf(false)
    var connectionState = mutableStateOf(ConnectionState.DISCONNECTED)
    var lastSentCommand = mutableStateOf("")
    var userJustSentCommand = mutableStateOf(false)
    var flushNextTTS = mutableStateOf(false)

    var currentGameUseTTS = mutableStateOf(true)
    var currentGamePlaySounds = mutableStateOf(true)

    var manualHost = mutableStateOf(repository.getManualHost())
    var manualPort = mutableStateOf(repository.getManualPort())
    var manualUseTTS = mutableStateOf(repository.getManualUseTTS())
    var manualPlaySounds = mutableStateOf(repository.getManualPlaySounds())
    var manualAutoReconnect = mutableStateOf(repository.getManualAutoReconnect())

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
    var commandSeparator = mutableStateOf(settingsRepository.getCommandSeparator())
    var quitCommands = mutableStateOf(settingsRepository.getQuitCommands())
    var commandIntervalMs = mutableStateOf(settingsRepository.getCommandIntervalMs())
    var shortcutPanelEnabled = mutableStateOf(settingsRepository.getShortcutPanelEnabled())
    var tiltModeActive = mutableStateOf(false)
    var tiltTriggerDegrees = mutableStateOf(settingsRepository.getTiltTriggerDegrees())
    var tiltConfirmMs = mutableStateOf(settingsRepository.getTiltConfirmMs())
    var tiltRepeatEnabled = mutableStateOf(settingsRepository.getTiltRepeatEnabled())
    val tiltMapping = mutableStateMapOf<String, String>()

    private val tiltSensor = TiltSensorController(
        application,
        object : TiltSensorController.Listener {
            override fun onZoneEntered(zone: TiltZone) = announceTiltZone(zone)
            override fun onZoneConfirmed(zone: TiltZone) = runTiltZone(zone, true)
            override fun onZoneRepeated(zone: TiltZone) = runTiltZone(zone, false)
        }
    )
    var backupMessage = mutableStateOf<String?>(null)
    var pendingImport = mutableStateOf<ShareBundle?>(null)
    var pendingExportSections = mutableStateOf<Set<ShareSection>>(emptySet())

    var storageChoiceVisible = mutableStateOf(false)
    var storageOptions = mutableStateListOf<StorageOption>()
    var currentStorage = mutableStateOf(AppStorage.currentOption(application))
    var storageReady = mutableStateOf(false)
    var storageMigrationProgress = mutableStateOf<MigrationProgress?>(null)
    private var storageMigrationJob: Job? = null
    var pendingShareIntent = mutableStateOf<Intent?>(null)
    var logsMessage = mutableStateOf<String?>(null)

    var welcomeVisible = mutableStateOf(!settingsRepository.getWelcomeShown())
    var helpStartPage = mutableStateOf<Int?>(null)

    var availableUpdate = mutableStateOf<UpdateInfo?>(null)
    var updateDialogVisible = mutableStateOf(false)
    var updateDownloadPercent = mutableStateOf<Int?>(null)
    var updateMessage = mutableStateOf<String?>(null)
    private var updateJob: Job? = null
    private var autoLoginJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var quitRequestedAt = 0L

    init {
        characters.addAll(repository.loadCharacters())
        timers.addAll(timerRepository.loadTimers())
        triggers.addAll(triggerRepository.loadTriggers())
        macros.addAll(macroRepository.loadMacros())
        shortcuts.addAll(shortcutRepository.loadShortcuts())
        for (zone in TiltZones.MAPPABLE_ZONES) {
            val stored = settingsRepository.getTiltShortcut(zone.name)
            if (stored.isNotBlank()) {
                tiltMapping[zone.name] = stored
            }
        }

        viewModelScope.launch {
            val needsChoice = withContext(Dispatchers.IO) {
                val needs = AppStorage.needsLocationChoice(application)
                if (!needs) {
                    AppStorage.baseDir(application)
                }
                needs
            }
            storageChoiceVisible.value = needsChoice
            storageReady.value = !needsChoice
            refreshStorageOptions()
        }

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
                    MudEvent.Connected -> onConnected()
                    is MudEvent.LineReceived -> onLineReceived(event.text)
                    is MudEvent.ConnectionFailed -> {
                        postStatusMessage(connectionFailureMessage(event.reason, event.detail))
                        onDisconnected(ConnectionState.FAILED)
                        if (reconnectAttempt > 0) {
                            scheduleReconnect()
                        }
                    }
                    is MudEvent.Disconnected -> {
                        val askedToQuit = quitRequestedRecently()
                        postStatusMessage(
                            getString(
                                when {
                                    askedToQuit -> R.string.session_ended_by_you
                                    event.serverClosed -> R.string.server_closed_connection
                                    else -> R.string.connection_lost
                                }
                            )
                        )
                        onDisconnected(ConnectionState.DISCONNECTED)
                        if (!askedToQuit) {
                            scheduleReconnect()
                        }
                    }
                    MudEvent.SendFailed -> {
                        val askedToQuit = quitRequestedRecently()
                        postStatusMessage(getString(R.string.send_failed))
                        onDisconnected(ConnectionState.DISCONNECTED)
                        if (!askedToQuit) {
                            scheduleReconnect()
                        }
                    }
                }
            }
        }
    }

    private fun getString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    private fun connectionFailureMessage(reason: ConnectionFailureReason, detail: String?): String {
        val reasonText = getString(
            when (reason) {
                ConnectionFailureReason.NO_INTERNET -> R.string.connection_reason_no_internet
                ConnectionFailureReason.HOST_NOT_FOUND -> R.string.connection_reason_host_not_found
                ConnectionFailureReason.SERVER_UNAVAILABLE -> R.string.connection_reason_server_unavailable
                ConnectionFailureReason.TIMEOUT -> R.string.connection_reason_timeout
                ConnectionFailureReason.UNKNOWN -> R.string.connection_reason_unknown
            }
        )
        val fullReason = if (reason == ConnectionFailureReason.UNKNOWN && !detail.isNullOrBlank()) {
            getString(R.string.connection_reason_detail, reasonText, detail)
        } else {
            reasonText
        }
        return getString(R.string.connection_failed_reason, fullReason)
    }

    fun connectionStatusText(): String = getString(
        R.string.connection_status_label,
        getString(
            when (connectionState.value) {
                ConnectionState.CONNECTING -> R.string.connection_status_connecting
                ConnectionState.CONNECTED -> R.string.connection_status_connected
                ConnectionState.FAILED -> R.string.connection_status_failed
                ConnectionState.DISCONNECTED -> R.string.connection_status_disconnected
            }
        )
    )

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
                sendThrottledSequence(commands, commandIntervalMs.value)
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

    private fun onConnected() {
        connectionState.value = ConnectionState.CONNECTED
        isConnected.value = true
        if (reconnectAttempt > 0) {
            postStatusMessage(getString(R.string.reconnected))
        }
        reconnectAttempt = 0
        reconnectJob = null
        val character = activeCharacter.value ?: return
        startTimers(character)
        autoLoginJob?.cancel()
        autoLoginJob = viewModelScope.launch {
            delay(500)
            if (character.autoLogin && character.name.isNotBlank() && character.password.isNotBlank()) {
                if (!isConnected.value) return@launch
                transmitThrottled(character.name.trim(), commandIntervalMs.value)
                delay(500)
                if (!isConnected.value) return@launch
                transmitThrottled(character.password, commandIntervalMs.value)
                delay(500)
            }
            for (cmd in character.postConnectCommands.split("\n")) {
                if (cmd.isNotBlank()) {
                    if (!isConnected.value) return@launch
                    transmitThrottled(cmd.trim(), commandIntervalMs.value)
                }
            }
        }
    }

    private fun onDisconnected(state: ConnectionState) {
        autoLoginJob?.cancel()
        autoLoginJob = null
        if (connectionState.value == ConnectionState.DISCONNECTED ||
            connectionState.value == ConnectionState.FAILED
        ) {
            return
        }
        connectionState.value = state
        isConnected.value = false
        setTiltModeActive(false)
        cancelOutboundJobs()
        stopTimers()
        audioManager.stopAll()
        logManager.endSession()
        MudConnectionManager.disconnect(getApplication())
    }

    fun addCharacter(name: String, host: String, port: Int, password: String, autoLogin: Boolean, commands: String, useTTS: Boolean, playSounds: Boolean, soundsFolder: String, autoReconnect: Boolean) {
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
            soundsFolder = soundsFolder,
            autoReconnect = autoReconnect
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

    fun saveMacro(macro: MudMacro) {
        val index = macros.indexOfFirst { it.id == macro.id }
        if (index != -1) {
            macros[index] = macro
        } else {
            macros.add(macro)
        }
        macroRepository.saveMacros(macros)
    }

    fun removeMacro(macro: MudMacro) {
        macros.remove(macro)
        macroRepository.saveMacros(macros)
    }

    fun startMacroRecording() {
        recordedMacroCommands.clear()
        macroRecordingState.value = MacroRecordingState.RECORDING
        postStatusMessage(getString(R.string.macro_recording_started))
    }

    fun toggleMacroRecordingPause() {
        when (macroRecordingState.value) {
            MacroRecordingState.RECORDING -> {
                macroRecordingState.value = MacroRecordingState.PAUSED
                postStatusMessage(getString(R.string.macro_recording_paused))
            }
            MacroRecordingState.PAUSED -> {
                macroRecordingState.value = MacroRecordingState.RECORDING
                postStatusMessage(getString(R.string.macro_recording_resumed))
            }
            MacroRecordingState.NONE -> {}
        }
    }

    fun ignoreLastRecordedMacroCommand() {
        if (macroRecordingState.value == MacroRecordingState.NONE) return
        if (recordedMacroCommands.isNotEmpty()) {
            recordedMacroCommands.removeAt(recordedMacroCommands.size - 1)
        } else {
            postStatusMessage(getString(R.string.macro_recording_nothing_to_ignore))
        }
    }

    fun stopMacroRecording(): Boolean {
        val hadCommands = recordedMacroCommands.isNotEmpty()
        macroRecordingState.value = MacroRecordingState.NONE
        if (!hadCommands) {
            postStatusMessage(getString(R.string.macro_recording_discarded_empty))
        }
        return hadCommands
    }

    fun discardMacroRecording() {
        macroRecordingState.value = MacroRecordingState.NONE
        recordedMacroCommands.clear()
    }

    fun saveMacroRecording(name: String, commands: String, scope: String, scopeValue: String) {
        saveMacro(
            MudMacro(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                commands = commands,
                scope = scope,
                scopeValue = scopeValue,
                enabled = true
            )
        )
        recordedMacroCommands.clear()
        postStatusMessage(getString(R.string.macro_recording_saved, name.trim()))
    }

    fun saveShortcut(shortcut: MudShortcut) {
        val index = shortcuts.indexOfFirst { it.id == shortcut.id }
        if (index != -1) {
            shortcuts[index] = shortcut
        } else {
            shortcuts.add(shortcut)
        }
        shortcutRepository.saveShortcuts(shortcuts)
    }

    fun removeShortcut(shortcut: MudShortcut) {
        shortcuts.remove(shortcut)
        shortcutRepository.saveShortcuts(shortcuts)
    }

    fun setShortcutPanelEnabled(value: Boolean) {
        shortcutPanelEnabled.value = value
        settingsRepository.saveShortcutPanelEnabled(value)
    }

    fun activeShortcuts(): List<MudShortcut> {
        val character = activeCharacter.value
        return shortcuts.filter { shortcut ->
            shortcut.enabled && (character == null || scopeMatches(shortcut.scope, shortcut.scopeValue, character))
        }
    }

    fun addDirectionShortcuts() {
        val resources = getApplication<Application>().resources
        val labels = resources.getStringArray(R.array.direction_shortcut_labels)
        val commands = resources.getStringArray(R.array.direction_shortcut_commands)
        var added = 0
        for (index in labels.indices) {
            val label = labels[index]
            val command = commands.getOrNull(index) ?: continue
            val direction = ShortcutDirection.PRESET_ORDER.getOrNull(index) ?: ""
            val alreadyThere = shortcuts.any { existing ->
                existing.scope == Scope.ALL &&
                    (
                        (direction.isNotBlank() && existing.direction == direction) ||
                            existing.label.equals(label, ignoreCase = true)
                        )
            }
            if (alreadyThere) continue
            shortcuts.add(
                MudShortcut(
                    id = UUID.randomUUID().toString(),
                    label = label,
                    command = command,
                    action = ShortcutAction.COMMAND,
                    scope = Scope.ALL,
                    scopeValue = "",
                    enabled = true,
                    direction = direction
                )
            )
            added++
        }
        if (added > 0) {
            shortcutRepository.saveShortcuts(shortcuts)
        }
        backupMessage.value = if (added > 0) {
            quantityText(R.plurals.direction_shortcuts_added, added)
        } else {
            getString(R.string.direction_shortcuts_already_there)
        }
    }

    fun isTiltAvailable(): Boolean = tiltSensor.isAvailable()

    fun tiltShortcutFor(zone: TiltZone): MudShortcut? {
        val id = tiltMapping[zone.name] ?: return null
        return shortcuts.firstOrNull { it.id == id }
    }

    private fun activeTiltShortcutFor(zone: TiltZone): MudShortcut? {
        val mapped = tiltShortcutFor(zone) ?: return null
        return activeShortcuts().firstOrNull { it.id == mapped.id }
    }

    fun setTiltShortcut(zone: TiltZone, shortcutId: String) {
        if (shortcutId.isBlank()) {
            tiltMapping.remove(zone.name)
        } else {
            tiltMapping[zone.name] = shortcutId
        }
        settingsRepository.saveTiltShortcut(zone.name, shortcutId)
    }

    private fun tiltRepeatMs(): Int = if (tiltRepeatEnabled.value) {
        commandIntervalMs.value.coerceAtLeast(MIN_TILT_REPEAT_MS)
    } else {
        0
    }

    private fun restartTiltIfActive() {
        if (!tiltModeActive.value) return
        tiltSensor.start(tiltTriggerDegrees.value, tiltConfirmMs.value, tiltRepeatMs())
    }

    fun setTiltTriggerDegrees(value: Int) {
        tiltTriggerDegrees.value = value
        settingsRepository.saveTiltTriggerDegrees(value)
        restartTiltIfActive()
    }

    fun setTiltConfirmMs(value: Int) {
        tiltConfirmMs.value = value
        settingsRepository.saveTiltConfirmMs(value)
        restartTiltIfActive()
    }

    fun setTiltRepeatEnabled(value: Boolean) {
        tiltRepeatEnabled.value = value
        settingsRepository.saveTiltRepeatEnabled(value)
        restartTiltIfActive()
    }

    fun suggestTiltMapping() {
        val resources = getApplication<Application>().resources
        val labels = resources.getStringArray(R.array.direction_shortcut_labels)
        val byZone = listOf(
            Triple(TiltZone.FORWARD, ShortcutDirection.NORTH, labels.getOrNull(1)),
            Triple(TiltZone.BACKWARD, ShortcutDirection.SOUTH, labels.getOrNull(7)),
            Triple(TiltZone.RIGHT, ShortcutDirection.EAST, labels.getOrNull(5)),
            Triple(TiltZone.LEFT, ShortcutDirection.WEST, labels.getOrNull(3))
        )
        var filled = 0
        for ((zone, direction, label) in byZone) {
            if (!tiltMapping[zone.name].isNullOrBlank()) continue
            val match = shortcuts.firstOrNull { it.direction == direction }
                ?: shortcuts.firstOrNull { label != null && it.label.equals(label, ignoreCase = true) }
                ?: continue
            setTiltShortcut(zone, match.id)
            filled++
        }
        backupMessage.value = if (filled > 0) {
            getString(R.string.tilt_mapping_suggested)
        } else {
            getString(R.string.tilt_mapping_nothing_to_suggest)
        }
    }

    fun setTiltModeActive(active: Boolean) {
        if (active == tiltModeActive.value) return
        if (active) {
            if (!tiltSensor.isAvailable()) {
                postStatusMessage(getString(R.string.tilt_sensor_missing))
                return
            }
            val started = tiltSensor.start(tiltTriggerDegrees.value, tiltConfirmMs.value, tiltRepeatMs())
            if (!started) {
                postStatusMessage(getString(R.string.tilt_sensor_missing))
                return
            }
            tiltModeActive.value = true
            postStatusMessage(getString(R.string.tilt_mode_on))
        } else {
            tiltSensor.stop()
            tiltModeActive.value = false
            postStatusMessage(getString(R.string.tilt_mode_off))
        }
    }

    fun onAppBackgrounded() {
        if (tiltModeActive.value) {
            tiltSensor.stop()
        }
    }

    fun onAppForegrounded() {
        if (tiltModeActive.value) {
            tiltSensor.start(tiltTriggerDegrees.value, tiltConfirmMs.value, tiltRepeatMs())
        }
    }

    fun recalibrateTilt() {
        if (!tiltModeActive.value) return
        tiltSensor.recalibrate()
        postStatusMessage(getString(R.string.tilt_recalibrated))
    }

    private fun announceTiltZone(zone: TiltZone) {
        val shortcut = activeTiltShortcutFor(zone)
        val label = shortcut?.label ?: getString(R.string.tilt_zone_unmapped)
        if (currentGameUseTTS.value) {
            ttsManager.speak(label, true)
        } else {
            announcements.tryEmit(label)
        }
    }

    private fun runTiltZone(zone: TiltZone, firstTime: Boolean) {
        val shortcut = activeTiltShortcutFor(zone) ?: return
        if (firstTime) {
            toneFeedback.beep()
        }
        runShortcut(shortcut)
    }

    fun runShortcut(shortcut: MudShortcut) {
        when (shortcut.action) {
            ShortcutAction.STOP_SOUND -> stopCurrentSounds()
            ShortcutAction.STOP_MACRO -> stopPendingCommands()
            ShortcutAction.SPEAK_LAST -> speakLastLine()
            ShortcutAction.REPEAT_LAST -> sendShortcutCommand("")
            else -> sendShortcutCommand(shortcut.command)
        }
    }

    private fun sendShortcutCommand(command: String) {
        if (!isConnected.value) {
            postStatusMessage(getString(R.string.shortcut_not_connected))
            return
        }
        if (command.isBlank() && lastSentCommand.value.isBlank()) {
            postStatusMessage(getString(R.string.shortcut_nothing_to_repeat))
            return
        }
        sendMessage(command)
    }

    private fun speakLastLine() {
        val line = gameMessages.lastOrNull { it.isNotBlank() }
        if (line == null) {
            postStatusMessage(getString(R.string.shortcut_no_last_line))
            return
        }
        if (currentGameUseTTS.value) {
            ttsManager.speak(line, true)
        } else {
            announcements.tryEmit(line)
        }
    }

    fun shareShortcut(shortcut: MudShortcut) {
        shareBundle(
            ShareBundle(kind = ShareKind.SHARE, shortcuts = listOf(shortcut)),
            shortcut.label,
            getString(R.string.share_subject_shortcut, shortcut.label)
        )
    }

    fun runMacro(macro: MudMacro) {
        sendThrottledSequence(MacroEngine.expandCommands(macro.commands, ""), macroIntervalMs(macro))
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
                            if (!isConnected.value) break
                            transmitThrottled(cmd.trim(), commandIntervalMs.value)
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

    fun soundFolders(): List<String> = AppStorage.soundFolders(getApplication())

    fun createSoundFolder(name: String): Boolean {
        val cleaned = name.trim()
        if (cleaned.isEmpty() || AppStorage.isReservedFolder(cleaned)) return false
        val dir = File(AppStorage.baseDir(getApplication()), cleaned)
        return dir.isDirectory || dir.mkdirs()
    }

    fun deleteSoundFolder(name: String) {
        if (name.isBlank() || AppStorage.isReservedFolder(name)) return
        File(AppStorage.baseDir(getApplication()), name).deleteRecursively()
        var changed = false
        for (index in characters.indices) {
            if (characters[index].soundsFolder == name) {
                characters[index] = characters[index].copy(soundsFolder = "")
                changed = true
            }
        }
        if (changed) {
            repository.saveCharacters(characters)
            val active = activeCharacter.value
            if (active != null && active.soundsFolder == name) {
                activeCharacter.value = active.copy(soundsFolder = "")
                audioManager.setSoundsFolder("")
            }
        }
    }

    fun charactersUsingFolder(name: String): List<MudCharacter> =
        characters.filter { it.soundsFolder == name }

    fun assignSoundsFolder(character: MudCharacter, folder: String) {
        updateCharacter(character.copy(soundsFolder = folder))
        if (activeCharacter.value?.id == character.id) {
            audioManager.setSoundsFolder(folder)
        }
    }

    fun soundFolderFileCount(name: String): Int =
        File(AppStorage.baseDir(getApplication()), name)
            .walkTopDown()
            .count { it.isFile }

    fun openHelp(page: Int) {
        helpStartPage.value = page
    }

    fun closeHelp() {
        helpStartPage.value = null
    }

    fun dismissWelcome(showHelp: Boolean) {
        welcomeVisible.value = false
        settingsRepository.saveWelcomeShown()
        if (showHelp) {
            helpStartPage.value = 0
        }
    }

    fun setQuitCommandsSetting(value: String) {
        quitCommands.value = value
        settingsRepository.saveQuitCommands(value)
    }

    private fun markIfQuitCommand(command: String) {
        if (QuitCommands.matches(command, QuitCommands.parse(quitCommands.value))) {
            quitRequestedAt = SystemClock.elapsedRealtime()
        }
    }

    private fun quitRequestedRecently(): Boolean {
        if (quitRequestedAt == 0L) return false
        return SystemClock.elapsedRealtime() - quitRequestedAt <= QUIT_INTENT_WINDOW_MS
    }

    fun setCommandSeparatorSetting(value: String) {
        commandSeparator.value = value
        settingsRepository.saveCommandSeparator(value)
    }

    fun setCommandIntervalSetting(millis: Int) {
        val sanitized = millis.coerceIn(0, IntervalFormat.MAX_INTERVAL_MS)
        commandIntervalMs.value = sanitized
        settingsRepository.saveCommandIntervalMs(sanitized)
        restartTiltIfActive()
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

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    fun currentBundle(sections: Set<ShareSection>): ShareBundle = ShareBundle(
        kind = ShareKind.BACKUP,
        characters = characters.toList(),
        triggers = triggers.toList(),
        timers = timers.toList(),
        macros = macros.toList(),
        shortcuts = shortcuts.toList(),
        settings = backupManager.settingsSnapshot()
    ).filtered(sections)

    fun refreshStorageOptions() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val available = withContext(Dispatchers.IO) { AppStorage.options(app) }
            val current = withContext(Dispatchers.IO) { AppStorage.currentOption(app) }
            storageOptions.clear()
            storageOptions.addAll(available)
            currentStorage.value = current
        }
    }

    fun storageLabelFor(option: StorageOption): String = getString(
        when (option.type) {
            StorageType.DOCUMENTS -> R.string.storage_documents
            StorageType.APP_INTERNAL -> R.string.storage_app_internal
            StorageType.REMOVABLE -> R.string.storage_removable
        }
    )

    fun storageFreeText(option: StorageOption): String =
        getString(R.string.storage_free_space, formatBytes(option.freeBytes))

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return getString(R.string.storage_free_unknown)
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) {
            return String.format(Locale.getDefault(), "%.1f GB", gb)
        }
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.getDefault(), "%.0f MB", mb)
    }

    fun chooseStorage(option: StorageOption) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val usable = withContext(Dispatchers.IO) {
                if (AppStorage.ensureUsable(option.baseDir)) {
                    AppStorage.select(app, option)
                    AppStorage.baseDir(app)
                    true
                } else {
                    false
                }
            }
            if (!usable) {
                backupMessage.value = getString(R.string.storage_not_writable)
                return@launch
            }
            storageChoiceVisible.value = false
            storageReady.value = true
            refreshStorageOptions()
            backupMessage.value = getString(
                R.string.storage_selected,
                storageLabelFor(option),
                option.baseDir.absolutePath
            )
        }
    }

    fun dismissStorageChoice() {
        val app = getApplication<Application>()
        storageChoiceVisible.value = false
        storageReady.value = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                AppStorage.markLocationChosen(app)
                AppStorage.baseDir(app)
            }
            refreshStorageOptions()
        }
    }

    fun isStorageMigrationRunning(): Boolean = storageMigrationJob?.isActive == true

    fun moveStorageTo(option: StorageOption) {
        if (isStorageMigrationRunning()) return
        val app = getApplication<Application>()
        val from = AppStorage.baseDir(app)
        val to = option.baseDir

        if (from.absolutePath == to.absolutePath) {
            backupMessage.value = getString(R.string.storage_same_location)
            return
        }

        audioManager.stopAll()
        logManager.endSession()

        storageMigrationProgress.value = MigrationProgress(0, 0, 0)
        storageMigrationJob = viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    storageMigrator.move(from, to) { progress ->
                        storageMigrationProgress.value = progress
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    storageMigrationProgress.value = null
                    backupMessage.value = getString(R.string.storage_move_cancelled)
                    resumeLogSessionIfNeeded()
                }
                throw e
            }

            val moved = result == MigrationResult.SUCCESS ||
                result == MigrationResult.NOTHING_TO_MOVE ||
                result == MigrationResult.SOURCE_NOT_CLEARED

            if (moved) {
                withContext(Dispatchers.IO) {
                    AppStorage.select(app, option)
                    AppStorage.baseDir(app)
                }
                refreshStorageOptions()
            }

            storageMigrationProgress.value = null
            backupMessage.value = when (result) {
                MigrationResult.SUCCESS, MigrationResult.NOTHING_TO_MOVE -> getString(
                    R.string.storage_move_ok,
                    storageLabelFor(option),
                    to.absolutePath
                )
                MigrationResult.SOURCE_NOT_CLEARED -> getString(
                    R.string.storage_move_ok_leftovers,
                    storageLabelFor(option),
                    from.absolutePath
                )
                MigrationResult.NO_SPACE -> getString(R.string.storage_move_no_space)
                MigrationResult.DESTINATION_UNUSABLE -> getString(R.string.storage_not_writable)
                MigrationResult.SAME_LOCATION -> getString(R.string.storage_same_location)
                MigrationResult.COPY_FAILED -> getString(R.string.storage_move_failed)
            }

            resumeLogSessionIfNeeded()
        }
    }

    private fun resumeLogSessionIfNeeded() {
        if (!logsEnabled.value || !isConnected.value) return
        activeCharacter.value?.let { logManager.startSession(it.name, it.host) }
    }

    fun cancelStorageMigration() {
        storageMigrationJob?.cancel()
        storageMigrationProgress.value = null
    }

    fun exportBundle(uri: Uri, sections: Set<ShareSection>) {
        if (sections.isEmpty()) {
            backupMessage.value = getString(R.string.export_nothing_selected)
            return
        }
        val json = ShareFormat.encode(currentBundle(sections), timestamp())
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
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

    fun openImportFromUri(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { shareManager.readText(uri) }
            openImportFromText(text)
        }
    }

    fun openImportFromText(text: String?) {
        val bundle = if (text.isNullOrBlank()) null else ShareFormat.decode(text)
        if (bundle == null) {
            backupMessage.value = getString(R.string.import_invalid_file)
            return
        }
        pendingImport.value = bundle
    }

    fun cancelImport() {
        pendingImport.value = null
    }

    fun applyImport(
        sections: Set<ShareSection>,
        replace: Boolean,
        overrideScope: Boolean,
        scope: String,
        scopeValue: String
    ) {
        val bundle = pendingImport.value ?: return
        pendingImport.value = null

        if (sections.isEmpty()) {
            backupMessage.value = getString(R.string.import_nothing_selected)
            return
        }

        var selected = bundle.filtered(sections)
        if (overrideScope) {
            selected = selected.withScope(scope, scopeValue)
        }

        if (selected.settings.isNotEmpty()) {
            backupManager.applySettings(selected.settings)
        }
        if (sections.contains(ShareSection.CHARACTERS)) {
            val idMap = mergeCharacters(selected.characters, replace)
            selected = selected.remapCharacterScope(idMap)
        }
        if (sections.contains(ShareSection.TRIGGERS)) {
            mergeTriggers(selected.triggers, replace)
        }
        if (sections.contains(ShareSection.TIMERS)) {
            mergeTimers(selected.timers, replace)
        }
        if (sections.contains(ShareSection.MACROS)) {
            mergeMacros(selected.macros, replace)
        }
        if (sections.contains(ShareSection.SHORTCUTS)) {
            mergeShortcuts(selected.shortcuts, replace)
        }

        if (selected.settings.isNotEmpty()) {
            reloadFromStorage()
        } else {
            restartTimersIfConnected()
        }

        backupMessage.value = importSummary(selected)
    }

    private fun mergeCharacters(incoming: List<MudCharacter>, replace: Boolean): Map<String, String> {
        val idMap = mutableMapOf<String, String>()
        if (incoming.isEmpty() && !replace) return idMap
        if (replace) {
            characters.clear()
            characters.addAll(incoming)
        } else {
            for (item in incoming) {
                val newId = if (characters.any { it.id == item.id }) {
                    UUID.randomUUID().toString()
                } else {
                    item.id
                }
                if (newId != item.id) {
                    idMap[item.id] = newId
                }
                characters.add(item.copy(id = newId))
            }
        }
        repository.saveCharacters(characters)
        return idMap
    }

    private fun mergeTriggers(incoming: List<MudTrigger>, replace: Boolean) {
        if (incoming.isEmpty() && !replace) return
        if (replace) {
            triggers.clear()
            triggers.addAll(incoming)
        } else {
            for (item in incoming) {
                val id = if (triggers.any { it.id == item.id }) UUID.randomUUID().toString() else item.id
                triggers.add(item.copy(id = id))
            }
        }
        triggerRepository.saveTriggers(triggers)
    }

    private fun mergeTimers(incoming: List<MudTimer>, replace: Boolean) {
        if (incoming.isEmpty() && !replace) return
        if (replace) {
            timers.clear()
            timers.addAll(incoming)
        } else {
            for (item in incoming) {
                val id = if (timers.any { it.id == item.id }) UUID.randomUUID().toString() else item.id
                timers.add(item.copy(id = id))
            }
        }
        timerRepository.saveTimers(timers)
    }

    private fun mergeMacros(incoming: List<MudMacro>, replace: Boolean) {
        if (incoming.isEmpty() && !replace) return
        if (replace) {
            macros.clear()
            macros.addAll(incoming)
        } else {
            for (item in incoming) {
                val id = if (macros.any { it.id == item.id }) UUID.randomUUID().toString() else item.id
                macros.add(item.copy(id = id, name = uniqueMacroName(item.name)))
            }
        }
        macroRepository.saveMacros(macros)
    }

    private fun mergeShortcuts(incoming: List<MudShortcut>, replace: Boolean) {
        if (incoming.isEmpty() && !replace) return
        if (replace) {
            shortcuts.clear()
            shortcuts.addAll(incoming)
        } else {
            for (item in incoming) {
                val id = if (shortcuts.any { it.id == item.id }) UUID.randomUUID().toString() else item.id
                shortcuts.add(item.copy(id = id))
            }
        }
        shortcutRepository.saveShortcuts(shortcuts)
    }

    private fun uniqueMacroName(name: String): String {
        val base = name.trim()
        if (base.isBlank()) return base
        if (macros.none { it.name.equals(base, ignoreCase = true) }) return base
        var index = 2
        while (macros.any { it.name.equals(base + "_" + index, ignoreCase = true) }) {
            index++
        }
        return base + "_" + index
    }

    private fun quantityText(pluralId: Int, count: Int): String =
        getApplication<Application>().resources.getQuantityString(pluralId, count, count)

    private fun importSummary(applied: ShareBundle): String {
        val parts = mutableListOf<String>()
        if (applied.characters.isNotEmpty()) {
            parts.add(quantityText(R.plurals.imported_characters, applied.characters.size))
        }
        if (applied.triggers.isNotEmpty()) {
            parts.add(quantityText(R.plurals.imported_triggers, applied.triggers.size))
        }
        if (applied.timers.isNotEmpty()) {
            parts.add(quantityText(R.plurals.imported_timers, applied.timers.size))
        }
        if (applied.macros.isNotEmpty()) {
            parts.add(quantityText(R.plurals.imported_macros, applied.macros.size))
        }
        if (applied.shortcuts.isNotEmpty()) {
            parts.add(quantityText(R.plurals.imported_shortcuts, applied.shortcuts.size))
        }
        if (applied.settings.isNotEmpty()) {
            parts.add(getString(R.string.imported_settings))
        }
        if (parts.isEmpty()) return getString(R.string.import_nothing_selected)
        return getString(R.string.import_summary, parts.joinToString(", "))
    }

    fun shareCharacter(character: MudCharacter) {
        shareBundle(
            ShareBundle(kind = ShareKind.SHARE, characters = listOf(character)),
            character.name,
            getString(R.string.share_subject_character, character.name)
        )
    }

    fun shareTrigger(trigger: MudTrigger) {
        shareBundle(
            ShareBundle(kind = ShareKind.SHARE, triggers = listOf(trigger)),
            trigger.name,
            getString(R.string.share_subject_trigger, trigger.name)
        )
    }

    fun shareTimer(timer: MudTimer) {
        val label = getString(R.string.share_timer_label, timer.seconds)
        shareBundle(
            ShareBundle(kind = ShareKind.SHARE, timers = listOf(timer)),
            label,
            getString(R.string.share_subject_timer, label)
        )
    }

    fun shareMacro(macro: MudMacro) {
        shareBundle(
            ShareBundle(kind = ShareKind.SHARE, macros = listOf(macro)),
            macro.name,
            getString(R.string.share_subject_macro, macro.name)
        )
    }

    private fun shareBundle(bundle: ShareBundle, baseName: String, subject: String) {
        val json = ShareFormat.encode(bundle, timestamp())
        val intent = shareManager.buildShareIntent(
            baseName,
            json,
            subject,
            getString(R.string.share_description, subject)
        )
        if (intent == null) {
            backupMessage.value = getString(R.string.share_failed)
        } else {
            pendingShareIntent.value = intent
        }
    }

    fun clearShareIntent() {
        pendingShareIntent.value = null
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
        macros.clear()
        macros.addAll(macroRepository.loadMacros())
        shortcuts.clear()
        shortcuts.addAll(shortcutRepository.loadShortcuts())

        manualHost.value = repository.getManualHost()
        manualPort.value = repository.getManualPort()
        manualUseTTS.value = repository.getManualUseTTS()
        manualPlaySounds.value = repository.getManualPlaySounds()
        manualAutoReconnect.value = repository.getManualAutoReconnect()

        encoding.value = settingsRepository.getEncoding()
        MudConnectionManager.setEncoding(encoding.value)

        ttsEngine.value = settingsRepository.getTtsEngine()
        ttsVoice.value = settingsRepository.getTtsVoice()
        ttsRate.value = settingsRepository.getTtsRate()
        ttsPitch.value = settingsRepository.getTtsPitch()
        ttsVolume.value = settingsRepository.getTtsVolume()
        ttsManager.setEngine(ttsEngine.value)
        ttsManager.configure(ttsRate.value, ttsPitch.value, ttsVolume.value, ttsVoice.value)

        commandSeparator.value = settingsRepository.getCommandSeparator()
        quitCommands.value = settingsRepository.getQuitCommands()
        commandIntervalMs.value = settingsRepository.getCommandIntervalMs()
        shortcutPanelEnabled.value = settingsRepository.getShortcutPanelEnabled()
        tiltTriggerDegrees.value = settingsRepository.getTiltTriggerDegrees()
        tiltConfirmMs.value = settingsRepository.getTiltConfirmMs()
        tiltRepeatEnabled.value = settingsRepository.getTiltRepeatEnabled()
        tiltMapping.clear()
        for (zone in TiltZones.MAPPABLE_ZONES) {
            val stored = settingsRepository.getTiltShortcut(zone.name)
            if (stored.isNotBlank()) {
                tiltMapping[zone.name] = stored
            }
        }

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
            if (soundPackInstaller.download(rawUrl, tempZip) { publishSoundPackProgress(it) }) {
                soundPackInstaller.extractAndCopy(tempZip, targetDir) { publishSoundPackProgress(it) }
            } else {
                null
            }
        }
    }

    fun importSoundPack(uri: Uri, folder: String) {
        if (soundPackJob?.isActive == true) return
        runSoundPackJob(folder) { tempZip, targetDir ->
            val copied = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                tempZip.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (copied) {
                soundPackInstaller.extractAndCopy(tempZip, targetDir) { publishSoundPackProgress(it) }
            } else {
                null
            }
        }
    }

    private fun publishSoundPackProgress(progress: SoundPackProgress) {
        soundPackProgress.value = progress
        soundPackNotifier.update(progress)
    }

    fun isSoundPackRunning(): Boolean = soundPackJob?.isActive == true

    private fun runSoundPackJob(
        folder: String,
        work: suspend (tempZip: File, targetDir: File) -> ExtractResult?
    ) {
        val app = getApplication<Application>()
        soundPackDialogVisible.value = true
        soundPackProgress.value = SoundPackProgress(SoundPackStep.DOWNLOADING, 0, totalKnown = false)
        soundPackJob = viewModelScope.launch {
            var resultMessage = getString(R.string.sound_pack_failed)
            val tempZip = File(AppStorage.tempDir(app), "soundpack.zip")
            try {
                val result = withContext(Dispatchers.IO) {
                    work(tempZip, File(AppStorage.baseDir(app), folder))
                }
                if (result != null) {
                    resultMessage = getString(R.string.sound_pack_installed, folder)
                    if (result.keptExisting > 0) {
                        resultMessage += " " + app.resources.getQuantityString(
                            R.plurals.sound_pack_kept_files,
                            result.keptExisting,
                            result.keptExisting
                        )
                    }
                }
            } catch (e: CancellationException) {
                resultMessage = getString(R.string.sound_pack_cancelled)
            } catch (e: Exception) {
                android.util.Log.e("jMud", "Falha na instalação do pacote de sons", e)
                resultMessage = getString(
                    R.string.sound_pack_failed_reason,
                    e.message ?: e.javaClass.simpleName
                )
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

    private fun logOutgoingMasked(command: String) {
        val password = activeCharacter.value?.password
        val line = if (!password.isNullOrBlank() && command == password) "********" else command
        logManager.logOutgoing(line)
    }

    private fun transmit(command: String) {
        if (logsEnabled.value) {
            logOutgoingMasked(command)
        }
        MudConnectionManager.sendMessage(command)
    }

    private suspend fun transmitThrottled(command: String, intervalMs: Int) {
        outboundMutex.withLock {
            if (intervalMs > 0 && lastTransmitAt != 0L) {
                val elapsed = SystemClock.elapsedRealtime() - lastTransmitAt
                if (elapsed in 0 until intervalMs.toLong()) {
                    delay(intervalMs - elapsed)
                }
            }
            markIfQuitCommand(command)
            transmit(command)
            lastTransmitAt = SystemClock.elapsedRealtime()
        }
    }

    private fun sendThrottledSequence(commands: List<String>, intervalMs: Int) {
        if (commands.isEmpty()) return
        val job = viewModelScope.launch {
            for (cmd in commands) {
                if (!isConnected.value) return@launch
                transmitThrottled(cmd, intervalMs)
            }
        }
        synchronized(outboundJobs) { outboundJobs.add(job) }
        job.invokeOnCompletion {
            synchronized(outboundJobs) { outboundJobs.remove(job) }
        }
    }

    private fun cancelOutboundJobs(): Boolean {
        val jobs = synchronized(outboundJobs) {
            val copy = outboundJobs.toList()
            outboundJobs.clear()
            copy
        }
        val hadPending = jobs.any { it.isActive }
        jobs.forEach { it.cancel() }
        return hadPending
    }

    fun stopPendingCommands() {
        val hadPending = cancelOutboundJobs()
        postStatusMessage(
            getString(
                if (hadPending) R.string.macro_interrupted else R.string.macro_nothing_running
            )
        )
    }

    fun stopCurrentSounds() {
        audioManager.stopAll()
        postStatusMessage(getString(R.string.sounds_interrupted))
    }

    fun connectManual() {
        val portInt = manualPort.value.toIntOrNull() ?: 4000
        repository.saveManualConnection(
            manualHost.value,
            manualPort.value,
            manualUseTTS.value,
            manualPlaySounds.value,
            manualAutoReconnect.value
        )

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
            soundsFolder = "Sons",
            autoReconnect = manualAutoReconnect.value
        )

        connect(manualCharacter)
    }

    fun connect(character: MudCharacter, preserveHistory: Boolean = false) {
        cancelReconnect()
        quitRequestedAt = 0L
        activeCharacter.value = character
        currentGameUseTTS.value = character.useTTS
        currentGamePlaySounds.value = character.playSounds
        audioManager.setSoundsFolder(character.soundsFolder)

        if (!preserveHistory) {
            gameMessages.clear()
            namedHistories.clear()
            lastSentCommand.value = ""
        }
        userJustSentCommand.value = false
        flushNextTTS.value = false
        isConnected.value = false
        connectionState.value = ConnectionState.CONNECTING
        currentScreen.value = AppScreen.GAME

        if (logsEnabled.value && !preserveHistory) {
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

        if (!notificationsAllowed()) {
            postStatusMessage(getString(R.string.notifications_denied_warning))
        }
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return getApplication<Application>().checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
    }

    private fun scheduleReconnect() {
        if (currentScreen.value != AppScreen.GAME) return
        val character = activeCharacter.value ?: return
        if (!character.autoReconnect) return

        if (reconnectAttempt >= RECONNECT_DELAYS_SECONDS.size) {
            postStatusMessage(getString(R.string.reconnect_gave_up, RECONNECT_DELAYS_SECONDS.size))
            reconnectAttempt = 0
            return
        }

        val seconds = RECONNECT_DELAYS_SECONDS[reconnectAttempt]
        reconnectAttempt++
        val attempt = reconnectAttempt

        postStatusMessage(
            getString(R.string.reconnect_scheduled, seconds, attempt, RECONNECT_DELAYS_SECONDS.size)
        )

        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(seconds * 1000L)
            if (currentScreen.value != AppScreen.GAME) return@launch
            if (isConnected.value) return@launch
            postStatusMessage(
                getString(R.string.reconnect_trying, attempt, RECONNECT_DELAYS_SECONDS.size)
            )
            connect(character, preserveHistory = true)
        }
    }

    fun sendMessage(message: String) {
        if (message.isNotBlank()) {
            lastSentCommand.value = message
        }
        val finalCommand = if (message.isNotBlank()) message else lastSentCommand.value

        if (finalCommand.isNotBlank()) {
            userJustSentCommand.value = true
            flushNextTTS.value = true

            if (macroRecordingState.value == MacroRecordingState.RECORDING) {
                recordCommand(finalCommand)
            }

            val invocation = MacroEngine.parseInvocation(finalCommand)
            if (invocation != null) {
                executeMacro(invocation)
                return
            }

            val parts = splitBySeparator(finalCommand)
            if (parts.size > 1) {
                sendThrottledSequence(parts, commandIntervalMs.value)
            } else {
                sendThrottledSequence(listOf(finalCommand), commandIntervalMs.value)
            }
        }
    }

    private fun macroIntervalMs(macro: MudMacro): Int =
        IntervalFormat.effectiveMillis(macro.intervalMs, commandIntervalMs.value)

    private fun findMacro(name: String): MudMacro? {
        val character = activeCharacter.value
        return macros.firstOrNull {
            it.enabled &&
                it.name.equals(name, ignoreCase = true) &&
                (character == null || scopeMatches(it.scope, it.scopeValue, character))
        }
    }

    private fun splitBySeparator(command: String): List<String> {
        val separator = commandSeparator.value
        if (separator.isEmpty()) return emptyList()
        return command.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun recordCommand(command: String) {
        val invocation = MacroEngine.parseInvocation(command)
        if (invocation != null) {
            val macro = findMacro(invocation.name)
            if (macro != null) {
                val expanded = MacroEngine.expandCommands(macro.commands, invocation.argsString)
                if (expanded.isNotEmpty()) {
                    recordedMacroCommands.addAll(expanded)
                    return
                }
            }
        }

        val parts = splitBySeparator(command)
        if (parts.size > 1) {
            recordedMacroCommands.addAll(parts)
        } else {
            recordedMacroCommands.add(command)
        }
    }

    private fun executeMacro(invocation: MacroEngine.Invocation) {
        val macro = findMacro(invocation.name)
        if (macro == null) {
            postStatusMessage(getString(R.string.macro_not_found, invocation.name))
            return
        }
        sendThrottledSequence(
            MacroEngine.expandCommands(macro.commands, invocation.argsString),
            macroIntervalMs(macro)
        )
    }

    fun reconnect() {
        val character = activeCharacter.value ?: return
        cancelReconnect()
        connect(character, preserveHistory = true)
    }

    fun clearHistory() {
        gameMessages.clear()
    }

    fun leaveGame() {
        cancelReconnect()
        setTiltModeActive(false)
        autoLoginJob?.cancel()
        autoLoginJob = null
        cancelOutboundJobs()
        stopTimers()
        ttsManager.stop()
        audioManager.stopAll()
        logManager.endSession()
        MudConnectionManager.disconnect(getApplication())
        isConnected.value = false
        connectionState.value = ConnectionState.DISCONNECTED
        lastSentCommand.value = ""
        userJustSentCommand.value = false
        currentScreen.value = AppScreen.MAIN
        activeCharacter.value = null
    }

    override fun onCleared() {
        super.onCleared()
        tiltSensor.stop()
        toneFeedback.release()
        MudConnectionManager.disconnect(getApplication())
        ttsManager.shutdown()
        audioManager.releaseAll()
        logManager.endSession()
        logManager.shutdown()
    }

    private companion object {
        const val MIN_TILT_REPEAT_MS = 300
        val RECONNECT_DELAYS_SECONDS = listOf(5, 10, 20, 30, 60)
        const val QUIT_INTENT_WINDOW_MS = 30_000L
    }
}
