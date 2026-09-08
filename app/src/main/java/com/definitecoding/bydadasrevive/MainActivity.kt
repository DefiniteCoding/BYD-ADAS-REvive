package com.definitecoding.bydadasrevive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.definitecoding.bydadasrevive.ui.ReviveScreen
import com.definitecoding.bydadasrevive.ui.ReviveTheme
import com.definitecoding.bydadasrevive.ui.ReviveViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReviveTheme {
                val model: ReviveViewModel = viewModel()
                // Some file managers register the APK mime type, some only */*.
                val mimeTypes = remember {
                    arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")
                }
                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> if (uri != null) model.stageFromUri(uri) }

                ReviveScreen(
                    viewModel = model,
                    onPickApk = { picker.launch(mimeTypes) },
                )
            }
        }
    }
}
