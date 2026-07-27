package com.joeshannon.joetv.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

// -----------------------------------------------------------------------------
// JoeTV App Manager
//
// Keeps the launcher synchronized with the applications installed on the device.
//
// Responsibilities:
// • Discover apps that can be opened from an Android TV launcher
// • Listen for package installation, removal, and update events
// • Exclude JoeTV and the disabled stock TV launcher
// • Add Android TV Settings when it is not returned as a launcher app
// • Generate short initials for apps without usable icons
// -----------------------------------------------------------------------------

/**
 * Keeps JoeTV's installed-app list synchronized with Android.
 *
 * The list refreshes:
 * - when JoeTV starts,
 * - whenever an app is installed,
 * - whenever an app is removed,
 * - whenever an app is updated or enabled/disabled,
 * - and whenever JoeTV returns to the foreground.
 *
 * @param context Any valid Android context. The application context is stored
 * internally to avoid accidentally retaining an Activity.
 */
class AppManager(
    context: Context
) {
    // Use the application context so this manager does not leak an Activity.
    private val appContext = context.applicationContext

    // Android's package manager provides information about installed apps.
    private val packageManager = appContext.packageManager

    // Backing state containing the apps currently available to JoeTV.
    private val _apps = mutableStateOf<List<JoeTvApp>>(emptyList())

    /**
     * Read-only Compose state observed by the JoeTV home screen.
     */
    val apps: State<List<JoeTvApp>> = _apps

    // Prevents the package receiver from being registered more than once.
    private var receiverRegistered = false

    /**
     * Receives Android package-change broadcasts and refreshes the app list.
     */
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_CHANGED -> refresh()
            }
        }
    }

    /**
     * Starts package monitoring and performs the initial installed-app scan.
     *
     * Calling this function again while the manager is already running has no
     * effect.
     */
    fun start() {
        if (receiverRegistered) return

        // Listen only for changes involving installed Android packages.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }

        // Android 13 and newer require an explicit receiver export setting.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                packageChangeReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(
                packageChangeReceiver,
                filter
            )
        }

        receiverRegistered = true
        refresh()
    }

    /**
     * Stops package monitoring and safely unregisters the broadcast receiver.
     */
    fun stop() {
        if (!receiverRegistered) return

        // runCatching prevents an unregister error from crashing the launcher.
        runCatching {
            appContext.unregisterReceiver(packageChangeReceiver)
        }

        receiverRegistered = false
    }

    /**
     * Rebuilds the list of applications available to JoeTV.
     */
    fun refresh() {
        _apps.value = scanInstalledApps()
    }

    /**
     * Searches Android for applications that advertise either a TV launcher
     * activity or a standard launcher activity.
     */
    private fun scanInstalledApps(): List<JoeTvApp> {
        // Some apps use the Android TV launcher category, while others only
        // expose a standard Android launcher activity. JoeTV checks both.
        val launcherIntents = listOf(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            },
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        )

        val discoveredApps = launcherIntents
            .flatMap { intent ->
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.MATCH_ALL
                )
            }
            .mapNotNull { resolveInfo ->
                val applicationInfo =
                    resolveInfo.activityInfo?.applicationInfo
                        ?: return@mapNotNull null

                val packageName = applicationInfo.packageName

                // JoeTV should not display itself as a launchable app.
                // The disabled stock Android TV launcher is also excluded.
                if (
                    packageName == appContext.packageName ||
                    packageName == "com.google.android.tvlauncher"
                ) {
                    return@mapNotNull null
                }

                // Prefer the user-facing application label and fall back to
                // the package name when no label is available.
                val appName = packageManager
                    .getApplicationLabel(applicationInfo)
                    .toString()
                    .ifBlank { packageName }

                JoeTvApp(
                    name = appName,
                    packageName = packageName,
                    description = "Open $appName",
                    initials = appInitials(appName)
                )
            }
            // An app may appear in both launcher queries, so duplicates are
            // removed using the package name.
            .distinctBy { app -> app.packageName }
            .toMutableList()

        // Android TV Settings does not always advertise a launcher activity,
        // so it is added manually when installed and not already discovered.
        val settingsPackage = "com.android.tv.settings"

        if (discoveredApps.none { app ->
                app.packageName == settingsPackage
            }
        ) {
            runCatching {
                @Suppress("DEPRECATION")
                val applicationInfo = packageManager.getApplicationInfo(
                    settingsPackage,
                    0
                )

                val appName = packageManager
                    .getApplicationLabel(applicationInfo)
                    .toString()
                    .ifBlank { "Settings" }

                discoveredApps += JoeTvApp(
                    name = appName,
                    packageName = settingsPackage,
                    description = "Manage JoeTV and device settings",
                    initials = "⚙"
                )
            }
        }

        // Keep the home screen predictable by sorting apps alphabetically.
        return discoveredApps.sortedBy { app ->
            app.name.lowercase()
        }
    }

    /**
     * Creates a compact fallback label for an application.
     *
     * Examples:
     * - "VLC" becomes "VL"
     * - "Smart Tube" becomes "ST"
     * - A blank name becomes "?"
     */
    private fun appInitials(name: String): String {
        val words = name
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }

        return when {
            words.isEmpty() -> "?"

            words.size == 1 -> words.first()
                .take(2)
                .uppercase()

            else -> words.take(2)
                .joinToString("") { word ->
                    word.take(1).uppercase()
                }
        }
    }
}