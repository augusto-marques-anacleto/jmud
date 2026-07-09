package br.com.augusto.jmud.ui.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.R
import br.com.augusto.jmud.domain.MudCharacter
import br.com.augusto.jmud.domain.Scope

@Composable
fun ScopeSelector(
    scope: String,
    scopeValue: String,
    characters: List<MudCharacter>,
    onScopeChange: (String, String) -> Unit
) {
    var showScopeDialog by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }

    val distinctHosts = characters
        .map { it.host }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

    val scopeLabel = when (scope) {
        Scope.MUD -> stringResource(R.string.scope_mud)
        Scope.CHARACTER -> stringResource(R.string.scope_character)
        else -> stringResource(R.string.scope_all)
    }
    AppButton(
        text = stringResource(R.string.scope_type_value, scopeLabel),
        onClick = { showScopeDialog = true },
        modifier = Modifier.fillMaxWidth()
    )
    if (scope == Scope.MUD) {
        val targetLabel = scopeValue.ifBlank { stringResource(R.string.action_choose) }
        AppButton(
            text = stringResource(R.string.server_value, targetLabel),
            onClick = { showTargetDialog = true },
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (scope == Scope.CHARACTER) {
        val targetLabel = characters.firstOrNull { it.id == scopeValue }?.name
            ?: stringResource(R.string.action_choose)
        AppButton(
            text = stringResource(R.string.character_value, targetLabel),
            onClick = { showTargetDialog = true },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showScopeDialog) {
        AlertDialog(
            onDismissRequest = { showScopeDialog = false },
            title = { Text(stringResource(R.string.scope_type_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RadioRow(
                        label = stringResource(R.string.scope_all),
                        selected = scope == Scope.ALL,
                        onSelect = {
                            onScopeChange(Scope.ALL, "")
                            showScopeDialog = false
                        }
                    )
                    RadioRow(
                        label = stringResource(R.string.scope_mud),
                        selected = scope == Scope.MUD,
                        onSelect = {
                            if (scope != Scope.MUD) {
                                onScopeChange(Scope.MUD, "")
                            }
                            showScopeDialog = false
                            showTargetDialog = true
                        }
                    )
                    RadioRow(
                        label = stringResource(R.string.scope_character),
                        selected = scope == Scope.CHARACTER,
                        onSelect = {
                            if (scope != Scope.CHARACTER) {
                                onScopeChange(Scope.CHARACTER, "")
                            }
                            showScopeDialog = false
                            showTargetDialog = true
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showScopeDialog = false }
                )
            }
        )
    }

    if (showTargetDialog && scope == Scope.MUD) {
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            title = { Text(stringResource(R.string.choose_server_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (distinctHosts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_servers_saved),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        distinctHosts.forEach { host ->
                            RadioRow(
                                label = host,
                                selected = scopeValue.equals(host, ignoreCase = true),
                                onSelect = {
                                    onScopeChange(Scope.MUD, host)
                                    showTargetDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showTargetDialog = false }
                )
            }
        )
    }

    if (showTargetDialog && scope == Scope.CHARACTER) {
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            title = { Text(stringResource(R.string.choose_character_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (characters.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_characters_registered),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        characters.forEach { character ->
                            RadioRow(
                                label = character.name,
                                selected = scopeValue == character.id,
                                onSelect = {
                                    onScopeChange(Scope.CHARACTER, character.id)
                                    showTargetDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showTargetDialog = false }
                )
            }
        )
    }
}
