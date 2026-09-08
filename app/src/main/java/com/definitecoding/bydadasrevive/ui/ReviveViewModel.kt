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
import com.definitecoding.bydadasrevive.install.ApkInstaller
import com.definitecoding.bydadasrevive.install.InstallOutcome
import com.definitecoding.bydadasrevive.log.RunLog
import com.definitecoding.bydadasrevive.log.RunRecord
import com.definitecoding.bydadasrevive.pkg.InstalledApp
import com.definitecoding.bydadasrevive.pkg.PackageFacts
import com.definitecoding.bydadasrevive.pkg.PackageInspector
import com.definitecoding.bydadasrevive.shell.ShellChannel
import com.definitecoding.bydadasrevive.shell.ShellState
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val adasBefore: PackageFacts? = null,
    val adasAfter: PackageFacts? = null,
    val clusterDebug: PackageFacts? = null,
    val shellProbeBefore: String? = null,
    val shellProbeAfter: String? = null,
    val apkCandidates: List<String> = emptyList(),
    val apkPath: String = DEFAULT_APK_PATH,
    val pickedUri: Uri? = null,
    val installOutput: String? = null,
    val uninstallOutput: String? = null,
    val launchOutput: String? = null,
    val adasPidAfter: String? = null,
    val ack: Boolean? = null,
    val console: List<String> = emptyList(),
    val allApps: List<InstalledApp> = emptyList(),
    val runStartedAt: Long = System.currentTimeMillis(),
)

class ReviveViewModel(application: Application) : AndroidViewModel(application) {

    private val inspector = PackageInspector(application.packageManager)
    private val shell = ShellChannel(application.filesDir)
    private val runLog = RunLog(application.filesDir)
    private val installer = ApkInstaller(application)

    private val _state = MutableStateFlow(ReviveState())

    val state: StateFlow<ReviveState> = _state.asStateFlow()
    val shellState: StateFlow<ShellState> = shell.state

    init {
        refreshPackages()
    }

    fun setEndpoint(host: String, port: String) {
        _state.value = _state.value.copy(host = host, port = port)
    }

    fun connect() = launchBusy {
        log("connecting to ${_state.value.host}:${_state.value.port}")
        val result = shell.connect(_state.value.host, _state.value.port.toIntOrNull() ?: 5555)
        result.fold(
            onSuccess = { banner ->
                log("connected: $banner")
                probeAdas(before = true)
            },
            onFailure = { error -> log("connect failed: ${error.message}") },
        )
    }

    fun disconnect() {
        shell.disconnect()
        log("disconnected")
    }

    /** Step 1 and 2: what PackageManager can see, no shell required. */
    fun refreshPackages() = launchBusy {
        val adas = inspector.facts(ADAS_PACKAGE)
        val cluster = inspector.facts(CLUSTER_PACKAGE)
        val apps = withContext(Dispatchers.IO) { inspector.installedApps() }
        _state.value = _state.value.copy(
            adasBefore = _state.value.adasBefore ?: adas,
            clusterDebug = cluster,
            allApps = apps,
        )
        log("PackageManager: $ADAS_PACKAGE ${if (adas.installed) "installed" else "NOT installed"}, ${apps.size} packages visible")
    }

    /**
     * PackageManager hides packages that were uninstalled for this user, so the shell
     * view is the one that distinguishes "gone" from "still on /system, just removed".
     */
    private suspend fun probeAdas(before: Boolean) {
        if (!shell.isConnected) return
        val listing = runShell("pm list packages -u -f $ADAS_PACKAGE")
        val dump = runShell(
            "dumpsys package $ADAS_PACKAGE | grep -iE " +
                "'versionCode|versionName|codePath|enabled=|firstInstallTime|lastUpdateTime|flags='"
        )
        val pid = runShell("pidof $ADAS_PACKAGE || echo '(not running)'")
        val text = listOf(listing, dump, pid).joinToString("\n").trim()
        _state.value = if (before) {
            _state.value.copy(shellProbeBefore = text)
        } else {
            _state.value.copy(shellProbeAfter = text, adasPidAfter = pid.trim())
        }
    }

    /** Step 3, primary path: no storage permission, shell reads Downloads directly. */
    fun listDownloadApks() = launchBusy {
        val output = runShell("ls -1 $DOWNLOAD_DIR/*.apk 2>/dev/null")
        val candidates = output.lines().map { it.trim() }.filter { it.endsWith(".apk") }
        _state.value = _state.value.copy(apkCandidates = candidates)
        log("found ${candidates.size} apk(s) in $DOWNLOAD_DIR")
    }

    fun setApkPath(path: String) {
        _state.value = _state.value.copy(apkPath = path, pickedUri = null)
    }

    /** Step 3, Files app path. The picked Uri streams straight into the install session. */
    fun setPickedUri(uri: Uri) {
        _state.value = _state.value.copy(pickedUri = uri)
        log("picked $uri")
    }

    /**
     * Installs through PackageInstaller as this app, with a system confirmation dialog.
     * Note this route cannot downgrade, so a lower version code than the installed one
     * means uninstalling first.
     */
    fun install() = launchBusy {
        val context = getApplication<Application>()
        val picked = _state.value.pickedUri
        val path = _state.value.apkPath.trim()

        val source: (() -> InputStream)? = when {
            picked != null -> {
                { context.contentResolver.openInputStream(picked) ?: error("cannot open $picked") }
            }
            path.isBlank() -> null
            else -> resolvePath(path)
        }

        if (source == null) {
            _state.value = _state.value.copy(installOutput = "No readable APK. Pick one with the Files app.")
            return@launchBusy
        }

        log("installing ${picked ?: path} via PackageInstaller")
        val outcome = installer.install(ADAS_PACKAGE, source)
        val rendered = when (outcome) {
            InstallOutcome.Success -> "Success"
            is InstallOutcome.Failure -> outcome.message
        }
        log("install: $rendered")
        _state.value = _state.value.copy(installOutput = rendered)
    }

    /**
     * Scoped storage keeps this app out of another app's files in Downloads, so a raw
     * path is read directly when possible and otherwise copied in by shell, which can
     * write to this app's own external directory.
     */
    private suspend fun resolvePath(path: String): (() -> InputStream)? {
        val direct = File(path)
        if (direct.canRead()) {
            log("reading $path directly")
            return { direct.inputStream() }
        }

        if (!shell.isConnected) {
            log("cannot read $path and no shell to copy it with")
            return null
        }

        val context = getApplication<Application>()
        val staged = File(context.getExternalFilesDir(null) ?: context.filesDir, "staged.apk")
        runShell("cp '$path' '${staged.absolutePath}' && chmod 644 '${staged.absolutePath}'")
        if (!staged.canRead() || staged.length() == 0L) {
            log("shell could not copy $path into ${staged.parent}")
            return null
        }
        log("copied ${staged.length()} bytes to ${staged.absolutePath}")
        return { staged.inputStream() }
    }

    /** Step 1 option: hand the uninstall to the platform, with its own dialog. */
    fun uninstall() = launchBusy {
        log("uninstalling $ADAS_PACKAGE via PackageInstaller")
        val outcome = installer.uninstall(ADAS_PACKAGE)
        val rendered = when (outcome) {
            InstallOutcome.Success -> "Uninstalled"
            is InstallOutcome.Failure -> outcome.message
        }
        log("uninstall: $rendered")
        _state.value = _state.value.copy(uninstallOutput = rendered)
        refreshPackages()
    }

    /**
     * The route that works on a system app: it stays on /system but stops existing for
     * this user, which is what frees the package name for a fresh install.
     */
    fun uninstallForUser() = launchBusy {
        val output = runShell("pm uninstall --user 0 $ADAS_PACKAGE")
        _state.value = _state.value.copy(uninstallOutput = output.trim())
        refreshPackages()
    }

    /** Step 4: re-check after the install, both PackageManager and shell. */
    fun recheck() = launchBusy {
        val adas = inspector.facts(ADAS_PACKAGE)
        _state.value = _state.value.copy(adasAfter = adas)
        probeAdas(before = false)
        log("recheck: $ADAS_PACKAGE ${if (adas.installed) "installed" else "NOT installed"}")
    }

    /** Step 5: shell start works whether or not the activity is exported. */
    fun launchClusterDebug() = launchBusy {
        val output = runShell(CLUSTER_COMMAND)
        _state.value = _state.value.copy(launchOutput = output.trim())
    }

    /** Fallback for when no shell is available and the activity happens to be exported. */
    fun launchClusterDebugDirect() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(CLUSTER_PACKAGE, CLUSTER_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val result = runCatching { getApplication<Application>().startActivity(intent) }
        _state.value = _state.value.copy(
            launchOutput = result.fold(
                onSuccess = { "startActivity dispatched (direct intent)" },
                onFailure = { "direct intent failed: ${it.message}" },
            ),
        )
    }

    /** Step 7: your call, recorded. The app does not pretend to see the cluster. */
    fun confirm(working: Boolean, note: String?) = launchBusy {
        _state.value = _state.value.copy(ack = working)
        val snapshot = _state.value
        withContext(Dispatchers.IO) {
            runLog.append(
                RunRecord(
                    startedAt = snapshot.runStartedAt,
                    adasBefore = snapshot.adasBefore?.let(::describe) ?: "unknown",
                    apkPath = snapshot.pickedUri?.toString() ?: snapshot.apkPath,
                    installOutput = snapshot.installOutput,
                    adasAfter = snapshot.adasAfter?.let(::describe),
                    clusterLaunchOutput = snapshot.launchOutput,
                    adasPidAfter = snapshot.adasPidAfter,
                    confirmedWorking = working,
                    note = note,
                )
            )
        }
        log("recorded result: ${if (working) "working" else "not working"}")
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        log("copied $label to clipboard")
    }

    fun exportLog(): String = runLog.asText()

    private suspend fun runShell(command: String): String {
        log("$ $command")
        val result = shell.run(command)
        return result.fold(
            onSuccess = { shellResult ->
                val rendered = shellResult.output.ifBlank { "(no output)" }
                log(rendered + (shellResult.exitCode?.let { " [rc=$it]" } ?: ""))
                shellResult.output
            },
            onFailure = { error ->
                val message = "error: ${error.message}"
                log(message)
                message
            },
        )
    }

    private fun describe(facts: PackageFacts): String = if (!facts.installed) {
        "${facts.packageName} not installed"
    } else {
        "${facts.packageName} v${facts.versionName} (${facts.versionCode}), " +
            "enabled=${facts.enabledSetting}, system=${facts.systemApp}, " +
            "lastUpdate=${facts.lastUpdateTime?.let { RunRecord.TIMESTAMP.format(Date(it)) }}"
    }

    private fun log(line: String) {
        val stamped = "${CONSOLE_TIME.format(Date())}  $line"
        _state.value = _state.value.copy(
            console = (_state.value.console + stamped).takeLast(400),
        )
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

    private companion object {
        val CONSOLE_TIME = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}
