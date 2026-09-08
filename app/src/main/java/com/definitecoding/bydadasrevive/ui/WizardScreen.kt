package com.definitecoding.bydadasrevive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.definitecoding.bydadasrevive.flow.Diagnosis
import com.definitecoding.bydadasrevive.flow.StepId
import com.definitecoding.bydadasrevive.flow.Triage
import com.definitecoding.bydadasrevive.flow.UserPath
import com.definitecoding.bydadasrevive.flow.titleOf
import com.definitecoding.bydadasrevive.log.DEVELOPER_EMAIL
import com.definitecoding.bydadasrevive.pkg.PackageFacts
import com.definitecoding.bydadasrevive.shell.ShellState
import java.util.Date

private val Tap = Modifier.defaultMinSize(minHeight = TAP_TARGET_HEIGHT)

/** Next is live only when the step it belongs to has actually been satisfied. */
private fun canAdvance(state: ReviveState, shell: ShellState): Boolean = when (state.currentStep) {
    StepId.Blocked -> false
    StepId.Parked -> state.parkedConfirmed
    StepId.PathChoice -> state.path != UserPath.Unchosen
    StepId.AdbGrant -> shell is ShellState.Connected
    // Working fine has no Next: the only way on is the explicit "Reinstall anyway".
    StepId.Triage -> state.triage != Triage.Unanswered && state.triage != Triage.WorkingFine
    StepId.ChooseApk -> state.apkIsCorrectPackage && !state.downgradeWithoutRemoval
    StepId.Uninstall -> !state.removalRequired || state.adasNow?.installed == false
    StepId.Install -> state.installOutput == "Success"
    StepId.Verify -> state.adasAfter?.installed == true
    StepId.Warn224 -> state.acknowledged224
    StepId.Launch -> state.launchSucceeded
    StepId.Confirm -> false
}

@Composable
fun WizardScreen(viewModel: ReviveViewModel, onPickApk: () -> Unit, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shell by viewModel.shellState.collectAsStateWithLifecycle()
    var showConsole by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            WizardHeader(
                state = state,
                shell = shell,
                showConsole = showConsole,
                onToggleConsole = { showConsole = !showConsole },
            )
            Spacer(Modifier.heightIn(min = 12.dp))

            Row(Modifier.fillMaxSize()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.weight(if (showConsole) 1.5f else 1f).fillMaxHeight(),
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(24.dp)
                            .widthIn(max = CONTENT_MAX_WIDTH)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            titleOf(state.currentStep),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        StepBody(state, shell, viewModel, onPickApk, onClose)
                    }
                }

                if (showConsole) {
                    Spacer(Modifier.width(16.dp))
                    ConsolePane(state, viewModel, Modifier.weight(1f).fillMaxHeight())
                }
            }

            Spacer(Modifier.heightIn(min = 12.dp))
            WizardFooter(state, shell, viewModel)
        }
    }
}

@Composable
private fun WizardHeader(
    state: ReviveState,
    shell: ShellState,
    showConsole: Boolean,
    onToggleConsole: () -> Unit,
) {
    val index = state.steps.indexOf(state.currentStep).coerceAtLeast(0)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "BYD ADAS REvive",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    state.profile.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            TextButton(onClick = onToggleConsole, modifier = Tap) {
                Text(if (showConsole) "Hide details" else "Show details")
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(
                text = when (shell) {
                    is ShellState.Connected -> "shell ready"
                    ShellState.Connecting -> "connecting"
                    is ShellState.Failed -> "no shell"
                    ShellState.Disconnected -> "no shell"
                },
                color = when (shell) {
                    is ShellState.Connected -> Ok
                    ShellState.Connecting -> Warn
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (state.currentStep != StepId.Blocked) {
            Spacer(Modifier.heightIn(min = 8.dp))
            Text(
                "Step ${index + 1} of ${state.steps.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { (index + 1f) / state.steps.size.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.escalationReason?.let {
            Spacer(Modifier.heightIn(min = 8.dp))
            Mono("adb access became necessary: $it", Warn)
        }
        state.exportMessage?.let { Mono(it) }
    }
}

@Composable
private fun WizardFooter(state: ReviveState, shell: ShellState, viewModel: ReviveViewModel) {
    val index = state.steps.indexOf(state.currentStep)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = viewModel::back,
            enabled = index > 0 && !state.busy,
            modifier = Tap,
        ) {
            Text("Back")
        }
        Spacer(Modifier.width(12.dp))
        ExportRow(viewModel)
        Spacer(Modifier.weight(1f))
        if (state.currentStep != StepId.Confirm && state.currentStep != StepId.Blocked) {
            Button(
                onClick = viewModel::advance,
                enabled = canAdvance(state, shell) && !state.busy,
                modifier = Tap,
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
private fun ExportRow(viewModel: ReviveViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = viewModel::saveLogToDownloads, modifier = Tap) { Text("Save log") }
        TextButton(onClick = viewModel::shareLog, modifier = Tap) { Text("Share") }
        TextButton(onClick = viewModel::mailLog, modifier = Tap) { Text("Email dev") }
        TextButton(onClick = viewModel::copyLog, modifier = Tap) { Text("Copy") }
    }
}

@Composable
private fun StepBody(
    state: ReviveState,
    shell: ShellState,
    viewModel: ReviveViewModel,
    onPickApk: () -> Unit,
    onClose: () -> Unit,
) {
    when (state.currentStep) {
        StepId.Blocked -> BlockedStep(state, viewModel)
        StepId.Parked -> ParkedStep(state, viewModel)
        StepId.PathChoice -> PathChoiceStep(state, viewModel)
        StepId.AdbGrant -> AdbGrantStep(state, shell, viewModel)
        StepId.Triage -> TriageStep(state, viewModel)
        StepId.ChooseApk -> ChooseApkStep(state, viewModel, onPickApk)
        StepId.Uninstall -> UninstallStep(state, shell, viewModel)
        StepId.Install -> InstallStep(state, viewModel)
        StepId.Verify -> VerifyStep(state, viewModel)
        StepId.Warn224 -> Warn224Step(state, viewModel)
        StepId.Launch -> LaunchStep(state, viewModel)
        StepId.Confirm -> ConfirmStep(state, viewModel, onClose)
    }
}

@Composable
private fun BlockedStep(state: ReviveState, viewModel: ReviveViewModel) {
    val notByd = (state.diagnosis as? Diagnosis.NotCompatible)?.notByd ?: false
    Text(
        if (notByd) {
            "This does not look like a BYD head unit. Build reports " +
                "\"${state.profile.manufacturer} / ${state.profile.brand}\"."
        } else {
            "This looks like a BYD unit, but $CLUSTER_PACKAGE is not installed. That package " +
                "ships on Chinese-spec DiLink 5 and later cars and cannot be added, so there is " +
                "nothing this app can do here."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = Bad,
    )
    Text(
        "Every repair step is disabled. What still works: the package list, the adb shell " +
            "channel, and the log export, so you can send a diagnostic to $DEVELOPER_EMAIL and " +
            "help work out whether your variant can be supported.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Mono(
        buildString {
            appendLine("manufacturer  ${state.profile.manufacturer}")
            appendLine("brand         ${state.profile.brand}")
            appendLine("model         ${state.profile.model}")
            appendLine("device        ${state.profile.device}")
            appendLine("android       ${state.profile.androidRelease}")
            appendLine("$CLUSTER_PACKAGE  ${if (state.clusterDebug?.installed == true) "present" else "absent"}")
            append("$ADAS_PACKAGE  ${if (state.adasNow?.installed == true) "present" else "absent"}")
        }
    )
    Row {
        OutlinedButton(onClick = viewModel::connect, enabled = !state.busy, modifier = Tap) { Text("Try adb anyway") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = viewModel::refreshPackages, enabled = !state.busy, modifier = Tap) { Text("Re-check") }
    }
    AllPackages(state)
}

@Composable
private fun ParkedStep(state: ReviveState, viewModel: ReviveViewModel) {
    Text(
        "Everything from here on changes software on your car and opens a factory " +
            "diagnostic screen. None of it is safe to do while driving, and some of it " +
            "takes the ADAS app away for a minute or two.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .background(Warn.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.parkedConfirmed, onCheckedChange = viewModel::confirmParked)
            Spacer(Modifier.width(8.dp))
            Text(
                "The vehicle is parked, in P, in a safe place, and I am not driving",
                style = MaterialTheme.typography.bodyLarge,
                color = Warn,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Text(
        "The app cannot read your gear selector or your speed, so this is on you.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PathChoiceStep(state: ReviveState, viewModel: ReviveViewModel) {
    Text(
        "Two ways through this. You can change your mind later, and the simple path will ask " +
            "for adb only if a step turns out to need it.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ChoiceCard(
        selected = state.path == UserPath.Simple,
        title = "Simple",
        body = "No adb setup. The app installs with Android's own installer and opens cluster " +
            "debug with a normal intent. Fewer checks, and it may stop to ask for adb if your " +
            "car will not allow one of the steps without it.",
        onClick = { viewModel.choosePath(UserPath.Simple) },
    )
    ChoiceCard(
        selected = state.path == UserPath.Advanced,
        title = "Advanced",
        body = "Grants adb shell access first. Everything works, the checks are deeper " +
            "(dumpsys, pidof, removed-for-user detection) and nothing gets stuck. Requires " +
            "\"$TCPIP_COMMAND\" to have been run once since the last reboot.",
        onClick = { viewModel.choosePath(UserPath.Advanced) },
    )
}

@Composable
private fun AdbGrantStep(state: ReviveState, shell: ShellState, viewModel: ReviveViewModel) {
    when (shell) {
        is ShellState.Connected -> {
            Text("Shell access is granted.", style = MaterialTheme.typography.bodyLarge, color = Ok)
            Mono(shell.banner, Ok)
        }
        else -> {
            Text(
                "Tap Request access. The car will show an \"Allow debugging?\" dialog - tick " +
                    "Always allow so it stops asking. If nothing happens, adbd is not listening " +
                    "yet and you need to run \"$TCPIP_COMMAND\" once in your adb shell app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.host,
                    onValueChange = { viewModel.setEndpoint(it, state.port) },
                    label = { Text("host") },
                    singleLine = true,
                    modifier = Modifier.width(180.dp),
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = state.port,
                    onValueChange = { viewModel.setEndpoint(state.host, it) },
                    label = { Text("port") },
                    singleLine = true,
                    modifier = Modifier.width(120.dp),
                )
            }
            Row {
                Button(onClick = viewModel::connect, enabled = !state.busy, modifier = Tap) { Text("Request access") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { viewModel.copyToClipboard("adb tcpip", TCPIP_COMMAND) },
                    modifier = Tap,
                ) {
                    Text("Copy \"$TCPIP_COMMAND\"")
                }
            }
            if (shell is ShellState.Failed) Mono(shell.message, Bad)
        }
    }
}

@Composable
private fun TriageStep(state: ReviveState, viewModel: ReviveViewModel) {
    Text(
        "$ADAS_PACKAGE is already installed" +
            (state.adasNow?.versionName?.let { " at version $it" } ?: "") +
            ". What is it doing?",
        style = MaterialTheme.typography.bodyLarge,
    )
    ChoiceCard(
        selected = state.triage == Triage.Malfunctioning,
        title = "It is installed but misbehaving",
        body = "The BYD ADAS app is there but not working properly.",
        onClick = { viewModel.answerTriage(Triage.Malfunctioning) },
    )
    ChoiceCard(
        selected = state.triage == Triage.RevertedToStock,
        title = "It stopped working and the stock ADAS came back",
        body = "The cluster went back to the original ADAS view.",
        onClick = { viewModel.answerTriage(Triage.RevertedToStock) },
    )
    ChoiceCard(
        selected = state.triage == Triage.WorkingFine,
        title = "It is working fine",
        body = "Nothing is wrong right now.",
        onClick = { viewModel.answerTriage(Triage.WorkingFine) },
    )

    if (state.triage == Triage.WorkingFine) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Text(
            "Then there is nothing to fix. Leave it alone unless you have a reason not to.",
            style = MaterialTheme.typography.bodyLarge,
            color = Ok,
        )
        OutlinedButton(onClick = viewModel::advance, enabled = !state.busy, modifier = Tap) {
            Text("Reinstall anyway")
        }
    }
    FactsBlock(state.adasNow)
}

@Composable
private fun ChooseApkStep(state: ReviveState, viewModel: ReviveViewModel, onPickApk: () -> Unit) {
    Text(
        "Point the app at the $ADAS_PACKAGE APK. It is read and checked before anything is " +
            "installed, so you know what is in the file first.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.apkPath,
        onValueChange = viewModel::setApkPath,
        label = { Text("path on the car") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row {
        Button(onClick = viewModel::inspectApk, enabled = !state.busy, modifier = Tap) { Text("Read this file") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onPickApk, modifier = Tap) { Text("Pick with Files app") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = viewModel::listDownloadApks, enabled = !state.busy, modifier = Tap) {
            Text("List $DOWNLOAD_DIR")
        }
    }
    state.apkCandidates.forEach { path ->
        TextButton(onClick = { viewModel.setApkPath(path) }, modifier = Tap) {
            Text(path, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
        }
    }
    state.installPhase?.let { Busy(it, null) }
    state.apkError?.let { Mono(it, Bad) }
    state.apkInfo?.let { info ->
        Mono(
            buildString {
                appendLine("package  ${info.packageName}")
                appendLine("version  ${info.versionName} (code ${info.versionCode})")
                append("size     ${info.sizeBytes / 1024} KB")
            },
            if (state.apkIsCorrectPackage) Ok else Bad,
        )
        if (!state.apkIsCorrectPackage) {
            Text(
                "That file is ${info.packageName}, not $ADAS_PACKAGE. Pick a different one.",
                color = Bad,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.removalRequired) {
            Text(
                "This file is older than what is installed, and Android's installer cannot " +
                    "downgrade. The next step will remove the installed copy first.",
                color = Warn,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.downgradeWithoutRemoval) {
            Text(
                "This file is older than the copy now on the car, and this run has no removal " +
                    "step because nothing was installed when it started. Start over and the " +
                    "wizard will offer the removal it needs.",
                color = Bad,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = viewModel::restartRun, enabled = !state.busy, modifier = Tap) {
                Text("Start over")
            }
        }
    }
}

@Composable
private fun UninstallStep(state: ReviveState, shell: ShellState, viewModel: ReviveViewModel) {
    var confirming by remember { mutableStateOf<Removal?>(null) }
    val gone = state.adasNow?.installed == false

    if (gone) {
        Text("$ADAS_PACKAGE is removed. Carry on to the install.", color = Ok)
    } else {
        Text(
            if (state.removalRequired) {
                "Removal is required here: the APK you chose is older than the installed copy."
            } else {
                "Removal is recommended for a clean reinstall, but not required. You can skip it."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.removalRequired) Warn else MaterialTheme.colorScheme.onSurface,
        )
        Row {
            Button(onClick = { confirming = Removal.Platform }, enabled = !state.busy, modifier = Tap) {
                Text("Remove it")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { confirming = Removal.ForUser },
                enabled = !state.busy && shell is ShellState.Connected,
                modifier = Tap,
            ) {
                Text("Remove for this user (shell)")
            }
        }
        if (!state.removalRequired) {
            OutlinedButton(onClick = viewModel::advance, enabled = !state.busy, modifier = Tap) {
                Text("Skip removal and install over it")
            }
        }
        if (shell !is ShellState.Connected) {
            Text(
                "The shell route needs adb access and is the one that works on a system app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    state.uninstallOutput?.let {
        val failed = !gone
        Mono(it, if (failed) Bad else Ok)
    }
    FactsBlock(state.adasNow)

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
}

@Composable
private fun InstallStep(state: ReviveState, viewModel: ReviveViewModel) {
    val info = state.apkInfo
    Text(
        if (info != null) {
            "Installing ${info.packageName} ${info.versionName}. Android will ask you to confirm."
        } else {
            "Go back and choose an APK first."
        },
        style = MaterialTheme.typography.bodyLarge,
    )
    if (state.diagnosis is Diagnosis.AdasRemovedForUser) {
        Text(
            "This package is still on /system and only removed for your user, so " +
                "\"pm install-existing\" is the lighter option if you have shell.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = viewModel::installExisting, enabled = !state.busy, modifier = Tap) {
            Text("pm install-existing")
        }
    }
    if ((state.diagnosis as? Diagnosis.AdasPresent)?.disabled == true) {
        OutlinedButton(onClick = viewModel::enablePackage, enabled = !state.busy, modifier = Tap) {
            Text("pm enable (it is disabled)")
        }
    }
    Button(
        onClick = viewModel::install,
        enabled = !state.busy && info != null,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Tap,
    ) {
        Text("Install")
    }
    state.installPhase?.let { Busy(it, state.installProgress) }
    state.installOutput?.let {
        val success = it.equals("Success", ignoreCase = true)
        Mono(it, if (success) Ok else Bad)
        if (!success) {
            Text(
                "CONFLICT means the signature does not match the copy on the car, or the version " +
                    "code is lower than the installed one. For a signature clash you need an APK " +
                    "pulled from a BYD head unit; for a downgrade, go back and remove first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VerifyStep(state: ReviveState, viewModel: ReviveViewModel) {
    Text(
        "A success message is not proof a fresh copy landed. The version code and the last " +
            "update time are.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = viewModel::recheck, enabled = !state.busy, modifier = Tap) { Text("Check now") }
    FactsBlock(state.adasAfter)
    val before = state.adasBefore
    val after = state.adasAfter
    if (before != null && after != null) {
        val changed = before.lastUpdateTime != after.lastUpdateTime ||
            before.versionCode != after.versionCode ||
            before.installed != after.installed
        Text(
            if (changed) "Changed since we started: yes" else "Changed since we started: no",
            color = if (changed) Ok else Warn,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    state.shellProbe?.let { Mono(it) }
}

@Composable
private fun Warn224Step(state: ReviveState, viewModel: ReviveViewModel) {
    Text(
        "Read this before the next step.",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .background(Warn.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(16.dp)
    ) {
        Text(
            "The cluster debug window that opens next is a factory diagnostic screen. Tap ONLY " +
                "the button labelled 224. Nothing else on that screen is part of this procedure, " +
                "and other buttons there can change how your car behaves. When you have tapped " +
                "224, come back to this app.",
            style = MaterialTheme.typography.bodyLarge,
            color = Warn,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = state.acknowledged224, onCheckedChange = viewModel::acknowledge224)
        Spacer(Modifier.width(4.dp))
        Text("I understand: only the button labelled 224")
    }
}

@Composable
private fun LaunchStep(state: ReviveState, viewModel: ReviveViewModel) {
    Text(CLUSTER_COMMAND, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
    Text(
        "The window opens on top of this app. Tap 224, then come back and press Next.",
        style = MaterialTheme.typography.bodyMedium,
        color = Warn,
    )
    Row {
        Button(
            onClick = viewModel::launchClusterDebug,
            enabled = !state.busy,
            modifier = Tap,
        ) {
            Text("Open cluster debug")
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = { viewModel.copyToClipboard("cluster command", CLUSTER_COMMAND) },
            modifier = Tap,
        ) {
            Text("Copy command")
        }
    }
    state.launchOutput?.let {
        Mono(it, if (state.launchSucceeded) Ok else Bad)
    }
    if (state.launchOutput != null && !state.launchSucceeded) {
        Text(
            "It did not open, so there is nothing to tap yet. Granting adb access is the usual " +
                "fix, because this activity often refuses to start for an ordinary app.",
            style = MaterialTheme.typography.bodyMedium,
            color = Bad,
        )
        OutlinedButton(onClick = viewModel::connect, enabled = !state.busy, modifier = Tap) {
            Text("Grant adb access and retry")
        }
    }
}

@Composable
private fun ConfirmStep(state: ReviveState, viewModel: ReviveViewModel, onClose: () -> Unit) {
    var note by remember { mutableStateOf("") }
    Text(
        "Look at the instrument cluster. Did it switch to the ADAS view or the large " +
            "speedometer?",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "This app cannot see your cluster. Android does not let an ordinary app observe whether " +
            "another app is functioning, so this answer is yours, and it is what gets recorded.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.adasPidAfter?.let {
        val running = !it.contains("not running")
        Text(
            if (running) "$ADAS_PACKAGE has a running process (pid $it)"
            else "$ADAS_PACKAGE has no running process, which may be a false alarm",
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
            modifier = Tap,
        ) {
            Text("Yes, it works")
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = { viewModel.confirm(false, note.ifBlank { null }) },
            enabled = !state.busy,
            colors = ButtonDefaults.buttonColors(containerColor = Bad, contentColor = Color.Black),
            modifier = Tap,
        ) {
            Text("No, still broken")
        }
    }
    state.ack?.let {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Text(
            if (it) {
                "Recorded as working. Save or email the log if you want to share what worked."
            } else {
                "Recorded as not working. Email the log to $DEVELOPER_EMAIL and it will include " +
                    "the versions, the install output and every command that ran."
            },
            color = if (it) Ok else Bad,
            fontWeight = FontWeight.SemiBold,
        )
        Row {
            Button(
                onClick = viewModel::saveLogToDownloads,
                enabled = !state.busy,
                modifier = Tap,
            ) {
                Text("Save the log")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = viewModel::restartRun,
                enabled = !state.busy,
                modifier = Tap,
            ) {
                Text("Start over")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onClose, modifier = Tap) {
                Text("Close")
            }
        }
    }
}

// ------------------------------------------------------------------ shared pieces

private enum class Removal { ForUser, Platform }

@Composable
private fun RemovalDialog(removal: Removal, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove $ADAS_PACKAGE?") },
        text = {
            Text(
                when (removal) {
                    Removal.ForUser ->
                        "Runs \"pm uninstall --user 0 $ADAS_PACKAGE\". Any copy on /system stays " +
                            "there, but the package stops existing for this user, which frees the " +
                            "package name for an install of any version."
                    Removal.Platform ->
                        "Hands the uninstall to Android, which shows its own confirmation. On a " +
                            "system app this usually only removes updates, or fails outright."
                }
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Bad, contentColor = Color.Black),
                modifier = Tap,
            ) {
                Text("Remove")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Tap) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceCard(selected: Boolean, title: String, body: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        // The whole card is the target. A small button on a card the size of a hand is
        // the wrong thing to ask someone to hit in a car.
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Busy(phase: String, fraction: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(phase, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
    if (fraction != null) {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AllPackages(state: ReviveState) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("filter installed packages (${state.allApps.size})") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val filtered = state.allApps.filter {
        query.isBlank() || it.packageName.contains(query, true) || it.label.contains(query, true)
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
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
private fun FactsBlock(facts: PackageFacts?) {
    if (facts == null) {
        Text("not checked yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (!facts.installed) {
        Text("${facts.packageName}: not visible to PackageManager", color = Bad)
        return
    }
    Mono(
        buildString {
            appendLine("version   ${facts.versionName} (code ${facts.versionCode})")
            appendLine("enabled   ${facts.enabledSetting}")
            appendLine("system    ${facts.systemApp}")
            appendLine("path      ${facts.codePath}")
            appendLine("installer ${facts.installerPackage ?: "(none)"}")
            append("updated   ${facts.lastUpdateTime?.let { Date(it) }}")
        },
        Ok,
    )
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
        Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
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
                TextButton(onClick = viewModel::copyLog, modifier = Tap) { Text("Copy all") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(state.console) { line ->
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
