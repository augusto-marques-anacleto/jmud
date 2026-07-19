package br.com.augusto.jmud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.augusto.jmud.R
import br.com.augusto.jmud.ui.components.AppButton

object HelpPages {
    const val OVERVIEW = 0
    const val CHARACTERS = 1
    const val GAME = 2
    const val KEYBOARD = 3
    const val MULTI_COMMANDS = 4
    const val TRIGGERS = 5
    const val TIMERS = 6
    const val SOUNDS = 7
    const val LOGS = 8
    const val BACKUP = 9
    const val ABOUT = 10
}

private data class HelpPage(val titleRes: Int, val bodyRes: Int)

private val helpPages = listOf(
    HelpPage(R.string.help_overview_title, R.string.help_overview_body),
    HelpPage(R.string.help_characters_title, R.string.help_characters_body),
    HelpPage(R.string.help_game_title, R.string.help_game_body),
    HelpPage(R.string.help_keyboard_title, R.string.help_keyboard_body),
    HelpPage(R.string.help_multi_commands_title, R.string.help_multi_commands_body),
    HelpPage(R.string.help_triggers_title, R.string.help_triggers_body),
    HelpPage(R.string.help_timers_title, R.string.help_timers_body),
    HelpPage(R.string.help_sounds_title, R.string.help_sounds_body),
    HelpPage(R.string.help_logs_title, R.string.help_logs_body),
    HelpPage(R.string.help_backup_title, R.string.help_backup_body),
    HelpPage(R.string.help_about_title, R.string.help_about_body)
)

@Composable
fun GeneralHelpDialog(startPage: Int, onDismiss: () -> Unit) {
    var pageIndex by remember {
        mutableIntStateOf(startPage.coerceIn(0, helpPages.lastIndex))
    }
    val page = helpPages[pageIndex]
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    R.string.help_page_title,
                    stringResource(page.titleRes),
                    pageIndex + 1,
                    helpPages.size
                ),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                Text(
                    text = stringResource(page.bodyRes),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(
                    text = stringResource(R.string.help_previous),
                    onClick = { pageIndex-- },
                    enabled = pageIndex > 0
                )
                AppButton(
                    text = stringResource(R.string.help_next),
                    onClick = { pageIndex++ },
                    enabled = pageIndex < helpPages.lastIndex
                )
            }
        },
        dismissButton = {
            AppButton(
                text = stringResource(R.string.action_close),
                onClick = onDismiss
            )
        }
    )
}

@Composable
fun WelcomeDialog(onExplore: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.welcome_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.welcome_message),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.welcome_show),
                onClick = onExplore
            )
        },
        dismissButton = {
            AppButton(
                text = stringResource(R.string.welcome_later),
                onClick = onDismiss
            )
        }
    )
}
