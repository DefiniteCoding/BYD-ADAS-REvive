package com.definitecoding.bydadasrevive.ui

import android.content.Context
import com.definitecoding.bydadasrevive.flow.StepId
import com.definitecoding.bydadasrevive.flow.Triage
import com.definitecoding.bydadasrevive.flow.UserPath

/** What a run needs to survive the process restart that follows an adb grant. */
data class SavedRun(
    val path: UserPath,
    val triage: Triage,
    val step: StepId,
    val apkPath: String,
    val acknowledged224: Boolean,
    val startedAt: Long,
)

/**
 * Disclaimer acceptance is remembered per version, so a reworded notice reappears.
 * A saved run is written only just before a deliberate restart and cleared as soon as
 * it is read back, so an unrelated launch never resumes into a stale wizard.
 */
class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("revive", Context.MODE_PRIVATE)

    var disclaimerSuppressedVersion: Int
        get() = prefs.getInt(KEY_SUPPRESSED, 0)
        set(value) = prefs.edit().putInt(KEY_SUPPRESSED, value).apply()

    var lastAcceptedAt: Long
        get() = prefs.getLong(KEY_ACCEPTED_AT, 0)
        set(value) = prefs.edit().putLong(KEY_ACCEPTED_AT, value).apply()

    val shouldShowDisclaimer: Boolean
        get() = disclaimerSuppressedVersion < DISCLAIMER_VERSION

    fun saveRun(run: SavedRun) {
        prefs.edit()
            .putString(KEY_PATH, run.path.name)
            .putString(KEY_TRIAGE, run.triage.name)
            .putString(KEY_STEP, run.step.name)
            .putString(KEY_APK, run.apkPath)
            .putBoolean(KEY_ACK224, run.acknowledged224)
            .putLong(KEY_STARTED, run.startedAt)
            .apply()
    }

    /** Returns the saved run once, then forgets it. */
    fun takeSavedRun(): SavedRun? {
        val step = prefs.getString(KEY_STEP, null) ?: return null
        val run = SavedRun(
            path = enumOrNull<UserPath>(prefs.getString(KEY_PATH, null)) ?: UserPath.Unchosen,
            triage = enumOrNull<Triage>(prefs.getString(KEY_TRIAGE, null)) ?: Triage.Unanswered,
            step = enumOrNull<StepId>(step) ?: StepId.PathChoice,
            apkPath = prefs.getString(KEY_APK, null) ?: DEFAULT_APK_PATH,
            acknowledged224 = prefs.getBoolean(KEY_ACK224, false),
            startedAt = prefs.getLong(KEY_STARTED, System.currentTimeMillis()),
        )
        clearRun()
        return run
    }

    fun clearRun() {
        prefs.edit()
            .remove(KEY_PATH)
            .remove(KEY_TRIAGE)
            .remove(KEY_STEP)
            .remove(KEY_APK)
            .remove(KEY_ACK224)
            .remove(KEY_STARTED)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private companion object {
        const val KEY_SUPPRESSED = "disclaimer_suppressed_version"
        const val KEY_ACCEPTED_AT = "disclaimer_accepted_at"
        const val KEY_PATH = "run_path"
        const val KEY_TRIAGE = "run_triage"
        const val KEY_STEP = "run_step"
        const val KEY_APK = "run_apk"
        const val KEY_ACK224 = "run_ack224"
        const val KEY_STARTED = "run_started_at"
    }
}
