package com.openstudio.photo

import android.graphics.Bitmap
import kotlin.math.sqrt

/**
 * Deterministic, offline image effects. Work is chunked to avoid allocating a
 * second full-resolution pixel array on large phone photos.
 */
internal object ImageAlgorithms {
    /** Unsharp-mask approximation using a 4-neighbour Laplacian. Amount is 0..1. */
    fun sharpen(source: Bitmap, amount: Float): Bitmap {
        if (amount <= 0f) return source
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val rowsPerChunk = 96
        var y = 0
        while (y < height) {
            val rows = minOf(rowsPerChunk, height - y)
            val readTop = maxOf(0, y - 1)
            val readBottom = minOf(height, y + rows + 1)
            val readRows = readBottom - readTop
            val pixels = IntArray(width * readRows)
            source.getPixels(pixels, 0, width, 0, readTop, width, readRows)
            val out = IntArray(width * rows)
            for (localY in 0 until rows) {
                val imageY = y + localY
                val centerRow = imageY - readTop
                for (x in 0 until width) {
                    val c = pixels[centerRow * width + x]
                    if (x == 0 || x == width - 1 || imageY == 0 || imageY == height - 1) {
                        out[localY * width + x] = c
                        continue
                    }
                    val n = pixels[(centerRow - 1) * width + x]
                    val s = pixels[(centerRow + 1) * width + x]
                    val w = pixels[centerRow * width + x - 1]
                    val e = pixels[centerRow * width + x + 1]
                    val a = ((c ushr 24) and 255)
                    fun channel(shift: Int): Int {
                        val center = (c ushr shift) and 255
                        val neighbours = ((n ushr shift) and 255) + ((s ushr shift) and 255) +
                            ((w ushr shift) and 255) + ((e ushr shift) and 255)
                        return (center + amount * (4 * center - neighbours) / 2f).toInt().coerceIn(0, 255)
                    }
                    out[localY * width + x] = (a shl 24) or (channel(16) shl 16) or
                        (channel(8) shl 8) or channel(0)
                }
            }
            output.setPixels(out, 0, width, 0, y, width, rows)
            y += rows
        }
        return output
    }

    /** Subtle radial vignette; amount is -1..1 (negative lightens the edges). */
    fun vignette(bitmap: Bitmap, amount: Float) {
        if (amount == 0f) return
        val width = bitmap.width
        val height = bitmap.height
        val cx = (width - 1) / 2f
        val cy = (height - 1) / 2f
        val maxDistance = sqrt(cx * cx + cy * cy).coerceAtLeast(1f)
        val rowsPerChunk = 96
        var top = 0
        while (top < height) {
            val rows = minOf(rowsPerChunk, height - top)
            val pixels = IntArray(width * rows)
            bitmap.getPixels(pixels, 0, width, 0, top, width, rows)
            for (localY in 0 until rows) {
                val dy = ((top + localY) - cy) / maxDistance
                for (x in 0 until width) {
                    val dx = (x - cx) / maxDistance
                    val radius = sqrt(dx * dx + dy * dy).coerceIn(0f, 1f)
                    val falloff = radius * radius
                    val factor = (1f - amount * falloff * 0.72f).coerceIn(0.25f, 1.6f)
                    val i = localY * width + x
                    val c = pixels[i]
                    fun scale(shift: Int) = ((((c ushr shift) and 255) * factor).toInt()).coerceIn(0, 255)
                    pixels[i] = (c and -0x1000000) or (scale(16) shl 16) or
                        (scale(8) shl 8) or scale(0)
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, top, width, rows)
            top += rows
        }
    }
}
