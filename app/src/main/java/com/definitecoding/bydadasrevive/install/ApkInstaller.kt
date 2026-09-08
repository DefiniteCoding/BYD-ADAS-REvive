package com.definitecoding.bydadasrevive.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

sealed interface InstallOutcome {
    data object Success : InstallOutcome
    data class Failure(val message: String) : InstallOutcome
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

    suspend fun install(packageName: String?, openApk: () -> InputStream): InstallOutcome {
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            packageName?.let { setAppPackageName(it) }
        }

        val sessionId = try {
            installer.createSession(params)
        } catch (error: Throwable) {
            return InstallOutcome.Failure("createSession failed: ${error.message}")
        }

        try {
            withContext(Dispatchers.IO) {
                installer.openSession(sessionId).use { session ->
                    // -1 because the length of a content Uri is not always known up front.
                    session.openWrite(WRITE_NAME, 0, -1).use { sink ->
                        openApk().use { source -> source.copyTo(sink) }
                        session.fsync(sink)
                    }
                }
            }
        } catch (error: Throwable) {
            installer.abandonSession(sessionId)
            return InstallOutcome.Failure("writing the session failed: ${error.message}")
        }

        return awaitStatus { intentSender ->
            installer.openSession(sessionId).commit(intentSender)
        }
    }

    suspend fun uninstall(packageName: String): InstallOutcome = awaitStatus { intentSender ->
        installer.uninstall(packageName, intentSender)
    }

    /**
     * Registers a one-shot receiver, hands its IntentSender to [start], and suspends
     * until the platform reports a terminal status. A pending-user-action status is
     * not terminal: it carries the confirmation dialog to show first.
     */
    private suspend fun awaitStatus(start: (IntentSender) -> Unit): InstallOutcome =
        suspendCancellableCoroutine { continuation ->
            val requestId = counter.incrementAndGet()
            val action = "${context.packageName}.INSTALL_STATUS.$requestId"

            val receiver = object : BroadcastReceiver() {
                @Suppress("DEPRECATION")
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            if (confirm == null) {
                                finish(InstallOutcome.Failure("no confirmation dialog was supplied"))
                            } else {
                                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(confirm) }
                                    .onFailure { finish(InstallOutcome.Failure("could not show the confirmation dialog: ${it.message}")) }
                            }
                        }
                        PackageInstaller.STATUS_SUCCESS -> finish(InstallOutcome.Success)
                        else -> finish(InstallOutcome.Failure(describe(status, intent)))
                    }
                }

                private fun finish(outcome: InstallOutcome) {
                    runCatching { context.unregisterReceiver(this) }
                    if (continuation.isActive) continuation.resume(outcome)
                }
            }

            context.registerReceiver(receiver, IntentFilter(action))
            continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }

            val pending = PendingIntent.getBroadcast(
                context,
                requestId,
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

            runCatching { start(pending.intentSender) }.onFailure { error ->
                runCatching { context.unregisterReceiver(receiver) }
                if (continuation.isActive) {
                    continuation.resume(InstallOutcome.Failure("commit failed: ${error.message}"))
                }
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

        /** Hidden extra, but it carries the real INSTALL_FAILED_* code when present. */
        const val EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS"

        val counter = AtomicInteger(0)
    }
}
