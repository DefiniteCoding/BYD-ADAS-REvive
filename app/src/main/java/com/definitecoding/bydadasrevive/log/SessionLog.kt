package com.definitecoding.bydadasrevive.log

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The console, persisted. Lines survive the app restart after an adb grant and across
 * launches, because the whole point is to hand a past run to the developer.
 */
class SessionLog(filesDir: File) {

    private val file = File(filesDir, "console.log")
    private val rotated = File(filesDir, "console.log.1")

    fun append(line: String): String {
        val stamped = "${STAMP.format(Date())}  $line"
        Log.i(TAG, line)
        runCatching {
            if (file.length() > MAX_BYTES) {
                rotated.delete()
                file.renameTo(rotated)
            }
            file.appendText(stamped + "\n")
        }
        return stamped
    }

    /** Oldest first, rotated file included, for seeding the in-memory tail on startup. */
    fun tail(lines: Int): List<String> {
        val all = buildList {
            if (rotated.exists()) addAll(runCatching { rotated.readLines() }.getOrDefault(emptyList()))
            if (file.exists()) addAll(runCatching { file.readLines() }.getOrDefault(emptyList()))
        }
        return all.takeLast(lines)
    }

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
