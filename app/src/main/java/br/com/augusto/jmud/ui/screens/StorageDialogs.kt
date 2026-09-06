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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.R
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.viewmodels.MudViewModel
import br.com.augusto.jmud.util.MigrationProgress
import br.com.augusto.jmud.util.StorageOption
import br.com.augusto.jmud.util.StorageType

@Composable
fun storageDescription(type: StorageType): String = stringResource(
    when (type) {
        StorageType.DOCUMENTS -> R.string.storage_documents_description
        StorageType.APP_INTERNAL -> R.string.storage_app_internal_description
        StorageType.REMOVABLE -> R.string.storage_removable_description
    }
)

@Composable
fun StorageChoiceDialog(viewModel: MudViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissStorageChoice() },
        title = { Text(stringResource(R.string.storage_choice_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.storage_choice_description),
                    style = MaterialTheme.typography.bodyLarge
                )
                viewModel.storageOptions.forEach { option ->
                    Text(
                        text = storageDescription(option.type),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    AppButton(
                        text = stringResource(
                            R.string.storage_option_button,
                            viewModel.storageLabelFor(option),
                            viewModel.storageFreeText(option)
                        ),
                        onClick = { viewModel.chooseStorage(option) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            AppButton(
                text = stringResource(R.string.storage_choice_later),
                onClick = { viewModel.dismissStorageChoice() }
            )
        }
    )
}

@Composable
fun StorageMoveDialog(viewModel: MudViewModel, onDismiss: () -> Unit) {
    var pending by remember { mutableStateOf<StorageOption?>(null) }
    val current = viewModel.currentStorage.value

    pending?.let { target ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.storage_move_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.storage_move_confirm,
                        viewModel.storageLabelFor(target),
                        target.baseDir.absolutePath
                    )
                )
            },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.storage_move_confirm_button),
                    onClick = {
                        viewModel.moveStorageTo(target)
                        pending = null
                        onDismiss()
                    }
                )
            },
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { pending = null }
                )
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.storage_move_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.storage_current_value,
                        viewModel.storageLabelFor(current),
                        current.baseDir.absolutePath
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                HorizontalDivider()
                val others = viewModel.storageOptions.filter { it.id != current.id }
                if (others.isEmpty()) {
                    Text(
                        text = stringResource(R.string.storage_no_other_option),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Text(
                        text = stringResource(R.string.storage_move_description),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    others.forEach { option ->
                        Text(
                            text = storageDescription(option.type),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        AppButton(
                            text = stringResource(
                                R.string.storage_option_button,
                                viewModel.storageLabelFor(option),
                                viewModel.storageFreeText(option)
                            ),
                            onClick = { pending = option },
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
                onClick = onDismiss
            )
        }
    )
}

@Composable
fun StorageMigrationDialog(progress: MigrationProgress, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.storage_moving_title)) },
        text = {
            Text(
                text = if (progress.totalFiles > 0) {
                    stringResource(
                        R.string.storage_moving_progress,
                        progress.copiedFiles,
                        progress.totalFiles,
                        progress.percent
                    )
                } else {
                    stringResource(R.string.storage_moving_preparing)
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        },
        confirmButton = {},
        dismissButton = {
            AppButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel
            )
        }
    )
}
