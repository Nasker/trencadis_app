package com.trencadis.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private data class HelpPage(
    val title: String,
    val body: String
)

private val helpPages = listOf(
    HelpPage(
        title = "Welcome to Trencadis",
        body = """Trencadis turns your camera into a live audiovisual synthesizer. Every frame becomes a mosaic of colored pixels that drive sound: hue shapes pitch, saturation affects filter brightness, brightness controls volume, and position moves sound in space.

Use the edge hints to explore the controls."""
    ),
    HelpPage(
        title = "Getting Around",
        body = """- Tap any edge icon to open its panel.
- Double-tap an empty area of the canvas to close all open panels.
- Double-tap the canvas again to hide or show the edge icons.
- Drag from the very edge to open panels without tapping."""
    ),
    HelpPage(
        title = "Modes & Camera",
        body = """Open the Modes panel (📷, left edge) to choose how pixels are read:
- Sequence — cycles through pixels like a sequencer.
- Brightest — always picks the brightest pixel.
- Center — locks on the center pixel for a drone.
- Pointer — touch the canvas to play like a theremin.

Also here: toggle front/back camera, change grid resolution, and freeze the current frame to analyze it."""
    ),
    HelpPage(
        title = "Music Settings",
        body = """Scales panel (𝄞, top): choose scale, root key, and optional chord mapping.

Rhythm panel (♪, bottom): set octave, note duration (figure), and tap the tempo button to set BPM.

Use the Play/Stop button next to TAP to pause or resume the sequencer. The app starts playing automatically when audio is ready.

INT/EXT selects the clock source: INT uses the internal metronome; EXT follows an external MIDI clock (USB or virtual device) and is selectable only while one is sending ticks. While synced, the tempo follows the external clock and TAP is disabled."""
    ),
    HelpPage(
        title = "Synth & Palette",
        body = """Synth panel (∿, right edge): shape the sound — oscillators, filter cutoff/resonance/envelope, distortion, FM, chorus, and delay. Enable MIDI here to send notes to other apps or devices.

Palette panel (🎨, left edge): switch Blob mode on/off and pick acid modulation patterns that warp the mosaic visuals."""
    ),
    HelpPage(
        title = "Presets",
        body = """Preset panel (💾, right edge, lower) lets you save the current sound and settings, load a saved preset, delete one, or share it with another Trencadis user.

Type a name and tap Save. To load, tap a preset name."""
    )
)

@Composable
fun HelpDialog(
    onDismiss: () -> Unit
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = helpPages[pageIndex]
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = page.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(scrollState)
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { (pageIndex + 1) / helpPages.size.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = true
                    ) {
                        Text("Skip")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { if (pageIndex > 0) pageIndex-- },
                            enabled = pageIndex > 0
                        ) {
                            Text("Prev")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (pageIndex < helpPages.lastIndex) {
                            Button(onClick = { pageIndex++ }) {
                                Text("Next")
                            }
                        } else {
                            Button(onClick = onDismiss) {
                                Text("Done")
                            }
                        }
                    }
                }
            }
        }
    }
}
