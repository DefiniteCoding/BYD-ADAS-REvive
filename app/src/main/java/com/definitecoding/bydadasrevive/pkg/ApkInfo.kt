package com.definitecoding.bydadasrevive.pkg

import android.content.pm.PackageManager
import java.io.File

data class ApkInfo(
    val path: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val sizeBytes: Long,
)

/**
 * Reads an APK's own manifest before installing it, so the wizard can say "this file is
 * com.byd.sr 1.0.72" instead of finding out from a failure. Needs a real file path,
 * which is why a picked Uri is staged first.
 */
fun readApk(packageManager: PackageManager, file: File): Result<ApkInfo> = runCatching {
    val info = packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        ?: error("Android could not parse this file as an APK")
    ApkInfo(
        path = file.absolutePath,
        packageName = info.packageName,
        versionName = info.versionName,
        versionCode = info.longVersionCode,
        sizeBytes = file.length(),
    )
}
