package com.definitecoding.bydadasrevive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.definitecoding.bydadasrevive.pkg.PackageFacts
import com.definitecoding.bydadasrevive.shell.ShellState
import java.util.Date

@Composable
fun ReviveScreen(
    viewModel: ReviveViewModel,
    onPickApk: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shell by viewModel.shellState.collectAsStateWithLifecycle()

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(16.dp)) {
            Column(
                modifier = Modifier
                    .weight(1.45f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Header(state, shell)
                ShellCard(state, shell, viewModel)
                AdasStatusCard(state, viewModel)
                ReinstallCard(state, viewModel, onPickApk)
                RecheckCard(state, viewModel)
                ClusterCard(state, viewModel)
                ConfirmCard(state, viewModel)
                Spacer(Modifier.heightIn(min = 8.dp))
            }
            Spacer(Modifier.width(16.dp))
            ConsolePane(state, viewModel, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun Header(state: ReviveState, shell: ShellState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "BYD ADAS REvive",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "DiLink 5 / Android 12 - reinstall $ADAS_PACKAGE and bring the cluster back",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        StatusPill(
            text = when (shell) {
                is ShellState.Connected -> "shell ready"
                ShellState.Connecting -> "connecting"
                is ShellState.Failed -> "shell failed"
                ShellState.Disconnected -> "no shell"
            },
            color = when (shell) {
                is ShellState.Connected -> Ok
                ShellState.Connecting -> Warn
                is ShellState.Failed -> Bad
                ShellState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        "$number",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun ShellCard(state: ReviveState, shell: ShellState, viewModel: ReviveViewModel) {
    StepCard(
        number = 0,
        title = "adb shell channel",
        subtitle = "Needs \"$TCPIP_COMMAND\" to have been run once since the last reboot",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.host,
                onValueChange = { viewModel.setEndpoint(it, state.port) },
                label = { Text("host") },
                singleLine = true,
                modifier = Modifier.width(200.dp),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = state.port,
                onValueChange = { viewModel.setEndpoint(state.host, it) },
                label = { Text("port") },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = viewModel::connect, enabled = !state.busy) { Text("Connect") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = viewModel::disconnect) { Text("Disconnect") }
        }

        when (shell) {
            is ShellState.Connected -> Mono(shell.banner, Ok)
            is ShellState.Failed -> {
                Mono(shell.message, Bad)
                Text(
                    "If nothing is listening: run \"$TCPIP_COMMAND\" in your existing adb shell app, " +
                        "then Connect again. The first connect makes the car show an " +
                        "\"Allow debugging?\" prompt - tick Always allow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { viewModel.copyToClipboard("adb tcpip", TCPIP_COMMAND) }) {
                    Text("Copy \"$TCPIP_COMMAND\"")
                }
            }
            else -> Unit
        }
    }
}

private enum class Removal { ForUser, Platform }

@Composable
private fun RemovalDialog(removal: Removal, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove $ADAS_PACKAGE?") },
        text = {
            Text(
                when (removal) {
                    Removal.ForUser ->
                        "Runs \"pm uninstall --user 0 $ADAS_PACKAGE\". The APK stays on /system, " +
                            "but the package stops existing for this user. This is the route that " +
                            "works on a system app, and it frees the package name for a fresh " +
                            "install of any version."
                    Removal.Platform ->
                        "Hands the uninstall to Android, which will show its own confirmation. " +
                            "On a system app this usually only removes updates, or fails outright."
                }
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Bad, contentColor = Color.Black),
            ) {
                Text("Remove")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AdasStatusCard(state: ReviveState, viewModel: ReviveViewModel) {
    var showAll by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf<Removal?>(null) }

    StepCard(
        number = 1,
        title = "Is $ADAS_PACKAGE installed?",
        subtitle = "PackageManager view, plus the shell view that also sees uninstalled-for-user",
    ) {
        FactsBlock(state.adasBefore)
        state.clusterDebug?.let {
            Text(
                "$CLUSTER_PACKAGE: " + if (it.installed) "present" else "NOT present",
                color = if (it.installed) Ok else Bad,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.shellProbeBefore?.let { Mono(it) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = viewModel::refreshPackages, enabled = !state.busy) {
                Text("Refresh")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "Hide all packages" else "All packages (${state.allApps.size})")
            }
        }

        // Only offered when the package is actually there, since a reinstall in place
        // cannot downgrade and removing it first is the way around that.
        if (state.adasBefore?.installed == true) {
            Text(
                "Installed. Reinstall in place from step 2, or remove it first if the APK you " +
                    "have carries a lower version code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                OutlinedButton(
                    onClick = { confirming = Removal.ForUser },
                    enabled = !state.busy,
                ) {
                    Text("Uninstall for this user (shell)")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { confirming = Removal.Platform },
                    enabled = !state.busy,
                ) {
                    Text("Uninstall (system dialog)")
                }
            }
            state.uninstallOutput?.let {
                val failed = it.contains("Failure", true) || it.contains("FAILED", true) ||
                    it.contains("FAILURE", true)
                Mono(it, if (failed) Bad else Ok)
            }
        }

        confirming?.let { removal ->
            RemovalDialog(
                removal = removal,
                onDismiss = { confirming = null },
                onConfirm = {
                    confirming = null
                    when (removal) {
                        Removal.ForUser -> viewModel.uninstallForUser()
                        Removal.Platform -> viewModel.uninstall()
                    }
                },
            )
        }

        if (showAll) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("filter") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val filtered = state.allApps.filter {
                query.isBlank() ||
                    it.packageName.contains(query, true) ||
                    it.label.contains(query, true)
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("${app.label}  ${app.versionName ?: ""}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.packageName + if (app.systemApp) "  [system]" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReinstallCard(state: ReviveState, viewModel: ReviveViewModel, onPickApk: () -> Unit) {
    StepCard(
        number = 2,
        title = "Install the APK",
        subtitle = "PackageInstaller, as this app. Android shows its own confirmation dialog.",
    ) {
        OutlinedTextField(
            value = state.apkPath,
            onValueChange = viewModel::setApkPath,
            label = { Text("apk path on the car") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row {
            OutlinedButton(onClick = viewModel::listDownloadApks, enabled = !state.busy) {
                Text("Browse $DOWNLOAD_DIR")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onPickApk) { Text("Pick with Files app") }
        }

        if (state.apkCandidates.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.apkCandidates.forEach { path ->
                    val selected = path == state.apkPath && state.pickedUri == null
                    TextButton(onClick = { viewModel.setApkPath(path) }) {
                        Text(
                            (if (selected) "* " else "  ") + path,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        state.pickedUri?.let {
            Text(
                "using the picked file: $it",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Button(
            onClick = viewModel::install,
            enabled = !state.busy && (state.pickedUri != null || state.apkPath.isNotBlank()),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("Install")
        }

        // An install can sit for a while on the shell copy or on the system dialog, so
        // say what it is doing rather than leaving a dead button behind.
        state.installPhase?.let { phase ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    phase,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val fraction = state.installProgress
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        state.installOutput?.let {
            val success = it.equals("Success", ignoreCase = true)
            Mono(it, if (success) Ok else Bad)
            if (!success) {
                Text(
                    "CONFLICT means the APK signature does not match the copy on the car, or its " +
                        "version code is lower than the installed one. PackageInstaller cannot " +
                        "downgrade, so for a lower version remove the package in step 1 first, " +
                        "then install.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecheckCard(state: ReviveState, viewModel: ReviveViewModel) {
    StepCard(
        number = 3,
        title = "Check $ADAS_PACKAGE again",
        subtitle = "Version and lastUpdateTime are the evidence a fresh copy actually landed",
    ) {
        Button(onClick = viewModel::recheck, enabled = !state.busy) { Text("Re-check") }
        FactsBlock(state.adasAfter)

        val before = state.adasBefore
        val after = state.adasAfter
        if (before != null && after != null) {
            val changed = before.lastUpdateTime != after.lastUpdateTime ||
                before.versionCode != after.versionCode ||
                before.installed != after.installed
            Text(
                if (changed) "Changed since step 1: yes" else "Changed since step 1: no",
                color = if (changed) Ok else Warn,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.shellProbeAfter?.let { Mono(it) }
    }
}

@Composable
private fun ClusterCard(state: ReviveState, viewModel: ReviveViewModel) {
    StepCard(
        number = 4,
        title = "Open cluster debug, then tap 224",
        subtitle = CLUSTER_COMMAND,
    ) {
        Row {
            Button(
                onClick = viewModel::launchClusterDebug,
                enabled = !state.busy,
            ) {
                Text("Run via shell")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = viewModel::launchClusterDebugDirect) { Text("Direct intent") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { viewModel.copyToClipboard("cluster command", CLUSTER_COMMAND) }) {
                Text("Copy command")
            }
        }
        Text(
            "The cluster debug window opens on top of this app. Tap the button labelled 224, " +
                "then come back here.",
            style = MaterialTheme.typography.bodyMedium,
            color = Warn,
        )
        state.launchOutput?.let {
            val failed = it.contains("Error", true) || it.contains("Denial", true) || it.contains("failed", true)
            Mono(it, if (failed) Bad else Ok)
        }
    }
}

@Composable
private fun ConfirmCard(state: ReviveState, viewModel: ReviveViewModel) {
    var note by remember { mutableStateOf("") }

    StepCard(
        number = 5,
        title = "Did the cluster come back?",
        subtitle = "Your call: the cluster switching to the ADAS view or the large speedometer",
    ) {
        state.adasPidAfter?.let {
            val running = !it.contains("not running")
            Text(
                if (running) "$ADAS_PACKAGE process is alive (pid $it)" else "$ADAS_PACKAGE has no running process",
                color = if (running) Ok else Warn,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("note for this run (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            Button(
                onClick = { viewModel.confirm(true, note.ifBlank { null }) },
                enabled = !state.busy,
                colors = ButtonDefaults.buttonColors(containerColor = Ok, contentColor = Color.Black),
            ) {
                Text("Yes, working")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { viewModel.confirm(false, note.ifBlank { null }) },
                enabled = !state.busy,
                colors = ButtonDefaults.buttonColors(containerColor = Bad, contentColor = Color.Black),
            ) {
                Text("No, still broken")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { viewModel.copyToClipboard("run log", viewModel.exportLog()) }) {
                Text("Copy run history")
            }
        }
        state.ack?.let {
            Text(
                if (it) {
                    "Recorded: $ADAS_PACKAGE confirmed working after this run."
                } else {
                    "Recorded: still not working. The run history has the install output and versions."
                },
                color = if (it) Ok else Bad,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FactsBlock(facts: PackageFacts?) {
    if (facts == null) {
        Text("not checked yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (!facts.installed) {
        Text(
            "${facts.packageName}: not visible to PackageManager",
            color = Bad,
            fontWeight = FontWeight.SemiBold,
        )
        return
    }
    Column {
        Text("${facts.packageName}: installed", color = Ok, fontWeight = FontWeight.SemiBold)
        Mono(
            buildString {
                appendLine("version   ${facts.versionName} (code ${facts.versionCode})")
                appendLine("enabled   ${facts.enabledSetting}")
                appendLine("system    ${facts.systemApp}")
                appendLine("path      ${facts.codePath}")
                appendLine("installer ${facts.installerPackage ?: "(none)"}")
                appendLine("first     ${facts.firstInstallTime?.let { Date(it) }}")
                append("updated   ${facts.lastUpdateTime?.let { Date(it) }}")
            }
        )
    }
}

@Composable
private fun Mono(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
private fun ConsolePane(state: ReviveState, viewModel: ReviveViewModel, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.console.size) {
        if (state.console.isNotEmpty()) listState.animateScrollToItem(state.console.size - 1)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Console", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.copyToClipboard("console", state.console.joinToString("\n")) }) {
                    Text("Copy")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(state.console) { line ->
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
