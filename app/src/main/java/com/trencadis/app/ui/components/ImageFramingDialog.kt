package com.trencadis.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max

/**
 * Full-screen crop UI shown after picking an image from the device gallery.
 * Lets the user pan/pinch-zoom the picture inside a frame that matches the
 * canvas aspect ratio, so the loaded still is framed exactly like a camera
 * capture before it enters the same pixel-grid pipeline as everything else.
 */
@Composable
fun ImageFramingDialog(
    source: Bitmap,
    aspectRatio: Float, // width / height, matches TrencadisState.screenAspectRatio
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val density = LocalDensity.current

    var userScale by remember { mutableStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }
    var frameSizePx by remember { mutableStateOf(IntSize.Zero) }

    val baseScale = remember(frameSizePx, source) {
        if (frameSizePx.width == 0 || frameSizePx.height == 0) 1f
        else max(
            frameSizePx.width.toFloat() / source.width,
            frameSizePx.height.toFloat() / source.height
        )
    }

    fun clampOffset(scale: Float, offset: Offset): Offset {
        val slackX = max(0f, (source.width * scale - frameSizePx.width) / 2f)
        val slackY = max(0f, (source.height * scale - frameSizePx.height) / 2f)
        return Offset(
            x = offset.x.coerceIn(-slackX, slackX),
            y = offset.y.coerceIn(-slackY, slackY)
        )
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Frame the image",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Drag to pan, pinch to zoom",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .let {
                                // Fit the frame within the available space while
                                // respecting the target canvas aspect ratio.
                                if (aspectRatio <= 1f) it.fillMaxHeight().aspectRatio(aspectRatio)
                                else it.fillMaxWidth().aspectRatio(aspectRatio)
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                            .onSizeChanged { frameSizePx = it }
                            .pointerInput(source) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (userScale * zoom).coerceIn(1f, 6f)
                                    userScale = newScale
                                    userOffset = clampOffset(baseScale * newScale, userOffset + pan)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = source.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(
                                    with(density) { source.width.toDp() },
                                    with(density) { source.height.toDp() }
                                )
                                .graphicsLayer(
                                    scaleX = baseScale * userScale,
                                    scaleY = baseScale * userScale,
                                    translationX = userOffset.x,
                                    translationY = userOffset.y
                                )
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }

                    Button(
                        onClick = {
                            val scale = baseScale * userScale
                            val frameW = frameSizePx.width.toFloat()
                            val frameH = frameSizePx.height.toFloat()
                            val bitmapCenterX = source.width / 2f
                            val bitmapCenterY = source.height / 2f

                            val left = bitmapCenterX + (-frameW / 2f - userOffset.x) / scale
                            val top = bitmapCenterY + (-frameH / 2f - userOffset.y) / scale
                            val right = bitmapCenterX + (frameW / 2f - userOffset.x) / scale
                            val bottom = bitmapCenterY + (frameH / 2f - userOffset.y) / scale

                            val cropLeft = left.coerceIn(0f, source.width.toFloat()).toInt()
                            val cropTop = top.coerceIn(0f, source.height.toFloat()).toInt()
                            val cropRight = right.coerceIn(0f, source.width.toFloat()).toInt()
                            val cropBottom = bottom.coerceIn(0f, source.height.toFloat()).toInt()
                            val cropWidth = (cropRight - cropLeft).coerceAtLeast(1)
                            val cropHeight = (cropBottom - cropTop).coerceAtLeast(1)

                            val cropped = Bitmap.createBitmap(
                                source, cropLeft, cropTop, cropWidth, cropHeight
                            )
                            onConfirm(cropped)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5A0)),
                        modifier = Modifier.weight(1f)
                    ) { Text("Use image", color = Color.Black) }
                }
            }
        }
    }
}
