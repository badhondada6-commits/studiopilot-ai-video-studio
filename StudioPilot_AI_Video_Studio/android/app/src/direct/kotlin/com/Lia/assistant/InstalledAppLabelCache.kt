package com.Lia.assistant

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

object InstalledAppLabelCache {
    private val labels = mutableMapOf<String, String>()

    @Synchronized
    fun prewarm(context: Context) {
        val packageManager = context.applicationContext.packageManager
        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            getInstalledApplicationsCompat(packageManager)
        }
        val newLabels = installedApps.associate { appInfo ->
            appInfo.packageName to packageManager.getApplicationLabel(appInfo).toString()
        }
        labels.clear()
        labels.putAll(newLabels)
    }

    @Synchronized
    fun labelFor(packageName: String): String? = labels[packageName]

    @Suppress("DEPRECATION")
    private fun getInstalledApplicationsCompat(packageManager: PackageManager): List<ApplicationInfo> =
        packageManager.getInstalledApplications(0)
}
