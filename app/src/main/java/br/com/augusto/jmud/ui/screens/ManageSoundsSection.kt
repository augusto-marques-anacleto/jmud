package br.com.augusto.jmud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.R
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.components.AppTextField
import br.com.augusto.jmud.ui.viewmodels.MudViewModel

@Composable
fun ManageSoundsSection(viewModel: MudViewModel) {
    var showFoldersDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var folderOptions by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var folderToAssign by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var folders by remember { mutableStateOf(viewModel.soundFolders()) }

    Text(
        text = stringResource(R.string.settings_manage_sounds),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() }
    )
    Text(
        text = stringResource(R.string.manage_sounds_description),
        style = MaterialTheme.typography.bodyLarge
    )
    AppButton(
        text = stringResource(R.string.sound_folders_button),
        onClick = {
            folders = viewModel.soundFolders()
            showFoldersDialog = true
        },
        modifier = Modifier.fillMaxWidth()
    )
    AppButton(
        text = stringResource(R.string.create_sound_folder),
        onClick = { showCreateDialog = true },
        modifier = Modifier.fillMaxWidth()
    )

    if (showFoldersDialog) {
        AlertDialog(
            onDismissRequest = { showFoldersDialog = false },
            title = { Text(stringResource(R.string.sound_folders_button)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (folders.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_sound_folders),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        folders.forEach { folderName ->
                            AppButton(
                                text = folderName,
                                onClick = { folderOptions = folderName },
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
                    onClick = { showFoldersDialog = false }
                )
            }
        )
    }

    folderOptions?.let { folderName ->
        val fileCount = remember(folderName) { viewModel.soundFolderFileCount(folderName) }
        val usedBy = viewModel.charactersUsingFolder(folderName)
        AlertDialog(
            onDismissRequest = { folderOptions = null },
            title = { Text(folderName) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.folder_files_count, fileCount, fileCount),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (usedBy.isEmpty()) {
                            stringResource(R.string.folder_used_by_none)
                        } else {
                            stringResource(R.string.folder_used_by, usedBy.joinToString(", ") { it.name })
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    AppButton(
                        text = stringResource(R.string.assign_to_character),
                        onClick = {
                            folderToAssign = folderName
                            folderOptions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppButton(
                        text = stringResource(R.string.delete_folder),
                        onClick = {
                            folderToDelete = folderName
                            folderOptions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_close),
                    onClick = { folderOptions = null }
                )
            }
        )
    }

    folderToAssign?.let { folderName ->
        AlertDialog(
            onDismissRequest = { folderToAssign = null },
            title = { Text(stringResource(R.string.assign_folder_title, folderName)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (viewModel.characters.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_characters_to_assign),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        viewModel.characters.forEach { character ->
                            val assignedMessage = stringResource(
                                R.string.folder_assigned,
                                folderName,
                                character.name
                            )
                            AppButton(
                                text = stringResource(
                                    R.string.assign_character_current,
                                    character.name,
                                    character.soundsFolder.ifBlank {
                                        stringResource(R.string.sounds_folder_none)
                                    }
                                ),
                                onClick = {
                                    viewModel.assignSoundsFolder(character, folderName)
                                    folderToAssign = null
                                    resultMessage = assignedMessage
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
                    text = stringResource(R.string.action_cancel),
                    onClick = { folderToAssign = null }
                )
            }
        )
    }

    folderToDelete?.let { folderName ->
        val usedBy = viewModel.charactersUsingFolder(folderName)
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.delete_folder)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.delete_folder_confirm, folderName),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (usedBy.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.delete_folder_used_warning,
                                usedBy.joinToString(", ") { it.name }
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.delete_folder_confirm_yes),
                    onClick = {
                        viewModel.deleteSoundFolder(folderName)
                        folders = viewModel.soundFolders()
                        folderToDelete = null
                    }
                )
            },
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { folderToDelete = null }
                )
            }
        )
    }

    if (showCreateDialog) {
        var folderNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
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
                    text = stringResource(R.string.action_create),
                    onClick = {
                        if (viewModel.createSoundFolder(folderNameInput)) {
                            folders = viewModel.soundFolders()
                            showCreateDialog = false
                        }
                    },
                    enabled = folderNameInput.isNotBlank()
                )
            },
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showCreateDialog = false }
                )
            }
        )
    }

    resultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            title = { Text(stringResource(R.string.settings_manage_sounds)) },
            text = { Text(message) },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.action_close),
                    onClick = { resultMessage = null }
                )
            }
        )
    }
}
