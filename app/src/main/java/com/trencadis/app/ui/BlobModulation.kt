package com.trencadis.app.ui

/**
 * Tuning parameters for the cubist blob / mosaic effect.
 *
 * These are split between the blob detector (which builds the polygons)
 * and the canvas renderer (which draws them).
 */
data class BlobModulation(
    // How many hue divisions are used to group colors.
    // Lower values = bigger blobs, fewer polygons. Higher = smaller, more numerous blobs.
    val hueBuckets: Int = 16,

    // Minimum number of pixels required to form a blob.
    // Lower = more tiny fragments, higher = only larger regions.
    val minBlobSize: Int = 50,

    // Maximum number of pixels a single blob may grow to. Oversized uniform regions
    // are split into several blobs of at most this size, so no single polygon
    // dominates the screen.
    val maxBlobSize: Int = 800,

    // Maximum number of blobs to draw per frame (performance cap).
    val maxBlobs: Int = 500,

    // Crossfade between tiles and blobs. 0.0 = only tiles, 1.0 = only blobs.
    // At 0.5 both layers are drawn at 50% opacity for a smooth blend.
    val blobBlend: Float = 1.0f,

    // Width of the dark outline around each blob polygon.
    val outlineWidth: Float = 10f,

    // Opacity of the dark outline.
    val outlineAlpha: Float = 0.5f,

    // When true, draw blobs on top of the tile overlay; when false, draw them behind.
    val blobsOnTop: Boolean = true
) {
    companion object {
        val Defaults = BlobModulation()
    }
}
