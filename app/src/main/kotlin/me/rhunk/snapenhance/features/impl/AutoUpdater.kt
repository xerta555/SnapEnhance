package me.rhunk.snapenhance.features.impl

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import me.rhunk.snapenhance.BuildConfig
import me.rhunk.snapenhance.Logger
import me.rhunk.snapenhance.config.ConfigProperty
import me.rhunk.snapenhance.features.Feature
import me.rhunk.snapenhance.features.FeatureLoadParams
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class AutoUpdater : Feature("AutoUpdater", loadParams = FeatureLoadParams.ACTIVITY_CREATE_ASYNC) {
    override fun asyncOnActivityCreate() {
        val checkForUpdateMode = context.config.state(ConfigProperty.AUTO_UPDATER)
        val currentTimeMillis = System.currentTimeMillis()
        val checkForUpdatesTimestamp = context.bridgeClient.getAutoUpdaterTime()

        val delayTimestamp = when (checkForUpdateMode) {
            "EVERY_LAUNCH" -> currentTimeMillis - checkForUpdatesTimestamp
            "DAILY" -> 86400000L
            "WEEKLY" -> 604800000L
            else -> return
        }

        if (checkForUpdatesTimestamp + delayTimestamp > currentTimeMillis) return

        runCatching {
            checkForUpdates()
        }.onFailure {
            Logger.error("Failed to check for updates: ${it.message}", it)
        }.onSuccess {
            context.bridgeClient.setAutoUpdaterTime(currentTimeMillis)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun checkForUpdates(): String? {
        val endpoint = Request.Builder().url("https://api.github.com/repos/rhunk/SnapEnhance/releases").build()
        val response = OkHttpClient().newCall(endpoint).execute()

        if (!response.isSuccessful) throw Throwable("Failed to fetch releases: ${response.code}")

        val releases = JSONArray(response.body.string()).also {
            if (it.length() == 0) throw Throwable("No releases found")
        }

        val latestRelease = releases.getJSONObject(0)
        val latestVersion = latestRelease.getString("tag_name")
        if (latestVersion.removePrefix("v") == BuildConfig.VERSION_NAME) return null

        val releaseContentBody = latestRelease.getString("body")
        val downloadEndpoint = latestRelease.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")

        context.runOnUiThread {
            AlertDialog.Builder(context.mainActivity)
                .setTitle(context.translation.get("auto_updater.dialog_title"))
                .setMessage(
                    context.translation.get("auto_updater.dialog_message")
                        .replace("{version}", latestVersion)
                        .replace("{body}", releaseContentBody)
                )
                .setNegativeButton(context.translation.get("auto_updater.dialog_negative_button")) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(context.translation.get("auto_updater.dialog_positive_button")) { dialog, _ ->
                    dialog.dismiss()
                    context.longToast(context.translation.get("auto_updater.downloading_toast"))

                    val request = DownloadManager.Request(Uri.parse(downloadEndpoint))
                        .setTitle(context.translation.get("auto_updater.download_manager_notification_title"))
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "latest-snapenhance.apk")
                        .setMimeType("application/vnd.android.package-archive")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

                    val downloadManager = context.androidContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val downloadId = downloadManager.enqueue(request)

                    val onCompleteReceiver = object: BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                            if (id != downloadId) return
                            context.unregisterReceiver(this)
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(downloadManager.getUriForDownloadedFile(downloadId), "application/vnd.android.package-archive")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            )
                        }
                    }

                    context.mainActivity?.registerReceiver(onCompleteReceiver, IntentFilter(
                        DownloadManager.ACTION_DOWNLOAD_COMPLETE
                    ))
                }.show()
        }

        return latestVersion
    }
}