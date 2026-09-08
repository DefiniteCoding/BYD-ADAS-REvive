package com.definitecoding.bydadasrevive.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DEVELOPER_EMAIL = "definitecoding@gmail.com"

sealed interface ExportResult {
    data class Saved(val location: String) : ExportResult
    data class Handed(val how: String) : ExportResult
    data class Failed(val message: String) : ExportResult
}

/**
 * Three ways off the car, because DiLink may have none of the usual ones. Saving to
 * Downloads always works; the share sheet and a mail composer are attempted and fall
 * back to the clipboard rather than throwing.
 */
class LogExporter(private val context: Context) {

    private fun fileName() =
        "revive-log-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"

    /** MediaStore lets an app create its own file in Downloads with no permission. */
    fun saveToDownloads(text: String): ExportResult {
        val name = fileName()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return runCatching {
                val target = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    name,
                )
                target.writeText(text)
                ExportResult.Saved(target.absolutePath) as ExportResult
            }.getOrElse { ExportResult.Failed(it.message ?: "could not write to Downloads") }
        }

        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Downloads rejected the new file")
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                ?: error("could not open the new file for writing")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            ExportResult.Saved("Downloads/$name") as ExportResult
        }.getOrElse { ExportResult.Failed(it.message ?: "could not write to Downloads") }
    }

    /** Share sheet with the log attached, via a FileProvider uri. */
    fun share(text: String): ExportResult {
        val staged = runCatching {
            val dir = File(context.filesDir, "export").apply { mkdirs() }
            File(dir, fileName()).apply { writeText(text) }
        }.getOrElse { return ExportResult.Failed(it.message ?: "could not stage the log") }

        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", staged)
        }.getOrElse { return ExportResult.Failed(it.message ?: "FileProvider refused the file") }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "BYD ADAS REvive log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return launch(
            Intent.createChooser(intent, "Send the log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "the share sheet",
            text,
        )
    }

    /** Mail composer with the log inline, since an attachment needs a real client. */
    fun mail(text: String): ExportResult {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$DEVELOPER_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "BYD ADAS REvive log")
            putExtra(Intent.EXTRA_TEXT, text.takeLast(MAIL_BODY_LIMIT))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launch(intent, "a mail composer", text)
    }

    private fun launch(intent: Intent, what: String, text: String): ExportResult =
        runCatching {
            context.startActivity(intent)
            ExportResult.Handed(what) as ExportResult
        }.getOrElse {
            copyToClipboard(text)
            ExportResult.Failed(
                "This car has nothing to open $what with. The log and $DEVELOPER_EMAIL " +
                    "are on the clipboard instead, and Save to Downloads still works."
            )
        }

    private fun copyToClipboard(text: String) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("REvive log", "$DEVELOPER_EMAIL\n\n$text"))
        }
    }

    private companion object {
        const val MAIL_BODY_LIMIT = 60_000
    }
}
