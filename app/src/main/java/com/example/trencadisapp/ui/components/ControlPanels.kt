package com.example.trencadisapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trencadisapp.SynthState
import com.example.trencadisapp.audio.MusicConstants
import com.example.trencadisapp.camera.PixelSelectionMode

@Composable
fun ModesPanel(
    currentMode: PixelSelectionMode,
    useFrontCamera: Boolean,
    onModeSelected: (PixelSelectionMode) -> Unit,
    onToggleCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
            .background(Color(0xAA7A7A7A))
            .padding(12.dp),
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
    onScaleSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(Color(0xAA7A7A7A))
            .padding(12.dp)
    ) {
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
                    onClick = { onScaleSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun ScaleButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(50.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color(0xFF2196F3) else Color(0xFF424242))
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
fun KeysPanel(
    currentKey: Int,
    currentOctave: Int,
    currentFigure: Int,
    tempo: Float,
    onKeySelected: (Int) -> Unit,
    onOctaveSelected: (Int) -> Unit,
    onFigureSelected: (Int) -> Unit,
    onTapTempo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color(0xAA7A7A7A))
            .padding(12.dp)
    ) {
        // Keys row (piano-like)
        Text(
            text = "KEY",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        androidx.compose.foundation.layout.Row(
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
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Octave row
        Text(
            text = "OCTAVE",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (0..6).forEach { index ->
                OctaveButton(
                    label = "x${index + 1}",
                    isSelected = currentOctave == index,
                    onClick = { onOctaveSelected(index) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Figure/Note duration row
        Text(
            text = "FIGURE",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val figureSymbols = listOf("𝅝", "𝅗𝅥", "♩", "♪", "𝅘𝅥𝅯", "𝅘𝅥𝅰", "𝅘𝅥𝅱")
            figureSymbols.forEachIndexed { index, symbol ->
                FigureButton(
                    label = symbol,
                    isSelected = currentFigure == index,
                    onClick = { onFigureSelected(index) }
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Tap tempo button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onTapTempo,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                    modifier = Modifier.size(60.dp, 40.dp)
                ) {
                    Text("TAP", fontSize = 10.sp)
                }
                Text(
                    text = "${tempo.toInt()} BPM",
                    color = Color.Cyan,
                    fontSize = 10.sp
                )
            }
        }
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
            .height(if (isBlackKey) 50.dp else 70.dp)
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
private fun OctaveButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp, 30.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color(0xFFFF9800) else Color(0xFF424242))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun FigureButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(35.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color(0xFFE91E63) else Color(0xFF424242))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp
        )
    }
}

@Composable
fun SynthPanel(
    synthState: SynthState,
    onSynthStateChange: ((SynthState) -> SynthState) -> Unit,
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
        SynthSlider("Feedback", synthState.feedback) { newValue ->
            onSynthStateChange { s -> s.copy(feedback = newValue) }
        }
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
