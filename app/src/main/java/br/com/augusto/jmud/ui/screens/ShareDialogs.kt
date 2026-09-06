package br.com.augusto.jmud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.R
import br.com.augusto.jmud.domain.MudCharacter
import br.com.augusto.jmud.domain.ShareBundle
import br.com.augusto.jmud.domain.ShareKind
import br.com.augusto.jmud.domain.ShareSection
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.components.ScopeSelector
import br.com.augusto.jmud.domain.Scope

@Composable
private fun sectionLabel(section: ShareSection): String = stringResource(
    when (section) {
        ShareSection.CHARACTERS -> R.string.section_characters
        ShareSection.TRIGGERS -> R.string.section_triggers
        ShareSection.TIMERS -> R.string.section_timers
        ShareSection.MACROS -> R.string.section_macros
        ShareSection.SHORTCUTS -> R.string.section_shortcuts
        ShareSection.SETTINGS -> R.string.section_settings
    }
)

@Composable
private fun sectionLabelWithCount(section: ShareSection, count: Int): String {
    val label = sectionLabel(section)
    return if (section == ShareSection.SETTINGS) {
        label
    } else {
        stringResource(R.string.section_with_count, label, count)
    }
}

private fun hasScopedSections(sections: Collection<ShareSection>): Boolean =
    sections.any {
        it == ShareSection.TRIGGERS ||
            it == ShareSection.TIMERS ||
            it == ShareSection.MACROS ||
            it == ShareSection.SHORTCUTS
    }

@Composable
fun ExportSelectionDialog(
    counts: Map<ShareSection, Int>,
    onConfirm: (Set<ShareSection>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(ShareSection.entries.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_selection_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.export_selection_description),
                    style = MaterialTheme.typography.bodyLarge
                )
                ShareSection.entries.forEach { section ->
                    SwitchRow(
                        label = sectionLabelWithCount(section, counts[section] ?: 0),
                        checked = selected.contains(section),
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + section else selected - section
                        }
                    )
                }
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.backup_export),
                onClick = { onConfirm(selected) },
                enabled = selected.isNotEmpty()
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
fun ImportBundleDialog(
    bundle: ShareBundle,
    characters: List<MudCharacter>,
    onConfirm: (Set<ShareSection>, Boolean, Boolean, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val available = remember(bundle) { bundle.availableSections() }
    var selected by remember(bundle) { mutableStateOf(available.toSet()) }
    var replace by remember(bundle) {
        mutableStateOf(bundle.kind == ShareKind.BACKUP && bundle.settings.isNotEmpty())
    }
    var overrideScope by remember(bundle) { mutableStateOf(false) }
    var scope by remember(bundle) { mutableStateOf(Scope.ALL) }
    var scopeValue by remember(bundle) { mutableStateOf("") }

    val scopeAvailable = hasScopedSections(selected)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.import_content_description),
                    style = MaterialTheme.typography.bodyLarge
                )
                available.forEach { section ->
                    SwitchRow(
                        label = sectionLabelWithCount(section, bundle.countOf(section)),
                        checked = selected.contains(section),
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + section else selected - section
                        }
                    )
                }

                HorizontalDivider()

                Text(
                    text = stringResource(
                        if (replace) R.string.import_mode_replace_description
                        else R.string.import_mode_add_description
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                SwitchRow(
                    label = stringResource(R.string.import_mode_replace),
                    checked = replace,
                    onCheckedChange = { replace = it }
                )

                if (scopeAvailable) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.import_scope_description),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    SwitchRow(
                        label = stringResource(R.string.import_scope_override),
                        checked = overrideScope,
                        onCheckedChange = { overrideScope = it }
                    )
                    if (overrideScope) {
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
                }
            }
        },
        confirmButton = {
            val scopeValid = !overrideScope || scope == Scope.ALL || scopeValue.isNotBlank()
            AppButton(
                text = stringResource(R.string.import_confirm),
                onClick = { onConfirm(selected, replace, overrideScope && scopeAvailable, scope, scopeValue) },
                enabled = selected.isNotEmpty() && scopeValid,
                modifier = Modifier.fillMaxWidth()
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
