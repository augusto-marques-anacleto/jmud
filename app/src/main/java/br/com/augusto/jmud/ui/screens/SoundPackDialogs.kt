package br.com.augusto.jmud.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.R
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.components.AppTextField
import br.com.augusto.jmud.util.SoundPackProgress
import br.com.augusto.jmud.util.SoundPackStep

@Composable
fun MoreOptionsDialog(
    triggersEnabled: Boolean,
    onTriggersEnabledChange: (Boolean) -> Unit,
    timersEnabled: Boolean,
    onTimersEnabledChange: (Boolean) -> Unit,
    onDownloadSoundPack: () -> Unit,
    onImportZip: () -> Unit,
    disconnectLabel: String,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_options)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow(
                    label = stringResource(R.string.triggers_enabled_switch),
                    checked = triggersEnabled,
                    onCheckedChange = onTriggersEnabledChange
                )
                SwitchRow(
                    label = stringResource(R.string.timers_enabled_switch),
                    checked = timersEnabled,
                    onCheckedChange = onTimersEnabledChange
                )
                AppButton(
                    text = stringResource(R.string.download_sound_pack),
                    onClick = onDownloadSoundPack,
                    modifier = Modifier.fillMaxWidth()
                )
                AppButton(
                    text = stringResource(R.string.import_zip_pack),
                    onClick = onImportZip,
                    modifier = Modifier.fillMaxWidth()
                )
                AppButton(
                    text = disconnectLabel,
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth()
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
fun DestinationFolderButton(
    context: Context,
    folder: String,
    suggestHost: String,
    suggestName: String,
    onFolderChange: (String) -> Unit
) {
    var showFolderSelector by remember { mutableStateOf(false) }

    if (showFolderSelector) {
        FolderSelectorDialog(
            context = context,
            host = suggestHost,
            name = suggestName,
            onDismiss = { showFolderSelector = false },
            onFolderSelected = { selected ->
                onFolderChange(selected)
                showFolderSelector = false
            }
        )
    }

    AppButton(
        text = stringResource(R.string.destination_folder_value, folder),
        onClick = { showFolderSelector = true },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun DownloadSoundPackDialog(
    context: Context,
    defaultFolder: String,
    suggestHost: String,
    suggestName: String,
    onDismiss: () -> Unit,
    onDownload: (String, String) -> Unit
) {
    var link by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(defaultFolder) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_sound_pack)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = stringResource(R.string.field_link),
                    modifier = Modifier.fillMaxWidth()
                )
                DestinationFolderButton(
                    context = context,
                    folder = folder,
                    suggestHost = suggestHost,
                    suggestName = suggestName,
                    onFolderChange = { folder = it }
                )
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.action_download),
                onClick = { onDownload(link, folder) },
                enabled = link.isNotBlank() && folder.isNotBlank()
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
fun ImportZipDialog(
    context: Context,
    defaultFolder: String,
    suggestHost: String,
    suggestName: String,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var folder by remember { mutableStateOf(defaultFolder) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_zip_pack)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DestinationFolderButton(
                    context = context,
                    folder = folder,
                    suggestHost = suggestHost,
                    suggestName = suggestName,
                    onFolderChange = { folder = it }
                )
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.choose_zip_and_import),
                onClick = { onImport(folder) },
                enabled = folder.isNotBlank()
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
fun SoundPackProgressDialog(
    progress: SoundPackProgress,
    onBackground: () -> Unit,
    onCancel: () -> Unit
) {
    val stepLabel = stringResource(
        when (progress.step) {
            SoundPackStep.DOWNLOADING -> R.string.step_downloading
            SoundPackStep.EXTRACTING -> R.string.step_extracting
        }
    )
    val detail = when (progress.step) {
        SoundPackStep.DOWNLOADING -> {
            if (progress.totalKnown) {
                stringResource(
                    R.string.download_speed_detail,
                    "%.1f".format(progress.speedMbps),
                    stringResource(R.string.eta_format, progress.etaSeconds / 60, progress.etaSeconds % 60)
                )
            } else {
                stringResource(R.string.downloaded_mb, "%.1f".format(progress.downloadedMb))
            }
        }
        SoundPackStep.EXTRACTING ->
            stringResource(R.string.file_progress, progress.currentFile, progress.totalFiles)
    }
    val status = if (progress.step == SoundPackStep.DOWNLOADING && !progress.totalKnown) {
        stringResource(R.string.labeled_value, stepLabel, detail)
    } else {
        stringResource(R.string.progress_status, stepLabel, progress.percent, detail)
    }

    AlertDialog(
        onDismissRequest = onBackground,
        title = { Text(stringResource(R.string.installing_sound_pack)) },
        text = {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.action_background),
                onClick = onBackground
            )
        },
        dismissButton = {
            AppButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel
            )
        }
    )
}
