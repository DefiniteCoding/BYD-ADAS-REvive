package com.definitecoding.bydadasrevive

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.definitecoding.bydadasrevive.ui.DisclaimerDialog
import com.definitecoding.bydadasrevive.ui.ReviveTheme
import com.definitecoding.bydadasrevive.ui.ReviveViewModel
import com.definitecoding.bydadasrevive.ui.WizardScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val model: ReviveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReviveTheme {
                // Some file managers register the APK mime type, some only */*.
                val mimeTypes = remember {
                    arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")
                }
                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> if (uri != null) model.setPickedUri(uri) }

                var showDisclaimer by remember { mutableStateOf(model.shouldShowDisclaimer) }
                val restarting by model.pendingRestart.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    if (!showDisclaimer) model.connectIfNeeded()
                }

                when {
                    restarting -> {
                        RestartingNotice()
                        LaunchedEffect(Unit) {
                            // Long enough to read, short enough not to feel stuck.
                            delay(1_500)
                            restartInPlace()
                        }
                    }
                    else -> {
                        WizardScreen(
                            viewModel = model,
                            onPickApk = { picker.launch(mimeTypes) },
                            onClose = { finish() },
                        )
                        if (showDisclaimer) {
                            DisclaimerDialog(
                                onAccept = { suppress ->
                                    showDisclaimer = false
                                    model.onDisclaimerAccepted(suppress)
                                },
                                onDecline = {
                                    model.onDisclaimerDeclined()
                                    finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Retries the handshake when coming back from the adb shell app or Settings. Nothing
     * touches the car until the notice has been answered, which is what the guard is for:
     * the view model reports the notice as no longer needed once it has been accepted or
     * suppressed, and never goes back to needing it within a process.
     */
    override fun onResume() {
        super.onResume()
        if (!model.shouldShowDisclaimer) model.connectIfNeeded()
    }

    /**
     * A fresh process, because the adb socket and every package check belong to the old
     * one. The key is trusted by now, so the next startup reconnects without a prompt.
     */
    private fun restartInPlace() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
        Runtime.getRuntime().exit(0)
    }
}

@Composable
private fun RestartingNotice() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            "adb shell access granted",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Restarting so every check runs with shell",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
