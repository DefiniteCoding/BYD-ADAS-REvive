package com.definitecoding.bydadasrevive.pkg

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

data class PackageFacts(
    val packageName: String,
    val installed: Boolean,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val enabled: Boolean? = null,
    val enabledSetting: String? = null,
    val systemApp: Boolean? = null,
    val codePath: String? = null,
    val firstInstallTime: Long? = null,
    val lastUpdateTime: Long? = null,
    val installerPackage: String? = null,
)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val systemApp: Boolean,
)

/** Everything a normal, unprivileged app can learn about a package on Android 12. */
class PackageInspector(private val packageManager: PackageManager) {

    fun facts(packageName: String): PackageFacts {
        val info: PackageInfo = try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (missing: PackageManager.NameNotFoundException) {
            return PackageFacts(packageName, installed = false)
        }

        val app = info.applicationInfo
        return PackageFacts(
            packageName = packageName,
            installed = true,
            versionName = info.versionName,
            versionCode = info.longVersionCode,
            enabled = app?.enabled,
            enabledSetting = describeEnabledSetting(packageName),
            systemApp = app?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 },
            codePath = app?.sourceDir,
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
            installerPackage = installerOf(packageName),
        )
    }

    fun installedApps(): List<InstalledApp> =
        packageManager.getInstalledApplications(0)
            .map { app ->
                InstalledApp(
                    packageName = app.packageName,
                    label = packageManager.getApplicationLabel(app).toString(),
                    versionName = runCatching {
                        packageManager.getPackageInfo(app.packageName, 0).versionName
                    }.getOrNull(),
                    systemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .sortedBy { it.label.lowercase() }

    private fun describeEnabledSetting(packageName: String): String =
        when (packageManager.getApplicationEnabledSetting(packageName)) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "default"
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "enabled"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "disabled"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> "disabled by user"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> "disabled until used"
            else -> "unknown"
        }

    private fun installerOf(packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }
    }.getOrNull()
}
