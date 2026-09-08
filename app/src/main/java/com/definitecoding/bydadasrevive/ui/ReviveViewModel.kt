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
import com.definitecoding.bydadasrevive.log.RunLog
import com.definitecoding.bydadasrevive.log.RunRecord
import com.definitecoding.bydadasrevive.pkg.InstalledApp
import com.definitecoding.bydadasrevive.pkg.PackageFacts
import com.definitecoding.bydadasrevive.pkg.PackageInspector
import com.definitecoding.bydadasrevive.shell.ShellChannel
import com.definitecoding.bydadasrevive.shell.ShellState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ADAS_PACKAGE = "com.byd.adas"
const val CLUSTER_PACKAGE = "com.byd.clusterdebug"
const val CLUSTER_ACTIVITY = "com.byd.clusterdebug.MainActivity"
const val CLUSTER_COMMAND = "am start -n $CLUSTER_PACKAGE/$CLUSTER_ACTIVITY"
const val TCPIP_COMMAND = "adb tcpip 5555"
const val DOWNLOAD_DIR = "/sdcard/Download"

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
    val selectedApk: String? = null,
    val installOutput: String? = null,
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

    fun selectApk(path: String) {
        _state.value = _state.value.copy(selectedApk = path)
        log("selected $path")
    }

    /**
     * Step 3, Files app path. SAF hands back a content Uri that shell cannot open, so
     * the bytes are staged to a path shell can pass to pm. Whether shell may read
     * Android/data on this build is not guaranteed; if it cannot, pm install says so
     * and the Downloads path above is the one to use.
     */
    fun stageFromUri(uri: Uri) = launchBusy {
        val context = getApplication<Application>()
        val staged = withContext(Dispatchers.IO) {
            runCatching {
                val target = File(
                    context.getExternalFilesDir(null) ?: context.filesDir,
                    "staged.apk",
                )
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Could not open $uri")
                target.absolutePath
            }
        }
        staged.fold(
            onSuccess = { path ->
                _state.value = _state.value.copy(selectedApk = path)
                log("staged picked file to $path")
            },
            onFailure = { error -> log("staging failed: ${error.message}") },
        )
    }

    fun install() = launchBusy {
        val path = _state.value.selectedApk ?: run {
            log("no apk selected")
            return@launchBusy
        }
        // -r reinstalls in place, -d allows the downgrade this APK may be relative to
        // the version the car shipped with.
        val output = runShell("pm install -r -d '$path'")
        _state.value = _state.value.copy(installOutput = output.trim())
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
                    apkPath = snapshot.selectedApk,
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
