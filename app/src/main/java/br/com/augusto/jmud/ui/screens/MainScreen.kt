package br.com.augusto.jmud.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import br.com.augusto.jmud.R
import br.com.augusto.jmud.ui.components.AppButton
import br.com.augusto.jmud.ui.viewmodels.AppScreen
import br.com.augusto.jmud.ui.viewmodels.MudViewModel

@Composable
fun AppNavigation(viewModel: MudViewModel) {
    if (viewModel.currentScreen.value == AppScreen.MAIN) {
        MainScreen(viewModel)
    } else {
        GameScreen(viewModel)
    }

    val shareContext = LocalContext.current
    val chooserTitle = stringResource(R.string.share_chooser_title)
    val shareIntent = viewModel.pendingShareIntent.value
    LaunchedEffect(shareIntent) {
        if (shareIntent != null) {
            try {
                shareContext.startActivity(Intent.createChooser(shareIntent, chooserTitle))
            } catch (e: Exception) {
            }
            viewModel.clearShareIntent()
        }
    }

    val incomingBundle = viewModel.pendingImport.value
    if (incomingBundle != null) {
        ImportBundleDialog(
            bundle = incomingBundle,
            characters = viewModel.characters,
            onConfirm = { sections, replace, overrideScope, scope, scopeValue ->
                viewModel.applyImport(sections, replace, overrideScope, scope, scopeValue)
            },
            onDismiss = { viewModel.cancelImport() }
        )
    }

    val transferMessage = viewModel.backupMessage.value
    if (transferMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearBackupMessage() },
            title = { Text(stringResource(R.string.settings_backup)) },
            text = { Text(transferMessage) },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.action_close),
                    onClick = { viewModel.clearBackupMessage() }
                )
            }
        )
    }

    val migrationProgress = viewModel.storageMigrationProgress.value
    if (migrationProgress != null) {
        StorageMigrationDialog(
            progress = migrationProgress,
            onCancel = { viewModel.cancelStorageMigration() }
        )
    }

    val helpPage = viewModel.helpStartPage.value
    if (viewModel.storageChoiceVisible.value) {
        StorageChoiceDialog(viewModel)
    } else if (helpPage != null) {
        GeneralHelpDialog(
            startPage = helpPage,
            onDismiss = { viewModel.closeHelp() }
        )
    } else if (viewModel.welcomeVisible.value) {
        WelcomeDialog(
            onExplore = { viewModel.dismissWelcome(showHelp = true) },
            onDismiss = { viewModel.dismissWelcome(showHelp = false) }
        )
    }

    val packProgress = viewModel.soundPackProgress.value
    if (packProgress != null && viewModel.soundPackDialogVisible.value) {
        SoundPackProgressDialog(
            progress = packProgress,
            onBackground = { viewModel.hideSoundPackDialog() },
            onCancel = { viewModel.cancelSoundPack() }
        )
    }

    val update = viewModel.availableUpdate.value
    if (update != null && viewModel.updateDialogVisible.value) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.update_available_message,
                        update.versionName,
                        update.changelog
                    )
                )
            },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.update_download_install),
                    onClick = { viewModel.downloadUpdate() }
                )
            },
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.update_not_now),
                    onClick = { viewModel.dismissUpdateDialog() }
                )
            }
        )
    }

    val updatePercent = viewModel.updateDownloadPercent.value
    if (updatePercent != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Text(
                    text = stringResource(R.string.update_downloading, updatePercent),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            },
            confirmButton = {},
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { viewModel.cancelUpdateDownload() }
                )
            }
        )
    }

    val updateMessage = viewModel.updateMessage.value
    if (updateMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearUpdateMessage() },
            title = { Text(stringResource(R.string.settings_updates)) },
            text = { Text(updateMessage) },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.action_close),
                    onClick = { viewModel.clearUpdateMessage() }
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MudViewModel) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_characters),
        stringResource(R.string.tab_triggers),
        stringResource(R.string.tab_timers),
        stringResource(R.string.tab_settings)
    )
    val icons = listOf(Icons.Filled.Person, Icons.Filled.Build, Icons.Filled.DateRange, Icons.Filled.Settings)
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            R.string.main_top_bar_title,
                            stringResource(R.string.app_name),
                            tabs[selectedTabIndex]
                        )
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = { Icon(imageVector = icons[index], contentDescription = title) },
                        label = { Text(text = title) },
                        modifier = Modifier.semantics { role = Role.Tab }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTabIndex) {
                0 -> CharactersTab(viewModel, context)
                1 -> TriggersTab(viewModel)
                2 -> TimersTab(viewModel)
                3 -> SettingsTab(viewModel)
            }
        }
    }
}
