package com.example.trencadisapp.ui

import android.Manifest
import android.content.Intent
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trencadisapp.TrencadisViewModel
import com.example.trencadisapp.camera.CameraPixelAnalyzer
import com.example.trencadisapp.ui.components.AcidPanel
import com.example.trencadisapp.ui.components.BlobPanel
import com.example.trencadisapp.ui.components.KeysPanel
import com.example.trencadisapp.ui.components.ModesPanel
import com.example.trencadisapp.ui.components.ScalesPanel
import com.example.trencadisapp.ui.components.SynthPanel
import com.example.trencadisapp.ui.components.PresetPanel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TrencadisScreen(
    viewModel: TrencadisViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val rootView = LocalView.current

    // Read ratio from the root view after layout — it fills the full hardware screen
    // including behind system bars, so its dimensions are exact. Re-runs on rotation.
    DisposableEffect(configuration.orientation) {
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val w = rootView.rootView.width.toFloat()
            val h = rootView.rootView.height.toFloat()
            android.util.Log.d("TrencadisAR", "rootView $w x $h ratio=${w/h}")
            viewModel.updateScreenAspectRatio(w, h)
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            rootView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

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
                screenAspectRatio = state.screenAspectRatio,
                blobModulation = if (state.useBlobMode) state.blobModulation else null,
                onPixelGridReady = { grid ->
                    viewModel.updatePixelGrid(grid)
                }
            )
            
            // Cubist visualization overlay with acid patterns
            CubistCanvas(
                pixelGrid = state.pixelGrid,
                selectedPixel = state.selectedPixel,
                selectionMode = state.selectionMode,
                blobModulation = if (state.useBlobMode) state.blobModulation else null,
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
                                       state.showAcidPanel || state.showBlobPanel || state.showPresetPanel
                    
                    if (anyPanelOpen) {
                        // Check if tap is outside all panel areas
                        val inModesArea = x < width * 0.35f && y < height * 0.5f
                        val inScalesArea = y < height * 0.15f
                        val inKeysArea = y > height * 0.7f && x > width * 0.1f && x < width * 0.9f
                        val inSynthArea = x > width * 0.65f && y < height * 0.6f
                        val inAcidArea = x < width * 0.35f && y > height * 0.5f
                        val inBlobArea = x < width * 0.35f && y >= height * 0.4f && y < height * 0.6f
                        val inPresetArea = x > width * 0.65f && y > height * 0.5f
                        
                        val inAnyPanelArea = (state.showModesPanel && inModesArea) ||
                                             (state.showScalesPanel && inScalesArea) ||
                                             (state.showKeysPanel && inKeysArea) ||
                                             (state.showSynthPanel && inSynthArea) ||
                                             (state.showAcidPanel && inAcidArea) ||
                                             (state.showBlobPanel && inBlobArea) ||
                                             (state.showPresetPanel && inPresetArea)
                        
                        if (!inAnyPanelArea) {
                            // Close all panels when double-tapping outside
                            viewModel.setModesPanel(false)
                            viewModel.setScalesPanel(false)
                            viewModel.setKeysPanel(false)
                            viewModel.setSynthPanel(false)
                            viewModel.setAcidPanel(false)
                            viewModel.setBlobPanel(false)
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
                    // Left edge middle - blob panel
                    viewModel.setBlobPanel(x < width * 0.08f && y >= height * 0.4f && y < height * 0.6f)
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
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                ModesPanel(
                    currentMode = state.selectionMode,
                    useFrontCamera = state.useFrontCamera,
                    useBlobMode = state.useBlobMode,
                    onModeSelected = { viewModel.setSelectionMode(it) },
                    onToggleCamera = { viewModel.toggleCamera() },
                    onToggleBlobMode = { viewModel.toggleBlobMode() }
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
                    onDeletePreset = { viewModel.deletePreset(it) },
                    onSharePreset = { name ->
                        viewModel.getShareIntent(name)?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "Share Preset"))
                        }
                    }
                )
            }

            // Blob Panel (Left edge, middle) - cubist blob/mosaic controls
            AnimatedVisibility(
                visible = state.showBlobPanel,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                BlobPanel(
                    blobModulation = state.blobModulation,
                    onModulationChanged = { viewModel.updateBlobModulation { _ -> it } }
                )
            }
            
            // Touch hint indicators at edges - hide all icons when any panel is open
            val anyPanelOpen = state.showModesPanel || state.showScalesPanel || 
                               state.showKeysPanel || state.showSynthPanel || state.showAcidPanel ||
                               state.showBlobPanel || state.showPresetPanel
            
            if (iconsVisible && !anyPanelOpen) {
                EdgeHints(
                    onModesClick = { viewModel.setModesPanel(true) },
                    onScalesClick = { viewModel.setScalesPanel(true) },
                    onKeysClick = { viewModel.setKeysPanel(true) },
                    onSynthClick = { viewModel.setSynthPanel(true) },
                    onAcidClick = { viewModel.setAcidPanel(true) },
                    onBlobClick = { viewModel.setBlobPanel(true) },
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
    screenAspectRatio: Float,
    blobModulation: com.example.trencadisapp.ui.BlobModulation?,
    onPixelGridReady: (com.example.trencadisapp.camera.PixelGrid) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    // Stable reference to the current analyzer; updated in-place for blob params
    val analyzerRef = remember { AtomicReference<CameraPixelAnalyzer?>(null) }

    // Hot-swap blob modulation on every recompose — no camera rebind required
    SideEffect { analyzerRef.get()?.blobModulation = blobModulation }

    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    // key() forces AndroidView recreation (and camera rebind) ONLY when hardware params change.
    // blobModulation is intentionally excluded from the key.
    key(blockSize, useFrontCamera, screenAspectRatio) {
        AndroidView(
            factory = { ctx ->
                val newAnalyzer = CameraPixelAnalyzer(
                    blockSize = blockSize,
                    mirrorHorizontally = useFrontCamera,
                    screenAspectRatio = screenAspectRatio,
                    blobModulation = blobModulation,
                    onPixelGridReady = onPixelGridReady
                )
                analyzerRef.set(newAnalyzer)

                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(cameraExecutor, newAnalyzer) }
                    val cameraSelector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                                         else CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageAnalysis
                        )
                    } catch (e: Exception) { }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .absoluteOffset(x = (-10000).dp)
        )
    }
}

@Composable
private fun EdgeHints(
    onModesClick: () -> Unit = {},
    onScalesClick: () -> Unit = {},
    onKeysClick: () -> Unit = {},
    onSynthClick: () -> Unit = {},
    onAcidClick: () -> Unit = {},
    onBlobClick: () -> Unit = {},
    onPresetClick: () -> Unit = {},
    showModes: Boolean = true,
    showScales: Boolean = true,
    showKeys: Boolean = true,
    showSynth: Boolean = true,
    showAcid: Boolean = true,
    showBlob: Boolean = true,
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
        
        // Left hint - Blob at 1/2 height (mosaic icon)
        if (showBlob) {
            PanelIconButton(
                icon = "🎨",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = maxHeight / 2 - 24.dp),
                onClick = onBlobClick
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
