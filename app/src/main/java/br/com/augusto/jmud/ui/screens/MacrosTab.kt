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
import br.com.augusto.jmud.domain.MudMacro
import br.com.augusto.jmud.domain.Scope
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.components.AppTextField
import br.com.augusto.jmud.ui.components.ScopeSelector
import br.com.augusto.jmud.ui.viewmodels.MudViewModel
import java.util.UUID

@Composable
fun MacrosTab(viewModel: MudViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var macroOptions by remember { mutableStateOf<MudMacro?>(null) }
    var macroToEdit by remember { mutableStateOf<MudMacro?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppButton(
                    text = stringResource(R.string.add_macro),
                    onClick = { showAddDialog = true },
                    modifier = Modifier.weight(1f)
                )
                AppButton(
                    text = stringResource(R.string.help_button),
                    onClick = { viewModel.openHelp(HelpPages.MACROS) }
                )
            }

            HorizontalDivider()

            if (viewModel.macros.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_macros_saved),
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
                    items(viewModel.macros, key = { it.id }) { macro ->
                        MacroCard(
                            macro = macro,
                            characters = viewModel.characters,
                            onEdit = { macroToEdit = macro },
                            onLongClick = { macroOptions = macro }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        macroOptions?.let { target ->
            AlertDialog(
                onDismissRequest = { macroOptions = null },
                title = { Text(stringResource(R.string.character_options_title, target.name)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            text = stringResource(R.string.action_edit),
                            onClick = {
                                macroToEdit = target
                                macroOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppButton(
                            text = stringResource(R.string.action_run),
                            onClick = {
                                viewModel.runMacro(target)
                                macroOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        AppButton(
                            text = stringResource(R.string.action_remove),
                            onClick = {
                                viewModel.removeMacro(target)
                                macroOptions = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    AppButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { macroOptions = null }
                    )
                }
            )
        }

        if (showAddDialog) {
            MacroDialog(
                initialMacro = null,
                characters = viewModel.characters,
                onDismiss = { showAddDialog = false },
                onSave = { macro ->
                    viewModel.saveMacro(macro)
                    showAddDialog = false
                }
            )
        }

        macroToEdit?.let { target ->
            MacroDialog(
                initialMacro = target,
                characters = viewModel.characters,
                onDismiss = { macroToEdit = null },
                onSave = { macro ->
                    viewModel.saveMacro(macro)
                    macroToEdit = null
                }
            )
        }
    }
}

@Composable
private fun macroScopeText(macro: MudMacro, characters: List<MudCharacter>): String =
    when (macro.scope) {
        Scope.MUD -> stringResource(R.string.timer_scope_mud_desc, macro.scopeValue)
        Scope.CHARACTER -> {
            val characterName = characters.firstOrNull { it.id == macro.scopeValue }?.name
            if (characterName != null) {
                stringResource(R.string.timer_scope_character_desc, characterName)
            } else {
                stringResource(R.string.timer_scope_removed_character)
            }
        }
        else -> stringResource(R.string.timer_scope_all_desc)
    }

@Composable
private fun macroDescription(macro: MudMacro, characters: List<MudCharacter>): String {
    val commandsText = macro.commands.split("\n")
        .filter { it.isNotBlank() }
        .joinToString("; ") { it.trim() }
    val description = stringResource(
        R.string.macro_description,
        macro.name,
        commandsText,
        macroScopeText(macro, characters)
    )
    return if (macro.enabled) {
        description
    } else {
        description + stringResource(R.string.trigger_disabled_suffix)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MacroCard(
    macro: MudMacro,
    characters: List<MudCharacter>,
    onEdit: () -> Unit,
    onLongClick: () -> Unit
) {
    val editLabel = stringResource(R.string.edit_item, macro.name)
    val runLabel = stringResource(R.string.action_run)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                customActions = listOf(
                    CustomAccessibilityAction(editLabel) { onEdit(); true },
                    CustomAccessibilityAction(runLabel) { onLongClick(); true }
                )
            }
            .combinedClickable(
                onClick = onEdit,
                onLongClick = onLongClick
            )
    ) {
        Text(
            text = macroDescription(macro, characters),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun MacroDialog(
    initialMacro: MudMacro?,
    characters: List<MudCharacter>,
    onDismiss: () -> Unit,
    onSave: (MudMacro) -> Unit
) {
    var name by remember { mutableStateOf(initialMacro?.name ?: "") }
    var commands by remember { mutableStateOf(initialMacro?.commands ?: "") }
    var scope by remember { mutableStateOf(initialMacro?.scope ?: Scope.ALL) }
    var scopeValue by remember { mutableStateOf(initialMacro?.scopeValue ?: "") }
    var enabled by remember { mutableStateOf(initialMacro?.enabled ?: true) }

    val scopeValueValid = when (scope) {
        Scope.ALL -> true
        else -> scopeValue.isNotBlank()
    }
    val isFormValid = name.isNotBlank() && commands.isNotBlank() && scopeValueValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initialMacro == null) R.string.add_macro else R.string.edit_macro_title
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
                    label = stringResource(R.string.field_macro_command_name),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
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
                        MudMacro(
                            id = initialMacro?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            commands = commands,
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
