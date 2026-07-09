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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.R
import br.com.augusto.jmud.domain.MudCharacter
import br.com.augusto.jmud.domain.MudTrigger
import br.com.augusto.jmud.domain.Scope
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.components.AppTextField
import br.com.augusto.jmud.ui.components.RadioRow
import br.com.augusto.jmud.ui.components.ScopeSelector
import br.com.augusto.jmud.ui.viewmodels.MudViewModel
import java.util.UUID

@Composable
fun TriggersTab(viewModel: MudViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var triggerOptions by remember { mutableStateOf<MudTrigger?>(null) }
    var triggerToEdit by remember { mutableStateOf<MudTrigger?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                AppButton(
                    text = stringResource(R.string.add_trigger),
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            if (viewModel.triggers.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_triggers_saved),
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
                    items(viewModel.triggers, key = { it.id }) { trigger ->
                        TriggerCard(
                            trigger = trigger,
                            characters = viewModel.characters,
                            onEdit = { triggerToEdit = trigger },
                            onRemove = { viewModel.removeTrigger(trigger) },
                            onLongClick = { triggerOptions = trigger }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        triggerOptions?.let { target ->
            AlertDialog(
                onDismissRequest = { triggerOptions = null },
                title = { Text(stringResource(R.string.character_options_title, target.name)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            text = stringResource(R.string.action_edit),
                            onClick = {
                                triggerToEdit = target
                                triggerOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppButton(
                            text = stringResource(R.string.action_remove),
                            onClick = {
                                viewModel.removeTrigger(target)
                                triggerOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    AppButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { triggerOptions = null }
                    )
                }
            )
        }

        if (showAddDialog) {
            TriggerDialog(
                initialTrigger = null,
                characters = viewModel.characters,
                onDismiss = { showAddDialog = false },
                onSave = { trigger ->
                    viewModel.saveTrigger(trigger)
                    showAddDialog = false
                }
            )
        }

        triggerToEdit?.let { target ->
            TriggerDialog(
                initialTrigger = target,
                characters = viewModel.characters,
                onDismiss = { triggerToEdit = null },
                onSave = { trigger ->
                    viewModel.saveTrigger(trigger)
                    triggerToEdit = null
                }
            )
        }
    }
}

@Composable
private fun matchTypeLabel(matchType: String): String = stringResource(
    when (matchType) {
        MudTrigger.MATCH_CONTAINS -> R.string.match_contains
        MudTrigger.MATCH_END -> R.string.match_end
        MudTrigger.MATCH_EXACT -> R.string.match_exact
        MudTrigger.MATCH_PATTERN -> R.string.match_pattern
        else -> R.string.match_start
    }
)

@Composable
private fun triggerDescription(trigger: MudTrigger, characters: List<MudCharacter>): String {
    val scopeText = when (trigger.scope) {
        Scope.MUD -> stringResource(R.string.timer_scope_mud_desc, trigger.scopeValue)
        Scope.CHARACTER -> {
            val characterName = characters.firstOrNull { it.id == trigger.scopeValue }?.name
            if (characterName != null) {
                stringResource(R.string.timer_scope_character_desc, characterName)
            } else {
                stringResource(R.string.timer_scope_removed_character)
            }
        }
        else -> stringResource(R.string.timer_scope_all_desc)
    }
    val description = stringResource(
        R.string.trigger_description,
        trigger.name,
        matchTypeLabel(trigger.matchType),
        trigger.message,
        scopeText
    )
    return if (trigger.enabled) {
        description
    } else {
        description + stringResource(R.string.trigger_disabled_suffix)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TriggerCard(
    trigger: MudTrigger,
    characters: List<MudCharacter>,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onLongClick: () -> Unit
) {
    val editLabel = stringResource(R.string.edit_item, trigger.name)
    val removeLabel = stringResource(R.string.remove_item, trigger.name)
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
            text = triggerDescription(trigger, characters),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun TriggerDialog(
    initialTrigger: MudTrigger?,
    characters: List<MudCharacter>,
    onDismiss: () -> Unit,
    onSave: (MudTrigger) -> Unit
) {
    var name by remember { mutableStateOf(initialTrigger?.name ?: "") }
    var message by remember { mutableStateOf(initialTrigger?.message ?: "") }
    var matchType by remember { mutableStateOf(initialTrigger?.matchType ?: MudTrigger.MATCH_START) }
    var commands by remember { mutableStateOf(initialTrigger?.commands ?: "") }
    var scope by remember { mutableStateOf(initialTrigger?.scope ?: Scope.ALL) }
    var scopeValue by remember { mutableStateOf(initialTrigger?.scopeValue ?: "") }
    var enabled by remember { mutableStateOf(initialTrigger?.enabled ?: true) }
    var ignoreLine by remember { mutableStateOf(initialTrigger?.ignoreLine ?: false) }
    var historyName by remember { mutableStateOf(initialTrigger?.historyName ?: "") }
    var soundName by remember { mutableStateOf(initialTrigger?.soundName ?: "") }
    var showMatchDialog by remember { mutableStateOf(false) }

    val scopeValueValid = when (scope) {
        Scope.ALL -> true
        else -> scopeValue.isNotBlank()
    }
    val hasAnyAction = commands.isNotBlank() || ignoreLine || historyName.isNotBlank() || soundName.isNotBlank()
    val isFormValid = name.isNotBlank() && message.isNotBlank() && hasAnyAction && scopeValueValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initialTrigger == null) R.string.add_trigger else R.string.edit_trigger_title
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
                    value = message,
                    onValueChange = { message = it },
                    label = stringResource(R.string.field_trigger_message),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                AppButton(
                    text = stringResource(R.string.match_type_value, matchTypeLabel(matchType)),
                    onClick = { showMatchDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = commands,
                    onValueChange = { commands = it },
                    label = stringResource(R.string.field_timer_commands),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5
                )
                SwitchRow(
                    label = stringResource(R.string.trigger_ignore_line),
                    checked = ignoreLine,
                    onCheckedChange = { ignoreLine = it }
                )
                AppTextField(
                    value = historyName,
                    onValueChange = { historyName = it },
                    label = stringResource(R.string.field_history_name),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                AppTextField(
                    value = soundName,
                    onValueChange = { soundName = it },
                    label = stringResource(R.string.field_sound_name),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
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
                        MudTrigger(
                            id = initialTrigger?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            message = message,
                            matchType = matchType,
                            commands = commands,
                            scope = scope,
                            scopeValue = scopeValue,
                            enabled = enabled,
                            ignoreLine = ignoreLine,
                            historyName = historyName.trim(),
                            soundName = soundName.trim()
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

    if (showMatchDialog) {
        val options = listOf(
            MudTrigger.MATCH_START,
            MudTrigger.MATCH_CONTAINS,
            MudTrigger.MATCH_END,
            MudTrigger.MATCH_EXACT,
            MudTrigger.MATCH_PATTERN
        )
        AlertDialog(
            onDismissRequest = { showMatchDialog = false },
            title = { Text(stringResource(R.string.match_type_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { option ->
                        RadioRow(
                            label = matchTypeLabel(option),
                            selected = matchType == option,
                            onSelect = {
                                matchType = option
                                showMatchDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showMatchDialog = false }
                )
            }
        )
    }
}
