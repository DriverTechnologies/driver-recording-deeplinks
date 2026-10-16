package com.drivertechnologies.recordingsample

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import androidx.core.graphics.drawable.toBitmap

/** Where Driver hands control back: [source] is the link's `source` parameter. */
data class ReturnTarget(
    val label: String,
    val source: String,
    val icon: Bitmap?,
)

private const val ICON_SIZE_PX = 96

/**
 * This sample (by its return URL) followed by every other launcher app, by package name. Seeing
 * other apps needs the launcher `<queries>` entry in AndroidManifest.xml.
 */
@WorkerThread
fun Context.returnTargets(): List<ReturnTarget> {
    val self = ReturnTarget(
        label = "This app",
        source = RecordingDeepLink.SOURCE_IDENTIFIER,
        icon = applicationInfo.loadIcon(packageManager).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX),
    )
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val others = packageManager
        .queryIntentActivities(launcher, 0)
        .map { it.activityInfo.applicationInfo }
        .filter { it.packageName != packageName }
        .distinctBy { it.packageName }
        .map { app ->
            ReturnTarget(
                label = app.loadLabel(packageManager).toString(),
                source = app.packageName,
                icon = app.loadIcon(packageManager).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX),
            )
        }.sortedBy { it.label.lowercase() }
    return listOf(self) + others
}
