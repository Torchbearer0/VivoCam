package com.example.vivocam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: FrameLayout
    private lateinit var captureRing: View
    private lateinit var modeLabel: TextView
    private lateinit var galleryButton: TextView
    private lateinit var flashButton: TextView
    private lateinit var timerButton: TextView
    private lateinit var ratioButton: TextView
    private lateinit var gridButton: TextView
    private lateinit var zoomReadout: TextView
    private lateinit var gridOverlay: GridOverlay
    private lateinit var focusIndicator: FocusIndicator

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var currentMode = "PHOTO"
    private var isRecording = false
    private var isPaused = false
    private var zoom = 1f
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var timerSeconds = 0
    private var captureCountdownActive = false
    private var gridEnabled = false
    private var aspectRatio = AspectRatio.RATIO_4_3
    private var videoQuality = Quality.FHD
    private var lastMediaUri: Uri? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val modes = listOf("PRO", "VIDEO", "PHOTO", "PORTRAIT", "NIGHT", "LONG EXP", "CLONE", "360°", "CINEMA", "ASTRO")

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.CAMERA] == true) startCamera()
        else Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        buildUi()
        if (hasPermissions()) startCamera() else permissionLauncher.launch(requiredPermissions())
    }

    private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    } else {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    private fun hasPermissions() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 9, 11)) }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        gridOverlay = GridOverlay().apply { visibility = View.GONE; isClickable = false }
        root.addView(gridOverlay, FrameLayout.LayoutParams(-1, -1))
        focusIndicator = FocusIndicator().apply { visibility = View.GONE; isClickable = false }
        root.addView(focusIndicator, FrameLayout.LayoutParams(-1, -1))

        val overlay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(10)); isClickable = false }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        flashButton = textButton("⚡ OFF", 13) { cycleFlash() }
        top.addView(flashButton, weightParams())
        timerButton = textButton("Timer OFF", 11) { chooseTimer() }
        top.addView(timerButton, weightParams())
        ratioButton = textButton("4:3", 13) { chooseAspectRatio() }
        top.addView(ratioButton, weightParams())
        top.addView(textButton("⚙", 22) { showSettings() }, weightParams())
        overlay.addView(top, LinearLayout.LayoutParams(-1, dp(42)))
        overlay.addView(space(1, 0), LinearLayout.LayoutParams(1, 0, 1f))

        val zoomRow = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        listOf("0.6×", "1×", "2×", "5×").forEach { label ->
            zoomRow.addView(textButton(label, 14) {
                val target = label.removeSuffix("×").toFloatOrNull() ?: 1f
                setZoom(target)
            }.apply { setBackgroundColor(if (label == "1×") 0xAA202329.toInt() else 0x55202329) }, LinearLayout.LayoutParams(dp(54), dp(36)).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        zoomReadout = TextView(this).apply { text = "1.0×"; textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        zoomRow.addView(zoomReadout, LinearLayout.LayoutParams(dp(48), dp(36)))
        overlay.addView(zoomRow, LinearLayout.LayoutParams(-1, dp(40)))

        val utilityRow = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        utilityRow.addView(textButton("EV ±", 12) { showExposureCompensation() }, LinearLayout.LayoutParams(0, dp(34), 1f))
        gridButton = textButton("Grid OFF", 12) { gridEnabled = !gridEnabled; gridOverlay.visibility = if (gridEnabled) View.VISIBLE else View.GONE; gridButton.text = if (gridEnabled) "Grid ON" else "Grid OFF" }
        utilityRow.addView(gridButton, LinearLayout.LayoutParams(0, dp(34), 1f))
        utilityRow.addView(textButton("Focus", 12) { focusAtCenter() }, LinearLayout.LayoutParams(0, dp(34), 1f))
        utilityRow.addView(textButton("Quality", 12) { chooseVideoQuality() }, LinearLayout.LayoutParams(0, dp(34), 1f))
        overlay.addView(utilityRow, LinearLayout.LayoutParams(-1, dp(36)))
        overlay.addView(space(1, 0), LinearLayout.LayoutParams(1, 0, .3f))

        modeLabel = TextView(this).apply { text = "PHOTO · Auto"; setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER; letterSpacing = .06f }
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
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dp(3), Color.WHITE) }
        }
        captureRing = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFD8F36A.toInt()) } }
        captureButton.addView(captureRing, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER))
        bottom.addView(captureButton, LinearLayout.LayoutParams(dp(76), dp(76)))
        bottom.addView(textButton("↻", 28) { switchCamera() }, LinearLayout.LayoutParams(0, dp(68), 1f))
        overlay.addView(bottom, LinearLayout.LayoutParams(-1, dp(86)))
        installPreviewGestures()
        setContentView(root)
    }

    private fun selectMode(mode: String, row: LinearLayout) {
        if (isRecording && mode != "VIDEO" && mode != "CINEMA") stopRecording()
        currentMode = mode
        modeLabel.text = when (mode) {
            "LONG EXP" -> "LONG EXPOSURE · Tripod recommended"
            "ASTRO" -> "ASTRO · Tripod + dark sky recommended"
            "CLONE" -> "CLONE · Capture multiple poses"
            "360°" -> "360° · Sweep slowly around yourself"
            "CINEMA" -> "CINEMATIC VIDEO · Standard video on this build"
            "PRO" -> "PRO · Tap EV to adjust exposure"
            "NIGHT" -> "NIGHT · Keep the phone steady"
            "PORTRAIT" -> "PORTRAIT · Depth effect depends on device"
            else -> mode
        }
        for (i in 0 until row.childCount) {
            val v = row.getChildAt(i) as TextView
            v.setTextColor(if (v.text.toString() == mode) 0xFFD8F36A.toInt() else Color.WHITE)
        }
        val isVideo = mode == "VIDEO" || mode == "CINEMA"
        captureRing.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (isVideo) 0xFFE84B4B.toInt() else 0xFFD8F36A.toInt()) }
        if (mode in listOf("LONG EXP", "ASTRO", "CLONE", "360°", "CINEMA", "PRO", "PORTRAIT", "NIGHT")) {
            val message = when (mode) {
                "LONG EXP" -> "This build provides stable photo capture and exposure compensation. True multi-second shutter and light-trail stacking are planned separately."
                "ASTRO" -> "Astro stacking and Milky Way processing are not implemented yet. Use a tripod; results depend on sensor and lens limits."
                "CLONE" -> "Clone compositing needs multiple aligned frames and a masking editor; this mode is currently a guided placeholder."
                "360°" -> "360° stitching needs guided overlapping frames and panorama stitching; this mode is currently a placeholder."
                "CINEMA" -> "Video recording works, but depth-based subject separation is not included in this build."
                "PRO" -> "Use EV ± for exposure compensation, tap the preview to focus, and pinch to zoom. Manual ISO/shutter controls are not yet enabled."
                "PORTRAIT" -> "Portrait depth processing depends on device support and is not yet added."
                else -> "Night mode currently uses the standard quality photo pipeline; computational night stacking is not added yet."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(aspectRatio)
                .setFlashMode(flashMode)
                .build()
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(videoQuality, FallbackStrategy.lowerQualityOrHigherThan(videoQuality))).build()
            videoCapture = VideoCapture.withOutput(recorder)
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.Builder().requireLensFacing(lensFacing).build(), preview, imageCapture, videoCapture)
                updateFlashUi()
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
        if (captureCountdownActive || isRecording) return
        if (timerSeconds > 0) {
            val delaySeconds = timerSeconds
            captureCountdownActive = true
            captureButton.isEnabled = false
            modeLabel.text = "CAPTURING IN $delaySeconds…"
            var remaining = delaySeconds
            val countdown = object : Runnable {
                override fun run() {
                    remaining--
                    if (remaining > 0) {
                        modeLabel.text = "CAPTURING IN $remaining…"
                        mainHandler.postDelayed(this, 1000L)
                    } else {
                        captureCountdownActive = false
                        captureButton.isEnabled = true
                        modeLabel.text = "$currentMode · Auto"
                        takePhotoNow()
                    }
                }
            }
            mainHandler.postDelayed(countdown, 1000L)
        } else takePhotoNow()
    }

    private fun takePhotoNow() {
        val capture = imageCapture ?: return
        val name = "VivoCam_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VivoCam")
        }
        val options = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                output.savedUri?.let { updateGalleryButton(it) }
                Toast.makeText(this@MainActivity, "Photo saved to Pictures/VivoCam", Toast.LENGTH_SHORT).show()
            }
            override fun onError(exception: ImageCaptureException) { Toast.makeText(this@MainActivity, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show() }
        })
    }

    private fun startRecording() {
        val capture = videoCapture ?: return
        val name = "VivoCam_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VivoCam")
        }
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        var pending = capture.output.prepareRecording(this, output)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isRecording = true; isPaused = false
                    captureRing.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFE84B4B.toInt()) }
                    modeLabel.text = "RECORDING · tap shutter to stop"
                }
                is VideoRecordEvent.Status -> {
                    if (isRecording) modeLabel.text = (if (isPaused) "PAUSED" else "REC") + " · " + formatDuration(event.recordingStats.recordedDurationNanos)
                }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false; isPaused = false; recording = null
                    captureRing.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFE84B4B.toInt()) }
                    modeLabel.text = "$currentMode · Auto"
                    if (!event.hasError()) { Toast.makeText(this, "Video saved to Movies/VivoCam", Toast.LENGTH_SHORT).show(); updateGalleryButton(event.outputResults.outputUri) }
                    else Toast.makeText(this, "Video failed: ${event.error}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun formatDuration(nanos: Long): String {
        val total = TimeUnit.NANOSECONDS.toSeconds(nanos)
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }
    private fun stopRecording() { recording?.stop() }
    private fun setZoom(value: Float) {
        camera?.let { c ->
            val state = c.cameraInfo.zoomState.value
            val min = state?.minZoomRatio ?: 1f
            val max = state?.maxZoomRatio ?: 1f
            zoom = value.coerceIn(min, max)
            c.cameraControl.setZoomRatio(zoom)
            zoomReadout.text = String.format(Locale.US, "%.1f×", zoom)
        } ?: run { zoom = value.coerceAtLeast(1f) }
    }
    private fun switchCamera() {
        if (isRecording) { Toast.makeText(this, "Stop recording before switching cameras", Toast.LENGTH_SHORT).show(); return }
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        startCamera()
    }

    private fun installPreviewGestures() {
        var scaleStartZoom = 1f
        var wasScaling = false
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean { scaleStartZoom = zoom; wasScaling = true; return true }
            override fun onScale(detector: ScaleGestureDetector): Boolean { setZoom(scaleStartZoom * detector.scaleFactor); return true }
        })
        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                if (!wasScaling) focusAt(event.x, event.y)
                wasScaling = false
            }
            true
        }
    }
    private fun focusAtCenter() { focusAt(previewView.width / 2f, previewView.height / 2f) }
    private fun focusAt(x: Float, y: Float) {
        val activeCamera = camera ?: return
        val point: MeteringPoint = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        activeCamera.cameraControl.startFocusAndMetering(action)
        focusIndicator.showAt(x, y)
    }

    private fun updateGalleryButton(uri: Uri) { lastMediaUri = uri; galleryButton.text = "▣"; galleryButton.contentDescription = "Open latest VivoCam media" }
    private fun openGallery() {
        val uri = lastMediaUri
        try {
            if (uri != null) {
                val type = if (uri.toString().contains("video", true) || uri.toString().endsWith("mp4")) "video/*" else "image/*"
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } else {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*"))
            }
        } catch (_: Exception) { Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show() }
    }

    private fun cycleFlash() {
        val available = camera?.cameraInfo?.hasFlashUnit() == true
        if (!available) { Toast.makeText(this, "Flash is unavailable on this camera", Toast.LENGTH_SHORT).show(); return }
        flashMode = when (flashMode) { ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO; ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON; else -> ImageCapture.FLASH_MODE_OFF }
        imageCapture?.flashMode = flashMode
        updateFlashUi()
    }
    private fun updateFlashUi() {
        val available = camera?.cameraInfo?.hasFlashUnit() == true
        flashButton.text = when { !available -> "⚡ N/A"; flashMode == ImageCapture.FLASH_MODE_AUTO -> "⚡ AUTO"; flashMode == ImageCapture.FLASH_MODE_ON -> "⚡ ON"; else -> "⚡ OFF" }
        flashButton.alpha = if (available) 1f else .55f
    }
    private fun chooseTimer() {
        val choices = arrayOf("Off", "3 seconds", "5 seconds", "10 seconds")
        val values = intArrayOf(0, 3, 5, 10)
        android.app.AlertDialog.Builder(this).setTitle("Photo timer")
            .setSingleChoiceItems(choices, values.indexOf(timerSeconds).coerceAtLeast(0)) { dialog, which ->
                timerSeconds = values[which]; timerButton.text = if (timerSeconds == 0) "Timer OFF" else "Timer ${timerSeconds}s"; dialog.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }
    private fun chooseAspectRatio() {
        val choices = arrayOf("4:3 (standard photo)", "16:9 (wide photo)")
        val selected = if (aspectRatio == AspectRatio.RATIO_16_9) 1 else 0
        android.app.AlertDialog.Builder(this).setTitle("Photo aspect ratio").setSingleChoiceItems(choices, selected) { dialog, which ->
            aspectRatio = if (which == 1) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
            ratioButton.text = if (which == 1) "16:9" else "4:3"
            dialog.dismiss(); startCamera()
        }.setNegativeButton("Cancel", null).show()
    }
    private fun chooseVideoQuality() {
        val labels = arrayOf("HD · 720p", "Full HD · 1080p", "Ultra HD · 4K (if supported)")
        val qualities = arrayOf(Quality.HD, Quality.FHD, Quality.UHD)
        val selected = qualities.indexOf(videoQuality).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this).setTitle("Video quality").setSingleChoiceItems(labels, selected) { dialog, which ->
            videoQuality = qualities[which]
            dialog.dismiss()
            if (isRecording) stopRecording()
            startCamera()
            Toast.makeText(this, "Video quality set to ${labels[which]}; device may use nearest supported quality", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showExposureCompensation() {
        val activeCamera = camera ?: run { Toast.makeText(this, "Camera is still starting", Toast.LENGTH_SHORT).show(); return }
        val state = activeCamera.cameraInfo.exposureState
        val range = state.exposureCompensationRange
        if (range.lower == 0 && range.upper == 0) { Toast.makeText(this, "Exposure compensation is unavailable", Toast.LENGTH_SHORT).show(); return }
        val seek = SeekBar(this).apply { max = range.upper - range.lower; progress = (state.exposureCompensationIndex - range.lower).coerceIn(0, max) }
        val label = TextView(this).apply { gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 16f }
        fun updateLabel(index: Int) { label.text = String.format(Locale.US, "Exposure: %+.1f EV", index * state.exposureCompensationStep.toFloat()) }
        updateLabel(state.exposureCompensationIndex)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val index = range.lower + progress; updateLabel(index); activeCamera.cameraControl.setExposureCompensationIndex(index)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(4)); addView(label, LinearLayout.LayoutParams(-1, dp(36))); addView(seek, LinearLayout.LayoutParams(-1, dp(48))) }
        android.app.AlertDialog.Builder(this).setTitle("Exposure compensation").setView(content)
            .setNeutralButton("Reset") { _, _ -> activeCamera.cameraControl.setExposureCompensationIndex(0) }
            .setPositiveButton("Done", null).show()
    }
    private fun showSettings() {
        val options = arrayOf("Exposure compensation", "Photo timer", "Photo aspect ratio", "Video quality", "Composition grid", "Reset zoom", "About camera support")
        android.app.AlertDialog.Builder(this).setTitle("VivoCam settings").setItems(options) { _, which ->
            when (which) {
                0 -> showExposureCompensation()
                1 -> chooseTimer()
                2 -> chooseAspectRatio()
                3 -> chooseVideoQuality()
                4 -> { gridEnabled = !gridEnabled; gridOverlay.visibility = if (gridEnabled) View.VISIBLE else View.GONE; gridButton.text = if (gridEnabled) "Grid ON" else "Grid OFF" }
                5 -> setZoom(1f)
                else -> android.app.AlertDialog.Builder(this).setTitle("Camera support").setMessage("VivoCam uses Android CameraX. Available zoom, flash, exposure, focus, and video quality depend on the active lens and device firmware. Advanced computational modes are not yet implemented.").setPositiveButton("OK", null).show()
            }
        }.setPositiveButton("Close", null).show()
    }

    private fun textButton(label: String, size: Int, action: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = size.toFloat(); gravity = Gravity.CENTER; setTextColor(Color.WHITE); isClickable = true; isFocusable = true
        setOnClickListener { action() }
    }
    private fun weightParams() = LinearLayout.LayoutParams(0, dp(42), 1f)
    private fun space(w: Int, h: Int) = View(this).apply { layoutParams = ViewGroup.LayoutParams(w, h) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class GridOverlay : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88FFFFFF.toInt(); strokeWidth = dp(1).toFloat(); style = Paint.Style.STROKE }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val thirdW = width / 3f; val thirdH = height / 3f
            canvas.drawLine(thirdW, 0f, thirdW, height.toFloat(), paint); canvas.drawLine(thirdW * 2, 0f, thirdW * 2, height.toFloat(), paint)
            canvas.drawLine(0f, thirdH, width.toFloat(), thirdH, paint); canvas.drawLine(0f, thirdH * 2, width.toFloat(), thirdH * 2, paint)
        }
    }
    private inner class FocusIndicator : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD8F36A.toInt(); strokeWidth = dp(2).toFloat(); style = Paint.Style.STROKE }
        private var cx = 0f; private var cy = 0f; private val radius = dp(26).toFloat()
        fun showAt(x: Float, y: Float) { cx = x; cy = y; visibility = View.VISIBLE; invalidate(); mainHandler.postDelayed({ visibility = View.GONE }, 900L) }
        override fun onDraw(canvas: Canvas) { super.onDraw(canvas); canvas.drawRoundRect(RectF(cx - radius, cy - radius, cx + radius, cy + radius), dp(4).toFloat(), dp(4).toFloat(), paint) }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if ((keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) && event?.repeatCount == 0) {
            onCapturePressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        recording?.stop()
        super.onDestroy()
    }

}
