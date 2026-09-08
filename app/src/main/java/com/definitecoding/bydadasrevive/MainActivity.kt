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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.definitecoding.bydadasrevive.ui.ReviveScreen
import com.definitecoding.bydadasrevive.ui.ReviveTheme
import com.definitecoding.bydadasrevive.ui.ReviveViewModel
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

                val restarting by model.pendingRestart.collectAsStateWithLifecycle()

                if (restarting) {
                    RestartingNotice()
                    LaunchedEffect(Unit) {
                        // Long enough to read the notice, short enough not to feel stuck.
                        delay(1_500)
                        restartInPlace()
                    }
                } else {
                    ReviveScreen(
                        viewModel = model,
                        onPickApk = { picker.launch(mimeTypes) },
                    )
                }
            }
        }
    }

    /** Retries the shell handshake when coming back from the adb shell app or Settings. */
    override fun onResume() {
        super.onResume()
        model.connectIfNeeded()
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

@androidx.compose.runtime.Composable
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
