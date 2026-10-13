package com.openstudio.photo

import androidx.activity.ComponentActivity
import android.content.ContentValues
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
    private var rotation = 0f
    private var flipped = false
    private var brightness = 0f
    private var contrast = 1f
    private var saturation = 1f
    private var warmth = 0f

    private val pickImage = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) try {
            contentResolver.openInputStream(uri)?.use { original = BitmapFactory.decodeStream(it) }
            rotation = 0f; flipped = false; brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f
            renderPreview()
            status.text = "Photo loaded — edits stay on your device."
        } catch (_: Exception) { toast("Could not open this photo.") }
    }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildUi() }

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
        preview = ImageView(this).apply {
            adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(32, 35, 42)); contentDescription = "Photo preview"
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(10); bottomMargin = dp(8)
        })
        val scroll = ScrollView(this)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        controls.addView(sliderRow("Brightness", -100, 100, 0) { brightness = it / 100f; renderPreview() })
        controls.addView(sliderRow("Contrast", 50, 200, 100) { contrast = it / 100f; renderPreview() })
        controls.addView(sliderRow("Saturation", 0, 200, 100) { saturation = it / 100f; renderPreview() })
        controls.addView(sliderRow("Warmth", -50, 50, 0) { warmth = it.toFloat(); renderPreview() })
        val transformRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        transformRow.addView(button("Rotate") { rotation = (rotation + 90f) % 360f; renderPreview() }, weighted())
        transformRow.addView(button("Flip") { flipped = !flipped; renderPreview() }, weighted())
        transformRow.addView(button("Reset") {
            brightness = 0f; contrast = 1f; saturation = 1f; warmth = 0f
            rotation = 0f; flipped = false; renderPreview(); toast("Edits reset")
        }, weighted())
        controls.addView(transformRow)
        scroll.addView(controls)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(255)))
        setContentView(root)
    }

    private fun sliderRow(label: String, min: Int, max: Int, initial: Int, changed: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val caption = TextView(this).apply { text = "$label: $initial"; textSize = 14f; setTextColor(Color.WHITE) }
        val seek = SeekBar(this).apply {
            this.max = max - min; progress = initial - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + min
                    caption.text = "$label: $value"; changed(value)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        box.addView(caption); box.addView(seek); box.setPadding(0, dp(2), 0, dp(2))
        return box
    }

    private fun renderPreview() {
        val source = original ?: run { preview.setImageDrawable(null); status.text = "Tap Open photo to begin."; return }
        try { preview.setImageBitmap(processedBitmap(source, 1600)) }
        catch (_: Exception) { preview.setImageBitmap(source); status.text = "Preview issue — original photo retained." }
    }

    private fun processedBitmap(source: Bitmap, maxDimension: Int? = null): Bitmap {
        val base = if (maxDimension != null && maxOf(source.width, source.height) > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(source.width, source.height)
            Bitmap.createScaledBitmap(source, (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1), true)
        } else source
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
        val corrected = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        Canvas(corrected).drawBitmap(base, 0f, 0f, paint)
        val transform = Matrix().apply { postRotate(rotation); if (flipped) postScale(-1f, 1f) }
        val result = Bitmap.createBitmap(corrected, 0, 0, corrected.width, corrected.height, transform, true)
        if (base !== source && !base.isRecycled) base.recycle()
        if (corrected !== result && !corrected.isRecycled) corrected.recycle()
        return result
    }

    private fun saveImage() {
        val source = original ?: run { toast("Open a photo first."); return }
        status.text = "Saving photo…"
        Thread {
            try {
                val bitmap = processedBitmap(source)
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
                if (bitmap !== source && !bitmap.isRecycled) bitmap.recycle()
                runOnUiThread { status.text = "Saved to Pictures/OpenStudio"; toast("Photo saved successfully") }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Save failed"; toast("Could not save: " + (e.message ?: "unknown error")) }
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
