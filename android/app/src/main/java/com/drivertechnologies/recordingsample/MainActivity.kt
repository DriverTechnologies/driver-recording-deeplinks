package com.drivertechnologies.recordingsample

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.drivertechnologies.recordingsample.RecordingDeepLink.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val KEY_LAST_SENT = "lastSent"

class MainActivity : ComponentActivity() {
    private var lastSent by mutableStateOf<Action?>(null)
    private var waiting by mutableStateOf(false)
    private var status by mutableStateOf<String?>(null)
    private var driverStatus by mutableStateOf<String?>(null)
    private var targets by mutableStateOf<List<ReturnTarget>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        savedInstanceState?.getString(KEY_LAST_SENT)?.let { lastSent = Action.valueOf(it) }
        if (savedInstanceState == null) handleReturn(intent)
        lifecycleScope.launch { targets = withContext(Dispatchers.IO) { returnTargets() } }
        // Re-check on every return: the user may have just signed in or granted permissions in Driver.
        // Scoped to RESUMED so a slow check from an earlier resume can't overwrite a newer one.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                driverStatus = withContext(Dispatchers.IO) { queryDriverStatus() }
            }
        }

        setContent {
            val dark = isSystemInDarkTheme()
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                RecordingScreen(
                    // Toggle on what was last sent: a return to `source` doesn't prove recording started.
                    nextAction = if (lastSent == Action.Start) Action.Stop else Action.Start,
                    waiting = waiting,
                    status = status,
                    driverStatus = driverStatus,
                    returnTargets = targets,
                    onTrigger = ::trigger,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LAST_SENT, lastSent?.name)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReturn(intent)
    }

    override fun onResume() {
        super.onResume()
        // Still waiting means Driver didn't open `source`; the user came back by hand.
        if (waiting) {
            waiting = false
            status = "Back without a handoff from Driver."
        }
    }

    private fun handleReturn(intent: Intent) {
        if (intent.dataString?.startsWith(RecordingDeepLink.SOURCE_IDENTIFIER) != true) return
        waiting = false
        status = "Driver handed control back after ${lastSent?.intentName ?: "a recording action"}."
    }

    private fun trigger(
        action: Action,
        source: String,
        dualCamera: Boolean,
    ) {
        if (triggerRecording(action, source, dualCamera)) {
            lastSent = action
            waiting = true
            status = if (action == Action.Start) "Waiting for Driver to start, up to ~12s…" else "Waiting for Driver to stop…"
        } else {
            status = "Driver isn't installed, or the link couldn't be opened."
        }
    }
}
