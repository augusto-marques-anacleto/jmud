package br.com.augusto.jmud.ui.screens

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.R
import br.com.augusto.jmud.domain.MudCharacter
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.components.AppTextField
import br.com.augusto.jmud.ui.viewmodels.MudViewModel
import br.com.augusto.jmud.util.AppStorage
import br.com.augusto.jmud.util.FolderNames
import java.io.File

@Composable
fun CharactersTab(viewModel: MudViewModel, context: Context) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var characterOptions by remember { mutableStateOf<MudCharacter?>(null) }
    var characterToEdit by remember { mutableStateOf<MudCharacter?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppButton(
                    text = stringResource(R.string.manual_connection),
                    onClick = { showManualDialog = true },
                    modifier = Modifier.weight(1f)
                )
                AppButton(
                    text = stringResource(R.string.action_add),
                    onClick = { showAddDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            if (viewModel.characters.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_characters_saved),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(viewModel.characters, key = { it.id }) { character ->
                        CharacterCard(
                            character = character,
                            onConnect = { viewModel.connect(character) },
                            onEdit = { characterToEdit = character },
                            onRemove = { viewModel.removeCharacter(character) },
                            onLongClick = { characterOptions = character }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        characterOptions?.let { target ->
            AlertDialog(
                onDismissRequest = { characterOptions = null },
                title = { Text(stringResource(R.string.character_options_title, target.name)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            text = stringResource(R.string.action_edit),
                            onClick = {
                                characterToEdit = target
                                characterOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppButton(
                            text = stringResource(R.string.action_remove),
                            onClick = {
                                viewModel.removeCharacter(target)
                                characterOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    AppButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { characterOptions = null }
                    )
                }
            )
        }

        if (showAddDialog) {
            AddCharacterDialog(
                initialCharacter = null,
                context = context,
                onDismiss = { showAddDialog = false },
                onSave = { name, host, port, password, autoLogin, commands, useTTS, playSounds, soundsFolder ->
                    viewModel.addCharacter(name, host, port, password, autoLogin, commands, useTTS, playSounds, soundsFolder)
                    showAddDialog = false
                }
            )
        }

        characterToEdit?.let { target ->
            AddCharacterDialog(
                initialCharacter = target,
                context = context,
                onDismiss = { characterToEdit = null },
                onSave = { name, host, port, password, autoLogin, commands, useTTS, playSounds, soundsFolder ->
                    viewModel.updateCharacter(
                        target.copy(
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
                    )
                    characterToEdit = null
                }
            )
        }

        if (showManualDialog) {
            ManualConnectionDialog(
                viewModel = viewModel,
                onDismiss = { showManualDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharacterCard(
    character: MudCharacter,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onLongClick: () -> Unit
) {
    val editLabel = stringResource(R.string.edit_item, character.name)
    val removeLabel = stringResource(R.string.remove_item, character.name)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                customActions = listOf(
                    CustomAccessibilityAction(editLabel) { onEdit(); true },
                    CustomAccessibilityAction(removeLabel) { onRemove(); true }
                )
            }
            .combinedClickable(
                onClick = onConnect,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = character.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${character.host}:${character.port}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ManualConnectionDialog(
    viewModel: MudViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_connection)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppTextField(
                    value = viewModel.manualHost.value,
                    onValueChange = { viewModel.manualHost.value = it },
                    label = stringResource(R.string.field_host),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                AppTextField(
                    value = viewModel.manualPort.value,
                    onValueChange = { viewModel.manualPort.value = it },
                    label = stringResource(R.string.field_port),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    )
                )
                SwitchRow(
                    label = stringResource(R.string.use_tts),
                    checked = viewModel.manualUseTTS.value,
                    onCheckedChange = { viewModel.manualUseTTS.value = it }
                )
                SwitchRow(
                    label = stringResource(R.string.play_sounds_default_folder),
                    checked = viewModel.manualPlaySounds.value,
                    onCheckedChange = { viewModel.manualPlaySounds.value = it }
                )
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.action_connect),
                onClick = {
                    onDismiss()
                    viewModel.connectManual()
                },
                enabled = viewModel.manualHost.value.isNotBlank() && viewModel.manualPort.value.isNotBlank()
            )
        },
        dismissButton = {
            AppButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val stateOn = stringResource(R.string.switch_on)
    val stateOff = stringResource(R.string.switch_off)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch
            )
            .semantics {
                stateDescription = if (checked) stateOn else stateOff
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
fun AddCharacterDialog(
    initialCharacter: MudCharacter?,
    context: Context,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String, Boolean, String, Boolean, Boolean, String) -> Unit
) {
    var name by remember { mutableStateOf(initialCharacter?.name ?: "") }
    var host by remember { mutableStateOf(initialCharacter?.host ?: "") }
    var port by remember { mutableStateOf(initialCharacter?.port?.toString() ?: "") }
    var password by remember { mutableStateOf(initialCharacter?.password ?: "") }
    var autoLogin by remember { mutableStateOf(initialCharacter?.autoLogin ?: false) }
    var commands by remember { mutableStateOf(initialCharacter?.postConnectCommands ?: "") }
    var useTTS by remember { mutableStateOf(initialCharacter?.useTTS ?: true) }
    var playSounds by remember { mutableStateOf(initialCharacter?.playSounds ?: true) }
    var soundsFolder by remember { mutableStateOf(initialCharacter?.soundsFolder ?: "") }
    var showFolderSelector by remember { mutableStateOf(false) }

    val isFormValid = name.isNotBlank() && host.isNotBlank() && port.isNotBlank()

    if (showFolderSelector) {
        FolderSelectorDialog(
            context = context,
            host = host,
            name = name,
            onDismiss = { showFolderSelector = false },
            onFolderSelected = { selectedFolder ->
                soundsFolder = selectedFolder
                showFolderSelector = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initialCharacter == null) R.string.add_character_title else R.string.edit_character_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.field_name),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                AppTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (it.isBlank()) autoLogin = false
                    },
                    label = stringResource(R.string.field_password),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                AppTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = stringResource(R.string.field_host),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                AppTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = stringResource(R.string.field_port),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )
                SwitchRow(
                    label = stringResource(R.string.auto_login),
                    checked = autoLogin,
                    onCheckedChange = { autoLogin = it }
                )
                AppTextField(
                    value = commands,
                    onValueChange = { commands = it },
                    label = stringResource(R.string.post_connect_commands),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(
                                R.string.sounds_folder_value,
                                soundsFolder.ifBlank { stringResource(R.string.sounds_folder_none) }
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        AppButton(
                            text = stringResource(R.string.choose_folder),
                            onClick = { showFolderSelector = true },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                SwitchRow(
                    label = stringResource(R.string.use_tts),
                    checked = useTTS,
                    onCheckedChange = { useTTS = it }
                )
                SwitchRow(
                    label = stringResource(R.string.play_sounds),
                    checked = playSounds,
                    onCheckedChange = { playSounds = it }
                )
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    val portInt = port.toIntOrNull() ?: 4000
                    onSave(name, host, portInt, password, autoLogin && password.isNotBlank(), commands, useTTS, playSounds, soundsFolder)
                },
                enabled = isFormValid
            )
        },
        dismissButton = {
            AppButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
fun FolderSelectorDialog(
    context: Context,
    host: String,
    name: String,
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    val baseAppDir = remember { AppStorage.baseDir(context) }

    var folders by remember {
        mutableStateOf<List<String>>(
            baseAppDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        )
    }
    var showCreatePrompt by remember { mutableStateOf(false) }

    if (showCreatePrompt) {
        CreateFolderPrompt(
            baseAppDir = baseAppDir,
            host = host,
            name = name,
            onDismiss = { showCreatePrompt = false },
            onFolderCreated = { newFolderName ->
                folders = baseAppDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                onFolderSelected(newFolderName)
                showCreatePrompt = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_sounds_folder_title)) },
        text = {
            Column {
                if (folders.isEmpty()) {
                    Text(
                        stringResource(R.string.no_folders_found),
                        modifier = Modifier.padding(bottom = 16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        folders.forEach { folderName ->
                            AppButton(
                                text = folderName,
                                onClick = { onFolderSelected(folderName) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                AppButton(
                    text = stringResource(R.string.create_new_folder),
                    onClick = { showCreatePrompt = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            AppButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
fun CreateFolderPrompt(
    baseAppDir: File,
    host: String,
    name: String,
    onDismiss: () -> Unit,
    onFolderCreated: (String) -> Unit
) {
    val suggestedName = remember { FolderNames.suggest(host, name) }

    var folderNameInput by remember { mutableStateOf(suggestedName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_folder_title)) },
        text = {
            AppTextField(
                value = folderNameInput,
                onValueChange = { folderNameInput = it },
                label = stringResource(R.string.field_folder_name),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.create_and_select),
                onClick = {
                    if (folderNameInput.isNotBlank()) {
                        val newDir = File(baseAppDir, folderNameInput.trim())
                        if (!newDir.exists()) {
                            newDir.mkdirs()
                        }
                        onFolderCreated(newDir.name)
                    }
                }
            )
        },
        dismissButton = {
            AppButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss
            )
        }
    )
}
