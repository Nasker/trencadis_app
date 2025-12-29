package com.example.trencadisapp.ui

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import com.example.trencadisapp.ui.components.KeysPanel
import com.example.trencadisapp.ui.components.ModesPanel
import com.example.trencadisapp.ui.components.ScalesPanel
import com.example.trencadisapp.ui.components.SynthPanel
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
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (cameraPermissionState.status.isGranted) {
            // Camera preview (hidden, just for analysis)
            CameraPreviewWithAnalysis(
                blockSize = state.blockSize,
                onPixelGridReady = { grid ->
                    viewModel.updatePixelGrid(grid)
                }
            )
            
            // Cubist visualization overlay
            CubistCanvas(
                pixelGrid = state.pixelGrid,
                selectedPixel = state.selectedPixel,
                selectionMode = state.selectionMode,
                cutoffValue = state.synthState.cutoff,
                modifier = Modifier.fillMaxSize(),
                onTouch = { x, y, isTouching ->
                    viewModel.setTouch(x, y, isTouching)
                }
            )
            
            // Edge detection for panel visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                val x = change.position.x
                                val y = change.position.y
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                
                                // Left edge - modes panel
                                viewModel.setModesPanel(x < width * 0.05f)
                                // Top edge - scales panel
                                viewModel.setScalesPanel(y < height * 0.05f)
                                // Bottom edge - keys panel
                                viewModel.setKeysPanel(y > height * 0.95f)
                                // Right edge - synth panel
                                viewModel.setSynthPanel(x > width * 0.95f)
                            }
                        )
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
                    onModeSelected = { viewModel.setSelectionMode(it) }
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
            
            // Touch hint indicators at edges
            if (!state.showModesPanel && !state.showScalesPanel && 
                !state.showKeysPanel && !state.showSynthPanel) {
                EdgeHints()
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
                
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            cameraExecutor,
                            CameraPixelAnalyzer(blockSize, onPixelGridReady)
                        )
                    }
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
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
private fun EdgeHints() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Left hint
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .height(60.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )
        // Top hint
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(60.dp)
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )
        // Bottom hint
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(60.dp)
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )
        // Right hint
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(4.dp)
                .height(60.dp)
                .background(Color.White.copy(alpha = 0.3f))
        )
    }
}
