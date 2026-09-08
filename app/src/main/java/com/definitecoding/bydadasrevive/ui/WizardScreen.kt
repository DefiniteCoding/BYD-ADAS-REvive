package com.definitecoding.bydadasrevive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.definitecoding.bydadasrevive.flow.shortTitleOf
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
    val snackbar = remember { SnackbarHostState() }

    // Results the user should notice even with the console hidden.
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(20.dp)) {
            WizardHeader(
                state = state,
                shell = shell,
                showConsole = showConsole,
                onToggleConsole = { showConsole = !showConsole },
                viewModel = viewModel,
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
    viewModel: ReviveViewModel,
) {
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
            LogMenu(viewModel)
            Spacer(Modifier.width(8.dp))
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
            Spacer(Modifier.heightIn(min = 10.dp))
            StepperRail(state, viewModel)
        }
        state.escalationReason?.let { reason ->
            Spacer(Modifier.heightIn(min = 10.dp))
            Banner(
                kind = StatusKind.Warning,
                text = "This step needs adb access: $reason",
                onDismiss = viewModel::dismissEscalationNotice,
            )
        }
    }
}

@Composable
private fun LogMenu(viewModel: ReviveViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, modifier = Tap) { Text("Log") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Save to Downloads") },
                onClick = { open = false; viewModel.saveLogToDownloads() },
            )
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = { open = false; viewModel.shareLog() },
            )
            DropdownMenuItem(
                text = { Text("Email the developer") },
                onClick = { open = false; viewModel.mailLog() },
            )
            DropdownMenuItem(
                text = { Text("Copy to clipboard") },
                onClick = { open = false; viewModel.copyLog() },
            )
        }
    }
}

/**
 * Named steps rather than "step 3 of 8", since a 1920px screen has room to show what
 * is coming. Tapping a completed step goes back to it.
 */
@Composable
private fun StepperRail(state: ReviveState, viewModel: ReviveViewModel) {
    val current = state.steps.indexOf(state.currentStep)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.steps.forEachIndexed { index, step ->
            val done = current >= 0 && index < current
            val active = index == current
            val colour = when {
                active -> MaterialTheme.colorScheme.primary
                done -> Ok
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(enabled = done) { viewModel.goTo(step) }
                    .background(
                        if (active) colour.copy(alpha = 0.16f) else Color.Transparent,
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (done) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "completed",
                        tint = Ok,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                } else {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colour,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    shortTitleOf(step),
                    style = MaterialTheme.typography.labelLarge,
                    color = colour,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (index < state.steps.lastIndex) {
                Text("-", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        "Pick either one. You can change your mind later, and the quick way will stop and ask " +
            "for more access only if it actually needs it.",
        style = MaterialTheme.typography.bodyLarge,
    )
    ChoiceCard(
        selected = state.path == UserPath.Simple,
        title = "Just fix it (recommended)",
        body = "Nothing to set up. Android's own installer does the work and you confirm each " +
            "step on screen.",
        onClick = { viewModel.choosePath(UserPath.Simple) },
    )
    ChoiceCard(
        selected = state.path == UserPath.Advanced,
        title = "Give the app full access first",
        body = "A little setup, then every step works and the app can check more thoroughly. " +
            "Choose this if the quick way got stuck for you before.",
        onClick = { viewModel.choosePath(UserPath.Advanced) },
    )
    TechnicalNote(
        "Just fix it   PackageInstaller sessions plus a plain startActivity, no shell.\n" +
            "Full access   an adb client on 127.0.0.1:5555, which enables dumpsys, pidof,\n" +
            "              pm uninstall --user 0 and removed-for-user detection.\n" +
            "              Needs \"$TCPIP_COMMAND\" run once since the last reboot."
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
                "Tap Request access, then look at the car's screen. It will ask \"Allow " +
                    "debugging?\" - tick Always allow so it stops asking every time.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "If nothing appears on screen, the car is not listening yet. Run this line once " +
                    "in your adb shell app and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        "Find the APK file on the car. The app opens it and tells you what is inside before " +
            "anything is installed, so there are no surprises.",
        style = MaterialTheme.typography.bodyLarge,
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
            Banner(
                kind = StatusKind.Failure,
                text = "That file is ${info.packageName}, which is a different app. You need " +
                    "the $ADAS_PACKAGE file.",
            )
            OutlinedButton(onClick = onPickApk, modifier = Tap) { Text("Pick another file") }
        }
        if (state.removalRequired) {
            Banner(
                kind = StatusKind.Warning,
                text = "This file is older than the version on the car, and Android will not " +
                    "install an older version over a newer one. The next step removes the " +
                    "current one first.",
            )
        }
        if (state.downgradeWithoutRemoval) {
            Banner(
                kind = StatusKind.Failure,
                text = "This file is older than the version now on the car. This run has no " +
                    "removal step, because nothing was installed when it started.",
            )
            OutlinedButton(onClick = viewModel::restartRun, enabled = !state.busy, modifier = Tap) {
                Text("Start over so removal is offered")
            }
        }
        TechnicalNote(
            "Read with PackageManager.getPackageArchiveInfo before any install.\n" +
                "PackageInstaller has no setRequestDowngrade for ordinary apps, so a lower\n" +
                "versionCode has to be preceded by an uninstall."
        )
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

    state.uninstallOutput?.let { output ->
        Banner(
            kind = if (gone) StatusKind.Success else StatusKind.Failure,
            text = if (gone) {
                "It is gone from the car."
            } else {
                "The car still reports it as installed."
            },
        )
        Mono(output, if (gone) Ok else Bad)
        if (!gone && shell !is ShellState.Connected) {
            OutlinedButton(onClick = viewModel::connect, enabled = !state.busy, modifier = Tap) {
                Text("Get full access and try the other route")
            }
        }
    }
    FactsBlock(state.adasNow)
    TechnicalNote(
        "Remove it            PackageInstaller.uninstall, with the platform's own dialog.\n" +
            "Remove for this user  pm uninstall --user 0 $ADAS_PACKAGE, over the shell\n" +
            "                      channel. Leaves any /system copy in place, which is what\n" +
            "                      works on a system app."
    )

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
        Banner(
            kind = StatusKind.Info,
            text = "This app was never fully deleted from the car, only hidden from your " +
                "profile. Restoring it is quicker than installing the file.",
        )
        OutlinedButton(onClick = viewModel::installExisting, enabled = !state.busy, modifier = Tap) {
            Text("Restore the hidden copy")
        }
        TechnicalNote("pm install-existing $ADAS_PACKAGE, over the shell channel.")
    }
    if ((state.diagnosis as? Diagnosis.AdasPresent)?.disabled == true) {
        Banner(kind = StatusKind.Warning, text = "The app is installed but switched off.")
        OutlinedButton(onClick = viewModel::enablePackage, enabled = !state.busy, modifier = Tap) {
            Text("Switch it back on")
        }
        TechnicalNote("pm enable $ADAS_PACKAGE, over the shell channel.")
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
    state.installOutput?.let { output ->
        val success = output.equals("Success", ignoreCase = true)
        Banner(
            kind = if (success) StatusKind.Success else StatusKind.Failure,
            text = if (success) {
                "Installed. The next step confirms it really landed."
            } else {
                "Android refused the install."
            },
        )
        Mono(output, if (success) Ok else Bad)
        if (!success) {
            val clash = output.contains("CONFLICT", ignoreCase = true)
            Text(
                if (clash) {
                    "Two things cause this. Either the file was signed by someone other than " +
                        "BYD, in which case you need one pulled off a BYD head unit, or it is " +
                        "an older version than the one on the car, which has to be removed first."
                } else {
                    "The line above is what Android reported. Send the log if it means nothing " +
                        "to you and it can be looked at."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row {
                if (state.steps.contains(StepId.Uninstall)) {
                    OutlinedButton(
                        onClick = { viewModel.goTo(StepId.Uninstall) },
                        enabled = !state.busy,
                        modifier = Tap,
                    ) {
                        Text("Go back and remove it first")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(
                    onClick = { viewModel.goTo(StepId.ChooseApk) },
                    enabled = !state.busy,
                    modifier = Tap,
                ) {
                    Text("Choose a different file")
                }
            }
        }
    }
}

@Composable
private fun VerifyStep(state: ReviveState, viewModel: ReviveViewModel) {
    Text(
        "\"Success\" on the last step is not proof by itself. What proves it is the version and " +
            "the time the app was last updated.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Button(onClick = viewModel::recheck, enabled = !state.busy, modifier = Tap) { Text("Check now") }
    FactsBlock(state.adasAfter)
    val before = state.adasBefore
    val after = state.adasAfter
    if (before != null && after != null) {
        val changed = before.lastUpdateTime != after.lastUpdateTime ||
            before.versionCode != after.versionCode ||
            before.installed != after.installed
        Banner(
            kind = if (changed) StatusKind.Success else StatusKind.Warning,
            text = if (changed) {
                "This is a different copy from the one there when you started."
            } else {
                "Nothing changed since you started, so the install may not have taken effect."
            },
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
    state.launchOutput?.let { output ->
        Banner(
            kind = if (state.launchSucceeded) StatusKind.Success else StatusKind.Failure,
            text = if (state.launchSucceeded) {
                "It opened. Tap 224 in that window, then come back."
            } else {
                "It did not open, so there is nothing to tap yet."
            },
        )
        Mono(output, if (state.launchSucceeded) Ok else Bad)
    }
    if (state.launchOutput != null && !state.launchSucceeded) {
        Text(
            "Giving the app full access is the usual fix: this screen often refuses to open " +
                "for an ordinary app.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = viewModel::connect, enabled = !state.busy, modifier = Tap) {
            Text("Get full access and try again")
        }
    }
    TechnicalNote("$CLUSTER_COMMAND over the shell channel, or an explicit startActivity.")
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

enum class StatusKind { Success, Warning, Failure, Info }

private fun iconFor(kind: StatusKind): ImageVector = when (kind) {
    StatusKind.Success -> Icons.Filled.CheckCircle
    StatusKind.Warning -> Icons.Filled.Warning
    StatusKind.Failure -> Icons.Filled.Error
    StatusKind.Info -> Icons.Filled.Info
}

@Composable
private fun colourFor(kind: StatusKind): Color = when (kind) {
    StatusKind.Success -> Ok
    StatusKind.Warning -> Warn
    StatusKind.Failure -> Bad
    StatusKind.Info -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun wordFor(kind: StatusKind): String = when (kind) {
    StatusKind.Success -> "Done"
    StatusKind.Warning -> "Careful"
    StatusKind.Failure -> "Problem"
    StatusKind.Info -> "Note"
}

/**
 * Outcome line with an icon and a leading word, so meaning does not rest on colour
 * alone. Anything raw from the car goes in a Mono block underneath.
 */
@Composable
private fun Banner(kind: StatusKind, text: String, onDismiss: (() -> Unit)? = null) {
    val colour = colourFor(kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colour.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconFor(kind), contentDescription = wordFor(kind), tint = colour)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                wordFor(kind),
                style = MaterialTheme.typography.labelLarge,
                color = colour,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colour)
        }
        if (onDismiss != null) {
            TextButton(onClick = onDismiss, modifier = Tap) {
                Icon(Icons.Filled.Close, contentDescription = "dismiss", tint = colour)
            }
        }
    }
}

/** Keeps the command names and the Android specifics out of the main reading path. */
@Composable
private fun TechnicalNote(text: String) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { open = !open }, modifier = Tap) {
            Text(if (open) "Hide the technical detail" else "What this does, technically")
        }
        if (open) Mono(text)
    }
}

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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = "Working: $phase" },
    ) {
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
