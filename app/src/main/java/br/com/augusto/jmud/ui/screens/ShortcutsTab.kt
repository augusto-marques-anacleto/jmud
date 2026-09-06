package br.com.augusto.jmud.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.R
import br.com.augusto.jmud.domain.MudCharacter
import br.com.augusto.jmud.domain.MudShortcut
import br.com.augusto.jmud.domain.Scope
import br.com.augusto.jmud.domain.ShortcutAction
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.components.AppTextField
import br.com.augusto.jmud.ui.components.RadioRow
import br.com.augusto.jmud.ui.components.ScopeSelector
import br.com.augusto.jmud.ui.viewmodels.MudViewModel
import java.util.UUID

@Composable
private fun actionLabel(action: String): String = stringResource(
    when (action) {
        ShortcutAction.REPEAT_LAST -> R.string.shortcut_action_repeat_last
        ShortcutAction.SPEAK_LAST -> R.string.shortcut_action_speak_last
        ShortcutAction.STOP_SOUND -> R.string.shortcut_action_stop_sound
        ShortcutAction.STOP_MACRO -> R.string.shortcut_action_stop_macro
        else -> R.string.shortcut_action_command
    }
)

@Composable
fun ShortcutsTab(viewModel: MudViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var shortcutOptions by remember { mutableStateOf<MudShortcut?>(null) }
    var shortcutToEdit by remember { mutableStateOf<MudShortcut?>(null) }
    var showTiltSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.shortcuts_description),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                SwitchRow(
                    label = stringResource(R.string.shortcut_panel_switch),
                    checked = viewModel.shortcutPanelEnabled.value,
                    onCheckedChange = { viewModel.setShortcutPanelEnabled(it) }
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppButton(
                        text = stringResource(R.string.add_shortcut),
                        onClick = { showAddDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    AppButton(
                        text = stringResource(R.string.help_button),
                        onClick = { viewModel.openHelp(HelpPages.SHORTCUTS) }
                    )
                }
            }
            item {
                AppButton(
                    text = stringResource(R.string.add_direction_shortcuts),
                    onClick = { viewModel.addDirectionShortcuts() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { HorizontalDivider() }

            item {
                Text(
                    text = stringResource(R.string.tilt_section_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
            }
            if (viewModel.isTiltAvailable()) {
                item {
                    Text(
                        text = stringResource(R.string.tilt_description),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                item {
                    SwitchRow(
                        label = stringResource(R.string.tilt_mode_switch),
                        checked = viewModel.tiltModeActive.value,
                        onCheckedChange = { viewModel.setTiltModeActive(it) }
                    )
                }
                item {
                    AppButton(
                        text = stringResource(R.string.tilt_configure),
                        onClick = { showTiltSettings = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.tilt_sensor_missing),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            item { HorizontalDivider() }

            if (viewModel.shortcuts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_shortcuts_saved),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                items(viewModel.shortcuts, key = { it.id }) { shortcut ->
                    ShortcutCard(
                        shortcut = shortcut,
                        characters = viewModel.characters,
                        onEdit = { shortcutToEdit = shortcut },
                        onShare = { viewModel.shareShortcut(shortcut) },
                        onLongClick = { shortcutOptions = shortcut }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        shortcutOptions?.let { target ->
            AlertDialog(
                onDismissRequest = { shortcutOptions = null },
                title = { Text(stringResource(R.string.character_options_title, target.label)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            text = stringResource(R.string.action_edit),
                            onClick = {
                                shortcutToEdit = target
                                shortcutOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppButton(
                            text = stringResource(R.string.action_run),
                            onClick = {
                                viewModel.runShortcut(target)
                                shortcutOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppButton(
                            text = stringResource(R.string.action_share),
                            onClick = {
                                viewModel.shareShortcut(target)
                                shortcutOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppButton(
                            text = stringResource(R.string.action_remove),
                            onClick = {
                                viewModel.removeShortcut(target)
                                shortcutOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    AppButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { shortcutOptions = null }
                    )
                }
            )
        }

        if (showTiltSettings) {
            TiltSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showTiltSettings = false }
            )
        }

        if (showAddDialog) {
            ShortcutDialog(
                initialShortcut = null,
                characters = viewModel.characters,
                onDismiss = { showAddDialog = false },
                onSave = { shortcut ->
                    viewModel.saveShortcut(shortcut)
                    showAddDialog = false
                }
            )
        }

        shortcutToEdit?.let { target ->
            ShortcutDialog(
                initialShortcut = target,
                characters = viewModel.characters,
                onDismiss = { shortcutToEdit = null },
                onSave = { shortcut ->
                    viewModel.saveShortcut(shortcut)
                    shortcutToEdit = null
                }
            )
        }
    }
}

@Composable
private fun shortcutScopeText(shortcut: MudShortcut, characters: List<MudCharacter>): String =
    when (shortcut.scope) {
        Scope.MUD -> stringResource(R.string.timer_scope_mud_desc, shortcut.scopeValue)
        Scope.CHARACTER -> {
            val characterName = characters.firstOrNull { it.id == shortcut.scopeValue }?.name
            if (characterName != null) {
                stringResource(R.string.timer_scope_character_desc, characterName)
            } else {
                stringResource(R.string.timer_scope_removed_character)
            }
        }
        else -> stringResource(R.string.timer_scope_all_desc)
    }

@Composable
private fun shortcutDescription(shortcut: MudShortcut, characters: List<MudCharacter>): String {
    val what = if (shortcut.action == ShortcutAction.COMMAND) {
        stringResource(R.string.shortcut_sends_command, shortcut.command)
    } else {
        actionLabel(shortcut.action)
    }
    val description = stringResource(
        R.string.shortcut_description,
        shortcut.label,
        what,
        shortcutScopeText(shortcut, characters)
    )
    return if (shortcut.enabled) {
        description
    } else {
        description + stringResource(R.string.trigger_disabled_suffix)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutCard(
    shortcut: MudShortcut,
    characters: List<MudCharacter>,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onLongClick: () -> Unit
) {
    val editLabel = stringResource(R.string.edit_item, shortcut.label)
    val shareLabel = stringResource(R.string.share_item, shortcut.label)
    val optionsLabel = stringResource(R.string.more_options)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                customActions = listOf(
                    CustomAccessibilityAction(editLabel) { onEdit(); true },
                    CustomAccessibilityAction(shareLabel) { onShare(); true },
                    CustomAccessibilityAction(optionsLabel) { onLongClick(); true }
                )
            }
            .combinedClickable(
                onClick = onEdit,
                onLongClick = onLongClick
            )
    ) {
        Text(
            text = shortcutDescription(shortcut, characters),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ShortcutDialog(
    initialShortcut: MudShortcut?,
    characters: List<MudCharacter>,
    onDismiss: () -> Unit,
    onSave: (MudShortcut) -> Unit
) {
    var label by remember { mutableStateOf(initialShortcut?.label ?: "") }
    var command by remember { mutableStateOf(initialShortcut?.command ?: "") }
    var action by remember { mutableStateOf(initialShortcut?.action ?: ShortcutAction.COMMAND) }
    var scope by remember { mutableStateOf(initialShortcut?.scope ?: Scope.ALL) }
    var scopeValue by remember { mutableStateOf(initialShortcut?.scopeValue ?: "") }
    var enabled by remember { mutableStateOf(initialShortcut?.enabled ?: true) }
    var showActionDialog by remember { mutableStateOf(false) }

    val scopeValueValid = when (scope) {
        Scope.ALL -> true
        else -> scopeValue.isNotBlank()
    }
    val commandValid = action != ShortcutAction.COMMAND || command.isNotBlank()
    val isFormValid = label.isNotBlank() && commandValid && scopeValueValid

    if (showActionDialog) {
        AlertDialog(
            onDismissRequest = { showActionDialog = false },
            title = { Text(stringResource(R.string.shortcut_action_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ShortcutAction.ALL.forEach { option ->
                        RadioRow(
                            label = actionLabel(option),
                            selected = action == option,
                            onSelect = {
                                action = option
                                showActionDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showActionDialog = false }
                )
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initialShortcut == null) R.string.add_shortcut else R.string.edit_shortcut_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = stringResource(R.string.field_shortcut_label),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                AppButton(
                    text = stringResource(R.string.shortcut_action_value, actionLabel(action)),
                    onClick = { showActionDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
                if (action == ShortcutAction.COMMAND) {
                    AppTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = stringResource(R.string.field_shortcut_command),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }
                ScopeSelector(
                    scope = scope,
                    scopeValue = scopeValue,
                    characters = characters,
                    onScopeChange = { newScope, newValue ->
                        scope = newScope
                        scopeValue = newValue
                    }
                )
                SwitchRow(
                    label = stringResource(R.string.trigger_enabled),
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    onSave(
                        MudShortcut(
                            id = initialShortcut?.id ?: UUID.randomUUID().toString(),
                            label = label.trim(),
                            command = if (action == ShortcutAction.COMMAND) command.trim() else "",
                            action = action,
                            scope = scope,
                            scopeValue = scopeValue,
                            enabled = enabled
                        )
                    )
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
