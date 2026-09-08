package com.definitecoding.bydadasrevive.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.definitecoding.bydadasrevive.flow.CarProfile
import com.definitecoding.bydadasrevive.flow.Diagnosis
import com.definitecoding.bydadasrevive.flow.StepId
import com.definitecoding.bydadasrevive.flow.Triage
import com.definitecoding.bydadasrevive.flow.UserPath
import com.definitecoding.bydadasrevive.flow.diagnose
import com.definitecoding.bydadasrevive.flow.stepsFor
import com.definitecoding.bydadasrevive.install.ApkInstaller
import com.definitecoding.bydadasrevive.install.ApkSource
import com.definitecoding.bydadasrevive.install.InstallOutcome
import com.definitecoding.bydadasrevive.log.ExportResult
import com.definitecoding.bydadasrevive.log.LogExporter
import com.definitecoding.bydadasrevive.log.RunLog
import com.definitecoding.bydadasrevive.log.RunRecord
import com.definitecoding.bydadasrevive.log.SessionLog
import com.definitecoding.bydadasrevive.pkg.ApkInfo
import com.definitecoding.bydadasrevive.pkg.InstalledApp
import com.definitecoding.bydadasrevive.pkg.PackageFacts
import com.definitecoding.bydadasrevive.pkg.PackageInspector
import com.definitecoding.bydadasrevive.pkg.readApk
import com.definitecoding.bydadasrevive.adb.ShellResult
import com.definitecoding.bydadasrevive.shell.ShellChannel
import com.definitecoding.bydadasrevive.shell.ShellState
import java.io.File
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ADAS_PACKAGE = "com.byd.sr"
const val CLUSTER_PACKAGE = "com.byd.clusterdebug"
const val CLUSTER_ACTIVITY = "com.byd.clusterdebug.MainActivity"
const val CLUSTER_COMMAND = "am start -n $CLUSTER_PACKAGE/$CLUSTER_ACTIVITY"
const val TCPIP_COMMAND = "adb tcpip 5555"
const val DOWNLOAD_DIR = "/sdcard/Download"
const val DEFAULT_APK_PATH = "$DOWNLOAD_DIR/com.byd.sr-1.0.72.apk"

data class ReviveState(
    val host: String = "127.0.0.1",
    val port: String = "5555",
    val busy: Boolean = false,
    val profile: CarProfile = CarProfile(),
    val diagnosis: Diagnosis = Diagnosis.Unknown,
    val runDiagnosis: Diagnosis = Diagnosis.Unknown,
    val path: UserPath = UserPath.Unchosen,
    val triage: Triage = Triage.Unanswered,
    val shellEscalated: Boolean = false,
    val escalationReason: String? = null,
    val currentStep: StepId = StepId.Parked,
    /** Where escalation should send the user back to once shell is granted. */
    val returnTo: StepId? = null,
    val parkedConfirmed: Boolean = false,
    val adasBefore: PackageFacts? = null,
    val adasNow: PackageFacts? = null,
    val adasAfter: PackageFacts? = null,
    val clusterDebug: PackageFacts? = null,
    val removedForUser: Boolean = false,
    val shellProbe: String? = null,
    val apkCandidates: List<String> = emptyList(),
    val apkPath: String = DEFAULT_APK_PATH,
    val pickedUri: Uri? = null,
    val apkInfo: ApkInfo? = null,
    val apkError: String? = null,
    val installOutput: String? = null,
    val installPhase: String? = null,
    val installProgress: Float? = null,
    val uninstallOutput: String? = null,
    val launchOutput: String? = null,
    val launchSucceeded: Boolean = false,
    val adasPidAfter: String? = null,
    val acknowledged224: Boolean = false,
    val ack: Boolean? = null,
    val console: List<String> = emptyList(),
    val allApps: List<InstalledApp> = emptyList(),
    val exportMessage: String? = null,
    val runStartedAt: Long = System.currentTimeMillis(),
) {
    /**
     * The step list must not change under the user, so it is derived from the diagnosis
     * frozen when the run began rather than from the live one. An incompatible car is
     * the exception: that always wins, whenever it is discovered.
     */
    val effectiveDiagnosis: Diagnosis
        get() = when {
            diagnosis is Diagnosis.NotCompatible -> diagnosis
            runDiagnosis !is Diagnosis.Unknown -> runDiagnosis
            else -> diagnosis
        }

    val steps: List<StepId> get() = stepsFor(effectiveDiagnosis, path, triage, shellEscalated)

    /** True when the APK on disk is the package this app is here to fix. */
    val apkIsCorrectPackage: Boolean get() = apkInfo?.packageName == ADAS_PACKAGE

    /** What is on the car right now, which is the only honest basis for a version compare. */
    val installedVersionCode: Long? get() = adasNow?.takeIf { it.installed }?.versionCode

    /** PackageInstaller cannot downgrade, so this decides whether removal is needed. */
    val isDowngrade: Boolean
        get() {
            val candidate = apkInfo?.versionCode ?: return false
            val installed = installedVersionCode ?: return false
            return candidate < installed
        }

    /** A downgrade this run can resolve, because it has a removal step. */
    val removalRequired: Boolean get() = isDowngrade && steps.contains(StepId.Uninstall)

    /**
     * A downgrade this run cannot resolve: the flow was pinned when nothing was
     * installed, so it has no removal step to offer. Starting over re-diagnoses.
     */
    val downgradeWithoutRemoval: Boolean get() = isDowngrade && !steps.contains(StepId.Uninstall)
}

class ReviveViewModel(application: Application) : AndroidViewModel(application) {

    private val inspector = PackageInspector(application.packageManager)
    private val shell = ShellChannel(application.filesDir)
    private val runLog = RunLog(application.filesDir)
    private val sessionLog = SessionLog(application.filesDir)
    private val installer = ApkInstaller(application)
    private val exporter = LogExporter(application)
    private val prefs = Prefs(application)

    /** Set once the car has trusted this app's adb key, so a new grant is detectable. */
    private val grantMarker = File(application.filesDir, "adb-access-granted")

    private val _state = MutableStateFlow(ReviveState(console = sessionLog.tail(400)))
    val state: StateFlow<ReviveState> = _state.asStateFlow()
    val shellState: StateFlow<ShellState> = shell.state

    private val _pendingRestart = MutableStateFlow(false)
    val pendingRestart: StateFlow<Boolean> = _pendingRestart.asStateFlow()

    /** A resumed run has just accepted the notice, so it is not asked again. */
    private var resumedRun = false

    init {
        log("--- session start on ${_state.value.profile.describe()} ---")
        prefs.takeSavedRun()?.let { saved ->
            resumedRun = true
            val landing = saved.returnTo ?: saved.step
            _state.value = _state.value.copy(
                path = saved.path,
                triage = saved.triage,
                currentStep = landing,
                returnTo = null,
                apkPath = saved.apkPath,
                acknowledged224 = saved.acknowledged224,
                parkedConfirmed = saved.parkedConfirmed,
                runStartedAt = saved.startedAt,
            )
            log("resumed the run at $landing after the restart")
        }
        refreshPackages()
    }

    // ---------------------------------------------------------------- disclaimer

    val shouldShowDisclaimer: Boolean get() = prefs.shouldShowDisclaimer && !resumedRun

    fun onDisclaimerAccepted(suppressFuture: Boolean) {
        prefs.lastAcceptedAt = System.currentTimeMillis()
        if (suppressFuture) prefs.disclaimerSuppressedVersion = DISCLAIMER_VERSION
        log("disclaimer v$DISCLAIMER_VERSION accepted${if (suppressFuture) " (suppressed for future launches)" else ""}")
        // Only now does the app go looking for shell access.
        connectIfNeeded()
    }

    fun onDisclaimerDeclined() {
        log("disclaimer declined - closing")
    }

    // ---------------------------------------------------------------- navigation

    fun choosePath(path: UserPath) {
        val state = _state.value
        _state.value = state.copy(path = path, runDiagnosis = state.diagnosis)
        log("path: ${path.name.lowercase()}, run pinned to ${state.diagnosis}")
        if (path == UserPath.Advanced) connectIfNeeded()
        advance()
    }

    fun answerTriage(triage: Triage) {
        _state.value = _state.value.copy(triage = triage)
        log("triage: ${triage.name}")
        if (triage != Triage.WorkingFine) advance()
    }

    fun confirmParked(value: Boolean) {
        _state.value = _state.value.copy(parkedConfirmed = value)
        if (value) log("user confirmed the vehicle is parked")
    }

    fun acknowledge224(value: Boolean) {
        _state.value = _state.value.copy(acknowledged224 = value)
    }

    fun advance() {
        val state = _state.value
        val index = state.steps.indexOf(state.currentStep)
        if (index < 0) {
            log("cannot move on: ${state.currentStep} is not part of this run")
            return
        }
        val next = state.steps.getOrNull(index + 1) ?: return
        _state.value = state.copy(currentStep = next)
        log("step: $next")
    }

    /** Going back invalidates whatever the later steps produced, so it is cleared. */
    fun back() {
        val state = _state.value
        val index = state.steps.indexOf(state.currentStep)
        if (index < 0) {
            log("cannot go back: ${state.currentStep} is not part of this run")
            return
        }
        if (index == 0) return
        val previous = state.steps[index - 1]
        _state.value = clearAfter(state, previous).copy(currentStep = previous)
        log("step: $previous (later results cleared)")
    }

    /** Drops every result produced by a step later than [step]. */
    private fun clearAfter(state: ReviveState, step: StepId): ReviveState {
        val order = state.steps
        fun isLater(candidate: StepId) = order.indexOf(candidate) > order.indexOf(step)
        return state.copy(
            uninstallOutput = state.uninstallOutput.takeUnless { isLater(StepId.Uninstall) },
            installOutput = state.installOutput.takeUnless { isLater(StepId.Install) },
            installPhase = null,
            installProgress = null,
            adasAfter = state.adasAfter.takeUnless { isLater(StepId.Verify) },
            acknowledged224 = state.acknowledged224 && !isLater(StepId.Warn224),
            launchOutput = state.launchOutput.takeUnless { isLater(StepId.Launch) },
            launchSucceeded = state.launchSucceeded && !isLater(StepId.Launch),
            ack = state.ack.takeUnless { isLater(StepId.Confirm) },
        )
    }

    /** Clears the wizard back to its first step, keeping the console and the log. */
    fun restartRun() {
        val state = _state.value
        _state.value = ReviveState(
            host = state.host,
            port = state.port,
            profile = state.profile,
            diagnosis = state.diagnosis,
            clusterDebug = state.clusterDebug,
            adasNow = state.adasNow,
            adasBefore = state.adasNow,
            allApps = state.allApps,
            console = state.console,
            apkPath = state.apkPath,
            parkedConfirmed = state.parkedConfirmed,
        )
        log("--- run reset ---")
        refreshPackages()
    }

    // ---------------------------------------------------------------- shell

    fun setEndpoint(host: String, port: String) {
        _state.value = _state.value.copy(host = host, port = port)
    }

    fun connectIfNeeded() {
        if (shell.isConnected) return
        connect()
    }

    /**
     * The connect attempt is itself the request for access: an untrusted key is what
     * makes adbd raise the "Allow debugging?" dialog on the car.
     */
    fun connect() = launchBusy {
        log("checking for adb shell access on ${_state.value.host}:${_state.value.port}")
        val firstGrant = !grantMarker.exists()
        if (firstGrant) log("no grant on record - watch the car screen for \"Allow debugging?\"")

        shell.connect(_state.value.host, _state.value.port.toIntOrNull() ?: 5555).fold(
            onSuccess = { banner ->
                log("connected: $banner")
                if (firstGrant) {
                    runCatching { grantMarker.createNewFile() }
                    val snapshot = _state.value
                    prefs.saveRun(
                        SavedRun(
                            path = snapshot.path,
                            triage = snapshot.triage,
                            step = snapshot.currentStep,
                            returnTo = snapshot.returnTo,
                            apkPath = snapshot.apkPath,
                            acknowledged224 = snapshot.acknowledged224,
                            parkedConfirmed = snapshot.parkedConfirmed,
                            startedAt = snapshot.runStartedAt,
                        )
                    )
                    log("access granted - restarting, and this run will be resumed at ${snapshot.currentStep}")
                    _pendingRestart.value = true
                } else {
                    probeWithShell()
                    resumeAfterGrant()
                }
            },
            onFailure = { error -> log("no shell access: ${error.message}") },
        )
    }

    /** Puts an escalated run back on the step that needed shell in the first place. */
    private fun resumeAfterGrant() {
        val target = _state.value.returnTo ?: return
        _state.value = _state.value.copy(currentStep = target, returnTo = null)
        log("back to $target now that shell is available")
    }

    fun disconnect() {
        shell.disconnect()
        log("disconnected")
    }

    /** Adds the adb step to a simple-path run that turned out to need shell after all. */
    private fun escalateToShell(reason: String) {
        if (_state.value.shellEscalated) return
        log("this step needs shell access: $reason")
        _state.value = _state.value.copy(
            shellEscalated = true,
            escalationReason = reason,
            returnTo = _state.value.currentStep,
            currentStep = StepId.AdbGrant,
        )
        connectIfNeeded()
    }

    // ---------------------------------------------------------------- detection

    fun refreshPackages() = launchBusy { refreshPackagesNow() }

    private suspend fun refreshPackagesNow() {
        val adas = inspector.facts(ADAS_PACKAGE)
        val cluster = inspector.facts(CLUSTER_PACKAGE)
        val apps = withContext(Dispatchers.IO) { inspector.installedApps() }
        val state = _state.value
        _state.value = state.copy(
            adasBefore = state.adasBefore ?: adas,
            adasNow = adas,
            clusterDebug = cluster,
            allApps = apps,
        )
        log(
            "PackageManager: $ADAS_PACKAGE ${if (adas.installed) "installed v${adas.versionName} (${adas.versionCode})" else "NOT installed"}, " +
                "$CLUSTER_PACKAGE ${if (cluster.installed) "present" else "ABSENT"}, ${apps.size} packages visible"
        )
        rediagnose()
    }

    private fun rediagnose() {
        val state = _state.value
        val diagnosis = diagnose(state.clusterDebug, state.adasNow, state.profile, state.removedForUser)
        val pinned = pinFor(state, diagnosis)
        if (diagnosis == state.diagnosis && pinned == state.runDiagnosis) return
        val step = if (diagnosis is Diagnosis.NotCompatible) StepId.Blocked else state.currentStep
        _state.value = state.copy(diagnosis = diagnosis, runDiagnosis = pinned, currentStep = step)
        // Logged after the write, since log() itself publishes state.
        if (diagnosis != state.diagnosis) log("diagnosis: $diagnosis")
        if (pinned != state.runDiagnosis) log("run pinned to $pinned")
    }

    /**
     * A run resumed after the restart is already past the path choice, so it never got
     * to pin its diagnosis. It gets pinned to the first real reading instead.
     */
    private fun pinFor(state: ReviveState, diagnosis: Diagnosis): Diagnosis = when {
        state.runDiagnosis !is Diagnosis.Unknown -> state.runDiagnosis
        state.currentStep == StepId.PathChoice -> Diagnosis.Unknown
        diagnosis is Diagnosis.Unknown -> Diagnosis.Unknown
        else -> diagnosis
    }

    /**
     * PackageManager hides a package that was uninstalled for this user, so shell is the
     * only way to tell "gone" from "still on /system, just removed".
     */
    private suspend fun probeWithShell() {
        if (!shell.isConnected) return
        val listing = runShell("pm list packages -u -f $ADAS_PACKAGE")
        val dump = runShell(
            "dumpsys package $ADAS_PACKAGE | grep -iE " +
                "'versionCode|versionName|codePath|enabled=|firstInstallTime|lastUpdateTime|flags='"
        )
        val pid = runShell("pidof $ADAS_PACKAGE || echo '(not running)'")
        val removed = listing.contains(ADAS_PACKAGE) && _state.value.adasNow?.installed == false
        _state.value = _state.value.copy(
            shellProbe = listOf(listing, dump, pid).joinToString("\n").trim(),
            adasPidAfter = pid.trim(),
            removedForUser = removed,
        )
        rediagnose()
    }

    // ---------------------------------------------------------------- apk choice

    fun listDownloadApks() = launchBusy {
        if (!shell.isConnected) {
            log("no shell, so $DOWNLOAD_DIR cannot be listed - type the path or use the Files app")
            return@launchBusy
        }
        val output = runShell("ls -1 $DOWNLOAD_DIR/*.apk 2>/dev/null")
        val candidates = output.lines().map { it.trim() }.filter { it.endsWith(".apk") }
        _state.value = _state.value.copy(apkCandidates = candidates)
        log("found ${candidates.size} apk(s) in $DOWNLOAD_DIR")
    }

    fun setApkPath(path: String) {
        val state = _state.value
        _state.value = clearAfter(state, StepId.ChooseApk)
            .copy(apkPath = path, pickedUri = null, apkInfo = null, apkError = null)
    }

    fun setPickedUri(uri: Uri) {
        val state = _state.value
        _state.value = clearAfter(state, StepId.ChooseApk)
            .copy(pickedUri = uri, apkInfo = null, apkError = null)
        log("picked $uri")
        inspectApk()
    }

    /** Reads the APK's own manifest so the wizard can name it before installing it. */
    fun inspectApk() = launchBusy {
        _state.value = _state.value.copy(apkInfo = null, apkError = null)
        val file = stageForReading() ?: return@launchBusy
        readApk(getApplication<Application>().packageManager, file).fold(
            onSuccess = { info ->
                _state.value = _state.value.copy(apkInfo = info)
                log("apk: ${info.packageName} v${info.versionName} (${info.versionCode}), ${info.sizeBytes} bytes")
                if (info.packageName != ADAS_PACKAGE) {
                    log("that is not $ADAS_PACKAGE - pick a different file")
                }
                if (_state.value.removalRequired) {
                    log("this file is older than what is installed, so removal first is required")
                }
            },
            onFailure = { error ->
                _state.value = _state.value.copy(apkError = error.message)
                log("apk unreadable: ${error.message}")
            },
        )
    }

    /**
     * Produces a real file this app can open. A picked Uri is copied in; a raw path is
     * used directly when scoped storage allows, and otherwise copied in by shell.
     */
    private suspend fun stageForReading(): File? {
        val context = getApplication<Application>()
        val staged = File(context.getExternalFilesDir(null) ?: context.filesDir, "staged.apk")
        val picked = _state.value.pickedUri

        if (picked != null) {
            _state.value = _state.value.copy(installPhase = "Reading the picked file")
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(picked)?.use { input ->
                        staged.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("could not open $picked")
                    staged
                }
            }
            _state.value = _state.value.copy(installPhase = null)
            return copied.getOrElse {
                _state.value = _state.value.copy(apkError = it.message)
                null
            }
        }

        val path = _state.value.apkPath.trim()
        if (path.isBlank()) {
            _state.value = _state.value.copy(apkError = "No path and no picked file")
            return null
        }

        val direct = File(path)
        if (direct.canRead()) return direct

        if (!shell.isConnected) {
            escalateToShell("this app cannot open $path under scoped storage")
            return null
        }

        _state.value = _state.value.copy(installPhase = "Copying the APK in with shell")
        runShell("cp '$path' '${staged.absolutePath}' && chmod 644 '${staged.absolutePath}'")
        _state.value = _state.value.copy(installPhase = null)
        if (!staged.canRead() || staged.length() == 0L) {
            _state.value = _state.value.copy(apkError = "shell could not copy $path")
            return null
        }
        log("copied ${staged.length()} bytes to ${staged.absolutePath}")
        return staged
    }

    // ---------------------------------------------------------------- install and remove

    fun install() = launchBusy {
        val info = _state.value.apkInfo ?: run {
            log("inspect the APK first")
            return@launchBusy
        }
        val file = File(info.path)
        _state.value = _state.value.copy(installOutput = null, installPhase = "Starting", installProgress = null)
        log("installing ${info.packageName} v${info.versionName} from ${info.path}")

        val source = ApkSource(info.path, file.length()) { file.inputStream() }
        val outcome = installer.install(ADAS_PACKAGE, source) { phase, fraction ->
            _state.value = _state.value.copy(installPhase = phase, installProgress = fraction)
            log(phase)
        }
        val rendered = when (outcome) {
            InstallOutcome.Success -> "Success"
            is InstallOutcome.Failure -> outcome.message
        }
        log("install finished: $rendered")
        _state.value = _state.value.copy(
            installOutput = rendered,
            installPhase = null,
            installProgress = null,
        )
        refreshPackagesNow()
    }

    fun uninstall() = launchBusy {
        log("uninstalling $ADAS_PACKAGE via PackageInstaller")
        val outcome = installer.uninstall(ADAS_PACKAGE)
        val rendered = when (outcome) {
            InstallOutcome.Success -> "Uninstalled"
            is InstallOutcome.Failure -> outcome.message
        }
        log("uninstall: $rendered")
        _state.value = _state.value.copy(uninstallOutput = rendered)
        refreshPackagesNow()
        if (outcome is InstallOutcome.Failure && _state.value.adasNow?.installed == true) {
            escalateToShell("the platform uninstall did not remove $ADAS_PACKAGE")
        }
    }

    fun uninstallForUser() = launchBusy {
        if (!shell.isConnected) {
            escalateToShell("pm uninstall --user 0 needs shell")
            return@launchBusy
        }
        val output = runShell("pm uninstall --user 0 $ADAS_PACKAGE")
        _state.value = _state.value.copy(uninstallOutput = output.trim())
        refreshPackagesNow()
    }

    /** For a package that is on /system but removed for this user. */
    fun installExisting() = launchBusy {
        if (!shell.isConnected) {
            escalateToShell("pm install-existing needs shell")
            return@launchBusy
        }
        val output = runShell("pm install-existing $ADAS_PACKAGE")
        _state.value = _state.value.copy(installOutput = output.trim())
        refreshPackagesNow()
    }

    fun enablePackage() = launchBusy {
        if (!shell.isConnected) {
            escalateToShell("pm enable needs shell")
            return@launchBusy
        }
        val output = runShell("pm enable $ADAS_PACKAGE")
        log("enable: ${output.trim()}")
        refreshPackagesNow()
    }

    // ---------------------------------------------------------------- verify and launch

    fun recheck() = launchBusy {
        val adas = inspector.facts(ADAS_PACKAGE)
        _state.value = _state.value.copy(adasAfter = adas)
        probeWithShell()
        log("recheck: $ADAS_PACKAGE ${if (adas.installed) "installed" else "NOT installed"}")
    }

    /** Shell start works whether or not the activity is exported; the intent may not. */
    fun launchClusterDebug() = launchBusy {
        if (!shell.isConnected) {
            launchClusterDebugDirect()
            return@launchBusy
        }
        val result = runShellResult(CLUSTER_COMMAND)
        // am start exits 0 even when it refuses, so the text has to be read too.
        val refused = listOf("Error", "Denial", "Exception", "does not exist")
            .any { result.output.contains(it, ignoreCase = true) }
        val ok = result.exitCode == 0 && !refused
        _state.value = _state.value.copy(
            launchOutput = result.output.trim().ifBlank { if (ok) "Started" else "no output" },
            launchSucceeded = ok,
        )
        if (!ok) log("cluster debug did not start")
    }

    fun launchClusterDebugDirect() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(CLUSTER_PACKAGE, CLUSTER_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val result = runCatching { getApplication<Application>().startActivity(intent) }
        result.fold(
            onSuccess = {
                log("cluster debug launched by intent")
                _state.value = _state.value.copy(
                    launchOutput = "Opened by direct intent",
                    launchSucceeded = true,
                )
            },
            onFailure = { error ->
                log("direct intent refused: ${error.message}")
                _state.value = _state.value.copy(
                    launchOutput = "direct intent failed: ${error.message}",
                    launchSucceeded = false,
                )
                escalateToShell("$CLUSTER_ACTIVITY will not start from an ordinary app")
            },
        )
    }

    fun confirm(working: Boolean, note: String?) = launchBusy {
        _state.value = _state.value.copy(ack = working)
        val snapshot = _state.value
        withContext(Dispatchers.IO) {
            runLog.append(
                RunRecord(
                    startedAt = snapshot.runStartedAt,
                    adasBefore = snapshot.adasBefore?.let(::describe) ?: "unknown",
                    apkPath = snapshot.apkInfo?.let { "${it.path} (${it.packageName} ${it.versionCode})" },
                    installOutput = snapshot.installOutput,
                    adasAfter = snapshot.adasAfter?.let(::describe),
                    clusterLaunchOutput = snapshot.launchOutput,
                    adasPidAfter = snapshot.adasPidAfter,
                    confirmedWorking = working,
                    note = note,
                )
            )
        }
        log("recorded result: ${if (working) "working" else "not working"}${note?.let { " - $it" } ?: ""}")
    }

    // ---------------------------------------------------------------- log export

    private fun exportText(): String = buildString {
        val state = _state.value
        appendLine("BYD ADAS REvive log")
        appendLine("exported: ${SessionLog.STAMP.format(Date())}")
        appendLine("device: ${state.profile.describe()}")
        appendLine("diagnosis: ${state.diagnosis}")
        appendLine("path: ${state.path}, triage: ${state.triage}, escalated: ${state.shellEscalated}")
        appendLine("disclaimer accepted at: ${prefs.lastAcceptedAt.takeIf { it > 0 }?.let { Date(it) } ?: "not recorded"}")
        appendLine()
        appendLine("=== run history (runs.jsonl) ===")
        appendLine(runLog.asText().ifBlank { "(no completed runs)" })
        appendLine("=== console ===")
        append(sessionLog.fullText())
    }

    fun saveLogToDownloads() = launchBusy {
        report(withContext(Dispatchers.IO) { exporter.saveToDownloads(exportText()) })
    }

    fun shareLog() = launchBusy {
        report(exporter.share(exportText()))
    }

    fun mailLog() = launchBusy {
        report(exporter.mail(exportText()))
    }

    fun copyLog() {
        copyToClipboard("REvive log", exportText())
    }

    private fun report(result: ExportResult) {
        val message = when (result) {
            is ExportResult.Saved -> "Saved to ${result.location}"
            is ExportResult.Handed -> "Handed to ${result.how}"
            is ExportResult.Failed -> result.message
        }
        log("export: $message")
        _state.value = _state.value.copy(exportMessage = message)
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        log("copied $label to clipboard")
    }

    // ---------------------------------------------------------------- plumbing

    private suspend fun runShell(command: String): String = runShellResult(command).output

    private suspend fun runShellResult(command: String): ShellResult {
        log("$ $command")
        return shell.run(command).fold(
            onSuccess = { result ->
                log(result.output.ifBlank { "(no output)" } + (result.exitCode?.let { " [rc=$it]" } ?: ""))
                result
            },
            onFailure = { error ->
                val message = "error: ${error.message}"
                log(message)
                ShellResult(message, null)
            },
        )
    }

    private fun describe(facts: PackageFacts): String = if (!facts.installed) {
        "${facts.packageName} not installed"
    } else {
        "${facts.packageName} v${facts.versionName} (${facts.versionCode}), " +
            "enabled=${facts.enabledSetting}, system=${facts.systemApp}, " +
            "lastUpdate=${facts.lastUpdateTime?.let { Date(it) }}"
    }

    private fun log(line: String) {
        val stamped = sessionLog.append(line)
        _state.value = _state.value.copy(console = (_state.value.console + stamped).takeLast(400))
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                block()
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }
}
