package com.drivertechnologies.recordingsample

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.core.net.toUri

/**
 * Self-contained; copy this file into your app to check, without opening Driver, whether it can
 * record. Also copy the `<queries>` block from AndroidManifest.xml: without it, Android 11+ hides
 * Driver from your app and every check reports [NOT_INSTALLED].
 */
object DriverStatus {
    const val HEALTHY = "healthy"
    const val UNAUTHENTICATED = "unauthenticated"
    const val MISSING_PERMISSIONS = "missing_permissions"
    const val UNAVAILABLE = "unavailable"

    /** Reported by this helper, not by Driver: no Driver version with the status check is installed. */
    const val NOT_INSTALLED = "not_installed"

    internal const val AUTHORITY = "com.trydriver.driver.status"
}

/** A blocking IPC call into Driver, which may cold-start it; call it off the main thread. */
@WorkerThread
fun Context.queryDriverStatus(): String =
    try {
        contentResolver.call("content://${DriverStatus.AUTHORITY}".toUri(), "status", null, null)
            ?.getString("status") ?: DriverStatus.UNAVAILABLE
    } catch (exception: IllegalArgumentException) {
        DriverStatus.NOT_INSTALLED // Unknown authority: Driver isn't installed.
    } catch (exception: RuntimeException) {
        DriverStatus.UNAVAILABLE // e.g. a SecurityException, or Driver's call() failing.
    }
