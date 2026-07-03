package com.example.trencadisapp.ui

/**
 * Tuning parameters for the cubist blob / mosaic effect.
 *
 * These are split between the blob detector (which builds the polygons)
 * and the canvas renderer (which draws them).
 */
data class BlobModulation(
    // How many hue divisions are used to group colors.
    // Lower values = bigger blobs, fewer polygons. Higher = smaller, more numerous blobs.
    val hueBuckets: Int = 8,

    // Minimum number of pixels required to form a blob.
    // Lower = more tiny fragments, higher = only larger regions.
    val minBlobSize: Int = 2,

    // Maximum number of blobs to draw per frame (performance cap).
    val maxBlobs: Int = 300,

    // Opacity of the filled polygon. 1.0 = fully opaque, 0.0 = invisible.
    val blobAlpha: Float = 1.0f,

    // Width of the dark outline around each blob polygon.
    val outlineWidth: Float = 3f,

    // Opacity of the dark outline.
    val outlineAlpha: Float = 0.6f,

    // Opacity of the original per-pixel tile overlay drawn under the blobs.
    // 0.0 = only blobs, 1.0 = full original tile effect visible underneath.
    val tileOverlayAlpha: Float = 0.15f,

    // When true, draw blobs on top of the tile overlay; when false, draw them behind.
    val blobsOnTop: Boolean = true
) {
    companion object {
        val Defaults = BlobModulation()
    }
}
