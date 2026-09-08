package com.definitecoding.bydadasrevive.log

import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The console, persisted. Lines survive the app restart after an adb grant and across
 * launches, because the whole point is to hand a past run to the developer.
 */
class SessionLog(filesDir: File, scope: CoroutineScope) {

    private val file = File(filesDir, "console.log")
    private val rotated = File(filesDir, "console.log.1")

    /**
     * Lines are handed to a single writer rather than written inline: a busy step logs
     * dozens of them, and an open-append-close each time is not something to do on the
     * thread drawing the screen. One consumer keeps them in order.
     */
    private val pending = Channel<String>(Channel.UNLIMITED)

    /**
     * Where the file stood before this session wrote anything. The tail stops here, so
     * a line this session has already shown cannot come back from disk as well.
     */
    private val baseline = runCatching { file.length() }.getOrDefault(0L)

    init {
        scope.launch(Dispatchers.IO) {
            for (line in pending) writeLine(line)
        }
    }

    fun append(line: String): String {
        val stamped = "${STAMP.format(Date())}  $line"
        Log.i(TAG, line)
        pending.trySend(stamped)
        return stamped
    }

    private fun writeLine(stamped: String) {
        runCatching {
            if (file.length() > MAX_BYTES) {
                rotated.delete()
                file.renameTo(rotated)
            }
            file.appendText(stamped + "\n")
        }
    }

    /** Earlier sessions only, oldest first, rotated file included. */
    fun tail(lines: Int): List<String> {
        val all = buildList {
            addAll(linesOf(rotated, Long.MAX_VALUE))
            addAll(linesOf(file, baseline))
        }
        return all.takeLast(lines)
    }

    private fun linesOf(source: File, limit: Long): List<String> = runCatching {
        if (!source.exists() || limit <= 0) return emptyList()
        val bytes = source.readBytes()
        val cut = if (limit >= bytes.size) bytes else bytes.copyOf(limit.toInt())
        String(cut).split('\n').filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    fun fullText(): String = buildString {
        if (rotated.exists()) append(runCatching { rotated.readText() }.getOrDefault(""))
        if (file.exists()) append(runCatching { file.readText() }.getOrDefault(""))
    }

    fun clear() {
        file.delete()
        rotated.delete()
    }

    companion object {
        const val TAG = "REvive"
        private const val MAX_BYTES = 512L * 1024
        val STAMP: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}
