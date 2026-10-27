package com.example.vivocam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: FrameLayout
    private lateinit var captureRing: View
    private lateinit var modeLabel: TextView
    private var zoomLabel: TextView? = null
    private lateinit var galleryButton: TextView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var currentMode = "PHOTO"
    private var isRecording = false
    private var zoom = 1f

    private val modes = listOf("PRO", "VIDEO", "PHOTO", "PORTRAIT", "NIGHT", "LONG EXP", "CLONE", "360°", "CINEMA", "ASTRO")
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.CAMERA] == true) startCamera()
        else Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        cameraExecutor = Executors.newSingleThreadExecutor()
        buildUi()
        if (hasPermissions()) startCamera() else permissionLauncher.launch(requiredPermissions())
    }

    private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) else arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private fun hasPermissions() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 9, 11)) }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        val overlay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)) }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        top.addView(textButton("⚡", 22) { Toast.makeText(this@MainActivity, "Flash control depends on the active camera", Toast.LENGTH_SHORT).show() }, weightParams())
        top.addView(textButton("HDR", 13) { Toast.makeText(this@MainActivity, "HDR is controlled by CameraX/device capabilities", Toast.LENGTH_SHORT).show() }, weightParams())
        top.addView(textButton("⚙", 22) { showSettings() }, weightParams())
        overlay.addView(top, LinearLayout.LayoutParams(-1, dp(42)))
        overlay.addView(space(1, 0), LinearLayout.LayoutParams(1, 0, 1f))

        val zoomRow = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        listOf("0.6×", "1×", "2×", "5×").forEach { label ->
            zoomRow.addView(textButton(label, 14) {
                val target = label.removeSuffix("×").toFloatOrNull() ?: 1f
                setZoom(target)
            }.apply { setBackgroundColor(if (label == "1×") 0xAA202329.toInt() else 0x55202329) }, LinearLayout.LayoutParams(dp(58), dp(38)).apply { setMargins(dp(5), 0, dp(5), 0) })
        }
        overlay.addView(zoomRow, LinearLayout.LayoutParams(-1, dp(44)))
        overlay.addView(space(1, 0), LinearLayout.LayoutParams(1, 0, .3f))

        modeLabel = TextView(this).apply { text = "PHOTO  ·  Auto"; setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER; letterSpacing = .08f }
        overlay.addView(modeLabel, LinearLayout.LayoutParams(-1, dp(24)))
        val modeScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false }
        val modeRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        modes.forEach { mode ->
            val item = TextView(this).apply {
                text = mode; textSize = 12f; gravity = Gravity.CENTER; setPadding(dp(12), 0, dp(12), 0)
                setTextColor(if (mode == currentMode) 0xFFD8F36A.toInt() else Color.WHITE)
                setOnClickListener { selectMode(mode, modeRow) }
            }
            modeRow.addView(item, LinearLayout.LayoutParams(-2, dp(42)))
        }
        modeScroll.addView(modeRow)
        overlay.addView(modeScroll, LinearLayout.LayoutParams(-1, dp(44)))

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(4)) }
        galleryButton = TextView(this).apply { text = "▧"; textSize = 27f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { openGallery() } }
        bottom.addView(galleryButton, LinearLayout.LayoutParams(0, dp(68), 1f))
        captureButton = FrameLayout(this).apply {
            setOnClickListener { onCapturePressed() }
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dp(3), Color.WHITE) }
        }
        captureRing = View(this).apply { background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0xFFD8F36A.toInt()) } }
        captureButton.addView(captureRing, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER))
        bottom.addView(captureButton, LinearLayout.LayoutParams(dp(76), dp(76)))
        bottom.addView(textButton("↻", 28) { switchCamera() }, LinearLayout.LayoutParams(0, dp(68), 1f))
        overlay.addView(bottom, LinearLayout.LayoutParams(-1, dp(86)))
        // overlay was already attached to root above; do not add it a second time.
        setContentView(root)
    }

    private fun selectMode(mode: String, row: LinearLayout) {
        currentMode = mode
        modeLabel.text = when (mode) {
            "LONG EXP" -> "LONG EXPOSURE  ·  Tripod recommended"
            "ASTRO" -> "ASTRO  ·  Tripod + dark sky recommended"
            "CLONE" -> "CLONE  ·  Capture multiple poses"
            "360°" -> "360°  ·  Sweep slowly around yourself"
            "CINEMA" -> "CINEMATIC VIDEO  ·  Shallow depth effect varies by device"
            else -> mode
        }
        for (i in 0 until row.childCount) {
            val v = row.getChildAt(i) as TextView
            v.setTextColor(if (v.text.toString() == mode) 0xFFD8F36A.toInt() else Color.WHITE)
        }
        val isVideo = mode == "VIDEO" || mode == "CINEMA"
        captureRing.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(if (isVideo) 0xFFE84B4B.toInt() else 0xFFD8F36A.toInt())
        }
        if (mode in listOf("LONG EXP", "ASTRO", "CLONE", "360°", "CINEMA", "PRO", "PORTRAIT", "NIGHT")) {
            val message = when (mode) {
                "LONG EXP" -> "Long-exposure capture is a planned processing mode. This starter currently takes standard CameraX photos; true multi-second exposure needs supported manual sensor controls or frame stacking."
                "ASTRO" -> "Astro stacking / Milky Way / M31 processing is not implemented yet. The phone's sensor and lens limit deep-sky detail; use a tripod and manual capture pipeline."
                "CLONE" -> "Clone compositing is not implemented yet. It requires multiple aligned frames and a masking/compositing editor."
                "360°" -> "360° stitching is not implemented yet. This mode needs guided overlapping captures and panorama stitching."
                "CINEMA" -> "Cinematic recording currently uses standard video capture; depth-based subject separation and cinematic grading need an additional pipeline."
                else -> "$mode is currently a UI preset. Device-specific tuning can be added next."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.FHD)).build()
            videoCapture = VideoCapture.withOutput(recorder)
            try {
                provider.unbindAll()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                camera = provider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture)
                setZoom(zoom)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not start camera: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onCapturePressed() {
        if (currentMode == "VIDEO" || currentMode == "CINEMA") {
            if (isRecording) stopRecording() else startRecording()
        } else takePhoto()
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val name = "VivoCam_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg"); if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VivoCam") }
        val options = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                output.savedUri?.let { updateGalleryButton(it) }
                Toast.makeText(this@MainActivity, "Photo saved", Toast.LENGTH_SHORT).show()
            }
            override fun onError(exception: ImageCaptureException) { Toast.makeText(this@MainActivity, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show() }
        })
    }

    private fun startRecording() {
        val capture = videoCapture ?: return
        val name = "VivoCam_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4"); if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VivoCam") }
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        var pending = capture.output.prepareRecording(this, output)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> { isRecording = true; captureRing.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0xFFE84B4B.toInt()) } }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false; recording = null
                    captureRing.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0xFFE84B4B.toInt()) }
                    if (!event.hasError()) { Toast.makeText(this, "Video saved", Toast.LENGTH_SHORT).show(); event.outputResults.outputUri.let { updateGalleryButton(it) } }
                    else Toast.makeText(this, "Video failed: ${event.error}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun stopRecording() { recording?.stop() }
    private fun setZoom(value: Float) {
        zoom = value
        camera?.let { c ->
            val state = c.cameraInfo.zoomState.value
            val clamped = value.coerceIn(state?.minZoomRatio ?: 1f, state?.maxZoomRatio ?: 1f)
            c.cameraControl.setZoomRatio(clamped)
            zoomLabel?.text = "${clamped}×"
        }
    }
    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        startCamera()
    }
    private fun updateGalleryButton(uri: Uri) { galleryButton.text = "▣"; galleryButton.contentDescription = "Latest media: $uri" }
    private fun openGallery() {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*"); addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
        try { startActivity(intent) } catch (_: Exception) { Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show() }
    }
    private fun showSettings() {
        val options = arrayOf("Camera app info", "Save location: Pictures/VivoCam", "Video quality: 1080p", "Advanced modes: roadmap in README")
        android.app.AlertDialog.Builder(this).setTitle("VivoCam settings").setItems(options) { _, which -> Toast.makeText(this, options[which], Toast.LENGTH_SHORT).show() }.setPositiveButton("Close", null).show()
    }
    private fun textButton(label: String, size: Int, action: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = size.toFloat(); gravity = Gravity.CENTER; setTextColor(Color.WHITE); isClickable = true; isFocusable = true
        setOnClickListener { action() }
    }
    private fun weightParams() = LinearLayout.LayoutParams(0, dp(42), 1f)
    private fun space(w: Int, h: Int) = View(this).apply { layoutParams = ViewGroup.LayoutParams(w, h) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown() }
}
