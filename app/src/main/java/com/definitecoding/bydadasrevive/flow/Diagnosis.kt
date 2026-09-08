package com.definitecoding.bydadasrevive.flow

import android.os.Build
import com.definitecoding.bydadasrevive.pkg.PackageFacts

/** Identity of the head unit, readable with no permissions and no shell. */
data class CarProfile(
    val brand: String = Build.BRAND ?: "",
    val manufacturer: String = Build.MANUFACTURER ?: "",
    val model: String = Build.MODEL ?: "",
    val device: String = Build.DEVICE ?: "",
    val androidRelease: String = Build.VERSION.RELEASE ?: "",
) {
    val looksByd: Boolean
        get() = listOf(brand, manufacturer, device).any { it.contains("byd", ignoreCase = true) }

    fun describe(): String = "$manufacturer $brand $model ($device), Android $androidRelease"
}

/** Which path the user chose when asked how much they want to set up. */
enum class UserPath { Unchosen, Simple, Advanced }

/** What the user says the car is actually doing, in the already-installed flow. */
enum class Triage { Unanswered, Malfunctioning, RevertedToStock, WorkingFine }

/**
 * The situation the app found itself in. Your flows 2 and 3 are one diagnosis whose
 * adb step is either pending or already satisfied, and likewise 4 and 5.
 */
sealed interface Diagnosis {

    /** Flow 1. No cluster debug package, so nothing here can help. */
    data class NotCompatible(val notByd: Boolean) : Diagnosis

    /** Flows 2 and 3. Cluster debug present, com.byd.sr genuinely absent. */
    data object AdasAbsent : Diagnosis

    /** Absent to PackageManager but still on /system, which only shell can tell. */
    data object AdasRemovedForUser : Diagnosis

    /** Flows 4 and 5. */
    data class AdasPresent(
        val disabled: Boolean,
        val installedVersionCode: Long?,
    ) : Diagnosis

    /** Nothing checked yet. */
    data object Unknown : Diagnosis
}

enum class StepId {
    Blocked,
    PathChoice,
    AdbGrant,
    Triage,
    Uninstall,
    ChooseApk,
    Install,
    Verify,
    Warn224,
    Launch,
    Confirm,
}

/**
 * Turns a diagnosis into the ordered list of steps the wizard walks. The adb step is
 * present only on the advanced path; the simple path escalates to it if some step
 * turns out to need shell after all.
 */
fun stepsFor(diagnosis: Diagnosis, path: UserPath, triage: Triage, shellEscalated: Boolean): List<StepId> {
    if (diagnosis is Diagnosis.NotCompatible) return listOf(StepId.Blocked)
    if (diagnosis is Diagnosis.Unknown) return listOf(StepId.PathChoice)

    val steps = mutableListOf(StepId.PathChoice)
    if (path == UserPath.Advanced || shellEscalated) steps += StepId.AdbGrant

    if (diagnosis is Diagnosis.AdasPresent) {
        steps += StepId.Triage
        // Choosing the APK first means the removal step can say whether it is merely
        // recommended or required, which depends on the two version codes.
        if (triage != Triage.Unanswered) {
            steps += StepId.ChooseApk
            steps += StepId.Uninstall
        }
    } else {
        steps += StepId.ChooseApk
    }

    if (steps.contains(StepId.ChooseApk)) {
        steps += listOf(
            StepId.Install,
            StepId.Verify,
            StepId.Warn224,
            StepId.Launch,
            StepId.Confirm,
        )
    }
    return steps
}

fun titleOf(step: StepId): String = when (step) {
    StepId.Blocked -> "This car is not supported"
    StepId.PathChoice -> "How do you want to do this?"
    StepId.AdbGrant -> "Grant adb shell access"
    StepId.Triage -> "What is the car doing right now?"
    StepId.Uninstall -> "Remove the current com.byd.sr"
    StepId.ChooseApk -> "Choose the APK"
    StepId.Install -> "Install it"
    StepId.Verify -> "Check it landed"
    StepId.Warn224 -> "Before you open cluster debug"
    StepId.Launch -> "Open cluster debug and tap 224"
    StepId.Confirm -> "Did the cluster come back?"
}

/** Reads the diagnosis off what PackageManager and, when present, shell reported. */
fun diagnose(
    clusterDebug: PackageFacts?,
    adas: PackageFacts?,
    profile: CarProfile,
    removedForUser: Boolean,
): Diagnosis {
    if (clusterDebug == null || adas == null) return Diagnosis.Unknown
    if (!clusterDebug.installed) return Diagnosis.NotCompatible(notByd = !profile.looksByd)
    if (adas.installed) {
        return Diagnosis.AdasPresent(
            disabled = adas.enabledSetting == "disabled" || adas.enabledSetting == "disabled by user",
            installedVersionCode = adas.versionCode,
        )
    }
    return if (removedForUser) Diagnosis.AdasRemovedForUser else Diagnosis.AdasAbsent
}
