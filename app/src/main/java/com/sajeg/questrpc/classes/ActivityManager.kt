package com.sajeg.questrpc.classes

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.my.kizzyrpc.KizzyRPC
import com.my.kizzyrpc.model.Activity
import com.my.kizzyrpc.model.Assets
import com.sajeg.questrpc.composables.getInstalledVrGames

object ActivityManager {
    var rpc: KizzyRPC? = null
    var lastApp: String = ""
    var startTime: Long = System.currentTimeMillis()
    var pauseTime: Long? = null

    fun start(context: Context) {
        SettingsManager().readString("token", context) { token ->
            rpc = KizzyRPC(token)
        }
    }

    fun stop(context: Context) {
        pauseTime = System.currentTimeMillis()
        SettingsManager().saveString("game", "null", context)
        if (rpc != null) {
            rpc!!.closeRPC()
            rpc = null
        }
    }

    fun resume() {
        if (pauseTime == null) {
            return
        }
        var timeStandBy = System.currentTimeMillis() - pauseTime!!
        if (timeStandBy > 3600000) {
            startTime = System.currentTimeMillis()
        } else {
            startTime = startTime + timeStandBy
        }
    }

    fun appChanged(packageName: String, context: Context) {
        if (lastApp != packageName) {
            startTime = System.currentTimeMillis()
        }
        val isVrGame = getInstalledVrGames(context).any { it.packageName == packageName }
        lastApp = packageName

        if (!isVrGame) {
            // Foreground app isn't a recognized VR game - the user left whatever game
            // was running (Quest Home, dashboard, browser, settings, ...). Clear the
            // presence instead of leaving it stuck on the last game. This replaces the
            // old check that only did this for the specific "com.oculus.shellenv"
            // package, which missed every other non-game screen.
            if (rpc != null) {
                stop(context)
            }
            return
        }

        AppManager().getExcludedApps(context) { excludedApps ->
            if (excludedApps.contains(packageName)) {
                return@getExcludedApps
            }
            AppManager().getCustomAppNames(context) { names ->
                names.forEach { name ->
                    if (name.packageName == packageName) {
                        Log.d("NowPlayingC", name.name)
                        createActivity(name.name, packageName, context)
                        return@getCustomAppNames
                    }
                }
                AppManager().getStoreNames(context) { savedStoreNames ->
                    savedStoreNames.forEach { name ->
                        if (name.packageName == packageName) {
                            Log.d("NowPlayingS", name.name)
                            createActivity(name.name, packageName, context)
                            return@getStoreNames
                        }
                    }
                    val appName = getAppNameFromPackageName(packageName.toString(), context)
                    Log.d("NowPlayingA", appName)
                    createActivity(appName, packageName, context)
                }
            }
        }
    }

    private fun getAppNameFromPackageName(packageName: String, context: Context): String {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)

            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun createActivity(name: String, packageName: String, context: Context) {
        SettingsManager().saveString("game", name, context)

        fun sendActivity() {
            WebRequest().getImageUrl(packageName) { image ->
                rpc?.setActivity(
                    activity = Activity(
                        applicationId = "1299052584761561161",
                        name = name,
                        details = "on a Meta ${
                            Settings.Global.getString(
                                context.contentResolver,
                                "device_name"
                            ) ?: "Unknown Device"
                        }",
                        type = 0,
                        assets = Assets(
                            largeImage = image,
                            smallImage = null,
                            largeText = "Meta Quest",
                            smallText = null
                        )
                    ),
                    status = "online",
                    since = startTime
                )
            }
        }

        // start(context) reads the saved token from disk asynchronously, so calling it
        // and immediately using `rpc` (as this used to do) raced: on a cold start, rpc
        // was still null by the time setActivity ran, silently dropping the very first
        // presence update until the app/game was restarted. Wait for the token instead.
        if (rpc == null) {
            SettingsManager().readString("token", context) { token ->
                rpc = KizzyRPC(token)
                sendActivity()
            }
        } else {
            sendActivity()
        }
    }
}