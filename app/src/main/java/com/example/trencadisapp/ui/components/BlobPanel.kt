package com.example.trencadisapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trencadisapp.ui.AcidModulation
import com.example.trencadisapp.ui.AcidPattern
import com.example.trencadisapp.ui.BlobModulation

@Composable
fun PalettePanel(
    useBlobMode: Boolean,
    blobModulation: BlobModulation,
    acidModulation: AcidModulation,
    acidPatternIndex: Int,
    onToggleBlobMode: () -> Unit,
    onBlobModulationChanged: (BlobModulation) -> Unit,
    onToggleAcid: () -> Unit,
    onPatternSelected: (Int) -> Unit,
    onAcidModulationChanged: (AcidModulation) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
            .background(Color(0xCC1A0A2E))
            .padding(12.dp)
            .width(280.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Text(
            text = "🎨 PALETTE",
            color = Color(0xFFFF9800),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        // Cubist enable + blob back/top toggles
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp, 30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (useBlobMode) Color(0xFFFF9800) else Color(0xFF424242))
                    .clickable(onClick = onToggleBlobMode),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (useBlobMode) "BLOB" else "TILE",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (useBlobMode) {
                Box(
                    modifier = Modifier
                        .size(60.dp, 30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (blobModulation.blobsOnTop) Color(0xFFFF9800) else Color(0xFF424242))
                        .clickable {
                            onBlobModulationChanged(blobModulation.copy(blobsOnTop = !blobModulation.blobsOnTop))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (blobModulation.blobsOnTop) "TOP" else "BACK",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Blob blend crossfade slider (only visible in blob mode)
        if (useBlobMode) {
            PaletteSlider(
                label = "BLEND",
                value = blobModulation.blobBlend,
                valueColor = Color(0xFFFF9800),
                onValueChange = { onBlobModulationChanged(blobModulation.copy(blobBlend = it)) }
            )

            // ── Advanced blob controls (collapsible) ──
            var showAdvanced by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
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
                PaletteSlider(
                    label = "HUE BKT",
                    value = blobModulation.hueBuckets.toFloat(),
                    min = 2f, max = 32f,
                    valueColor = Color(0xFFFF9800),
                    onValueChange = { onBlobModulationChanged(blobModulation.copy(hueBuckets = it.toInt())) }
                )
                PaletteSlider(
                    label = "MIN SIZE",
                    value = blobModulation.minBlobSize.toFloat(),
                    min = 5f, max = 200f,
                    valueColor = Color(0xFFFF9800),
                    onValueChange = { onBlobModulationChanged(blobModulation.copy(minBlobSize = it.toInt())) }
                )
                PaletteSlider(
                    label = "MAX BLOB",
                    value = blobModulation.maxBlobs.toFloat(),
                    min = 50f, max = 1000f,
                    valueColor = Color(0xFFFF9800),
                    onValueChange = { onBlobModulationChanged(blobModulation.copy(maxBlobs = it.toInt())) }
                )
                PaletteSlider(
                    label = "OUTLINE",
                    value = blobModulation.outlineWidth,
                    min = 0f, max = 30f,
                    valueColor = Color(0xFFFF9800),
                    onValueChange = { onBlobModulationChanged(blobModulation.copy(outlineWidth = it)) }
                )
                PaletteSlider(
                    label = "OUT α",
                    value = blobModulation.outlineAlpha,
                    valueColor = Color(0xFFFF9800),
                    onValueChange = { onBlobModulationChanged(blobModulation.copy(outlineAlpha = it)) }
                )
            }
        }

        // BRI-SIZE slider (brightnessSizeBoost from acid, always visible)
        PaletteSlider(
            label = "BRI SIZE",
            value = kotlin.math.sqrt(acidModulation.brightnessSizeBoost / 1.5f).coerceIn(0f, 1f),
            valueColor = Color(0xFFFF9800),
            onValueChange = {
                val exponentialBoost = it * it * 1.5f
                onAcidModulationChanged(acidModulation.copy(brightnessSizeBoost = exponentialBoost))
            }
        )

        // ── Acid section ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ACID",
                color = if (acidModulation.enabled) Color(0xFFFF00FF) else Color.White,
                fontSize = 12.sp,
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

        // Acid modulation sliders
        PaletteSlider(
            label = "HUE",
            value = acidModulation.hueAmount,
            valueColor = Color(0xFFFF00FF),
            onValueChange = { onAcidModulationChanged(acidModulation.copy(hueAmount = it)) }
        )
        PaletteSlider(
            label = "SIZE",
            value = acidModulation.sizeAmount,
            valueColor = Color(0xFFFF00FF),
            onValueChange = { onAcidModulationChanged(acidModulation.copy(sizeAmount = it)) }
        )
        PaletteSlider(
            label = "ROTATE",
            value = acidModulation.rotationAmount,
            valueColor = Color(0xFFFF00FF),
            onValueChange = { onAcidModulationChanged(acidModulation.copy(rotationAmount = it)) }
        )
        PaletteSlider(
            label = "ALPHA",
            value = acidModulation.alphaAmount,
            valueColor = Color(0xFFFF00FF),
            onValueChange = { onAcidModulationChanged(acidModulation.copy(alphaAmount = it)) }
        )
        PaletteSlider(
            label = "SPEED",
            value = kotlin.math.sqrt(acidModulation.animationSpeed / 0.5f).coerceIn(0f, 1f),
            valueColor = Color(0xFFFF00FF),
            onValueChange = {
                val exponentialSpeed = it * it * 0.5f
                onAcidModulationChanged(acidModulation.copy(animationSpeed = exponentialSpeed))
            }
        )
    }
}

@Composable
private fun PaletteSlider(
    label: String,
    value: Float,
    min: Float = 0f,
    max: Float = 1f,
    valueColor: Color = Color.White,
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
            color = valueColor.copy(alpha = 0.8f),
            fontSize = 10.sp,
            modifier = Modifier.width(56.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = valueColor,
                activeTrackColor = valueColor,
                inactiveTrackColor = Color(0xFF2A1A4E)
            ),
            modifier = Modifier.weight(1f)
        )
    }
}
