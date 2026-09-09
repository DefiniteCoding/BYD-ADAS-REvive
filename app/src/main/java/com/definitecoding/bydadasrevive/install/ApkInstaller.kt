package com.definitecoding.bydadasrevive.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface InstallOutcome {
    data object Success : InstallOutcome
    data class Failure(val message: String) : InstallOutcome
}

/** Where the APK bytes come from, and how many of them there are if that is knowable. */
class ApkSource(val label: String, val size: Long?, val open: () -> InputStream)

/**
 * Progress for the UI. [fraction] is null while the step has no measurable length,
 * which is most of them.
 */
fun interface InstallProgress {
    fun onPhase(phase: String, fraction: Float?)
}

/**
 * Installs and uninstalls through the platform PackageInstaller, as this app rather
 * than as shell. The user confirms each one in a system dialog.
 *
 * Note this cannot downgrade: setRequestDowngrade is system-only, so replacing a
 * higher version code with a lower one means uninstalling first.
 */
class ApkInstaller(private val context: Context) {

    private val installer = context.packageManager.packageInstaller

    suspend fun install(
        packageName: String?,
        source: ApkSource,
        progress: InstallProgress,
    ): InstallOutcome {
        progress.onPhase("Creating the install session", null)
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            packageName?.let { setAppPackageName(it) }
            source.size?.let { setSize(it) }
        }

        val sessionId = try {
            installer.createSession(params)
        } catch (error: Throwable) {
            return InstallOutcome.Failure("createSession failed: ${error.message}")
        }

        try {
            withContext(Dispatchers.IO) {
                installer.openSession(sessionId).use { session ->
                    session.openWrite(WRITE_NAME, 0, source.size ?: -1).use { sink ->
                        source.open().use { stream -> pump(stream, sink, source.size, progress) }
                        progress.onPhase("Flushing to disk", null)
                        session.fsync(sink)
                    }
                }
            }
        } catch (error: Throwable) {
            installer.abandonSession(sessionId)
            return InstallOutcome.Failure("writing the session failed: ${error.message}")
        }

        progress.onPhase("Waiting for Android's confirmation dialog", null)
        val outcome = awaitStatus { intentSender ->
            installer.openSession(sessionId).commit(intentSender)
        }
        if (outcome is InstallOutcome.Failure) {
            // Harmless if the platform already finished with it; that just throws.
            runCatching { installer.abandonSession(sessionId) }
        }
        return outcome
    }

    /** Copies the APK in, reporting often enough that the bar visibly moves. */
    private fun pump(
        source: InputStream,
        sink: java.io.OutputStream,
        total: Long?,
        progress: InstallProgress,
    ) {
        val buffer = ByteArray(128 * 1024)
        var written = 0L
        var lastReported = 0L
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            sink.write(buffer, 0, read)
            written += read
            if (written - lastReported >= REPORT_EVERY) {
                lastReported = written
                val megabytes = written / 1_048_576.0
                progress.onPhase(
                    "Copying the APK, %.1f MB".format(megabytes),
                    total?.takeIf { it > 0 }?.let { (written.toDouble() / it).toFloat() },
                )
            }
        }
        progress.onPhase("Copied ${written / 1024} KB", 1f)
    }

    suspend fun uninstall(packageName: String): InstallOutcome = awaitStatus { intentSender ->
        installer.uninstall(packageName, intentSender)
    }

    /**
     * Registers a one-shot receiver, hands its IntentSender to [start], and waits for
     * the platform to report a terminal status.
     *
     * Two windows, because the two waits are nothing alike: Android answers the request
     * itself in about a second, while the confirmation dialog waits on a person. Neither
     * is unbounded, since a status that never arrives used to leave the whole app
     * disabled with no way back.
     */
    private suspend fun awaitStatus(start: (IntentSender) -> Unit): InstallOutcome {
        val requestId = counter.incrementAndGet()
        val action = "${context.packageName}.INSTALL_STATUS.$requestId"
        val outcome = CompletableDeferred<InstallOutcome>()
        val dialogShown = AtomicBoolean(false)

        val receiver = object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            override fun onReceive(receiverContext: Context, intent: Intent) {
                when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        if (confirm == null) {
                            outcome.complete(
                                InstallOutcome.Failure("no confirmation dialog was supplied")
                            )
                        } else {
                            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(confirm) }
                                .onSuccess { dialogShown.set(true) }
                                .onFailure {
                                    outcome.complete(
                                        InstallOutcome.Failure(
                                            "could not show the confirmation dialog: ${it.message}"
                                        )
                                    )
                                }
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> outcome.complete(InstallOutcome.Success)
                    else -> outcome.complete(InstallOutcome.Failure(describe(status, intent)))
                }
            }
        }

        // Android 14 throws for a context-registered receiver that declares neither export
        // flag. Not exported is the right one: the status arrives through a PendingIntent
        // this app created, so the system fires it as this app, and exporting it would let
        // anything else on the car send this action with STATUS_SUCCESS and have the
        // wizard report an install that never happened.
        //
        // Branched by hand rather than through ContextCompat, which below API 33 ignores
        // the flag and registers with a synthesised signature permission instead, throwing
        // if the manifest merge did not contribute it. This line runs at the moment an
        // install is committed, on an API 31 car, and is no place to find that out.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(action))
        }
        try {
            val pending = PendingIntent.getBroadcast(
                context,
                requestId,
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

            runCatching { start(pending.intentSender) }.onFailure { error ->
                return InstallOutcome.Failure("commit failed: ${error.message}")
            }

            withTimeoutOrNull(PLATFORM_REPLY_MS) { outcome.await() }?.let { return it }

            // It may have completed in the moment the window closed.
            if (outcome.isCompleted) return outcome.await()

            if (!dialogShown.get()) {
                return InstallOutcome.Failure(
                    "Android never answered the request. Nothing was installed or removed. " +
                        "Try again."
                )
            }
            return withTimeoutOrNull(USER_REPLY_MS) { outcome.await() }
                ?: InstallOutcome.Failure(
                    "Timed out waiting for Android's confirmation dialog to be answered."
                )
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun describe(status: Int, intent: Intent): String {
        val name = when (status) {
            PackageInstaller.STATUS_FAILURE -> "FAILURE"
            PackageInstaller.STATUS_FAILURE_ABORTED -> "ABORTED (you dismissed the dialog)"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "BLOCKED"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "CONFLICT (signature or version clash)"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "INCOMPATIBLE"
            PackageInstaller.STATUS_FAILURE_INVALID -> "INVALID APK"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "STORAGE"
            else -> "status $status"
        }
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val legacy = intent.getIntExtra(EXTRA_LEGACY_STATUS, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
        return listOfNotNull(name, message, legacy?.let { "legacy code $it" }).joinToString(" - ")
    }

    private companion object {
        const val WRITE_NAME = "revive.apk"
        const val REPORT_EVERY = 512L * 1024

        /** Android answers the request itself in about a second. */
        const val PLATFORM_REPLY_MS = 45_000L

        /** The dialog waits on a person, so this one is generous. */
        const val USER_REPLY_MS = 300_000L

        /** Hidden extra, but it carries the real INSTALL_FAILED_* code when present. */
        const val EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS"

        val counter = AtomicInteger(0)
    }
}
