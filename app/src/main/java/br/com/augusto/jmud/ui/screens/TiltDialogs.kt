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
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.components.RadioRow
import br.com.augusto.jmud.ui.viewmodels.MudViewModel
import br.com.augusto.jmud.util.IntervalFormat
import br.com.augusto.jmud.util.TiltZone
import br.com.augusto.jmud.util.TiltZones
import java.text.DecimalFormatSymbols

@Composable
fun tiltZoneLabel(zone: TiltZone): String = stringResource(
    when (zone) {
        TiltZone.FORWARD -> R.string.tilt_zone_forward
        TiltZone.BACKWARD -> R.string.tilt_zone_backward
        TiltZone.RIGHT -> R.string.tilt_zone_right
        TiltZone.LEFT -> R.string.tilt_zone_left
        TiltZone.NEUTRAL -> R.string.tilt_zone_neutral
    }
)

@Composable
fun TiltSettingsDialog(viewModel: MudViewModel, onDismiss: () -> Unit) {
    var zoneToMap by remember { mutableStateOf<TiltZone?>(null) }
    var showSensitivity by remember { mutableStateOf(false) }
    var showConfirmTime by remember { mutableStateOf(false) }

    val decimalSeparator = remember { DecimalFormatSymbols.getInstance().decimalSeparator }
    val noneLabel = stringResource(R.string.tilt_zone_none)

    zoneToMap?.let { zone ->
        AlertDialog(
            onDismissRequest = { zoneToMap = null },
            title = { Text(stringResource(R.string.tilt_select_shortcut_title, tiltZoneLabel(zone))) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RadioRow(
                        label = noneLabel,
                        selected = viewModel.tiltShortcutFor(zone) == null,
                        onSelect = {
                            viewModel.setTiltShortcut(zone, "")
                            zoneToMap = null
                        }
                    )
                    viewModel.shortcuts.forEach { shortcut ->
                        RadioRow(
                            label = shortcut.label,
                            selected = viewModel.tiltShortcutFor(zone)?.id == shortcut.id,
                            onSelect = {
                                viewModel.setTiltShortcut(zone, shortcut.id)
                                zoneToMap = null
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { zoneToMap = null }
                )
            }
        )
    }

    if (showSensitivity) {
        AlertDialog(
            onDismissRequest = { showSensitivity = false },
            title = { Text(stringResource(R.string.tilt_sensitivity_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TiltZones.TRIGGER_OPTIONS.forEach { degrees ->
                        RadioRow(
                            label = stringResource(R.string.tilt_sensitivity_option, degrees),
                            selected = viewModel.tiltTriggerDegrees.value == degrees,
                            onSelect = {
                                viewModel.setTiltTriggerDegrees(degrees)
                                showSensitivity = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showSensitivity = false }
                )
            }
        )
    }

    if (showConfirmTime) {
        AlertDialog(
            onDismissRequest = { showConfirmTime = false },
            title = { Text(stringResource(R.string.tilt_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TiltZones.CONFIRM_OPTIONS.forEach { millis ->
                        RadioRow(
                            label = stringResource(
                                R.string.tilt_confirm_option,
                                IntervalFormat.formatMillis(millis, decimalSeparator)
                            ),
                            selected = viewModel.tiltConfirmMs.value == millis,
                            onSelect = {
                                viewModel.setTiltConfirmMs(millis)
                                showConfirmTime = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showConfirmTime = false }
                )
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tilt_configure)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.tilt_mapping_description),
                    style = MaterialTheme.typography.bodyLarge
                )
                TiltZones.MAPPABLE_ZONES.forEach { zone ->
                    val mapped = viewModel.tiltShortcutFor(zone)?.label ?: noneLabel
                    AppButton(
                        text = stringResource(R.string.tilt_zone_value, tiltZoneLabel(zone), mapped),
                        onClick = { zoneToMap = zone },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AppButton(
                    text = stringResource(R.string.tilt_suggest_mapping),
                    onClick = { viewModel.suggestTiltMapping() },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.tilt_repeat_description),
                    style = MaterialTheme.typography.bodyLarge
                )
                SwitchRow(
                    label = stringResource(R.string.tilt_repeat_switch),
                    checked = viewModel.tiltRepeatEnabled.value,
                    onCheckedChange = { viewModel.setTiltRepeatEnabled(it) }
                )

                AppButton(
                    text = stringResource(
                        R.string.tilt_sensitivity_value,
                        viewModel.tiltTriggerDegrees.value
                    ),
                    onClick = { showSensitivity = true },
                    modifier = Modifier.fillMaxWidth()
                )
                AppButton(
                    text = stringResource(
                        R.string.tilt_confirm_value,
                        IntervalFormat.formatMillis(viewModel.tiltConfirmMs.value, decimalSeparator)
                    ),
                    onClick = { showConfirmTime = true },
                    modifier = Modifier.fillMaxWidth()
                )
                if (viewModel.tiltModeActive.value) {
                    AppButton(
                        text = stringResource(R.string.tilt_recalibrate),
                        onClick = { viewModel.recalibrateTilt() },
                        modifier = Modifier.fillMaxWidth()
                    )
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
