package com.trencadis.app.camera

import androidx.compose.ui.geometry.Offset
import com.trencadis.app.ui.BlobModulation

/**
 * Detects contiguous blobs of similar-color pixels in a PixelGrid and computes a
 * convex hull polygon for each blob. This produces the cubist mosaic effect:
 * adjacent pixels of similar color are merged into a single polygonal tile.
 *
 * Designed for per-frame execution at ~30fps. Allocations are kept minimal:
 * - flat IntArray/BooleanArray instead of 2D jagged arrays
 * - an IntArray manual stack encodes (x,y) as a single int (x*rows+y)
 * - 4 explicit if-branches instead of creating neighbor lists per step
 * - average color computed in a single BFS pass, no per-blob pixel list
 */
object BlobDetector {

    data class PixelBlob(
        val id: Int,
        val colorClass: Int,
        val hull: List<Offset>, // convex hull in grid-cell centre coordinates
        val center: Offset,
        val averageColor: PixelData
    )

    /** Quantize a pixel into a coarse color class in HSV space. */
    private fun colorClassOf(pixel: PixelData, hueBuckets: Int): Int {
        val hueBucket = (pixel.hue / 360f * hueBuckets).toInt() % hueBuckets
        val satBucket = if (pixel.saturation > 0.4f) 1 else 0
        val briBucket = if (pixel.brightness > 0.4f) 1 else 0
        return (hueBucket * 4) + (satBucket * 2) + briBucket
    }

    /** Detect blobs in the given pixel grid using the supplied modulation parameters. */
    fun detectBlobs(
        grid: PixelGrid,
        modulation: BlobModulation
    ): List<PixelBlob> {
        val hueBuckets = modulation.hueBuckets.coerceIn(1, 32)
        val minBlobSize = modulation.minBlobSize.coerceAtLeast(1)
        val maxBlobSize = modulation.maxBlobSize.coerceAtLeast(minBlobSize)
        val maxBlobs = modulation.maxBlobs.coerceAtLeast(1)
        val cols = grid.cols
        val rows = grid.rows
        val total = cols * rows
        val pixels = grid.pixels

        // Flat classMap: index = x*rows + y
        val classMap = IntArray(total)
        for (i in pixels.indices) {
            classMap[i] = colorClassOf(pixels[i], hueBuckets)
        }

        // BFS state: flat visited array + manual IntArray stack (no boxing)
        val visited = BooleanArray(total)
        val stack = IntArray(total) // each entry encodes: x*rows+y
        
        // Component list: store as IntArray to avoid Pair allocations.
        // Each entry encodes cell index = x*rows+y.
        val componentBuf = IntArray(total)

        // We'll store (size, firstPixelIndex, encodedCells[]) for each candidate.
        // To avoid a huge list-of-lists, build blobs directly when size >= minBlobSize.
        var nextId = 0
        val blobs = mutableListOf<PixelBlob>()

        for (startIdx in 0 until total) {
            if (visited[startIdx]) continue

            val targetClass = classMap[startIdx]
            visited[startIdx] = true
            var stackTop = 0
            var compSize = 0

            stack[stackTop++] = startIdx

            // Accumulators for average color — computed in one pass during BFS
            var sumR = 0.0; var sumG = 0.0; var sumB = 0.0
            var sumBri = 0.0; var sumHue = 0.0; var sumSat = 0.0
            var sumX = 0.0; var sumY = 0.0
            var firstPixel: PixelData? = null

            while (stackTop > 0) {
                val idx = stack[--stackTop]
                componentBuf[compSize++] = idx

                val px = pixels[idx]
                if (firstPixel == null) firstPixel = px
                sumR += px.red; sumG += px.green; sumB += px.blue
                sumBri += px.brightness; sumHue += px.hue; sumSat += px.saturation
                sumX += px.gridX; sumY += px.gridY

                // Expand 4 neighbours without creating any objects.
                // Stop growing once the component (drained + still stacked cells)
                // reaches maxBlobSize — leftover cells stay unvisited and form
                // their own blobs on later iterations, splitting oversized regions.
                if (compSize + stackTop >= maxBlobSize) continue
                val x = px.gridX; val y = px.gridY

                if (x > 0) {
                    val ni = idx - rows
                    if (!visited[ni] && classMap[ni] == targetClass) { visited[ni] = true; stack[stackTop++] = ni }
                }
                if (x < cols - 1) {
                    val ni = idx + rows
                    if (!visited[ni] && classMap[ni] == targetClass) { visited[ni] = true; stack[stackTop++] = ni }
                }
                if (y > 0) {
                    val ni = idx - 1
                    if (!visited[ni] && classMap[ni] == targetClass) { visited[ni] = true; stack[stackTop++] = ni }
                }
                if (y < rows - 1) {
                    val ni = idx + 1
                    if (!visited[ni] && classMap[ni] == targetClass) { visited[ni] = true; stack[stackTop++] = ni }
                }
            }

            if (compSize < minBlobSize) continue
            if (blobs.size >= maxBlobs) continue

            val n = compSize.toDouble()
            val avgR = (sumR / n).toFloat()
            val avgG = (sumG / n).toFloat()
            val avgB = (sumB / n).toFloat()

            val rep = firstPixel ?: continue
            val averageColor = rep.copy(
                red = avgR, green = avgG, blue = avgB,
                brightness = (sumBri / n).toFloat(),
                hue = (sumHue / n).toFloat(),
                saturation = (sumSat / n).toFloat()
            )
            val center = Offset((sumX / n).toFloat(), (sumY / n).toFloat())
            val hull = convexHull(componentBuf, compSize, pixels)

            blobs.add(
                PixelBlob(
                    id = nextId++,
                    colorClass = targetClass,
                    hull = hull,
                    center = center,
                    averageColor = averageColor
                )
            )
        }

        return blobs
    }

    /**
     * Convex hull of grid-cell centre points using Andrew's monotone chain.
     * Takes a pre-allocated buffer of encoded pixel indices to avoid extra allocations.
     */
    private fun convexHull(
        buf: IntArray,
        count: Int,
        pixels: List<PixelData>
    ): List<Offset> {
        if (count <= 3) {
            return (0 until count).map { i ->
                val px = pixels[buf[i]]
                Offset(px.gridX + 0.5f, px.gridY + 0.5f)
            }
        }

        val sorted = (0 until count)
            .map { i -> val px = pixels[buf[i]]; Offset(px.gridX + 0.5f, px.gridY + 0.5f) }
            .sortedWith(compareBy({ it.x }, { it.y }))

        fun cross(o: Offset, a: Offset, b: Offset): Float =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

        val lower = ArrayList<Offset>(count)
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0)
                lower.removeAt(lower.size - 1)
            lower.add(p)
        }

        val upper = ArrayList<Offset>(count)
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0)
                upper.removeAt(upper.size - 1)
            upper.add(p)
        }

        if (lower.isNotEmpty()) lower.removeAt(lower.size - 1)
        if (upper.isNotEmpty()) upper.removeAt(upper.size - 1)
        lower.addAll(upper)
        return simplifyConvexHull(lower)
    }

    /**
     * Remove near-collinear hull vertices so only genuine corners remain.
     * A point is kept only if the cross product of (prev→curr) × (curr→next) exceeds
     * the epsilon threshold — i.e., the turn at that vertex is significant enough.
     * This produces fewer, longer straight edges → more angular / cubist appearance.
     */
    private fun simplifyConvexHull(hull: ArrayList<Offset>, epsilon: Float = 0.45f): List<Offset> {
        if (hull.size <= 3) return hull
        val result = ArrayList<Offset>(hull.size)
        val n = hull.size
        for (i in hull.indices) {
            val prev = hull[(i - 1 + n) % n]
            val curr = hull[i]
            val next = hull[(i + 1) % n]
            val cross = (curr.x - prev.x) * (next.y - curr.y) -
                        (curr.y - prev.y) * (next.x - curr.x)
            if (cross > epsilon) result.add(curr)
        }
        return if (result.size < 3) hull else result
    }
}
