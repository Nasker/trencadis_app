package com.example.trencadisapp.ui

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trencadisapp.TrencadisViewModel
import com.example.trencadisapp.camera.CameraPixelAnalyzer
import com.example.trencadisapp.ui.components.AcidPanel
import com.example.trencadisapp.ui.components.KeysPanel
import com.example.trencadisapp.ui.components.ModesPanel
import com.example.trencadisapp.ui.components.ScalesPanel
import com.example.trencadisapp.ui.components.SynthPanel
import com.example.trencadisapp.ui.components.PresetPanel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TrencadisScreen(
    viewModel: TrencadisViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    // Request camera permission on first launch
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    // Initialize audio when permission is granted
    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted && !state.isAudioInitialized) {
            viewModel.initializeAudio()
        }
    }
    
    // Tap tempo state
    var lastTapTime by remember { mutableLongStateOf(0L) }
    
    // Hide/show icons with two-finger tap
    var iconsVisible by remember { mutableStateOf(true) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (cameraPermissionState.status.isGranted) {
            // Camera preview (hidden, just for analysis)
            CameraPreviewWithAnalysis(
                blockSize = state.blockSize,
                useFrontCamera = state.useFrontCamera,
                onPixelGridReady = { grid ->
                    viewModel.updatePixelGrid(grid)
                }
            )
            
            // Cubist visualization overlay with acid patterns
            CubistCanvas(
                pixelGrid = state.pixelGrid,
                selectedPixel = state.selectedPixel,
                selectionMode = state.selectionMode,
                cutoffValue = state.synthState.cutoff,
                acidModulation = state.acidModulation,
                acidPatternIndex = state.acidPatternIndex,
                modifier = Modifier.fillMaxSize(),
                onTouch = { x, y, isTouching, canvasWidth, canvasHeight ->
                    viewModel.setTouch(x, y, isTouching, canvasWidth, canvasHeight)
                },
                onDoubleTap = { x, y, width, height ->
                    // Check if any panel is open
                    val anyPanelOpen = state.showModesPanel || state.showScalesPanel || 
                                       state.showKeysPanel || state.showSynthPanel || 
                                       state.showAcidPanel || state.showPresetPanel
                    
                    if (anyPanelOpen) {
                        // Check if tap is outside all panel areas
                        val inModesArea = x < width * 0.35f && y < height * 0.5f
                        val inScalesArea = y < height * 0.15f
                        val inKeysArea = y > height * 0.7f && x > width * 0.1f && x < width * 0.9f
                        val inSynthArea = x > width * 0.65f && y < height * 0.6f
                        val inAcidArea = x < width * 0.35f && y > height * 0.5f
                        val inPresetArea = x > width * 0.65f && y > height * 0.5f
                        
                        val inAnyPanelArea = (state.showModesPanel && inModesArea) ||
                                             (state.showScalesPanel && inScalesArea) ||
                                             (state.showKeysPanel && inKeysArea) ||
                                             (state.showSynthPanel && inSynthArea) ||
                                             (state.showAcidPanel && inAcidArea) ||
                                             (state.showPresetPanel && inPresetArea)
                        
                        if (!inAnyPanelArea) {
                            // Close all panels when double-tapping outside
                            viewModel.setModesPanel(false)
                            viewModel.setScalesPanel(false)
                            viewModel.setKeysPanel(false)
                            viewModel.setSynthPanel(false)
                            viewModel.setAcidPanel(false)
                            viewModel.setPresetPanel(false)
                        }
                    } else {
                        // No panel open - toggle icons visibility
                        iconsVisible = !iconsVisible
                    }
                },
                onEdgeDrag = { x, y, width, height ->
                    // Left edge - modes panel (upper quarter)
                    viewModel.setModesPanel(x < width * 0.05f && y < height * 0.4f)
                    // Top edge - scales panel
                    viewModel.setScalesPanel(y < height * 0.05f)
                    // Bottom edge - keys panel
                    viewModel.setKeysPanel(y > height * 0.95f && x > width * 0.2f)
                    // Right edge upper - synth panel (upper half)
                    viewModel.setSynthPanel(x > width * 0.95f && y < height * 0.5f)
                    // Left edge lower - acid panel (around 3/4 height)
                    viewModel.setAcidPanel(x < width * 0.1f && y > height * 0.6f && y < height * 0.9f)
                    // Right edge lower - preset panel (around 3/4 height)
                    viewModel.setPresetPanel(x > width * 0.9f && y > height * 0.6f && y < height * 0.9f)
                }
            )
            
            
            // Modes Panel (Left edge)
            AnimatedVisibility(
                visible = state.showModesPanel,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                ModesPanel(
                    currentMode = state.selectionMode,
                    useFrontCamera = state.useFrontCamera,
                    onModeSelected = { viewModel.setSelectionMode(it) },
                    onToggleCamera = { viewModel.toggleCamera() }
                )
            }
            
            // Scales Panel (Top edge)
            AnimatedVisibility(
                visible = state.showScalesPanel,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ScalesPanel(
                    currentScale = state.musicState.scaleIndex,
                    onScaleSelected = { viewModel.setScale(it) }
                )
            }
            
            // Keys Panel (Bottom edge)
            AnimatedVisibility(
                visible = state.showKeysPanel,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                KeysPanel(
                    currentKey = state.musicState.keyIndex,
                    currentOctave = state.musicState.octaveIndex,
                    currentFigure = state.musicState.figureIndex,
                    tempo = state.musicState.tempo,
                    onKeySelected = { viewModel.setKey(it) },
                    onOctaveSelected = { viewModel.setOctave(it) },
                    onFigureSelected = { viewModel.setFigure(it) },
                    onTapTempo = {
                        val currentTime = System.currentTimeMillis()
                        viewModel.tapTempo(currentTime, lastTapTime)
                        lastTapTime = currentTime
                    }
                )
            }
            
            // Synth Panel (Right edge)
            AnimatedVisibility(
                visible = state.showSynthPanel,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                SynthPanel(
                    synthState = state.synthState,
                    onSynthStateChange = { viewModel.updateSynthState(it) }
                )
            }
            
            // Acid Panel (Left side, above bottom) - slides in/out
            AnimatedVisibility(
                visible = state.showAcidPanel,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 80.dp)  // Move up to avoid piano roll icon overlap
            ) {
                AcidPanel(
                    acidModulation = state.acidModulation,
                    acidPatternIndex = state.acidPatternIndex,
                    onToggleAcid = { viewModel.toggleAcid() },
                    onPatternSelected = { viewModel.setAcidPattern(it) },
                    onModulationChanged = { newModulation -> viewModel.setAcidModulation(newModulation) }
                )
            }
            
            // Preset Panel (Right edge, below synth)
            AnimatedVisibility(
                visible = state.showPresetPanel,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 80.dp)
            ) {
                PresetPanel(
                    presetNames = state.presetNames,
                    onSavePreset = { viewModel.savePreset(it) },
                    onLoadPreset = { viewModel.loadPreset(it) },
                    onDeletePreset = { viewModel.deletePreset(it) }
                )
            }
            
            // Touch hint indicators at edges - hide all icons when any panel is open
            val anyPanelOpen = state.showModesPanel || state.showScalesPanel || 
                               state.showKeysPanel || state.showSynthPanel || state.showAcidPanel ||
                               state.showPresetPanel
            
            if (iconsVisible && !anyPanelOpen) {
                EdgeHints(
                    onModesClick = { viewModel.setModesPanel(true) },
                    onScalesClick = { viewModel.setScalesPanel(true) },
                    onKeysClick = { viewModel.setKeysPanel(true) },
                    onSynthClick = { viewModel.setSynthPanel(true) },
                    onAcidClick = { viewModel.setAcidPanel(true) },
                    onPresetClick = { viewModel.setPresetPanel(true) }
                )
            }
            
        } else {
            // Permission not granted
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Camera permission required\nTap to grant",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.pointerInput(Unit) {
                        detectDragGestures { _, _ ->
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewWithAnalysis(
    blockSize: Int,
    useFrontCamera: Boolean,
    onPixelGridReady: (com.example.trencadisapp.camera.PixelGrid) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
    
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .absoluteOffset(x = (-10000).dp), // Hide off-screen but keep active
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                
                // Mirror only for front camera (selfie mode)
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            cameraExecutor,
                            CameraPixelAnalyzer(
                                blockSize = blockSize,
                                mirrorHorizontally = useFrontCamera,
                                onPixelGridReady = onPixelGridReady
                            )
                        )
                    }
                
                val cameraSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    // Handle camera binding failure
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

@Composable
private fun EdgeHints(
    onModesClick: () -> Unit = {},
    onScalesClick: () -> Unit = {},
    onKeysClick: () -> Unit = {},
    onSynthClick: () -> Unit = {},
    onAcidClick: () -> Unit = {},
    onPresetClick: () -> Unit = {},
    showModes: Boolean = true,
    showScales: Boolean = true,
    showKeys: Boolean = true,
    showSynth: Boolean = true,
    showAcid: Boolean = true,
    showPreset: Boolean = true
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val quarterHeight = maxHeight * 0.25f
        val threeQuarterHeight = maxHeight * 0.75f
        
        // Left hint - Camera/Modes at 1/4 height (camera icon)
        if (showModes) {
            PanelIconButton(
                icon = "📷",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = quarterHeight - 24.dp),
                onClick = onModesClick
            )
        }
        
        // Left hint - Acid at 3/4 height (spiral)
        if (showAcid) {
            PanelIconButton(
                icon = "🌀",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = threeQuarterHeight - 24.dp),
                onClick = onAcidClick
            )
        }
        
        // Top hint - Scales (treble clef / sol key)
        if (showScales) {
            PanelIconButton(
                icon = "𝄞",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                onClick = onScalesClick
            )
        }
        
        // Bottom hint - Keys/Piano roll (eighth note)
        if (showKeys) {
            PanelIconButton(
                icon = "♪",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                onClick = onKeysClick
            )
        }
        
        // Right hint - Synth (waveform ~) at 1/4 height
        if (showSynth) {
            PanelIconButton(
                icon = "∿",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = quarterHeight - 24.dp),
                onClick = onSynthClick
            )
        }
        
        // Right hint - Presets (diskette) at 3/4 height
        if (showPreset) {
            PanelIconButton(
                icon = "💾",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = threeQuarterHeight - 24.dp),
                onClick = onPresetClick
            )
        }
    }
}

@Composable
private fun PanelIconButton(
    icon: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(
                Color.Black.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = 24.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}
