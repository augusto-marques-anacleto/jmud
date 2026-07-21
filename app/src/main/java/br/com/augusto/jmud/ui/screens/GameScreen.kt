package br.com.augusto.jmud.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.R
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.components.AppTextField
import br.com.augusto.jmud.ui.viewmodels.MacroRecordingState
import br.com.augusto.jmud.ui.viewmodels.MudViewModel
import br.com.augusto.jmud.util.StoragePermissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PendingSoundAction { NONE, DOWNLOAD, IMPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: MudViewModel) {
    var command by remember { mutableStateOf("") }
    var showBackConfirm by remember { mutableStateOf(false) }
    var showDisconnectedSend by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingImportFolder by remember { mutableStateOf("") }
    var linkToOpen by remember { mutableStateOf<String?>(null) }
    var gameTab by remember { mutableIntStateOf(0) }
    var showHistories by remember { mutableStateOf(false) }
    var historyToView by remember { mutableStateOf<String?>(null) }
    var pendingSoundAction by remember { mutableStateOf(PendingSoundAction.NONE) }
    var showSoundPackFolderRequired by remember { mutableStateOf(false) }
    var showSoundPackPermissionRequired by remember { mutableStateOf(false) }
    var showSoundPackFolderSelector by remember { mutableStateOf(false) }
    var showMacroRecordingDialog by remember { mutableStateOf(false) }
    var showSaveMacroRecordingDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val urlRegex = remember { Regex("https?://\\S+") }
    val defaultSoundsFolder = viewModel.activeCharacter.value?.soundsFolder?.ifBlank { "Sons" } ?: "Sons"
    val suggestHost = viewModel.activeCharacter.value?.host ?: ""
    val suggestName = viewModel.activeCharacter.value?.name ?: ""

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && pendingImportFolder.isNotBlank()) {
            viewModel.importSoundPack(uri, pendingImportFolder)
        }
    }

    val continueSoundAction: () -> Unit = continueSoundAction@{
        val action = pendingSoundAction
        if (action == PendingSoundAction.NONE) return@continueSoundAction
        if (viewModel.activeCharacter.value?.soundsFolder.isNullOrBlank()) {
            showSoundPackFolderRequired = true
            return@continueSoundAction
        }
        when (action) {
            PendingSoundAction.DOWNLOAD -> showDownloadDialog = true
            PendingSoundAction.IMPORT -> showImportDialog = true
            PendingSoundAction.NONE -> {}
        }
        pendingSoundAction = PendingSoundAction.NONE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            continueSoundAction()
        } else {
            pendingSoundAction = PendingSoundAction.NONE
            showSoundPackPermissionRequired = true
        }
    }

    val startSoundAction: (PendingSoundAction) -> Unit = { action ->
        pendingSoundAction = action
        if (StoragePermissions.allGranted(context)) {
            continueSoundAction()
        } else {
            permissionLauncher.launch(StoragePermissions.required())
        }
    }

    var reviewIndex by remember { mutableIntStateOf(-1) }

    var commandEditText by remember { mutableStateOf<EditText?>(null) }
    val listState = rememberLazyListState()
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    val characterName = viewModel.activeCharacter.value?.name
        ?: stringResource(R.string.unknown_character)

    val reviewLine: (Int) -> Unit = { step ->
        val messages = viewModel.gameMessages
        if (messages.isNotEmpty()) {
            reviewIndex = if (reviewIndex == -1) {
                messages.size - 1
            } else {
                (reviewIndex + step).coerceIn(0, messages.size - 1)
            }
            coroutineScope.launch { listState.scrollToItem(reviewIndex) }
            @Suppress("DEPRECATION")
            view.announceForAccessibility(messages[reviewIndex])
        }
    }

    val onSendCommand: () -> Unit = {
        val finalCommand = if (command.isNotBlank()) command else viewModel.lastSentCommand.value
        if (finalCommand.isNotBlank()) {
            if (viewModel.isConnected.value) {
                viewModel.sendMessage(finalCommand)
                command = ""
                reviewIndex = -1
                commandEditText?.setText("")
                commandEditText?.requestFocus()
            } else {
                showDisconnectedSend = true
            }
        }
    }

    BackHandler {
        if (gameTab != 0) {
            gameTab = 0
        } else if (viewModel.isConnected.value) {
            showBackConfirm = true
        } else {
            viewModel.leaveGame()
        }
    }

    LaunchedEffect(Unit) {
        var previousCount = 0
        snapshotFlow { viewModel.gameMessages.size }.collect { count ->
            if (count < previousCount) {
                reviewIndex = -1
            }
            previousCount = count
            if (count > 0) {
                if (!listState.canScrollForward || viewModel.userJustSentCommand.value) {
                    listState.scrollToItem(count - 1)
                    viewModel.userJustSentCommand.value = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.announcements.collect { line ->
            @Suppress("DEPRECATION")
            view.announceForAccessibility(line)
        }
    }

    LaunchedEffect(showBackConfirm, showDisconnectedSend, showMacroRecordingDialog) {
        if (!showBackConfirm && !showDisconnectedSend && !showMacroRecordingDialog) {
            commandEditText?.requestFocus()
        }
    }

    val configuration = LocalConfiguration.current
    val hasHardwareKeyboard = configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
        configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO

    LaunchedEffect(commandEditText) {
        val editText = commandEditText ?: return@LaunchedEffect
        editText.requestFocus()
        if (!hasHardwareKeyboard) {
            delay(300)
            val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    LaunchedEffect(gameTab) {
        if (gameTab != 0) {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.game_title, characterName),
                        modifier = Modifier.semantics { heading() }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val gameTabs = listOf(
                stringResource(R.string.tab_game),
                stringResource(R.string.tab_triggers),
                stringResource(R.string.tab_timers),
                stringResource(R.string.tab_macros),
                stringResource(R.string.tab_settings)
            )
            TabRow(selectedTabIndex = gameTab) {
                gameTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = gameTab == index,
                        onClick = { gameTab = index },
                        text = { Text(title) },
                        modifier = Modifier.semantics { role = Role.Tab }
                    )
                }
            }
            when (gameTab) {
                1 -> TriggersTab(viewModel)
                2 -> TimersTab(viewModel)
                3 -> MacrosTab(viewModel)
                4 -> SettingsTab(viewModel)
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GameToggle(
                            label = stringResource(R.string.tts_short),
                            checked = viewModel.currentGameUseTTS.value,
                            onCheckedChange = { viewModel.setGameUseTTS(it) },
                            modifier = Modifier.weight(1f)
                        )
                        GameToggle(
                            label = stringResource(R.string.sounds_short),
                            checked = viewModel.currentGamePlaySounds.value,
                            onCheckedChange = { viewModel.setGamePlaySounds(it) },
                            modifier = Modifier.weight(1f)
                        )
                        AppButton(
                            text = stringResource(R.string.more_options),
                            onClick = { showMoreOptions = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val historyLabel = stringResource(R.string.history_label)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    @Suppress("DEPRECATION")
                                    view.announceForAccessibility(historyLabel)
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                val native = keyEvent.nativeKeyEvent
                                val reviewKeys = intArrayOf(
                                    KeyEvent.KEYCODE_DPAD_UP,
                                    KeyEvent.KEYCODE_DPAD_DOWN,
                                    KeyEvent.KEYCODE_PAGE_UP,
                                    KeyEvent.KEYCODE_PAGE_DOWN,
                                    KeyEvent.KEYCODE_MOVE_HOME,
                                    KeyEvent.KEYCODE_MOVE_END,
                                    KeyEvent.KEYCODE_ESCAPE
                                )
                                if (native.action != KeyEvent.ACTION_DOWN) {
                                    native.keyCode in reviewKeys
                                } else {
                                    when (native.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            reviewLine(-1)
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            reviewLine(1)
                                            true
                                        }
                                        KeyEvent.KEYCODE_PAGE_UP -> {
                                            reviewLine(-10)
                                            true
                                        }
                                        KeyEvent.KEYCODE_PAGE_DOWN -> {
                                            reviewLine(10)
                                            true
                                        }
                                        KeyEvent.KEYCODE_MOVE_HOME -> {
                                            reviewIndex = 1
                                            reviewLine(-1)
                                            true
                                        }
                                        KeyEvent.KEYCODE_MOVE_END -> {
                                            reviewIndex = -1
                                            reviewLine(1)
                                            true
                                        }
                                        KeyEvent.KEYCODE_ESCAPE -> {
                                            commandEditText?.requestFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            }
                            .focusable()
                    ) {
                        items(viewModel.gameMessages) { message ->
                            val link = urlRegex.find(message)?.value?.trimEnd('.', ',', ';', ')', ']', '>')
                            if (link != null) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.clickable(
                                        onClickLabel = stringResource(R.string.open_link_click_label)
                                    ) {
                                        linkToOpen = link
                                    }
                                )
                            } else {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AppButton(
                            text = stringResource(R.string.scroll_to_end),
                            onClick = {
                                coroutineScope.launch {
                                    if (viewModel.gameMessages.isNotEmpty()) {
                                        listState.scrollToItem(viewModel.gameMessages.size - 1)
                                    }
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppButton(
                            text = stringResource(R.string.clear_history),
                            onClick = { viewModel.clearHistory() }
                        )

                        AppTextField(
                            value = command,
                            onValueChange = { command = it },
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.field_command),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            onImeAction = onSendCommand,
                            onEditTextCreated = { commandEditText = it },
                            onKeyEvent = { keyEvent ->
                                when (keyEvent.keyCode) {
                                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                        if (keyEvent.action == KeyEvent.ACTION_UP) {
                                            onSendCommand()
                                        }
                                        true
                                    }
                                    KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> {
                                        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                                            reviewLine(
                                                if (keyEvent.keyCode == KeyEvent.KEYCODE_PAGE_UP) -1 else 1
                                            )
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }
                        )

                        AppButton(
                            text = stringResource(R.string.action_send),
                            onClick = onSendCommand,
                            onLongClick = { showHistories = true },
                            longClickLabel = stringResource(R.string.show_histories_label)
                        )
                    }
                }
            }
        }

        if (showBackConfirm) {
            AlertDialog(
                onDismissRequest = { showBackConfirm = false },
                title = { Text(stringResource(R.string.attention_title)) },
                text = { Text(stringResource(R.string.disconnect_confirm_message)) },
                confirmButton = {
                    AppButton(
                        text = stringResource(R.string.confirm_disconnect),
                        onClick = {
                            showBackConfirm = false
                            viewModel.leaveGame()
                        }
                    )
                },
                dismissButton = {
                    AppButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { showBackConfirm = false }
                    )
                }
            )
        }

        if (showDisconnectedSend) {
            AlertDialog(
                onDismissRequest = { showDisconnectedSend = false },
                title = { Text(stringResource(R.string.connection_closed_title)) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.reconnect_question))
                        AppButton(
                            text = stringResource(R.string.action_reconnect),
                            onClick = {
                                showDisconnectedSend = false
                                viewModel.reconnect()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppButton(
                            text = stringResource(R.string.back_to_home),
                            onClick = {
                                showDisconnectedSend = false
                                viewModel.leaveGame()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    AppButton(
                        text = stringResource(R.string.stay_in_history),
                        onClick = { showDisconnectedSend = false }
                    )
                }
            )
        }

        linkToOpen?.let { link ->
            AlertDialog(
                onDismissRequest = { linkToOpen = null },
                title = { Text(stringResource(R.string.open_link_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.open_link_question))
                        Text(link, style = MaterialTheme.typography.bodyLarge)
                    }
                },
                confirmButton = {
                    AppButton(
                        text = stringResource(R.string.action_open),
                        onClick = {
                            linkToOpen = null
                            viewModel.openLink(link)
                        }
                    )
                },
                dismissButton = {
                    AppButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { linkToOpen = null }
                    )
                }
            )
        }

        if (showMoreOptions) {
            MoreOptionsDialog(
                triggersEnabled = viewModel.triggersEnabled.value,
                onTriggersEnabledChange = { viewModel.setTriggersEnabledSetting(it) },
                timersEnabled = viewModel.timersEnabled.value,
                onTimersEnabledChange = { viewModel.setTimersEnabledSetting(it) },
                onDownloadSoundPack = {
                    showMoreOptions = false
                    if (viewModel.isSoundPackRunning()) {
                        viewModel.showSoundPackDialog()
                    } else {
                        startSoundAction(PendingSoundAction.DOWNLOAD)
                    }
                },
                onHelp = {
                    showMoreOptions = false
                    viewModel.openHelp(HelpPages.GAME)
                },
                onImportZip = {
                    showMoreOptions = false
                    if (viewModel.isSoundPackRunning()) {
                        viewModel.showSoundPackDialog()
                    } else {
                        startSoundAction(PendingSoundAction.IMPORT)
                    }
                },
                onRecordMacro = {
                    showMoreOptions = false
                    showMacroRecordingDialog = true
                },
                disconnectLabel = stringResource(
                    if (viewModel.isConnected.value) R.string.action_disconnect else R.string.back_to_home
                ),
                onDisconnect = {
                    showMoreOptions = false
                    if (viewModel.isConnected.value) {
                        showBackConfirm = true
                    } else {
                        viewModel.leaveGame()
                    }
                },
                onDismiss = { showMoreOptions = false }
            )
        }

        if (showHistories) {
            AlertDialog(
                onDismissRequest = { showHistories = false },
                title = { Text(stringResource(R.string.histories_title)) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (viewModel.namedHistories.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_histories),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            viewModel.namedHistories.keys.sorted().forEach { historyName ->
                                AppButton(
                                    text = historyName,
                                    onClick = {
                                        showHistories = false
                                        historyToView = historyName
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    AppButton(
                        text = stringResource(R.string.action_close),
                        onClick = { showHistories = false }
                    )
                }
            )
        }

        historyToView?.let { historyName ->
            AlertDialog(
                onDismissRequest = { historyToView = null },
                title = { Text(historyName) },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(viewModel.namedHistories[historyName].orEmpty()) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    AppButton(
                        text = stringResource(R.string.action_close),
                        onClick = { historyToView = null }
                    )
                }
            )
        }

        if (showDownloadDialog) {
            DownloadSoundPackDialog(
                context = context,
                defaultFolder = defaultSoundsFolder,
                suggestHost = suggestHost,
                suggestName = suggestName,
                onDismiss = { showDownloadDialog = false },
                onDownload = { link, folder ->
                    showDownloadDialog = false
                    viewModel.downloadSoundPack(link, folder)
                }
            )
        }

        if (showImportDialog) {
            ImportZipDialog(
                context = context,
                defaultFolder = defaultSoundsFolder,
                suggestHost = suggestHost,
                suggestName = suggestName,
                onDismiss = { showImportDialog = false },
                onImport = { folder ->
                    showImportDialog = false
                    pendingImportFolder = folder
                    zipPicker.launch(
                        arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                    )
                }
            )
        }

        if (showSoundPackFolderRequired) {
            AlertDialog(
                onDismissRequest = {
                    showSoundPackFolderRequired = false
                    pendingSoundAction = PendingSoundAction.NONE
                },
                title = { Text(stringResource(R.string.sounds_folder_required_title)) },
                text = { Text(stringResource(R.string.sound_pack_folder_required_message)) },
                confirmButton = {
                    AppButton(
                        text = stringResource(R.string.choose_folder),
                        onClick = {
                            showSoundPackFolderRequired = false
                            showSoundPackFolderSelector = true
                        }
                    )
                },
                dismissButton = {
                    AppButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = {
                            showSoundPackFolderRequired = false
                            pendingSoundAction = PendingSoundAction.NONE
                        }
                    )
                }
            )
        }

        if (showSoundPackFolderSelector) {
            FolderSelectorDialog(
                context = context,
                host = suggestHost,
                name = suggestName,
                onDismiss = {
                    showSoundPackFolderSelector = false
                    pendingSoundAction = PendingSoundAction.NONE
                },
                onFolderSelected = { selected ->
                    showSoundPackFolderSelector = false
                    viewModel.activeCharacter.value?.let { viewModel.assignSoundsFolder(it, selected) }
                    continueSoundAction()
                }
            )
        }

        if (showSoundPackPermissionRequired) {
            AlertDialog(
                onDismissRequest = { showSoundPackPermissionRequired = false },
                title = { Text(stringResource(R.string.sound_pack_permission_required_title)) },
                text = { Text(stringResource(R.string.sound_pack_permission_required_message)) },
                confirmButton = {
                    AppButton(
                        text = stringResource(R.string.action_close),
                        onClick = { showSoundPackPermissionRequired = false }
                    )
                },
                dismissButton = {}
            )
        }

        if (showMacroRecordingDialog) {
            MacroRecordingDialog(
                state = viewModel.macroRecordingState.value,
                recordedCount = viewModel.recordedMacroCommands.size,
                onStart = {
                    viewModel.startMacroRecording()
                    showMacroRecordingDialog = false
                },
                onTogglePause = { viewModel.toggleMacroRecordingPause() },
                onIgnoreLast = { viewModel.ignoreLastRecordedMacroCommand() },
                onStop = {
                    showMacroRecordingDialog = false
                    if (viewModel.stopMacroRecording()) {
                        showSaveMacroRecordingDialog = true
                    }
                },
                onDismiss = { showMacroRecordingDialog = false }
            )
        }

        if (showSaveMacroRecordingDialog) {
            SaveMacroRecordingDialog(
                recordedCommands = viewModel.recordedMacroCommands.toList(),
                characters = viewModel.characters,
                onDismiss = {
                    showSaveMacroRecordingDialog = false
                    viewModel.discardMacroRecording()
                },
                onSave = { name, commands, scope, scopeValue ->
                    showSaveMacroRecordingDialog = false
                    viewModel.saveMacroRecording(name, commands, scope, scopeValue)
                }
            )
        }
    }
}

@Composable
private fun GameToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val stateOn = stringResource(R.string.switch_on)
    val stateOff = stringResource(R.string.switch_off)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            )
            .semantics {
                stateDescription = if (checked) stateOn else stateOff
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(end = 8.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}
