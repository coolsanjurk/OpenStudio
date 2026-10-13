package com.openstudio.photo

/**
 * Optional Rust-backed pixel processing. If the native library is unavailable,
 * MainActivity uses its Kotlin/Android fallback implementation.
 */
internal class NativeImageProcessor private constructor() {
    external fun nativeApplyFilters(
        pixels: IntArray,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        warmth: Float
    )

    companion object {
        val instance = NativeImageProcessor()
        val isAvailable: Boolean = try {
            System.loadLibrary("openstudio_native")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
