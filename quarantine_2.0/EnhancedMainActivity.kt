package com.trencadis.app

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.trencadis.app.camera.CameraPreviewWithAnalysis
import com.trencadis.app.ui.EnhancedTrencadisScreen
import com.trencadis.app.ui.theme.TrencadisAppTheme
import com.trencadis.app.ui.components.CameraPermissionDialog

/**
 * Enhanced MainActivity for Trencadis 2.0
 * Integrates all new features while maintaining the immersive experience
 */
class EnhancedMainActivity : ComponentActivity() {
    
    private lateinit var enhancedViewModel: EnhancedTrencadisViewModel
    private var showPermissionDialog = false
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            showPermissionDialog = true
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up immersive edge-to-edge display
        enableEdgeToEdge()
        setupImmersiveMode()
        
        // Initialize enhanced view model
        enhancedViewModel = EnhancedTrencadisViewModel(application)
        
        // Check and request camera permissions
        checkCameraPermission()
        
        // Get screen dimensions
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = display
            display.getRealMetrics(displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            display.getMetrics(displayMetrics)
        }
        
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()
        enhancedViewModel.updateCanvasDimensions(screenWidth, screenHeight)
        
        // Set up enhanced camera analyzer
        setupCameraAnalyzer()
        
        // Set up Compose UI
        setContent {
            TrencadisAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showPermissionDialog) {
                        CameraPermissionDialog(
                            onRequestPermission = {
                                showPermissionDialog = false
                                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            },
                            onDismiss = { showPermissionDialog = false }
                        )
                    }
                    
                    // Enhanced Trencadis Screen with progressive disclosure
                    EnhancedTrencadisScreen(
                        viewModel = enhancedViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Hidden camera preview for analysis
                    CameraPreviewWithAnalysis(
                        analyzer = enhancedViewModel.getEnhancedCameraAnalyzer(),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
    
    /**
     * Set up immersive mode
     */
    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
    }
    
    /**
     * Check camera permission
     */
    private fun checkCameraPermission() {
        when {
            ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }
    
    /**
     * Set up enhanced camera analyzer
     */
    private fun setupCameraAnalyzer() {
        val screenAspectRatio = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            16f / 9f
        } else {
            9f / 16f
        }
        
        enhancedViewModel.setupEnhancedCameraAnalyzer(
            blockSize = 20,
            mirrorHorizontally = false,
            screenAspectRatio = screenAspectRatio,
            blobModulation = null, // Can be configured later
            onPixelGridReady = { pixelGrid ->
                enhancedViewModel.onPixelGridReady(pixelGrid)
            }
        )
    }
    
    override fun onResume() {
        super.onResume()
        // Re-enter immersive mode
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup handled by ViewModel's onCleared
    }
}
