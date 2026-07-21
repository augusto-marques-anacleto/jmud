package br.com.augusto.jmud

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import br.com.augusto.jmud.ui.screens.AppNavigation
import br.com.augusto.jmud.ui.theme.JMudTheme
import br.com.augusto.jmud.ui.viewmodels.MudViewModel
import br.com.augusto.jmud.util.SoundPackNotifier
import br.com.augusto.jmud.util.StoragePermissions

class MainActivity : ComponentActivity() {
    private val viewModel: MudViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNeededPermissions()
        handleIntent(intent)
        setContent {
            JMudTheme {
                AppNavigation(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(SoundPackNotifier.EXTRA_SHOW_PROGRESS, false) == true) {
            viewModel.showSoundPackDialog()
        }
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        for (permission in StoragePermissions.required()) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                needed.add(permission)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 100)
        }
    }
}
