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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.R
import br.com.augusto.jmud.domain.MudCharacter
import br.com.augusto.jmud.domain.MudTimer
import br.com.augusto.jmud.domain.Scope
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.components.AppTextField
import br.com.augusto.jmud.ui.components.ScopeSelector
import br.com.augusto.jmud.ui.viewmodels.MudViewModel

@Composable
fun TimersTab(viewModel: MudViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var timerOptions by remember { mutableStateOf<MudTimer?>(null) }
    var timerToEdit by remember { mutableStateOf<MudTimer?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                AppButton(
                    text = stringResource(R.string.add_timer),
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            if (viewModel.timers.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_timers_saved),
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
                    items(viewModel.timers, key = { it.id }) { timer ->
                        TimerCard(
                            timer = timer,
                            characters = viewModel.characters,
                            onEdit = { timerToEdit = timer },
                            onRemove = { viewModel.removeTimer(timer) },
                            onLongClick = { timerOptions = timer }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        timerOptions?.let { target ->
            AlertDialog(
                onDismissRequest = { timerOptions = null },
                title = { Text(stringResource(R.string.timer_options_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            text = stringResource(R.string.action_edit),
                            onClick = {
                                timerToEdit = target
                                timerOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppButton(
                            text = stringResource(R.string.action_remove),
                            onClick = {
                                viewModel.removeTimer(target)
                                timerOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    AppButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { timerOptions = null }
                    )
                }
            )
        }

        if (showAddDialog) {
            TimerDialog(
                initialTimer = null,
                characters = viewModel.characters,
                onDismiss = { showAddDialog = false },
                onSave = { seconds, commands, scope, scopeValue, enabled ->
                    viewModel.addTimer(seconds, commands, scope, scopeValue, enabled)
                    showAddDialog = false
                }
            )
        }

        timerToEdit?.let { target ->
            TimerDialog(
                initialTimer = target,
                characters = viewModel.characters,
                onDismiss = { timerToEdit = null },
                onSave = { seconds, commands, scope, scopeValue, enabled ->
                    viewModel.updateTimer(
                        target.copy(
                            seconds = seconds,
                            commands = commands,
                            scope = scope,
                            scopeValue = scopeValue,
                            enabled = enabled
                        )
                    )
                    timerToEdit = null
                }
            )
        }
    }
}

@Composable
private fun timerDescription(timer: MudTimer, characters: List<MudCharacter>): String {
    val commandsText = timer.commands.split("\n")
        .filter { it.isNotBlank() }
        .joinToString("; ") { it.trim() }
    val scopeText = when (timer.scope) {
        Scope.MUD -> stringResource(R.string.timer_scope_mud_desc, timer.scopeValue)
        Scope.CHARACTER -> {
            val characterName = characters.firstOrNull { it.id == timer.scopeValue }?.name
            if (characterName != null) {
                stringResource(R.string.timer_scope_character_desc, characterName)
            } else {
                stringResource(R.string.timer_scope_removed_character)
            }
        }
        else -> stringResource(R.string.timer_scope_all_desc)
    }
    val description = pluralStringResource(
        R.plurals.timer_every_seconds,
        timer.seconds,
        timer.seconds,
        commandsText,
        scopeText
    )
    return if (timer.enabled) {
        description
    } else {
        description + stringResource(R.string.timer_disabled_suffix)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerCard(
    timer: MudTimer,
    characters: List<MudCharacter>,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onLongClick: () -> Unit
) {
    val editLabel = stringResource(R.string.edit_timer)
    val removeLabel = stringResource(R.string.remove_timer)
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
                onClick = onEdit,
                onLongClick = onLongClick
            )
    ) {
        Text(
            text = timerDescription(timer, characters),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun TimerDialog(
    initialTimer: MudTimer?,
    characters: List<MudCharacter>,
    onDismiss: () -> Unit,
    onSave: (Int, String, String, String, Boolean) -> Unit
) {
    var secondsInput by remember { mutableStateOf(initialTimer?.seconds?.toString() ?: "") }
    var commands by remember { mutableStateOf(initialTimer?.commands ?: "") }
    var scope by remember { mutableStateOf(initialTimer?.scope ?: Scope.ALL) }
    var scopeValue by remember { mutableStateOf(initialTimer?.scopeValue ?: "") }
    var enabled by remember { mutableStateOf(initialTimer?.enabled ?: true) }

    val secondsInt = secondsInput.toIntOrNull()
    val scopeValueValid = when (scope) {
        Scope.ALL -> true
        else -> scopeValue.isNotBlank()
    }
    val isFormValid = secondsInt != null && secondsInt >= 1 && commands.isNotBlank() && scopeValueValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initialTimer == null) R.string.add_timer else R.string.edit_timer_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppTextField(
                    value = secondsInput,
                    onValueChange = { secondsInput = it },
                    label = stringResource(R.string.field_seconds),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )
                if (secondsInt != null && secondsInt < 1) {
                    Text(
                        text = stringResource(R.string.min_time_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                AppTextField(
                    value = commands,
                    onValueChange = { commands = it },
                    label = stringResource(R.string.field_timer_commands),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5
                )
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
                    label = stringResource(R.string.timer_enabled),
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    onSave(secondsInt ?: 1, commands, scope, scopeValue, enabled)
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
