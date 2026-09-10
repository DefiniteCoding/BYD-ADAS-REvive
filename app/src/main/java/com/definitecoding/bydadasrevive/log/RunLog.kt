package com.definitecoding.bydadasrevive.log

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

data class RunRecord(
    val startedAt: Long,
    val adasBefore: String,
    val apkPath: String?,
    val installOutput: String?,
    val adasAfter: String?,
    val clusterLaunchOutput: String?,
    val adasPidAfter: String?,
    val confirmedWorking: Boolean?,
    val note: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("startedAt", startedAt)
        put("startedAtLocal", TIMESTAMP.format(Date(startedAt)))
        put("adasBefore", adasBefore)
        put("apkPath", apkPath ?: JSONObject.NULL)
        put("installOutput", installOutput ?: JSONObject.NULL)
        put("adasAfter", adasAfter ?: JSONObject.NULL)
        put("clusterLaunchOutput", clusterLaunchOutput ?: JSONObject.NULL)
        put("adasPidAfter", adasPidAfter ?: JSONObject.NULL)
        put("confirmedWorking", confirmedWorking ?: JSONObject.NULL)
        put("note", note ?: JSONObject.NULL)
    }

    companion object {
        val TIMESTAMP: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}

/**
 * Append-only history of attempts, so a run that worked can be compared against one
 * that did not, and shared with other owners.
 */
class RunLog(filesDir: File) {

    private val file = File(filesDir, "runs.jsonl")
    private val rotated = File(filesDir, "runs.jsonl.1")

    fun append(record: RunRecord) {
        if (file.length() > MAX_BYTES) {
            rotated.delete()
            file.renameTo(rotated)
        }
        file.appendText(record.toJson().toString() + "\n")
    }

    fun readAll(): List<JSONObject> = (textOf(rotated) + textOf(file))
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        .toList()

    fun asText(): String = textOf(rotated) + textOf(file)

    private fun textOf(source: File): String =
        if (!source.exists()) "" else runCatching { source.readText() }.getOrDefault("")

    fun clear() {
        file.delete()
        rotated.delete()
    }

    private companion object {
        /**
         * One record per completed attempt, so this should never be reached. It is
         * here because an append-only file with no ceiling is a file that grows for
         * as long as the app is installed.
         */
        const val MAX_BYTES = 128L * 1024
    }
}
