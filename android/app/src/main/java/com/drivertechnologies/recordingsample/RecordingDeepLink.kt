package com.drivertechnologies.recordingsample

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** Self-contained; copy this file into your app to trigger Driver recordings. */
object RecordingDeepLink {
    /**
     * This app's return URL (see the `VIEW` filter in AndroidManifest.xml). `source` can also be
     * another app's package name; without `source`, Driver acts without reopening any app.
     */
    const val SOURCE_IDENTIFIER = "driver-recording-sample://recording"

    enum class Action(val intentName: String) {
        Start("start_recording"),
        Stop("stop_recording"),
    }

    /**
     * Uri.Builder percent-encodes `source`; string concatenation would not. [dualCamera] sets
     * `start_recording`'s `dual_camera` for that recording; `null` leaves the user's own
     * setting.
     */
    fun uri(
        action: Action,
        source: String? = SOURCE_IDENTIFIER,
        dualCamera: Boolean? = null,
    ): Uri =
        Uri.Builder()
            .scheme("https")
            .authority("drivertechnologies.com")
            .path("/actions")
            .appendQueryParameter("intent", action.intentName)
            .apply {
                if (source != null) appendQueryParameter("source", source)
                if (action == Action.Start && dualCamera != null) appendQueryParameter("dual_camera", dualCamera.toString())
            }
            .build()
}

/**
 * Returns `false` when only a browser could open the link, usually because Driver isn't installed.
 * Don't pre-flight with `resolveActivity`: on Android 11+ it reports Driver as missing unless Driver
 * is declared in your `<queries>`.
 */
fun Context.triggerRecording(
    action: RecordingDeepLink.Action,
    source: String? = RecordingDeepLink.SOURCE_IDENTIFIER,
    dualCamera: Boolean? = null,
): Boolean =
    try {
        val intent = Intent(Intent.ACTION_VIEW, RecordingDeepLink.uri(action, source, dualCamera))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // The link is plain https, so without this a browser opens it and the launch still succeeds.
        // Before Android 11 the flag doesn't exist, and a browser can still take the link.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        }
        startActivity(intent)
        true
    } catch (exception: ActivityNotFoundException) {
        false
    }
