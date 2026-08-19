package com.trencadis.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trencadis.app.SynthState
import com.trencadis.app.audio.MusicConstants
import com.trencadis.app.camera.PixelSelectionMode
import com.trencadis.app.midi.MidiOutputMode
import com.trencadis.app.midi.MidiState
import com.trencadis.app.midi.SyncSource
import com.trencadis.app.ui.AcidModulation
import com.trencadis.app.ui.AcidPattern

@Composable
fun ModesPanel(
    currentMode: PixelSelectionMode,
    useFrontCamera: Boolean,
    blockSize: Int,
    isCustomGridResolution: Boolean,
    minGridResolution: Int,
    maxGridResolution: Int,
    onModeSelected: (PixelSelectionMode) -> Unit,
    onToggleCamera: () -> Unit,
    onGridResolutionChanged: (Int) -> Unit,
    onGridResolutionReset: () -> Unit,
    isFrameFrozen: Boolean = false,
    onCaptureStill: () -> Unit = {},
    onLoadImage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
            .background(Color(0xAA7A7A7A))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "MODE",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        
        val modes = listOf(
            PixelSelectionMode.SEQUENCE to "SEQ",
            PixelSelectionMode.BRIGHTEST to "BRI",
            PixelSelectionMode.CENTER to "CNT",
            PixelSelectionMode.POINTER to "PTR"
        )
        
        modes.forEach { (mode, label) ->
            ModeButton(
                label = label,
                isSelected = currentMode == mode,
                onClick = { onModeSelected(mode) }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "CAM",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Camera toggle button
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (useFrontCamera) Color(0xFF2196F3) else Color(0xFF424242))
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onToggleCamera),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (useFrontCamera) "FRONT" else "BACK",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Freeze the live feed into a still frame (fed to graphics + audio),
        // or resume the live camera if already frozen.
        Box(
            modifier = Modifier
                .size(60.dp, 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFrameFrozen) Color(0xFFE53935) else Color(0xFF424242))
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onCaptureStill),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isFrameFrozen) "▶ LIVE" else "📸 FREEZE",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Load a picture from the device gallery, frame it, and feed it into
        // the same still-frame pipeline as FREEZE (all effects apply to it).
        Box(
            modifier = Modifier
                .size(60.dp, 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF424242))
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onLoadImage),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🖼 LOAD",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Advanced grid controls (collapsible) ──
        var showAdvanced by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { showAdvanced = !showAdvanced }
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = if (showAdvanced) "▼ ADVANCED" else "▶ ADVANCED",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (showAdvanced) {
            Text(
                text = "GRID: $blockSize",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            // Changing the resolution rebinds the camera, so the value is only
            // committed when the drag ends — not on every slider tick.
            var pendingResolution by remember(blockSize) { mutableStateOf(blockSize.toFloat()) }
            Slider(
                value = pendingResolution,
                onValueChange = { pendingResolution = it },
                onValueChangeFinished = { onGridResolutionChanged(pendingResolution.toInt()) },
                valueRange = minGridResolution.toFloat()..maxGridResolution.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF4CAF50),
                    activeTrackColor = Color(0xFF4CAF50),
                    inactiveTrackColor = Color(0xFF424242)
                ),
                modifier = Modifier.width(120.dp)
            )
            Box(
                modifier = Modifier
                    .size(60.dp, 30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCustomGridResolution) Color(0xFF424242) else Color(0xFF4CAF50))
                    .clickable(onClick = onGridResolutionReset),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AUTO",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF4CAF50) else Color(0xFF424242))
            .border(
                width = 2.dp,
                color = if (isSelected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ScalesPanel(
    currentScale: Int,
    currentKey: Int,
    currentChordType: Int,
    useChordMapping: Boolean = false,
    chordFollowEnabled: Boolean = false,
    detectedChordLabel: String = "",
    onScaleSelected: (Int) -> Unit,
    onKeySelected: (Int) -> Unit,
    onChordTypeSelected: (Int) -> Unit,
    onChordFollowEnabled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(Color(0xAA7A7A7A))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val followLabel = buildString {
                append("Listen")
                if (chordFollowEnabled && detectedChordLabel.isNotBlank()) {
                    append(": ")
                    append(detectedChordLabel)
                }
            }
            Text(
                text = followLabel,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = chordFollowEnabled,
                onCheckedChange = onChordFollowEnabled,
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00E5A0),
                    checkedTrackColor = Color(0xFF00E5A0).copy(alpha = 0.4f)
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "SCALE",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            MusicConstants.SCALE_NAMES.forEachIndexed { index, name ->
                ScaleButton(
                    label = name.take(4),
                    isSelected = currentScale == index,
                    isActiveRow = !useChordMapping,
                    onClick = { onScaleSelected(index) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "KEY",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MusicConstants.KEY_NAMES.forEachIndexed { index, name ->
                val isBlackKey = name.contains("#")
                KeyButton(
                    label = name,
                    isSelected = currentKey == index,
                    isBlackKey = isBlackKey,
                    onClick = { onKeySelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "CHORD",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            MusicConstants.CHORD_TYPE_SHORT_NAMES.forEachIndexed { index, name ->
                ScaleButton(
                    label = name,
                    isSelected = currentChordType == index,
                    isActiveRow = useChordMapping,
                    onClick = { onChordTypeSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun ScaleButton(
    label: String,
    isSelected: Boolean,
    isActiveRow: Boolean = true,
    onClick: () -> Unit
) {
    val bg = when {
        isSelected && isActiveRow -> Color(0xFF2196F3)
        isSelected -> Color(0xFF616161)
        else -> Color(0xFF424242)
    }
    Box(
        modifier = Modifier
            .width(50.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RhythmPanel(
    currentOctave: Int,
    currentFigure: Int,
    tempo: Float,
    isPlaying: Boolean,
    syncSource: SyncSource,
    externalClockAvailable: Boolean,
    isClockLocked: Boolean,
    onOctaveSelected: (Int) -> Unit,
    onFigureSelected: (Int) -> Unit,
    onTapTempo: () -> Unit,
    onTogglePlay: () -> Unit,
    onSyncSourceSelected: (SyncSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val figureSymbols = MusicConstants.FIGURE_SYMBOLS
    val octaveCount = MusicConstants.OCTAVE_MULTIPLIERS.size
    val figureCount = figureSymbols.size
    val insetPx = with(LocalDensity.current) { 4.dp.toPx() }

    fun updateFromOffset(offset: Offset, size: Size) {
        val drawW = size.width - 2 * insetPx
        val drawH = size.height - 2 * insetPx
        val x = (offset.x - insetPx).coerceIn(0f, drawW)
        val y = (offset.y - insetPx).coerceIn(0f, drawH)
        val figure = (x / drawW * figureCount).toInt().coerceIn(0, figureCount - 1)
        val octave = ((1f - y / drawH) * octaveCount).toInt().coerceIn(0, octaveCount - 1)
        onFigureSelected(figure)
        onOctaveSelected(octave)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color(0xAA7A7A7A))
            .padding(12.dp)
    ) {
        Text(
            text = "RHYTHM",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Y axis: octave labels (x1 at the bottom, x7 at the top)
            Column(
                modifier = Modifier
                    .width(24.dp)
                    .height(160.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (i in octaveCount - 1 downTo 0) {
                    Text(
                        text = "x${i + 1}",
                        color = if (currentOctave == i) Color(0xFFFF9800) else Color.White,
                        fontSize = 10.sp,
                        fontWeight = if (currentOctave == i) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // XY pad: X = figure, Y = octave
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                updateFromOffset(
                                    down.position,
                                    Size(size.width.toFloat(), size.height.toFloat())
                                )
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change != null && change.positionChanged()) {
                                        change.consume()
                                        updateFromOffset(
                                            change.position,
                                            Size(
                                                size.width.toFloat(),
                                                size.height.toFloat()
                                            )
                                        )
                                    }
                                    if (change != null && !change.pressed) break
                                } while (true)
                            }
                        }
                        .drawBehind {
                            val cols = figureCount
                            val rows = octaveCount
                            val inset = 4.dp.toPx()
                            val drawW = size.width - 2 * inset
                            val drawH = size.height - 2 * inset
                            val cellW = drawW / cols
                            val cellH = drawH / rows
                            val gap = 2.dp.toPx()

                            for (r in 0 until rows) {
                                for (c in 0 until cols) {
                                    val isSelected = (rows - 1 - r) == currentOctave && c == currentFigure
                                    val baseColor = if ((r + c) % 2 == 0) Color(0xFF9A9A9A).copy(alpha = 0.5f) else Color(0xFF8E8E8E).copy(alpha = 0.5f)
                                    val topLeft = Offset(inset + c * cellW + gap, inset + r * cellH + gap)
                                    val cellSize = Size(cellW - 2 * gap, cellH - 2 * gap)

                                    drawRect(
                                        color = if (isSelected) Color(0xFFFF9800).copy(alpha = 0.45f) else baseColor,
                                        topLeft = topLeft,
                                        size = cellSize
                                    )

                                    if (isSelected) {
                                        drawRect(
                                            color = Color(0xFFFF9800),
                                            topLeft = topLeft,
                                            size = cellSize,
                                            style = Stroke(width = 2f)
                                        )
                                    }
                                }
                            }

                            // selection dot
                            val selR = rows - 1 - currentOctave
                            val selX = inset + currentFigure * cellW + cellW / 2f
                            val selY = inset + selR * cellH + cellH / 2f
                            drawCircle(
                                color = Color(0xFFFF9800),
                                radius = minOf(cellW, cellH) * 0.18f,
                                center = Offset(selX, selY)
                            )
                        }
                        .clip(RoundedCornerShape(16.dp))
                )

                // X axis: figure symbols
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    figureSymbols.forEachIndexed { index, symbol ->
                        Text(
                            text = symbol,
                            color = if (currentFigure == index) Color(0xFFE91E63) else Color.White,
                            fontSize = 18.sp,
                            fontWeight = if (currentFigure == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play/Stop + Tap tempo + sync source, stacked vertically at equal distances
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.height(160.dp)
            ) {
                // Transport toggle: green play arrow when stopped, black stop square when playing.
                // Disabled while an external clock is actually driving the sequencer — the DAW
                // transport Start/Stop/Continue messages rule in that case.
                Button(
                    onClick = onTogglePlay,
                    enabled = !(syncSource == SyncSource.EXTERNAL && isClockLocked),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) Color(0xFFEEEEEE) else Color(0xFF1A1A1A)
                    ),
                    modifier = Modifier.size(60.dp, 48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = if (isPlaying) Color.Black else Color(0xFF4CAF50)
                    )
                }

                // Tap tempo (disabled while following external clock)
                Button(
                    onClick = onTapTempo,
                    enabled = !isClockLocked,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                    modifier = Modifier.size(60.dp, 48.dp)
                ) {
                    Text("TAP", fontSize = 10.sp)
                }

                // Sync source: INT = internal metro, EXT = follow MIDI clock.
                // EXT is selectable only while an external clock is sending ticks.
                Row {
                    SyncSourceButton(
                        label = "INT",
                        isSelected = syncSource == SyncSource.INTERNAL,
                        isEnabled = true,
                        onClick = { onSyncSourceSelected(SyncSource.INTERNAL) },
                        modifier = Modifier.clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                    )
                    SyncSourceButton(
                        label = "EXT",
                        isSelected = syncSource == SyncSource.EXTERNAL,
                        isEnabled = externalClockAvailable,
                        isActive = isClockLocked,
                        onClick = { onSyncSourceSelected(SyncSource.EXTERNAL) },
                        modifier = Modifier.clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                    )
                }

                Text(
                    text = "${tempo.toInt()} BPM" + if (isClockLocked) " \u2022 EXT" else "",
                    color = if (isClockLocked) Color(0xFF00E5A0) else Color.Cyan,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun SyncSourceButton(
    label: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    Box(
        modifier = modifier
            .size(30.dp, 24.dp)
            .background(
                when {
                    isSelected && isActive -> Color(0xFF00E5A0)
                    isSelected -> Color(0xFFFF9800)
                    else -> Color(0xFF1A1A1A)
                }
            )
            .clickable(enabled = isEnabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = when {
                isSelected -> Color.Black
                isEnabled -> Color.White
                else -> Color.White.copy(alpha = 0.3f)
            },
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun KeyButton(
    label: String,
    isSelected: Boolean,
    isBlackKey: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(if (isBlackKey) 32.dp else 45.dp)
            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(
                when {
                    isSelected -> Color(0xFF4CAF50)
                    isBlackKey -> Color(0xFF212121)
                    else -> Color(0xFFEEEEEE)
                }
            )
            .border(1.dp, Color.Gray, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = label,
            color = if (isBlackKey && !isSelected) Color.White else Color.Black,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

@Composable
fun SynthPanel(
    synthState: SynthState,
    onSynthStateChange: ((SynthState) -> SynthState) -> Unit,
    midiState: MidiState = MidiState(),
    onMidiEnabled: (Boolean) -> Unit = {},
    onMidiOutputMode: (MidiOutputMode) -> Unit = {},
    onMidiChannel: (Int) -> Unit = {},
    onMidiBleEnabled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            .background(Color(0xAA7A7A7A))
            .padding(12.dp)
            .width(280.dp)
    ) {
        Text(
            text = "SYNTH",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Oscillator toggles
        Text("Oscillators", color = Color.White, fontSize = 10.sp)
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            OscToggle("SUB", synthState.subOsc) { 
                onSynthStateChange { it.copy(subOsc = !it.subOsc) }
            }
            OscToggle("SIN", synthState.sinOsc) { 
                onSynthStateChange { it.copy(sinOsc = !it.sinOsc) }
            }
            OscToggle("SAW", synthState.sawOsc) { 
                onSynthStateChange { it.copy(sawOsc = !it.sawOsc) }
            }
            OscToggle("SQR", synthState.sqrOsc) { 
                onSynthStateChange { it.copy(sqrOsc = !it.sqrOsc) }
            }
            OscToggle("NOI", synthState.noiseOsc) { 
                onSynthStateChange { it.copy(noiseOsc = !it.noiseOsc) }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Filter section
        Text("Filter", color = Color.White, fontSize = 10.sp)
        SynthSlider("Cutoff", synthState.cutoff) { newValue ->
            onSynthStateChange { it.copy(cutoff = newValue) }
        }
        SynthSlider("Resonance", synthState.resonance) { newValue ->
            onSynthStateChange { s -> s.copy(resonance = newValue) }
        }
        SynthSlider("Envelope", synthState.envelope, -1f, 1f) { newValue ->
            onSynthStateChange { s -> s.copy(envelope = newValue) }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Amp section
        Text("Amp", color = Color.White, fontSize = 10.sp)
        SynthSlider("Attack", synthState.attack) { newValue ->
            onSynthStateChange { s -> s.copy(attack = newValue) }
        }
        SynthSlider("Release", synthState.release) { newValue ->
            onSynthStateChange { s -> s.copy(release = newValue) }
        }
        SynthSlider("Gate", synthState.gateLength) { newValue ->
            onSynthStateChange { s -> s.copy(gateLength = newValue) }
        }
        SynthSlider("Distortion", synthState.distortion) { newValue ->
            onSynthStateChange { s -> s.copy(distortion = newValue) }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Effects section
        Text("Effects", color = Color.White, fontSize = 10.sp)
        SynthSlider("FM", synthState.fm) { newValue ->
            onSynthStateChange { s -> s.copy(fm = newValue) }
        }
        SynthSlider("FM Amt", synthState.fmAmount) { newValue ->
            onSynthStateChange { s -> s.copy(fmAmount = newValue) }
        }
        SynthSlider("Chorus F", synthState.chorusFreq) { newValue ->
            onSynthStateChange { s -> s.copy(chorusFreq = newValue) }
        }
        SynthSlider("Chorus M", synthState.chorusMod) { newValue ->
            onSynthStateChange { s -> s.copy(chorusMod = newValue) }
        }
        SynthSlider("Delay", synthState.delayFigure, -2f, 4f) { newValue ->
            onSynthStateChange { s -> s.copy(delayFigure = newValue) }
        }
        SynthSlider("Feedback", synthState.feedback, 0f, 0.49f) { newValue ->
            onSynthStateChange { s -> s.copy(feedback = newValue) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // MIDI section
        Text("MIDI", color = Color.White, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable", color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = midiState.enabled,
                onCheckedChange = onMidiEnabled,
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00E5A0),
                    checkedTrackColor = Color(0xFF00E5A0).copy(alpha = 0.4f)
                )
            )
        }
        if (midiState.enabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Output", color = Color.White, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MidiModeButton("Pd", midiState.outputMode == MidiOutputMode.INTERNAL) {
                    onMidiOutputMode(MidiOutputMode.INTERNAL)
                }
                MidiModeButton("MIDI", midiState.outputMode == MidiOutputMode.MIDI_OUT) {
                    onMidiOutputMode(MidiOutputMode.MIDI_OUT)
                }
                MidiModeButton("Both", midiState.outputMode == MidiOutputMode.BOTH) {
                    onMidiOutputMode(MidiOutputMode.BOTH)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ch", color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
                val channels = (1..16).map { it.toString() }
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier.horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    channels.forEachIndexed { idx, label ->
                        val ch = idx + 1
                        MidiModeButton(label, midiState.channel == ch, compact = true) {
                            onMidiChannel(ch)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val bleLabel = when {
                    midiState.bleConnected -> "BLE ●"
                    midiState.bleEnabled   -> "BLE ◌"
                    else                   -> "BLE"
                }
                val bleLabelColor = when {
                    midiState.bleConnected -> Color(0xFF00E5A0)
                    else                   -> Color.White
                }
                Text(bleLabel, color = bleLabelColor, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = midiState.bleEnabled,
                    onCheckedChange = onMidiBleEnabled,
                    modifier = Modifier.height(24.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E5A0),
                        checkedTrackColor = Color(0xFF00E5A0).copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}

@Composable
private fun MidiModeButton(
    label: String,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (selected) Color(0xFF00E5A0) else Color.White.copy(alpha = 0.15f)
    val textColor = if (selected) Color.Black else Color.White
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, fontSize = if (compact) 9.sp else 10.sp)
    }
}

@Composable
private fun OscToggle(
    label: String,
    isOn: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(45.dp, 30.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isOn) Color(0xFF4CAF50) else Color(0xFF616161))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SynthSlider(
    label: String,
    value: Float,
    min: Float = 0f,
    max: Float = 1f,
    onValueChange: (Float) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.width(60.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF785050),
                activeTrackColor = Color(0xFF785050),
                inactiveTrackColor = Color(0xFF424242)
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun AcidPanel(
    acidModulation: AcidModulation,
    acidPatternIndex: Int,
    onToggleAcid: () -> Unit,
    onPatternSelected: (Int) -> Unit,
    onModulationChanged: (AcidModulation) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topEnd = 16.dp))
            .background(Color(0xCC1A0A2E))
            .padding(12.dp)
            .width(280.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header with toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "🌀 ACID",
                color = if (acidModulation.enabled) Color(0xFFFF00FF) else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Box(
                modifier = Modifier
                    .size(50.dp, 30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (acidModulation.enabled) Color(0xFFFF00FF) else Color(0xFF424242))
                    .clickable(onClick = onToggleAcid),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (acidModulation.enabled) "ON" else "OFF",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Pattern selector
        Text(
            text = "PATTERN",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AcidPattern.PATTERN_NAMES.forEachIndexed { index, (name, _) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (index == acidPatternIndex) Color(0xFFFF00FF) 
                            else Color(0xFF2A1A4E)
                        )
                        .clickable { onPatternSelected(index) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = if (index == acidPatternIndex) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        
        // Modulation sliders
        AcidSlider(
            label = "HUE",
            value = acidModulation.hueAmount,
            onValueChange = { onModulationChanged(acidModulation.copy(hueAmount = it)) }
        )
        
        AcidSlider(
            label = "SIZE",
            value = acidModulation.sizeAmount,
            onValueChange = { onModulationChanged(acidModulation.copy(sizeAmount = it)) }
        )
        
        AcidSlider(
            label = "ROTATE",
            value = acidModulation.rotationAmount,
            onValueChange = { onModulationChanged(acidModulation.copy(rotationAmount = it)) }
        )
        
        AcidSlider(
            label = "ALPHA",
            value = acidModulation.alphaAmount,
            onValueChange = { onModulationChanged(acidModulation.copy(alphaAmount = it)) }
        )
        
        // Speed slider with exponential curve for more low-speed range
        // Slider goes 0-1, we map it exponentially: speed = slider^2 * 0.5
        // This gives range 0 to 0.5 with more precision at low values
        AcidSlider(
            label = "SPEED",
            value = kotlin.math.sqrt(acidModulation.animationSpeed / 0.5f).coerceIn(0f, 1f),
            onValueChange = { 
                val exponentialSpeed = it * it * 0.5f  // 0 to 0.5 with exponential curve
                onModulationChanged(acidModulation.copy(animationSpeed = exponentialSpeed)) 
            }
        )
    }
}

@Composable
private fun AcidSlider(
    label: String,
    value: Float,
    min: Float = 0f,
    max: Float = 1f,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFFF00FF).copy(alpha = 0.8f),
            fontSize = 10.sp,
            modifier = Modifier.width(50.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF00FF),
                activeTrackColor = Color(0xFFFF00FF),
                inactiveTrackColor = Color(0xFF2A1A4E)
            ),
            modifier = Modifier.weight(1f)
        )
    }
}
