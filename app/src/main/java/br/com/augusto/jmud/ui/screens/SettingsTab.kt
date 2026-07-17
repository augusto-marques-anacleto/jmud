package br.com.augusto.jmud.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.BuildConfig
import br.com.augusto.jmud.R
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.components.AppSlider
import br.com.augusto.jmud.ui.components.RadioRow
import br.com.augusto.jmud.ui.viewmodels.MudViewModel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ENCODING_ISO = "ISO-8859-1"
private const val ENCODING_UTF8 = "UTF-8"

@Composable
fun SettingsTab(viewModel: MudViewModel) {
    var showEngineDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteLogsDialog by remember { mutableStateOf(false) }
    var showUtf8Warning by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_connection),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.settings_encoding),
            style = MaterialTheme.typography.bodyLarge
        )
        RadioRow(
            label = stringResource(R.string.encoding_iso),
            selected = viewModel.encoding.value == ENCODING_ISO,
            onSelect = { viewModel.setEncodingSetting(ENCODING_ISO) }
        )
        RadioRow(
            label = stringResource(R.string.encoding_utf8),
            selected = viewModel.encoding.value == ENCODING_UTF8,
            onSelect = {
                if (viewModel.encoding.value != ENCODING_UTF8) {
                    showUtf8Warning = true
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.settings_tts),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )

        val engines = remember { viewModel.availableEngines() }
        val engineLabel = engines
            .firstOrNull { it.first == viewModel.ttsEngine.value }?.second
            ?: stringResource(R.string.system_default)
        AppButton(
            text = stringResource(R.string.tts_engine_value, engineLabel),
            onClick = { showEngineDialog = true },
            modifier = Modifier.fillMaxWidth()
        )

        val voiceLabel = viewModel.ttsVoice.value.ifBlank { stringResource(R.string.default_option) }
        AppButton(
            text = stringResource(R.string.tts_voice_value, voiceLabel),
            onClick = { showVoiceDialog = true },
            modifier = Modifier.fillMaxWidth()
        )

        val timesTemplate = stringResource(R.string.times_format)
        val percentTemplate = stringResource(R.string.percent_format)
        val decimalFormat = remember { DecimalFormat("0.##") }
        val timesText: (Float) -> String = { timesTemplate.format(decimalFormat.format(it)) }
        val percentText: (Float) -> String = { percentTemplate.format((it * 100).roundToInt()) }

        SliderSetting(
            label = stringResource(R.string.tts_rate),
            value = viewModel.ttsRate.value,
            min = 0.5f,
            max = 3.0f,
            step = 0.25f,
            valueText = timesText,
            onChange = { viewModel.setTtsRateSetting(it) }
        )
        SliderSetting(
            label = stringResource(R.string.tts_pitch),
            value = viewModel.ttsPitch.value,
            min = 0.5f,
            max = 2.0f,
            step = 0.25f,
            valueText = timesText,
            onChange = { viewModel.setTtsPitchSetting(it) }
        )
        SliderSetting(
            label = stringResource(R.string.tts_volume),
            value = viewModel.ttsVolume.value,
            min = 0.1f,
            max = 1.0f,
            step = 0.1f,
            valueText = percentText,
            onChange = { viewModel.setTtsVolumeSetting(it) }
        )

        AppButton(
            text = stringResource(R.string.use_system_rate_pitch),
            onClick = { viewModel.resetTtsRateAndPitchToSystem() },
            modifier = Modifier.fillMaxWidth()
        )

        AppButton(
            text = stringResource(R.string.test_voice),
            onClick = { viewModel.testVoice() },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.settings_logs),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        SwitchRow(
            label = stringResource(R.string.save_session_logs),
            checked = viewModel.logsEnabled.value,
            onCheckedChange = { viewModel.setLogsEnabledSetting(it) }
        )
        val retentionLabel = if (viewModel.logRetentionDays.value <= 0) {
            stringResource(R.string.retention_never)
        } else {
            pluralStringResource(
                R.plurals.retention_days,
                viewModel.logRetentionDays.value,
                viewModel.logRetentionDays.value
            )
        }
        AppButton(
            text = stringResource(R.string.log_retention_value, retentionLabel),
            onClick = { showRetentionDialog = true },
            enabled = viewModel.logsEnabled.value,
            modifier = Modifier.fillMaxWidth()
        )
        AppButton(
            text = stringResource(R.string.logs_delete_all),
            onClick = { showDeleteLogsDialog = true },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.settings_backup),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.backup_description),
            style = MaterialTheme.typography.bodyLarge
        )
        AppButton(
            text = stringResource(R.string.backup_export),
            onClick = {
                val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                exportLauncher.launch("jmud_backup_$date.jmud")
            },
            modifier = Modifier.fillMaxWidth()
        )
        AppButton(
            text = stringResource(R.string.backup_import),
            onClick = { showImportConfirmDialog = true },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.settings_updates),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.installed_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyLarge
        )
        AppButton(
            text = stringResource(R.string.check_updates),
            onClick = { viewModel.checkForUpdates() },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showUtf8Warning) {
        AlertDialog(
            onDismissRequest = { showUtf8Warning = false },
            title = { Text(stringResource(R.string.attention_title)) },
            text = { Text(stringResource(R.string.encoding_utf8_warning)) },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.encoding_utf8_confirm),
                    onClick = {
                        showUtf8Warning = false
                        viewModel.setEncodingSetting(ENCODING_UTF8)
                    }
                )
            },
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showUtf8Warning = false }
                )
            }
        )
    }

    if (showDeleteLogsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteLogsDialog = false },
            title = { Text(stringResource(R.string.logs_delete_all)) },
            text = { Text(stringResource(R.string.logs_delete_confirm)) },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.logs_delete_confirm_yes),
                    onClick = {
                        showDeleteLogsDialog = false
                        viewModel.deleteAllLogs()
                    }
                )
            },
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showDeleteLogsDialog = false }
                )
            }
        )
    }

    val logsMessage = viewModel.logsMessage.value
    if (logsMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearLogsMessage() },
            title = { Text(stringResource(R.string.settings_logs)) },
            text = { Text(logsMessage) },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.action_close),
                    onClick = { viewModel.clearLogsMessage() }
                )
            }
        )
    }

    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = { Text(stringResource(R.string.backup_import)) },
            text = { Text(stringResource(R.string.backup_import_warning)) },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.backup_import_confirm),
                    onClick = {
                        showImportConfirmDialog = false
                        importLauncher.launch(arrayOf("*/*"))
                    }
                )
            },
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showImportConfirmDialog = false }
                )
            }
        )
    }

    val backupMessage = viewModel.backupMessage.value
    if (backupMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearBackupMessage() },
            title = { Text(stringResource(R.string.settings_backup)) },
            text = { Text(backupMessage) },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.action_close),
                    onClick = { viewModel.clearBackupMessage() }
                )
            }
        )
    }

    if (showEngineDialog) {
        val engines = remember { viewModel.availableEngines() }
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            title = { Text(stringResource(R.string.tts_engine_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RadioRow(
                        label = stringResource(R.string.system_default),
                        selected = viewModel.ttsEngine.value.isBlank(),
                        onSelect = {
                            viewModel.setTtsEngineSetting("")
                            showEngineDialog = false
                        }
                    )
                    engines.forEach { (name, label) ->
                        RadioRow(
                            label = label,
                            selected = viewModel.ttsEngine.value == name,
                            onSelect = {
                                viewModel.setTtsEngineSetting(name)
                                showEngineDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showEngineDialog = false }
                )
            }
        )
    }

    if (showRetentionDialog) {
        AlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            title = { Text(stringResource(R.string.log_retention_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(7, 15, 30, 90).forEach { days ->
                        RadioRow(
                            label = pluralStringResource(R.plurals.retention_days, days, days),
                            selected = viewModel.logRetentionDays.value == days,
                            onSelect = {
                                viewModel.setLogRetentionSetting(days)
                                showRetentionDialog = false
                            }
                        )
                    }
                    RadioRow(
                        label = stringResource(R.string.retention_never_delete),
                        selected = viewModel.logRetentionDays.value <= 0,
                        onSelect = {
                            viewModel.setLogRetentionSetting(0)
                            showRetentionDialog = false
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showRetentionDialog = false }
                )
            }
        )
    }

    if (showVoiceDialog) {
        val voices = remember { viewModel.availableVoices() }
        AlertDialog(
            onDismissRequest = { showVoiceDialog = false },
            title = { Text(stringResource(R.string.tts_voice_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (voices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_voices_available),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    RadioRow(
                        label = stringResource(R.string.default_option),
                        selected = viewModel.ttsVoice.value.isBlank(),
                        onSelect = {
                            viewModel.setTtsVoiceSetting("")
                            showVoiceDialog = false
                        }
                    )
                    voices.forEach { voice ->
                        RadioRow(
                            label = voice,
                            selected = viewModel.ttsVoice.value == voice,
                            onSelect = {
                                viewModel.setTtsVoiceSetting(voice)
                                showVoiceDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showVoiceDialog = false }
                )
            }
        )
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    valueText: (Float) -> String,
    onChange: (Float) -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }
    val steps = ((max - min) / step).roundToInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        AppSlider(
            value = value,
            onValueChange = onChange,
            label = label,
            min = min,
            max = max,
            step = step,
            modifier = Modifier.fillMaxWidth()
        )
        AppButton(
            text = stringResource(R.string.labeled_value, label, valueText(value)),
            onClick = { showOptions = true },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text(label) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (0..steps).forEach { index ->
                        val option = min + index * step
                        RadioRow(
                            label = valueText(option),
                            selected = abs(option - value) < step / 2,
                            onSelect = {
                                onChange(option)
                                showOptions = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showOptions = false }
                )
            }
        )
    }
}
