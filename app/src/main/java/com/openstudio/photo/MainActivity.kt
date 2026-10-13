package com.openstudio.photo

import androidx.activity.ComponentActivity
import android.content.ContentValues
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.IOException
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private var original: Bitmap? = null
    // Keep track of the generated preview so its native pixel memory can be released
    // when the next slider movement produces a replacement.
    private var renderedPreview: Bitmap? = null
    private var rotation = 0f
    private var flipped = false
    private var brightness = 0f
    private var contrast = 1f
    private var saturation = 1f
    private var warmth = 0f
    private var sharpness = 0f
    private var vignette = 0f
    private var cropRatio: Float? = null
    private val textLayers = mutableListOf<String>()
    private val editHistory = EditHistory<EditorState>()

    private data class EditorState(
        val rotation: Float, val flipped: Boolean, val brightness: Float, val contrast: Float,
        val saturation: Float, val warmth: Float, val sharpness: Float, val vignette: Float,
        val cropRatio: Float?, val textLayers: List<String>
    )

    private fun currentEditorState() = EditorState(
        rotation, flipped, brightness, contrast, saturation, warmth, sharpness, vignette,
        cropRatio, textLayers.toList()
    )

    private fun recordUndo() {
        if (::preview.isInitialized && original != null) editHistory.record(currentEditorState())
    }

    private fun applyEditorState(state: EditorState) {
        rotation = state.rotation; flipped = state.flipped; brightness = state.brightness
        contrast = state.contrast; saturation = state.saturation; warmth = state.warmth
        sharpness = state.sharpness; vignette = state.vignette; cropRatio = state.cropRatio
        textLayers.clear(); textLayers.addAll(state.textLayers)
        renderPreview()
        status.text = "Edit restored"
    }

    private fun undoEdit() {
        val previous = editHistory.undo(currentEditorState())
        if (previous == null) toast("Nothing to undo.") else applyEditorState(previous)
    }

    private fun redoEdit() {
        val next = editHistory.redo(currentEditorState())
        if (next == null) toast("Nothing to redo.") else applyEditorState(next)
    }

    private val pickImage = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) try {
            val decoded = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: throw IOException("Image decoder returned no image")
            val previousOriginal = original
            original = decoded
            rotation = 0f; flipped = false; brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f; sharpness = 0f; vignette = 0f; cropRatio = null; textLayers.clear(); editHistory.clear()
            renderPreview()
            if (previousOriginal != null && previousOriginal !== decoded && !previousOriginal.isRecycled) previousOriginal.recycle()
            status.text = "Photo loaded — edits stay on your device."
        } catch (_: Exception) { toast("Could not open this photo.") }
    }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildUi() }

    override fun onDestroy() {
        // Avoid retaining full-size pixel buffers after the editor is closed.
        val previewBitmap = renderedPreview
        if (previewBitmap != null && previewBitmap !== original && !previewBitmap.isRecycled) previewBitmap.recycle()
        renderedPreview = null
        val source = original
        if (source != null && !source.isRecycled) source.recycle()
        original = null
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.rgb(18, 20, 25))
        }
        root.addView(TextView(this).apply {
            text = "OpenStudio"; textSize = 26f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(6))
        })
        status = TextView(this).apply {
            text = "Free • Offline • Private"; textSize = 13f; setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(status)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(button("Open photo") { pickImage.launch("image/*") }, weighted())
        buttons.addView(button("Save copy") { saveImage() }, weighted())
        root.addView(buttons)
        root.addView(button("Privacy / leak check") { runPrivacyCheck() }, LinearLayout.LayoutParams(-1, dp(48)))
        preview = ImageView(this).apply {
            adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(32, 35, 42)); contentDescription = "Photo preview"
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(10); bottomMargin = dp(8)
        })
        val scroll = ScrollView(this)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        controls.addView(sliderRow("Brightness", -100, 100, 0, beforeChange = { recordUndo() }) { brightness = it / 100f; renderPreview() })
        controls.addView(sliderRow("Contrast", 50, 200, 100, beforeChange = { recordUndo() }) { contrast = it / 100f; renderPreview() })
        controls.addView(sliderRow("Saturation", 0, 200, 100, beforeChange = { recordUndo() }) { saturation = it / 100f; renderPreview() })
        controls.addView(sliderRow("Warmth", -50, 50, 0, beforeChange = { recordUndo() }) { warmth = it.toFloat(); renderPreview() })
        controls.addView(sliderRow("Sharpen", 0, 100, 0, beforeChange = { recordUndo() }) { sharpness = it / 100f; renderPreview() })
        controls.addView(sliderRow("Vignette", -100, 100, 0, beforeChange = { recordUndo() }) { vignette = it / 100f; renderPreview() })
        val transformRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        transformRow.addView(button("Rotate") { recordUndo(); rotation = (rotation + 90f) % 360f; renderPreview() }, weighted())
        transformRow.addView(button("Flip") { recordUndo(); flipped = !flipped; renderPreview() }, weighted())
        transformRow.addView(button("Reset") {
            recordUndo()
            brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f
            sharpness = 0f; vignette = 0f; cropRatio = null; textLayers.clear()
            rotation = 0f; flipped = false; renderPreview(); toast("Edits reset")
        }, weighted())
        controls.addView(transformRow)
        val historyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        historyRow.addView(button("Undo") { undoEdit() }, weighted())
        historyRow.addView(button("Redo") { redoEdit() }, weighted())
        controls.addView(historyRow)
        val creativeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        creativeRow.addView(button("Crop") { showCropOptions() }, weighted())
        creativeRow.addView(button("Add text") { addTextLayer() }, weighted())
        controls.addView(creativeRow)
        scroll.addView(controls)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(255)))
        setContentView(root)
    }

    /** Fail-closed privacy smoke check for the APK's key platform-level safeguards. */
    private fun runPrivacyCheck() {
        val checks = listOf(
            "No Android Internet permission" to
                (checkSelfPermission(android.Manifest.permission.INTERNET) != android.content.pm.PackageManager.PERMISSION_GRANTED),
            "Android backup disabled" to
                (applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP == 0),
            "Cleartext network traffic disabled" to
                (applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC == 0)
        )
        val passed = checks.all { it.second }
        val report = checks.joinToString("\n") { (label, ok) -> "${if (ok) "PASS" else "FAIL"} — $label" } +
            "\n\n" + if (passed) {
                "Basic privacy checks passed. This is not a guarantee against every possible leak."
            } else {
                "Privacy check FAILED. Do not use this build with private photos; report this issue."
            }
        status.text = if (passed) "Privacy check passed • 3/3" else "PRIVACY CHECK FAILED"
        android.app.AlertDialog.Builder(this)
            .setTitle(if (passed) "Privacy check passed" else "Privacy warning")
            .setMessage(report)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun sliderRow(label: String, min: Int, max: Int, initial: Int, beforeChange: () -> Unit = {}, changed: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val caption = TextView(this).apply { text = "$label: $initial"; textSize = 14f; setTextColor(Color.WHITE) }
        val seek = SeekBar(this).apply {
            this.max = max - min; progress = initial - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + min
                    caption.text = "$label: $value"; changed(value)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) { beforeChange() }
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        box.addView(caption); box.addView(seek); box.setPadding(0, dp(2), 0, dp(2))
        return box
    }

    private fun renderPreview() {
        val source = original ?: run {
            preview.setImageDrawable(null)
            releaseRenderedPreview()
            status.text = "Tap Open photo to begin."
            return
        }
        try {
            val next = processedBitmap(source, 1600)
            val previous = renderedPreview
            preview.setImageBitmap(next)
            renderedPreview = next
            if (previous != null && previous !== source && previous !== next && !previous.isRecycled) previous.recycle()
        } catch (_: Exception) {
            preview.setImageBitmap(source)
            releaseRenderedPreview()
            status.text = "Preview issue — original photo retained."
        }
    }

    private fun releaseRenderedPreview() {
        val previous = renderedPreview
        renderedPreview = null
        if (previous != null && previous !== original && !previous.isRecycled) previous.recycle()
    }

    private fun processedBitmap(source: Bitmap, maxDimension: Int? = null): Bitmap {
        val base = if (maxDimension != null && maxOf(source.width, source.height) > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(source.width, source.height)
            Bitmap.createScaledBitmap(source, (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1), true)
        } else source
        val corrected = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        if (NativeImageProcessor.isAvailable) {
            // Process in row chunks to limit temporary memory use on high-resolution photos.
            val chunkRows = 128
            var top = 0
            while (top < base.height) {
                val rows = minOf(chunkRows, base.height - top)
                val pixels = IntArray(base.width * rows)
                base.getPixels(pixels, 0, base.width, 0, top, base.width, rows)
                NativeImageProcessor.instance.nativeApplyFilters(
                    pixels, brightness, contrast, saturation, warmth
                )
                corrected.setPixels(pixels, 0, base.width, 0, top, base.width, rows)
                top += rows
            }
        } else {
            val matrix = ColorMatrix()
            matrix.setSaturation(saturation)
            matrix.postConcat(ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness * 255f + warmth,
                0f, contrast, 0f, 0f, brightness * 255f,
                0f, 0f, contrast, 0f, brightness * 255f - warmth,
                0f, 0f, 0f, 1f, 0f
            )))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }
            Canvas(corrected).drawBitmap(base, 0f, 0f, paint)
        }
        var effected = corrected
        if (sharpness > 0f) {
            val sharpened = ImageAlgorithms.sharpen(effected, sharpness)
            if (effected !== sharpened && !effected.isRecycled) effected.recycle()
            effected = sharpened
        }
        if (vignette != 0f) ImageAlgorithms.vignette(effected, vignette)
        val transform = Matrix().apply { postRotate(rotation); if (flipped) postScale(-1f, 1f) }
        var result = Bitmap.createBitmap(effected, 0, 0, effected.width, effected.height, transform, true)
        if (base !== source && !base.isRecycled) base.recycle()
        if (effected !== result && !effected.isRecycled) effected.recycle()
        val ratio = cropRatio
        if (ratio != null && ratio > 0f) {
            val currentRatio = result.width.toFloat() / result.height
            val cropW: Int
            val cropH: Int
            if (currentRatio > ratio) { cropH = result.height; cropW = (cropH * ratio).toInt().coerceIn(1, result.width) }
            else { cropW = result.width; cropH = (cropW / ratio).toInt().coerceIn(1, result.height) }
            val cropped = Bitmap.createBitmap(result, (result.width - cropW) / 2, (result.height - cropH) / 2, cropW, cropH)
            if (cropped !== result && !result.isRecycled) result.recycle()
            result = cropped
        }
        if (textLayers.isNotEmpty()) {
            val withText = result.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(withText)
            textLayers.forEachIndexed { index, label ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = maxOf(28f, result.width * 0.055f)
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(4f, 2f, 2f, Color.BLACK)
                }
                canvas.drawText(label, result.width / 2f, result.height * (0.8f - index * 0.08f), paint)
            }
            if (!result.isRecycled) result.recycle()
            result = withText
        }
        return result
    }

    private fun showCropOptions() {
        val options = arrayOf("Reset crop", "Square 1:1", "Portrait 4:5", "Landscape 16:9")
        android.app.AlertDialog.Builder(this).setTitle("Center crop").setItems(options) { _, which ->
            recordUndo()
            cropRatio = when (which) { 0 -> null; 1 -> 1f; 2 -> 4f / 5f; else -> 16f / 9f }
            renderPreview()
        }.show()
    }

    private fun addTextLayer() {
        val input = EditText(this).apply { hint = "Text to overlay on photo" }
        android.app.AlertDialog.Builder(this).setTitle("Add text layer").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    recordUndo()
                    textLayers.add(value)
                    renderPreview()
                    status.text = "Text overlay added • " + textLayers.size + " layer(s)"
                }
            }.show()
    }

    private fun saveImage() {
        val source = original ?: run { toast("Open a photo first."); return }
        status.text = "Saving photo…"
        Thread {
            var bitmap: Bitmap? = null
            try {
                bitmap = processedBitmap(source)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "OpenStudio_" + System.currentTimeMillis() + ".jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenStudio")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Could not create image")
                contentResolver.openOutputStream(uri)?.use {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) throw IOException("Image encoding failed")
                } ?: throw IOException("Could not write image")
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                runOnUiThread { status.text = "Saved to Pictures/OpenStudio"; toast("Photo saved successfully") }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Save failed"; toast("Could not save: " + (e.message ?: "unknown error")) }
            } finally {
                val savedBitmap = bitmap
                if (savedBitmap != null && savedBitmap !== source && !savedBitmap.isRecycled) savedBitmap.recycle()
            }
        }.start()
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 12f; setOnClickListener { action() }
    }
    private fun weighted() = LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
