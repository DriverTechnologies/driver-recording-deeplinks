package com.drivertechnologies.recordingsample

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.drivertechnologies.recordingsample.RecordingDeepLink.Action

private val RecordRed = Color(0xFFE53935)
private val ReadyGreen = Color(0xFF43A047)
private val WarningAmber = Color(0xFFFFA000)

@Composable
fun RecordingScreen(
    nextAction: Action,
    waiting: Boolean,
    status: String?,
    driverStatus: String?,
    returnTargets: List<ReturnTarget>,
    onTrigger: (Action, source: String, dualCamera: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (nextAction == Action.Start) "Start recording" else "Stop recording"
    var source by rememberSaveable { mutableStateOf(RecordingDeepLink.SOURCE_IDENTIFIER) }
    var dualCamera by rememberSaveable { mutableStateOf(false) }
    // Starting needs Driver ready; stopping never does, so a running recording can always end.
    val startBlocked = nextAction == Action.Start && driverStatus != DriverStatus.HEALTHY

    Scaffold(modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // verticalScroll keeps the viewport as min height, so this centers until content overflows.
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Driver remote recording", style = MaterialTheme.typography.titleLarge)
                DriverStatusPill(driverStatus)
            }

            RecordButton(
                nextAction = nextAction,
                waiting = waiting,
                enabled = !waiting && !startBlocked,
                label = label,
                onClick = { onTrigger(nextAction, source, dualCamera) },
            )

            AnimatedContent(
                targetState = when {
                    startBlocked -> "Driver needs to be ready before you can start recording."
                    else -> status ?: "Tap to ${label.lowercase()} in Driver."
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "status",
            ) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            RecordingOptions(
                returnTargets = returnTargets,
                source = source,
                onSourceChange = { source = it },
                dualCamera = dualCamera,
                onDualCameraChange = { dualCamera = it },
                enabled = !waiting,
                modifier = Modifier.fillMaxWidth(),
            )

            NextLink(action = nextAction, source = source, dualCamera = dualCamera, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A camera-style shutter: a circle that starts, morphing to a square that stops. */
@Composable
private fun RecordButton(
    nextAction: Action,
    waiting: Boolean,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = updateTransition(targetState = nextAction, label = "shutter")
    val coreSize = transition.animateDp(label = "coreSize") { if (it == Action.Start) 120.dp else 64.dp }
    val coreCorner = transition.animateDp(label = "coreCorner") { if (it == Action.Start) 60.dp else 14.dp }
    val ringColor = MaterialTheme.colorScheme.outlineVariant
    // Only tick frames while waiting on Driver.
    val halo = if (waiting) {
        rememberInfiniteTransition(label = "halo").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_400, easing = LinearOutSlowInEasing)),
            label = "halo",
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .size(168.dp)
            // Dimmed only when blocked: while waiting, the halo shows the button is busy instead.
            .alpha(if (enabled || waiting) 1f else 0.38f)
            .drawBehind {
                val radius = size.minDimension / 2
                halo?.let {
                    drawCircle(
                        color = RecordRed.copy(alpha = 1f - it.value),
                        radius = radius * (1f + 0.35f * it.value),
                        style = Stroke(width = 4.dp.toPx()),
                    )
                }
                drawCircle(ringColor, radius = radius - 4.dp.toPx(), style = Stroke(width = 8.dp.toPx()))
                val side = coreSize.value.toPx()
                drawRoundRect(
                    color = RecordRed,
                    topLeft = center - Offset(side / 2, side / 2),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(coreCorner.value.toPx()),
                )
            }
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun DriverStatusPill(driverStatus: String?, modifier: Modifier = Modifier) {
    val (dot, text) = when (driverStatus) {
        null -> MaterialTheme.colorScheme.outline to "Checking Driver…"
        DriverStatus.HEALTHY -> ReadyGreen to "Driver is ready"
        DriverStatus.UNAUTHENTICATED -> WarningAmber to "Sign in to Driver"
        DriverStatus.MISSING_PERMISSIONS -> WarningAmber to "Driver needs permissions"
        DriverStatus.NOT_INSTALLED -> RecordRed to "Driver isn't installed"
        else -> MaterialTheme.colorScheme.outline to "Driver status unavailable"
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(dot, CircleShape),
            )
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RecordingOptions(
    returnTargets: List<ReturnTarget>,
    source: String,
    onSourceChange: (String) -> Unit,
    dualCamera: Boolean,
    onDualCameraChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            ReturnTargetPicker(returnTargets, source, onSourceChange, enabled)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = dualCamera,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onDualCameraChange,
                    ).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Dual camera", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Front camera if unsupported",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = dualCamera, onCheckedChange = null, enabled = enabled)
            }
        }
    }
}

/** Driver opens `source` once it's done; this sample by default, or any launcher app by package. */
@Composable
private fun ReturnTargetPicker(
    returnTargets: List<ReturnTarget>,
    source: String,
    onSourceChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = returnTargets.firstOrNull { it.source == source }

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled && returnTargets.isNotEmpty(), role = Role.DropdownList) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(selected?.icon)
            Column(Modifier.weight(1f)) {
                Text(
                    "Return to",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    selected?.label ?: source,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            returnTargets.forEach { target ->
                DropdownMenuItem(
                    text = { Text(target.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { AppIcon(target.icon) },
                    onClick = {
                        onSourceChange(target.source)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AppIcon(icon: Bitmap?, modifier: Modifier = Modifier) {
    val iconModifier = modifier.size(32.dp)
    if (icon != null) {
        Image(icon.asImageBitmap(), contentDescription = null, modifier = iconModifier)
    } else {
        Box(iconModifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
    }
}

@Composable
private fun NextLink(
    action: Action,
    source: String,
    dualCamera: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        val uri = RecordingDeepLink.uri(action, source, dualCamera)
        SelectionContainer {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tapping opens", style = MaterialTheme.typography.labelMedium)
                Text(
                    uri.toString(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                // `source` is percent-encoded in the link; this is what Driver reads back.
                Text(
                    "Driver decodes source as",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    uri.getQueryParameter("source").orEmpty(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordingScreenPreview() {
    MaterialTheme {
        RecordingScreen(
            nextAction = Action.Start,
            waiting = false,
            status = null,
            driverStatus = DriverStatus.HEALTHY,
            returnTargets = emptyList(),
            onTrigger = { _, _, _ -> },
        )
    }
}
