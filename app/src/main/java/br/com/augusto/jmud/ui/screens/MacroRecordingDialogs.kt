package br.com.augusto.jmud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.R
import br.com.augusto.jmud.domain.MudCharacter
import br.com.augusto.jmud.domain.Scope
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.components.AppTextField
import br.com.augusto.jmud.ui.components.ScopeSelector
import br.com.augusto.jmud.ui.viewmodels.MacroRecordingState

@Composable
fun MacroRecordingDialog(
    state: MacroRecordingState,
    recordedCount: Int,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onIgnoreLast: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val statusText = if (state == MacroRecordingState.NONE) {
        stringResource(R.string.macro_recording_state_none)
    } else {
        val stateLabel = stringResource(
            if (state == MacroRecordingState.PAUSED) {
                R.string.macro_recording_state_paused
            } else {
                R.string.macro_recording_state_recording
            }
        )
        val countLabel = pluralStringResource(R.plurals.macro_recorded_count, recordedCount, recordedCount)
        stringResource(R.string.macro_recording_status_format, stateLabel, countLabel)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.macro_recording_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                AppButton(
                    text = stringResource(R.string.action_start_recording),
                    onClick = onStart,
                    enabled = state == MacroRecordingState.NONE,
                    modifier = Modifier.fillMaxWidth()
                )
                AppButton(
                    text = stringResource(
                        if (state == MacroRecordingState.PAUSED) {
                            R.string.action_resume_recording
                        } else {
                            R.string.action_pause_recording
                        }
                    ),
                    onClick = onTogglePause,
                    enabled = state != MacroRecordingState.NONE,
                    modifier = Modifier.fillMaxWidth()
                )
                AppButton(
                    text = stringResource(R.string.action_ignore_last_command),
                    onClick = onIgnoreLast,
                    enabled = state != MacroRecordingState.NONE,
                    modifier = Modifier.fillMaxWidth()
                )
                AppButton(
                    text = stringResource(R.string.action_stop_recording),
                    onClick = onStop,
                    enabled = state != MacroRecordingState.NONE,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            AppButton(
                text = stringResource(R.string.action_close),
                onClick = onDismiss
            )
        }
    )
}

@Composable
fun SaveMacroRecordingDialog(
    recordedCommands: List<String>,
    characters: List<MudCharacter>,
    onDismiss: () -> Unit,
    onSave: (name: String, commands: String, scope: String, scopeValue: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var commands by remember { mutableStateOf(recordedCommands.joinToString("\n")) }
    var scope by remember { mutableStateOf(Scope.ALL) }
    var scopeValue by remember { mutableStateOf("") }

    val clipboardManager = LocalClipboardManager.current
    val view = LocalView.current
    val copiedMessage = stringResource(R.string.copied_to_clipboard_message)

    val scopeValueValid = when (scope) {
        Scope.ALL -> true
        else -> scopeValue.isNotBlank()
    }
    val isFormValid = name.isNotBlank() && commands.isNotBlank() && scopeValueValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_macro_recording_title)) },
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
                    value = commands,
                    onValueChange = { commands = it },
                    label = stringResource(R.string.field_timer_commands),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 8
                )
                AppButton(
                    text = stringResource(R.string.copy_to_clipboard),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(commands))
                        @Suppress("DEPRECATION")
                        view.announceForAccessibility(copiedMessage)
                    },
                    modifier = Modifier.fillMaxWidth()
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
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.action_save),
                onClick = { onSave(name.trim(), commands, scope, scopeValue) },
                enabled = isFormValid
            )
        },
        dismissButton = {
            AppButton(
                text = stringResource(R.string.discard_macro_recording),
                onClick = onDismiss
            )
        }
    )
}
