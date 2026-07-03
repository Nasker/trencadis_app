package com.example.trencadisapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trencadisapp.ui.BlobModulation
import kotlin.math.roundToInt

@Composable
fun BlobPanel(
    blobModulation: BlobModulation,
    onModulationChanged: (BlobModulation) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topEnd = 16.dp))
            .background(Color(0xCC2E1A0A))
            .padding(12.dp)
            .width(280.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "🎨 BLOB",
                color = Color(0xFFFF9800),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .size(50.dp, 30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (blobModulation.blobsOnTop) Color(0xFFFF9800) else Color(0xFF424242))
                    .clickable { onModulationChanged(blobModulation.copy(blobsOnTop = !blobModulation.blobsOnTop)) },
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

        BlobSlider(
            label = "HUE BUCKETS",
            value = blobModulation.hueBuckets.toFloat(),
            min = 1f,
            max = 16f,
            onValueChange = { onModulationChanged(blobModulation.copy(hueBuckets = it.roundToInt().coerceIn(1, 16))) }
        )

        BlobSlider(
            label = "MIN SIZE",
            value = blobModulation.minBlobSize.toFloat(),
            min = 1f,
            max = 50f,
            onValueChange = { onModulationChanged(blobModulation.copy(minBlobSize = it.roundToInt().coerceAtLeast(1))) }
        )

        BlobSlider(
            label = "MAX BLOBS",
            value = blobModulation.maxBlobs.toFloat(),
            min = 20f,
            max = 500f,
            onValueChange = { onModulationChanged(blobModulation.copy(maxBlobs = it.roundToInt().coerceAtLeast(1))) }
        )

        BlobSlider(
            label = "BLOB ALPHA",
            value = blobModulation.blobAlpha,
            format = "%.2f",
            onValueChange = { onModulationChanged(blobModulation.copy(blobAlpha = it)) }
        )

        BlobSlider(
            label = "OUTLINE",
            value = blobModulation.outlineWidth,
            min = 0f,
            max = 10f,
            format = "%.1f",
            onValueChange = { onModulationChanged(blobModulation.copy(outlineWidth = it)) }
        )

        BlobSlider(
            label = "OUTLINE ALPHA",
            value = blobModulation.outlineAlpha,
            format = "%.2f",
            onValueChange = { onModulationChanged(blobModulation.copy(outlineAlpha = it)) }
        )

        BlobSlider(
            label = "TILE OVERLAY",
            value = blobModulation.tileOverlayAlpha,
            format = "%.2f",
            onValueChange = { onModulationChanged(blobModulation.copy(tileOverlayAlpha = it)) }
        )
    }
}

@Composable
private fun BlobSlider(
    label: String,
    value: Float,
    min: Float = 0f,
    max: Float = 1f,
    steps: Int = 0,
    format: String = "%.0f",
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.width(72.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF9800),
                activeTrackColor = Color(0xFFFF9800),
                inactiveTrackColor = Color(0xFF424242)
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = format.format(value),
            color = Color(0xFFFF9800),
            fontSize = 10.sp,
            modifier = Modifier.width(36.dp)
        )
    }
}
