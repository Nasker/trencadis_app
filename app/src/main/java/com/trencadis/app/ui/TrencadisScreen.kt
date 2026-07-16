package com.trencadis.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
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
import com.trencadis.app.TrencadisViewModel
import com.trencadis.app.camera.CameraPixelAnalyzer
import com.trencadis.app.camera.PixelSelectionMode
import com.trencadis.app.ui.components.RhythmPanel
import com.trencadis.app.ui.components.ModesPanel
import com.trencadis.app.ui.components.PalettePanel
import com.trencadis.app.ui.components.ScalesPanel
import com.trencadis.app.midi.MidiOutputMode
import com.trencadis.app.midi.MidiState
import com.trencadis.app.ui.components.SynthPanel
import com.trencadis.app.ui.components.PresetPanel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
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

    // BLE permissions (only needed on Android 12+)
    val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        rememberMultiplePermissionsState(listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ))
    } else null

    // Request camera permission on first launch
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    // Initialize audio when camera permission is granted
    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted && !state.isAudioInitialized) {
            viewModel.initializeAudio()
        }
    }

    // Only enable BLE automatically if the user already tried to toggle it on but
    // was blocked waiting for permission — track that intent with this flag.
    var pendingBleEnable by remember { mutableStateOf(false) }
    if (blePermissions != null) {
        LaunchedEffect(blePermissions.allPermissionsGranted) {
            if (blePermissions.allPermissionsGranted && pendingBleEnable) {
                pendingBleEnable = false
                viewModel.setBleEnabled(true)
            }
        }
    }
    
    // Tap tempo state
    var lastTapTime by remember { mutableLongStateOf(0L) }
    
    // Hide/show icons with two-finger tap
    var iconsVisible by remember { mutableStateOf(true) }

    // In pointer mode the play surface owns touch, so keep icons visible for panel access
    // and disable icon-toggling via the canvas double-tap.
    LaunchedEffect(state.selectionMode) {
        if (state.selectionMode == PixelSelectionMode.POINTER) {
            iconsVisible = true
        }
    }

    // Stable reference to the live camera analyzer, used to trigger still capture
    // from the capture button without threading state through the camera composable.
    val analyzerRef = remember { AtomicReference<CameraPixelAnalyzer?>(null) }
    // Mirrors the analyzer's frozen/live status so the UI can reflect it.
    var isFrameFrozen by remember { mutableStateOf(false) }
    // The analyzer is recreated when these change (see key() below), which drops
    // any freeze it was holding — keep the UI mirror in sync.
    LaunchedEffect(state.blockSize, state.useFrontCamera, state.screenAspectRatio) {
        isFrameFrozen = false
    }
    
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
                mediaCaptureManager = viewModel.mediaCaptureManager,
                analyzerRef = analyzerRef,
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
                envelopeTrail = state.envelopeTrail,
                modifier = Modifier.fillMaxSize(),
                onTouch = { x, y, isTouching, canvasWidth, canvasHeight ->
                    viewModel.setTouch(x, y, isTouching, canvasWidth, canvasHeight)
                },
                onDoubleTap = { x, y, width, height ->
                    // In pointer mode the canvas is a play surface; don't treat touches as
                    // panel-management gestures.
                    if (state.selectionMode != PixelSelectionMode.POINTER) {
                        // Check if any panel is open
                        val anyPanelOpen = state.showModesPanel || state.showScalesPanel ||
                                           state.showKeysPanel || state.showSynthPanel ||
                                           state.showPalettePanel || state.showPresetPanel

                        if (anyPanelOpen) {
                            // Check if tap is outside all panel areas
                            val inModesArea = x < width * 0.35f && y < height * 0.5f
                            val inScalesArea = y < height * 0.35f
                            val inKeysArea = y > height * 0.7f && x > width * 0.1f && x < width * 0.9f
                            val inSynthArea = x > width * 0.65f && y < height * 0.6f
                            val inPaletteArea = x < width * 0.35f && y > height * 0.4f
                            val inPresetArea = x > width * 0.65f && y > height * 0.5f

                            val inAnyPanelArea = (state.showModesPanel && inModesArea) ||
                                                 (state.showScalesPanel && inScalesArea) ||
                                                 (state.showKeysPanel && inKeysArea) ||
                                                 (state.showSynthPanel && inSynthArea) ||
                                                 (state.showPalettePanel && inPaletteArea) ||
                                                 (state.showPresetPanel && inPresetArea)

                            if (!inAnyPanelArea) {
                                // Close all panels when double-tapping outside
                                viewModel.setModesPanel(false)
                                viewModel.setScalesPanel(false)
                                viewModel.setKeysPanel(false)
                                viewModel.setSynthPanel(false)
                                viewModel.setPalettePanel(false)
                                viewModel.setPresetPanel(false)
                            }
                        } else {
                            // No panel open - toggle icons visibility
                            iconsVisible = !iconsVisible
                        }
                    }
                },
                onEdgeDrag = { x, y, width, height ->
                    // Pointer mode and sequence mode own the canvas surface; don't open panels from drags.
                    if (state.selectionMode != PixelSelectionMode.POINTER && state.selectionMode != PixelSelectionMode.SEQUENCE) {
                        // Left edge - modes panel (upper quarter)
                        viewModel.setModesPanel(x < width * 0.05f && y < height * 0.35f)
                        // Left edge middle/lower - palette panel (blob + acid unified)
                        viewModel.setPalettePanel(x < width * 0.1f && y >= height * 0.5f && y < height * 0.9f)
                        // Top edge - scales panel (sticky)
                        if (y < height * 0.05f) viewModel.setScalesPanel(true)
                        // Bottom edge - keys/rhythm panel (sticky)
                        if (y > height * 0.95f && x > width * 0.2f) viewModel.setKeysPanel(true)
                        // Right edge upper - synth panel (upper half)
                        viewModel.setSynthPanel(x > width * 0.95f && y < height * 0.5f)
                        // Right edge lower - preset panel (around 3/4 height)
                        viewModel.setPresetPanel(x > width * 0.9f && y > height * 0.6f && y < height * 0.9f)
                    }
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
                    blockSize = state.blockSize,
                    isCustomGridResolution = state.customGridResolution != null,
                    minGridResolution = TrencadisViewModel.MIN_GRID_RESOLUTION,
                    maxGridResolution = TrencadisViewModel.MAX_GRID_RESOLUTION,
                    onModeSelected = { viewModel.setSelectionMode(it) },
                    onToggleCamera = { viewModel.toggleCamera() },
                    onGridResolutionChanged = { viewModel.setGridResolution(it) },
                    onGridResolutionReset = { viewModel.resetGridResolution() },
                    isFrameFrozen = isFrameFrozen,
                    onCaptureStill = {
                        if (isFrameFrozen) {
                            analyzerRef.get()?.resumeLiveCamera()
                            isFrameFrozen = false
                            android.widget.Toast.makeText(context, "Live camera resumed", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            analyzerRef.get()?.captureStillImage()
                            isFrameFrozen = true
                            android.widget.Toast.makeText(context, "Frame frozen & saved", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            
            // Scales Panel (Top edge): scale + root key + future chord selector
            AnimatedVisibility(
                visible = state.showScalesPanel,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ScalesPanel(
                    currentScale = state.musicState.scaleIndex,
                    currentKey = state.musicState.keyIndex,
                    currentChordType = state.musicState.chordTypeIndex,
                    useChordMapping = state.musicState.useChordMapping,
                    onScaleSelected = { viewModel.setScale(it) },
                    onKeySelected = { viewModel.setKey(it) },
                    onChordTypeSelected = { viewModel.setChordType(it) }
                )
            }

            // Rhythm Panel (Bottom edge): XY pad for figure/octave + tap tempo
            AnimatedVisibility(
                visible = state.showKeysPanel,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                RhythmPanel(
                    currentOctave = state.musicState.octaveIndex,
                    currentFigure = state.musicState.figureIndex,
                    tempo = state.musicState.tempo,
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
                    onSynthStateChange = { viewModel.updateSynthState(it) },
                    midiState = state.midiState,
                    onMidiEnabled = { viewModel.setMidiEnabled(it) },
                    onMidiOutputMode = { viewModel.setMidiOutputMode(it) },
                    onMidiChannel = { viewModel.setMidiChannel(it) },
                    onMidiBleEnabled = { enabled ->
                        if (!enabled) {
                            pendingBleEnable = false
                            viewModel.setBleEnabled(false)
                        } else if (blePermissions == null || blePermissions.allPermissionsGranted) {
                            viewModel.setBleEnabled(true)
                        } else {
                            pendingBleEnable = true
                            blePermissions.launchMultiplePermissionRequest()
                        }
                    }
                )
            }
            
            // Palette Panel (Left edge, middle/lower) — unified blob + acid controls
            AnimatedVisibility(
                visible = state.showPalettePanel,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                PalettePanel(
                    useBlobMode = state.useBlobMode,
                    blobModulation = state.blobModulation,
                    acidModulation = state.acidModulation,
                    acidPatternIndex = state.acidPatternIndex,
                    onToggleBlobMode = { viewModel.toggleBlobMode() },
                    onBlobModulationChanged = { viewModel.updateBlobModulation { _ -> it } },
                    onToggleAcid = { viewModel.toggleAcid() },
                    onPatternSelected = { viewModel.setAcidPattern(it) },
                    onAcidModulationChanged = { newModulation -> viewModel.setAcidModulation(newModulation) }
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

            // Touch hint indicators at edges. Side buttons hide whenever any panel is
            // open to avoid cluttering the middle. Top/bottom buttons hide only when
            // their own panel is open so the scales and rhythm panels can be toggled
            // together.
            val anyPanelOpen = state.showModesPanel || state.showScalesPanel || 
                               state.showKeysPanel || state.showSynthPanel || 
                               state.showPalettePanel || state.showPresetPanel
            if (iconsVisible) {
                EdgeHints(
                    midiState = state.midiState,
                    onModesClick = { viewModel.setModesPanel(true) },
                    onScalesClick = { viewModel.setScalesPanel(true) },
                    onKeysClick = { viewModel.setKeysPanel(true) },
                    onSynthClick = { viewModel.setSynthPanel(true) },
                    onPaletteClick = { viewModel.setPalettePanel(true) },
                    onPresetClick = { viewModel.setPresetPanel(true) },
                    showModes = !state.showModesPanel && !anyPanelOpen,
                    showScales = !state.showScalesPanel,
                    showKeys = !state.showKeysPanel,
                    showSynth = !state.showSynthPanel && !anyPanelOpen,
                    showPalette = !state.showPalettePanel && !anyPanelOpen,
                    showPreset = !state.showPresetPanel && !anyPanelOpen
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
    blobModulation: com.trencadis.app.ui.BlobModulation?,
    mediaCaptureManager: com.trencadis.app.media.RawMediaCaptureManager? = null,
    analyzerRef: AtomicReference<CameraPixelAnalyzer?> = remember { AtomicReference(null) },
    onPixelGridReady: (com.trencadis.app.camera.PixelGrid) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

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
                    onPixelGridReady = onPixelGridReady,
                    mediaCaptureManager = mediaCaptureManager,
                    coroutineScope = coroutineScope
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
    midiState: MidiState = MidiState(),
    onModesClick: () -> Unit = {},
    onScalesClick: () -> Unit = {},
    onKeysClick: () -> Unit = {},
    onSynthClick: () -> Unit = {},
    onPaletteClick: () -> Unit = {},
    onPresetClick: () -> Unit = {},
    showModes: Boolean = true,
    showScales: Boolean = true,
    showKeys: Boolean = true,
    showSynth: Boolean = true,
    showPalette: Boolean = true,
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
        
        // Left hint - Palette at 3/4 height (mosaic icon — unified blob + acid)
        if (showPalette) {
            PanelIconButton(
                icon = "🎨",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = threeQuarterHeight - 24.dp),
                onClick = onPaletteClick
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
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = quarterHeight - 24.dp)
            ) {
                PanelIconButton(
                    icon = "∿",
                    onClick = onSynthClick
                )
                val dotColor = when {
                    midiState.isClockLocked -> Color(0xFF00E5A0)
                    midiState.enabled       -> Color(0xFFFFD040)
                    else                    -> Color.Transparent
                }
                if (dotColor != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .background(dotColor, androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
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
