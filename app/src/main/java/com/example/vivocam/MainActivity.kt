package com.example.vivocam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Range
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
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
import java.nio.ByteOrder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.camera2.interop.Camera2Interop
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
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
    private lateinit var gridOverlay: GridOverlay
    private lateinit var focusIndicator: FocusIndicator
    private lateinit var adjustmentPanel: LinearLayout
    private lateinit var adjustmentLabel: TextView
    private lateinit var adjustmentValue: TextView
    private lateinit var adjustmentSeek: SeekBar
    private lateinit var controlChipRow: LinearLayout
    private lateinit var cinematicBars: CinemaBars
    private lateinit var cloneGhostOverlay: ImageView
    private lateinit var cloneActionRow: LinearLayout
    private lateinit var cloneUndoButton: TextView
    private lateinit var cloneResetButton: TextView
    private lateinit var cloneFinishButton: TextView

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var currentMode = "PHOTO"
    private var isRecording = false
    private var isPaused = false
    private var computationalCaptureBusy = false
    private var computationalFrameCount = 0
    private var computationalFrameIndex = 0
    private var computationalMode = ""
    private val computationalFrames = mutableListOf<File>()
    private var cloneComposite: Bitmap? = null
    private val cloneUndoStack = mutableListOf<Bitmap>()
    private var cloneProcessing = false
    private val cloneSegmenter by lazy {
        Segmentation.getClient(SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build())
    }
    private var zoom = 1f
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var timerSeconds = 0
    private var captureCountdownActive = false
    private var gridEnabled = false
    private var aspectRatio = AspectRatio.RATIO_4_3
    private var videoQuality = Quality.FHD
    private var manualExposureEnabled = false
    private var manualSupported = false
    private var manualIso = 400
    private var manualExposureTimeNs = 10_000_000L
    private var manualIsoMin = 50
    private var manualIsoMax = 3200
    private var manualExposureMinNs = 125_000L
    private var manualExposureMaxNs = 1_000_000_000L
    private var activeCameraId: String? = null
    private var activeAdjustment = "Zoom"
    private var focusMaxDiopters = 0f
    private var manualFocusEnabled = false
    private var manualFocusDistance = 0f
    private var whiteBalanceMode = CaptureRequest.CONTROL_AWB_MODE_AUTO
    private var whiteBalanceLabel = "Auto"
    private val shutterSteps = listOf(125_000L, 250_000L, 500_000L, 1_000_000L, 2_000_000L, 4_000_000L, 8_000_000L, 16_666_667L, 33_333_333L, 66_666_667L, 125_000_000L, 250_000_000L, 500_000_000L, 1_000_000_000L, 2_000_000_000L, 4_000_000_000L, 8_000_000_000L, 15_000_000_000L, 30_000_000_000L)
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
        cloneGhostOverlay = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.42f
            visibility = View.GONE
            isClickable = false
            contentDescription = "Clone composition alignment overlay"
        }
        root.addView(cloneGhostOverlay, FrameLayout.LayoutParams(-1, -1))
        gridOverlay = GridOverlay().apply { visibility = View.GONE; isClickable = false }
        root.addView(gridOverlay, FrameLayout.LayoutParams(-1, -1))
        focusIndicator = FocusIndicator().apply { visibility = View.GONE; isClickable = false }
        root.addView(focusIndicator, FrameLayout.LayoutParams(-1, -1))
        cinematicBars = CinemaBars().apply { visibility = View.GONE; isClickable = false }
        root.addView(cinematicBars, FrameLayout.LayoutParams(-1, -1))

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            isClickable = false
        }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        // Keep only the small status/action row at the top. Camera adjustments live in the bottom dock.
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        flashButton = textButton("⚡ OFF", 12) { cycleFlash(); refreshControlChipLabels() }
        top.addView(flashButton, weightParams())
        timerButton = textButton("Timer OFF", 11) { cycleTimerFromDock() }
        top.addView(timerButton, weightParams())
        ratioButton = textButton("4:3", 12) { cycleAspectRatioFromDock() }
        top.addView(ratioButton, weightParams())
        top.addView(textButton("⚙", 20) { showSettings() }, LinearLayout.LayoutParams(dp(44), dp(42)))
        overlay.addView(top, LinearLayout.LayoutParams(-1, dp(44)))
        overlay.addView(space(1, 0), LinearLayout.LayoutParams(1, 0, 1f))

        // One bottom adjustment dock replaces the old zoom and utility strips in the middle of the preview.
        adjustmentPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedSurface(0xD914171B.toInt(), 0x44FFFFFF)
            isClickable = true
        }
        val chipScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false }
        controlChipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val controls = listOf("Zoom", "EV", "ISO", "Shutter", "Focus", "WB", "Timer", "Ratio", "Flash", "Grid", "Quality")
        controls.forEach { name ->
            val chip = textButton(name, 11) { onDockControlTapped(name) }
            chip.tag = name
            controlChipRow.addView(chip, LinearLayout.LayoutParams(-2, dp(32)).apply { setMargins(0, 0, dp(6), 0) })
        }
        chipScroll.addView(controlChipRow)
        adjustmentPanel.addView(chipScroll, LinearLayout.LayoutParams(-1, dp(36)))
        val labelRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        adjustmentLabel = TextView(this).apply { textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL }
        labelRow.addView(adjustmentLabel, LinearLayout.LayoutParams(0, dp(24), 1f))
        adjustmentValue = TextView(this).apply { textSize = 12f; setTextColor(0xFFD8F36A.toInt()); gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT }
        labelRow.addView(adjustmentValue, LinearLayout.LayoutParams(-2, dp(24)))
        adjustmentPanel.addView(labelRow)
        adjustmentSeek = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFD8F36A.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFD8F36A.toInt())
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x66FFFFFF)
        }
        adjustmentPanel.addView(adjustmentSeek, LinearLayout.LayoutParams(-1, dp(32)))
        adjustmentSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) applyAdjustmentProgress(progress) }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {
                if ((activeAdjustment == "ISO" || activeAdjustment == "Shutter") && manualExposureEnabled && manualSupported) startCamera()
            }
        })
        overlay.addView(adjustmentPanel, LinearLayout.LayoutParams(-1, dp(108)))
        overlay.addView(space(1, 0), LinearLayout.LayoutParams(1, dp(6)))

        modeLabel = TextView(this).apply { text = "PHOTO · Auto"; setTextColor(Color.WHITE); textSize = 11f; gravity = Gravity.CENTER; letterSpacing = .04f }
        overlay.addView(modeLabel, LinearLayout.LayoutParams(-1, dp(22)))
        cloneActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        cloneUndoButton = textButton("↶ Undo", 12) { undoCloneSubject() }
        cloneResetButton = textButton("Reset", 12) { resetCloneSession() }
        cloneFinishButton = textButton("Save composite", 12) { saveCloneComposite() }
        cloneActionRow.addView(cloneUndoButton, LinearLayout.LayoutParams(0, dp(36), 1f))
        cloneActionRow.addView(cloneResetButton, LinearLayout.LayoutParams(0, dp(36), 1f))
        cloneActionRow.addView(cloneFinishButton, LinearLayout.LayoutParams(0, dp(36), 1.3f))
        overlay.addView(cloneActionRow, LinearLayout.LayoutParams(-1, dp(38)))
        val modeScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false }
        val modeRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        modes.forEach { mode ->
            val item = TextView(this).apply {
                text = mode; textSize = 12f; gravity = Gravity.CENTER; setPadding(dp(12), 0, dp(12), 0)
                setTextColor(if (mode == currentMode) 0xFFD8F36A.toInt() else Color.WHITE)
                setOnClickListener { selectMode(mode, modeRow) }
            }
            modeRow.addView(item, LinearLayout.LayoutParams(-2, dp(40)))
        }
        modeScroll.addView(modeRow)
        overlay.addView(modeScroll, LinearLayout.LayoutParams(-1, dp(42)))

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(4), dp(4), 0) }
        galleryButton = TextView(this).apply { text = "▧"; textSize = 27f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { openGallery() } }
        bottom.addView(galleryButton, LinearLayout.LayoutParams(0, dp(64), 1f))
        captureButton = FrameLayout(this).apply {
            setOnClickListener { onCapturePressed() }
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dp(3), Color.WHITE) }
        }
        captureRing = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFD8F36A.toInt()) } }
        captureButton.addView(captureRing, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
        bottom.addView(captureButton, LinearLayout.LayoutParams(dp(72), dp(68)))
        bottom.addView(textButton("↻", 27) { switchCamera() }, LinearLayout.LayoutParams(0, dp(64), 1f))
        overlay.addView(bottom, LinearLayout.LayoutParams(-1, dp(70)))
        installPreviewGestures()
        setContentView(root)
        renderAdjustmentControl()
        refreshControlChipLabels()
    }

    private fun roundedSurface(color: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        setColor(color)
        setStroke(dp(1), stroke)
    }

    private fun onDockControlTapped(name: String) {
        when (name) {
            "Zoom", "EV", "ISO", "Shutter", "Focus" -> {
                activeAdjustment = name
                if ((name == "ISO" || name == "Shutter") && manualSupported && !manualExposureEnabled) {
                    manualExposureEnabled = true
                    flashMode = ImageCapture.FLASH_MODE_OFF
                    applyLiveCamera2Options()
                    updateFlashUi()
                }
                if (name == "Focus" && focusMaxDiopters <= 0f) {
                    focusAtCenter()
                    Toast.makeText(this, "Manual focus distance is not exposed by this lens; tap the preview to focus", Toast.LENGTH_SHORT).show()
                }
                renderAdjustmentControl()
                refreshControlChipLabels()
            }
            "WB" -> cycleWhiteBalance()
            "Timer" -> cycleTimerFromDock()
            "Ratio" -> cycleAspectRatioFromDock()
            "Flash" -> { cycleFlash(); refreshControlChipLabels() }
            "Grid" -> { gridEnabled = !gridEnabled; gridOverlay.visibility = if (gridEnabled) View.VISIBLE else View.GONE; refreshControlChipLabels() }
            "Quality" -> cycleVideoQualityFromDock()
        }
    }

    private fun refreshControlChipLabels() {
        if (!::controlChipRow.isInitialized) return
        for (i in 0 until controlChipRow.childCount) {
            val chip = controlChipRow.getChildAt(i) as TextView
            val name = chip.tag?.toString() ?: continue
            val label = when (name) {
                "EV" -> "EV"
                "ISO" -> if (manualSupported) "ISO" else "ISO · N/A"
                "Shutter" -> if (manualSupported) "Shutter" else "Shutter · N/A"
                "Focus" -> if (focusMaxDiopters > 0f) "Focus" else "AF"
                "WB" -> "WB $whiteBalanceLabel"
                "Timer" -> if (timerSeconds == 0) "Timer OFF" else "Timer ${timerSeconds}s"
                "Ratio" -> if (aspectRatio == AspectRatio.RATIO_16_9) "16:9" else "4:3"
                "Flash" -> when (flashMode) { ImageCapture.FLASH_MODE_AUTO -> "Flash AUTO"; ImageCapture.FLASH_MODE_ON -> "Flash ON"; else -> "Flash OFF" }
                "Grid" -> if (gridEnabled) "Grid ON" else "Grid OFF"
                "Quality" -> when (videoQuality) { Quality.HD -> "720p"; Quality.FHD -> "1080p"; else -> "4K" }
                else -> name
            }
            chip.text = label
            chip.background = roundedSurface(if (name == activeAdjustment) 0xFF394126.toInt() else 0xAA202329.toInt(), if (name == activeAdjustment) 0xFFD8F36A.toInt() else 0x44FFFFFF)
            chip.alpha = if ((name == "ISO" || name == "Shutter") && !manualSupported) .5f else 1f
        }
    }

    private fun renderAdjustmentControl() {
        if (!::adjustmentSeek.isInitialized) return
        val active = camera
        adjustmentSeek.isEnabled = true
        when (activeAdjustment) {
            "Zoom" -> {
                val state = active?.cameraInfo?.zoomState?.value
                val min = state?.minZoomRatio ?: 1f; val max = state?.maxZoomRatio ?: 1f
                adjustmentSeek.max = 1000; adjustmentSeek.progress = (((zoom - min) / (max - min).coerceAtLeast(.001f)) * 1000).toInt().coerceIn(0, 1000)
                adjustmentLabel.text = "ZOOM"; adjustmentValue.text = String.format(Locale.US, "%.1f×", zoom)
            }
            "EV" -> {
                val state = active?.cameraInfo?.exposureState
                val range = state?.exposureCompensationRange
                if (range == null || range.lower == range.upper) { adjustmentSeek.isEnabled = false; adjustmentSeek.max = 1; adjustmentSeek.progress = 0; adjustmentLabel.text = "EXPOSURE COMPENSATION"; adjustmentValue.text = "N/A" }
                else { adjustmentSeek.max = range.upper - range.lower; adjustmentSeek.progress = (state.exposureCompensationIndex - range.lower).coerceIn(0, adjustmentSeek.max); adjustmentLabel.text = "EXPOSURE COMPENSATION"; adjustmentValue.text = formatEv(state.exposureCompensationIndex, state.exposureCompensationStep.toFloat()) }
            }
            "ISO" -> {
                adjustmentSeek.isEnabled = manualSupported
                adjustmentSeek.max = 1000
                adjustmentSeek.progress = if (manualSupported) (((manualIso - manualIsoMin).toFloat() / (manualIsoMax - manualIsoMin).coerceAtLeast(1)) * 1000).toInt() else 0
                adjustmentLabel.text = "MANUAL ISO"; adjustmentValue.text = if (manualSupported) "ISO $manualIso" else "Not supported"
            }
            "Shutter" -> {
                adjustmentSeek.isEnabled = manualSupported
                adjustmentSeek.max = 1000
                adjustmentSeek.progress = if (manualSupported) ((kotlin.math.ln(manualExposureTimeNs.toDouble()) - kotlin.math.ln(manualExposureMinNs.toDouble())) / (kotlin.math.ln(manualExposureMaxNs.toDouble()) - kotlin.math.ln(manualExposureMinNs.toDouble())).coerceAtLeast(.001) * 1000).toInt().coerceIn(0, 1000) else 0
                adjustmentLabel.text = "SHUTTER SPEED"; adjustmentValue.text = if (manualSupported) formatShutter(manualExposureTimeNs) else "Not supported"
            }
            "Focus" -> {
                adjustmentSeek.isEnabled = focusMaxDiopters > 0f
                adjustmentSeek.max = 1000
                adjustmentSeek.progress = if (focusMaxDiopters > 0f) (manualFocusDistance / focusMaxDiopters * 1000).toInt().coerceIn(0, 1000) else 0
                adjustmentLabel.text = "MANUAL FOCUS · INFINITY ← → NEAR"; adjustmentValue.text = if (focusMaxDiopters > 0f) String.format(Locale.US, "%.2f D", manualFocusDistance) else "Tap preview"
            }
        }
    }

    private fun applyAdjustmentProgress(progress: Int) {
        when (activeAdjustment) {
            "Zoom" -> {
                val state = camera?.cameraInfo?.zoomState?.value
                val min = state?.minZoomRatio ?: 1f; val max = state?.maxZoomRatio ?: 1f
                setZoom(min + (max - min) * progress / 1000f)
                adjustmentValue.text = String.format(Locale.US, "%.1f×", zoom)
            }
            "EV" -> {
                val state = camera?.cameraInfo?.exposureState ?: return
                val range = state.exposureCompensationRange
                if (range.lower == range.upper) return
                val index = range.lower + progress
                camera?.cameraControl?.setExposureCompensationIndex(index)
                adjustmentValue.text = formatEv(index, state.exposureCompensationStep.toFloat())
            }
            "ISO" -> if (manualSupported) {
                manualExposureEnabled = true
                manualIso = manualIsoMin + ((manualIsoMax - manualIsoMin).toFloat() * progress / 1000f).toInt()
                applyLiveCamera2Options()
                updateFlashUi()
                adjustmentValue.text = "ISO $manualIso"
                modeLabel.text = "PRO · ISO $manualIso · ${formatShutter(manualExposureTimeNs)}"
            }
            "Shutter" -> if (manualSupported) {
                manualExposureEnabled = true
                val fraction = progress / 1000.0
                manualExposureTimeNs = kotlin.math.exp(kotlin.math.ln(manualExposureMinNs.toDouble()) + (kotlin.math.ln(manualExposureMaxNs.toDouble()) - kotlin.math.ln(manualExposureMinNs.toDouble())) * fraction).toLong().coerceIn(manualExposureMinNs, manualExposureMaxNs)
                applyLiveCamera2Options()
                updateFlashUi()
                adjustmentValue.text = formatShutter(manualExposureTimeNs)
                modeLabel.text = "PRO · ISO $manualIso · ${formatShutter(manualExposureTimeNs)}"
            }
            "Focus" -> if (focusMaxDiopters > 0f) {
                manualFocusEnabled = true
                manualFocusDistance = focusMaxDiopters * progress / 1000f
                applyLiveCamera2Options()
                adjustmentValue.text = String.format(Locale.US, "%.2f D", manualFocusDistance)
            }
        }
    }

    private fun formatEv(index: Int, step: Float): String = String.format(Locale.US, "%+.1f EV", index * step)

    private fun cycleTimerFromDock() {
        timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 5; 5 -> 10; else -> 0 }
        timerButton.text = if (timerSeconds == 0) "Timer OFF" else "Timer ${timerSeconds}s"
        refreshControlChipLabels()
    }

    private fun cycleAspectRatioFromDock() {
        aspectRatio = if (aspectRatio == AspectRatio.RATIO_4_3) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
        ratioButton.text = if (aspectRatio == AspectRatio.RATIO_16_9) "16:9" else "4:3"
        startCamera()
        refreshControlChipLabels()
    }

    private fun cycleVideoQualityFromDock() {
        videoQuality = when (videoQuality) { Quality.HD -> Quality.FHD; Quality.FHD -> Quality.UHD; else -> Quality.HD }
        if (isRecording) stopRecording()
        startCamera()
        refreshControlChipLabels()
        Toast.makeText(this, "Video quality: ${when (videoQuality) { Quality.HD -> "720p"; Quality.FHD -> "1080p"; else -> "4K (if supported)" }}", Toast.LENGTH_SHORT).show()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun cycleWhiteBalance() {
        val choices = listOf(CaptureRequest.CONTROL_AWB_MODE_AUTO to "Auto", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT to "Tungsten", CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT to "Fluorescent", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT to "Daylight", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to "Cloudy")
        val next = (choices.indexOfFirst { it.first == whiteBalanceMode } + 1).let { if (it <= 0 || it >= choices.size) 0 else it }
        whiteBalanceMode = choices[next].first
        whiteBalanceLabel = choices[next].second
        applyLiveCamera2Options()
        refreshControlChipLabels()
        Toast.makeText(this, "White balance: $whiteBalanceLabel", Toast.LENGTH_SHORT).show()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyLiveCamera2Options() {
        val active = camera ?: return
        try {
            val builder = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, whiteBalanceMode)
            if (manualExposureEnabled) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    .setCaptureRequestOption(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manualIso.coerceIn(manualIsoMin, manualIsoMax))
                    .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, manualExposureTimeNs.coerceIn(manualExposureMinNs, manualExposureMaxNs))
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            if (manualFocusEnabled && focusMaxDiopters > 0f) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, manualFocusDistance.coerceIn(0f, focusMaxDiopters))
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            Camera2CameraControl.from(active.cameraControl).setCaptureRequestOptions(builder.build())
        } catch (_: Exception) {
            Toast.makeText(this, "This camera does not support that live adjustment", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectMode(mode: String, row: LinearLayout) {
        if (currentMode == "CLONE" && cloneProcessing && mode != "CLONE") {
            Toast.makeText(this, "Please wait for Clone processing to finish", Toast.LENGTH_SHORT).show()
            return
        }
        if (isRecording && mode != "VIDEO" && mode != "CINEMA") stopRecording()
        if (mode !in listOf("PRO", "LONG EXP", "ASTRO") && manualExposureEnabled) {
            manualExposureEnabled = false
            startCamera()
        }
        if (currentMode == "CLONE" && mode != "CLONE" && cloneComposite != null) {
            // Keep the unfinished session available only while Clone remains selected; leaving the mode releases it.
            clearCloneSession()
        }
        currentMode = mode
        cloneActionRow.visibility = if (mode == "CLONE" && cloneComposite != null) View.VISIBLE else View.GONE
        cloneGhostOverlay.visibility = if (mode == "CLONE" && cloneComposite != null) View.VISIBLE else View.GONE
        modeLabel.text = when (mode) {
            "LONG EXP" -> "LONG EXPOSURE · Tripod recommended"
            "ASTRO" -> "ASTRO · Tripod + dark sky recommended"
            "CLONE" -> if (cloneComposite == null) "CLONE · Capture base photo" else "CLONE · Move and shoot · ${cloneUndoStack.size - 1} added pose(s)"
            "360°" -> "360° · Sweep slowly around yourself"
            "CINEMA" -> "CINEMA · 2.39:1 framing guides · selected video quality"
            "PRO" -> "PRO · ISO / shutter / EV"
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
        cinematicBars.visibility = if (mode == "CINEMA") View.VISIBLE else View.GONE
        if (mode == "PRO") {
            activeAdjustment = if (manualSupported) "ISO" else "EV"
            renderAdjustmentControl()
            refreshControlChipLabels()
            if (!manualSupported) Toast.makeText(this, "Manual ISO/shutter is not exposed by this camera; EV, zoom and tap focus remain available", Toast.LENGTH_LONG).show()
            return
        }
        when (mode) {
            "LONG EXP" -> {
                if (manualSupported) {
                    manualExposureEnabled = true
                    manualIso = manualIso.coerceIn(manualIsoMin, manualIsoMax).coerceAtLeast(400.coerceAtMost(manualIsoMax))
                    manualExposureTimeNs = 500_000_000L.coerceIn(manualExposureMinNs, manualExposureMaxNs)
                    applyLiveCamera2Options(); updateFlashUi(); startCamera()
                    activeAdjustment = "Shutter"; renderAdjustmentControl(); refreshControlChipLabels()
                    Toast.makeText(this, "Long-exposure blend: 8 frames at ${formatShutter(manualExposureTimeNs)} each · use a tripod", Toast.LENGTH_LONG).show()
                } else Toast.makeText(this, "Manual long shutter isn't exposed by this lens; Long Exposure will blend 8 short frames instead", Toast.LENGTH_LONG).show()
            }
            "ASTRO" -> {
                if (manualSupported) {
                    manualExposureEnabled = true
                    manualIso = 800.coerceIn(manualIsoMin, manualIsoMax)
                    manualExposureTimeNs = 1_000_000_000L.coerceIn(manualExposureMinNs, manualExposureMaxNs)
                    applyLiveCamera2Options(); updateFlashUi(); startCamera()
                }
                activeAdjustment = if (manualSupported) "ISO" else "EV"; renderAdjustmentControl(); refreshControlChipLabels()
                Toast.makeText(this, "Astro stack: 8 frames. Keep the phone fixed on a tripod and use a dark sky", Toast.LENGTH_LONG).show()
            }
            "CLONE" -> Toast.makeText(this, "Clone: capture a base, then each shutter press adds a person automatically. Keep the phone still.", Toast.LENGTH_LONG).show()
            "CINEMA" -> {
                startCamera()
                val fpsNote = if (supportsExact24Fps()) "24 FPS target" else "24 FPS unavailable on this camera; using device-supported rate"
                Toast.makeText(this, "Cinema: $fpsNote · video and preview stabilization enabled where supported", Toast.LENGTH_LONG).show()
            }
            "NIGHT" -> Toast.makeText(this, "Night stack: 6 frames blended to reduce random noise. Hold steady", Toast.LENGTH_LONG).show()
            "360°" -> Toast.makeText(this, "360° capture still needs panorama stitching; not included in this build", Toast.LENGTH_LONG).show()
            "PORTRAIT" -> Toast.makeText(this, "Portrait depth blur requires a depth map; this mode currently captures a regular photo", Toast.LENGTH_LONG).show()
        }
    }

    private fun startCamera() {
        if (::captureButton.isInitialized) captureButton.isEnabled = false
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val previewStabilizationSupported = if (currentMode == "CINEMA") try {
                Preview.getPreviewCapabilities(provider.getCameraInfo(selector)).isStabilizationSupported
            } catch (_: Exception) { false } else false
            val videoStabilizationSupported = if (currentMode == "CINEMA") try {
                Recorder.getVideoCapabilities(provider.getCameraInfo(selector)).isStabilizationSupported
            } catch (_: Exception) { false } else false
            val previewBuilder = Preview.Builder()
            applyManualExposure(previewBuilder)
            if (previewStabilizationSupported) previewBuilder.setPreviewStabilizationEnabled(true)
            val preview = previewBuilder.build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val imageBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(aspectRatio)
                .setFlashMode(if (manualExposureEnabled) ImageCapture.FLASH_MODE_OFF else flashMode)
            applyManualExposure(imageBuilder)
            imageCapture = imageBuilder.build()
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(videoQuality, FallbackStrategy.lowerQualityOrHigherThan(videoQuality))).build()
            val videoBuilder = VideoCapture.Builder(recorder)
            if (currentMode == "CINEMA") {
                if (supportsExact24Fps()) videoBuilder.setTargetFrameRate(Range(24, 24))
                videoBuilder.setVideoStabilizationEnabled(videoStabilizationSupported)
            }
            videoCapture = videoBuilder.build()
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture)
                refreshManualCapabilities()
                updateFlashUi()
                setZoom(zoom)
                applyLiveCamera2Options()
                renderAdjustmentControl()
                refreshControlChipLabels()
                if (::captureButton.isInitialized) captureButton.isEnabled = !computationalCaptureBusy
            } catch (e: Exception) {
                if (::captureButton.isInitialized) captureButton.isEnabled = true
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
        if (currentMode == "CLONE") {
            captureCloneStep(capture)
            return
        }
        if (currentMode in listOf("ASTRO", "LONG EXP", "NIGHT")) {
            startComputationalCapture(capture, currentMode)
            return
        }
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

    /** MIUI-style cumulative Clone: first shutter establishes the background; every later shutter adds a segmented person. */
    private fun captureCloneStep(capture: ImageCapture) {
        if (cloneProcessing) return
        cloneProcessing = true
        captureButton.isEnabled = false
        val file = File(cacheDir, "clone_step_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val current = decodeCloneFrame(file)
                file.delete()
                if (current == null) {
                    cloneProcessing = false; captureButton.isEnabled = true
                    Toast.makeText(this@MainActivity, "Could not decode Clone frame", Toast.LENGTH_SHORT).show()
                    return
                }
                val base = cloneComposite
                if (base == null) {
                    cloneComposite = current
                    pushCloneUndoSnapshot(current)
                    showCloneGhost(current)
                    cloneProcessing = false; captureButton.isEnabled = true
                    cloneActionRow.visibility = View.VISIBLE
                    modeLabel.text = "CLONE · Base set · move to your first pose"
                    Toast.makeText(this@MainActivity, "Base captured. Move into position and tap the shutter again.", Toast.LENGTH_LONG).show()
                    return
                }
                val subjectFrame = if (current.width != base.width || current.height != base.height)
                    Bitmap.createScaledBitmap(current, base.width, base.height, true) else current
                if (subjectFrame !== current) current.recycle()
                modeLabel.text = "CLONE · detecting person…"
                cloneSegmenter.process(InputImage.fromBitmap(subjectFrame, 0))
                    .addOnSuccessListener { mask ->
                        try {
                            val merged = compositeSegmentedSubject(base, subjectFrame, mask.buffer, mask.width, mask.height)
                            pushCloneUndoSnapshot(merged)
                            cloneComposite?.takeIf { it !== merged }?.recycle()
                            cloneComposite = merged
                            showCloneGhost(merged)
                            modeLabel.text = "CLONE · ${cloneUndoStack.size - 1} pose(s) added · keep shooting"
                            cloneActionRow.visibility = View.VISIBLE
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Could not composite subject: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        } finally {
                            if (!subjectFrame.isRecycled) subjectFrame.recycle()
                            cloneProcessing = false; captureButton.isEnabled = true
                        }
                    }
                    .addOnFailureListener { e ->
                        if (!subjectFrame.isRecycled) subjectFrame.recycle()
                        cloneProcessing = false; captureButton.isEnabled = true
                        modeLabel.text = "CLONE · person detection failed"
                        Toast.makeText(this@MainActivity, "On-device person segmentation failed: ${e.localizedMessage}. Try again in better light.", Toast.LENGTH_LONG).show()
                    }
            }
            override fun onError(exception: ImageCaptureException) {
                file.delete(); cloneProcessing = false; captureButton.isEnabled = true
                Toast.makeText(this@MainActivity, "Clone capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun decodeCloneFrame(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return decodeOrientedFrame(file, opts)
    }

    private fun pushCloneUndoSnapshot(bitmap: Bitmap) {
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        cloneUndoStack.add(copy)
        while (cloneUndoStack.size > 7) cloneUndoStack.removeAt(0).recycle()
    }

    private fun showCloneGhost(bitmap: Bitmap) {
        cloneGhostOverlay.setImageBitmap(bitmap)
        cloneGhostOverlay.alpha = 0.42f
        cloneGhostOverlay.visibility = View.VISIBLE
    }

    private fun compositeSegmentedSubject(base: Bitmap, subject: Bitmap, maskBuffer: java.nio.ByteBuffer, maskWidth: Int, maskHeight: Int): Bitmap {
        require(base.width == subject.width && base.height == subject.height) { "Clone frames must match" }
        require(maskWidth > 0 && maskHeight > 0) { "Invalid segmentation mask" }
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val basePixels = IntArray(base.width * base.height)
        val subjectPixels = IntArray(subject.width * subject.height)
        out.getPixels(basePixels, 0, base.width, 0, 0, base.width, base.height)
        subject.getPixels(subjectPixels, 0, subject.width, 0, 0, subject.width, subject.height)
        val mask = maskBuffer.duplicate().order(ByteOrder.nativeOrder())
        mask.rewind()
        for (y in 0 until base.height) {
            val my = ((y.toLong() * maskHeight) / base.height).toInt().coerceIn(0, maskHeight - 1)
            for (x in 0 until base.width) {
                val mx = ((x.toLong() * maskWidth) / base.width).toInt().coerceIn(0, maskWidth - 1)
                val index = my * maskWidth + mx
                val offset = index * 4
                if (offset + 4 > mask.limit()) continue
                val confidence = mask.getFloat(offset).coerceIn(0f, 1f)
                // Suppress weak edge/background predictions, retain a soft transition to avoid cut-out edges.
                val alpha = ((confidence - 0.32f) / 0.58f).coerceIn(0f, 1f)
                if (alpha < 0.02f) continue
                val src = subjectPixels[y * base.width + x]
                val dstIndex = y * base.width + x
                val dst = basePixels[dstIndex]
                val inv = 1f - alpha
                val r = (Color.red(src) * alpha + Color.red(dst) * inv).toInt().coerceIn(0, 255)
                val g = (Color.green(src) * alpha + Color.green(dst) * inv).toInt().coerceIn(0, 255)
                val b = (Color.blue(src) * alpha + Color.blue(dst) * inv).toInt().coerceIn(0, 255)
                basePixels[dstIndex] = Color.rgb(r, g, b)
            }
        }
        out.setPixels(basePixels, 0, base.width, 0, 0, base.width, base.height)
        return out
    }

    private fun undoCloneSubject() {
        if (cloneProcessing || cloneUndoStack.size < 2) return
        if (cloneUndoStack.isNotEmpty()) cloneUndoStack.removeAt(cloneUndoStack.lastIndex).recycle()
        val previous = cloneUndoStack.lastOrNull() ?: return
        cloneComposite?.takeIf { it !== previous }?.recycle()
        cloneComposite = previous.copy(Bitmap.Config.ARGB_8888, false)
        showCloneGhost(cloneComposite!!)
        modeLabel.text = "CLONE · Undone · ${cloneUndoStack.size - 1} pose(s)"
    }

    private fun resetCloneSession() {
        if (cloneProcessing) return
        clearCloneSession()
        if (currentMode == "CLONE") modeLabel.text = "CLONE · Capture base photo"
        Toast.makeText(this, "Clone composition reset. Capture a new base photo.", Toast.LENGTH_SHORT).show()
    }

    private fun saveCloneComposite() {
        val bitmap = cloneComposite ?: return Toast.makeText(this, "Capture a base and at least one pose first", Toast.LENGTH_SHORT).show()
        if (cloneProcessing) return
        cloneProcessing = true; captureButton.isEnabled = false; cloneFinishButton.isEnabled = false
        Thread {
            try {
                val saved = saveProcessedBitmap(bitmap, "CLONE")
                mainHandler.post {
                    updateGalleryButton(saved)
                    Toast.makeText(this, "Clone composite saved to Pictures/VivoCam", Toast.LENGTH_LONG).show()
                    clearCloneSession()
                    modeLabel.text = "CLONE · Saved · capture a new base"
                    cloneProcessing = false; captureButton.isEnabled = true; cloneFinishButton.isEnabled = true
                }
            } catch (e: Exception) {
                mainHandler.post {
                    cloneProcessing = false; captureButton.isEnabled = true; cloneFinishButton.isEnabled = true
                    Toast.makeText(this, "Could not save Clone: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun clearCloneSession() {
        cloneGhostOverlay.setImageDrawable(null)
        cloneGhostOverlay.visibility = View.GONE
        cloneActionRow.visibility = View.GONE
        cloneComposite?.recycle(); cloneComposite = null
        cloneUndoStack.forEach { if (!it.isRecycled) it.recycle() }; cloneUndoStack.clear()
    }

    private fun startComputationalCapture(capture: ImageCapture, mode: String) {
        if (computationalCaptureBusy) return
        computationalCaptureBusy = true
        computationalMode = mode
        computationalFrameCount = when (mode) { "CLONE" -> 3; "NIGHT" -> 6; else -> 8 }
        computationalFrameIndex = 0
        computationalFrames.clear()
        val dir = File(cacheDir, "vivocam_frames").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        captureButton.isEnabled = false
        modeLabel.text = "$mode · preparing stack"
        captureComputationalFrame(capture, dir)
    }

    private fun captureComputationalFrame(capture: ImageCapture, dir: File) {
        if (computationalFrameIndex >= computationalFrameCount) {
            if (computationalMode == "CLONE") {
                mainHandler.post { openCloneMaskEditor(computationalFrames.toList()) }
                return
            }
            modeLabel.text = "$computationalMode · aligning and stacking ${computationalFrameCount} frames…"
            Thread {
                try {
                    val bitmap = processComputationalFrames(computationalFrames, computationalMode)
                    val saved = saveProcessedBitmap(bitmap, computationalMode)
                    bitmap.recycle()
                    computationalFrames.forEach { it.delete() }
                    mainHandler.post {
                        computationalCaptureBusy = false
                        captureButton.isEnabled = true
                        modeLabel.text = "$computationalMode · ${computationalFrameCount} frames stacked"
                        updateGalleryButton(saved)
                        Toast.makeText(this, "$computationalMode composite saved to Pictures/VivoCam", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    computationalFrames.forEach { it.delete() }
                    mainHandler.post {
                        computationalCaptureBusy = false
                        captureButton.isEnabled = true
                        modeLabel.text = "$computationalMode · capture failed"
                        Toast.makeText(this, "Could not combine frames: ${e.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
            return
        }
        val frame = File(dir, "${computationalMode.lowercase(Locale.US)}_${computationalFrameIndex}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(frame).build()
        capture.takePicture(options, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                computationalFrames.add(frame)
                computationalFrameIndex++
                modeLabel.text = "$computationalMode · frame $computationalFrameIndex/$computationalFrameCount"
                // A small gap lets auto exposure/focus settle between frames.
                if (computationalMode == "CLONE" && computationalFrameIndex < computationalFrameCount) {
                    modeLabel.text = "CLONE · move to pose ${computationalFrameIndex + 1}"
                    mainHandler.postDelayed({ captureComputationalFrame(capture, dir) }, 2200L)
                } else mainHandler.postDelayed({ captureComputationalFrame(capture, dir) }, 220L)
            }
            override fun onError(exception: ImageCaptureException) {
                computationalFrames.forEach { it.delete() }
                frame.delete()
                computationalCaptureBusy = false
                captureButton.isEnabled = true
                modeLabel.text = "$computationalMode · capture failed"
                Toast.makeText(this@MainActivity, "Frame capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun processComputationalFrames(files: List<File>, mode: String): Bitmap {
        require(files.isNotEmpty()) { "No frames captured" }
        val sample = if (mode == "CLONE") 4 else 2
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val first = decodeOrientedFrame(files.first(), decodeOptions)
            ?: throw IllegalStateException("Could not decode first frame")
        val width = first.width
        val height = first.height
        val count = width * height
        val result = IntArray(count)
        first.getPixels(result, 0, width, 0, 0, width, height)
        first.recycle()
        val sampleCounts = if (mode == "ASTRO") IntArray(count) { 1 } else null
        for (frameIndex in 1 until files.size) {
            val frame = decodeOrientedFrame(files[frameIndex], decodeOptions) ?: continue
            if (frame.width != width || frame.height != height) { frame.recycle(); continue }
            val pixels = IntArray(count)
            frame.getPixels(pixels, 0, width, 0, 0, width, height)
            frame.recycle()
            when (mode) {
                "ASTRO" -> {
                    // Translation-only star registration. High-pass luminance matching reduces sensitivity to exposure changes.
                    val shift = estimateStarTranslation(result, pixels, width, height)
                    val dx = shift.first; val dy = shift.second
                    val counts = sampleCounts!!
                    for (y in 0 until height) {
                        val sy = y + dy
                        if (sy !in 0 until height) continue
                        var x = 0
                        while (x < width) {
                            val sx = x + dx
                            if (sx in 0 until width) {
                                val i = y * width + x
                                val b = pixels[sy * width + sx]
                                val n = counts[i]
                                val a = result[i]
                                val r = (((a shr 16 and 255) * n + (b shr 16 and 255)) / (n + 1)).coerceIn(0, 255)
                                val g = (((a shr 8 and 255) * n + (b shr 8 and 255)) / (n + 1)).coerceIn(0, 255)
                                val bl = (((a and 255) * n + (b and 255)) / (n + 1)).coerceIn(0, 255)
                                result[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
                                counts[i] = n + 1
                            }
                            x++
                        }
                    }
                }
                "NIGHT" -> {
                    for (i in 0 until count) {
                        val a = result[i]; val b = pixels[i]
                        val r = (((a shr 16 and 255) * frameIndex + (b shr 16 and 255)) / (frameIndex + 1)).coerceIn(0, 255)
                        val g = (((a shr 8 and 255) * frameIndex + (b shr 8 and 255)) / (frameIndex + 1)).coerceIn(0, 255)
                        val bl = (((a and 255) * frameIndex + (b and 255)) / (frameIndex + 1)).coerceIn(0, 255)
                        result[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
                    }
                }
                "LONG EXP" -> {
                    for (i in 0 until count) {
                        val a = result[i]; val b = pixels[i]
                        val r = maxOf(a shr 16 and 255, b shr 16 and 255)
                        val g = maxOf(a shr 8 and 255, b shr 8 and 255)
                        val bl = maxOf(a and 255, b and 255)
                        result[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
                    }
                }
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.setPixels(result, 0, width, 0, 0, width, height) }
    }

    private fun estimateStarTranslation(reference: IntArray, candidate: IntArray, width: Int, height: Int): Pair<Int, Int> {
        if (width < 80 || height < 80) return 0 to 0
        val maxX = (width * 0.045f).toInt().coerceIn(4, 32)
        val maxY = (height * 0.045f).toInt().coerceIn(4, 32)
        fun lum(p: Int) = ((p shr 16 and 255) * 0.299f + (p shr 8 and 255) * 0.587f + (p and 255) * 0.114f).toInt()
        fun feature(data: IntArray, x: Int, y: Int): Int {
            val i = y * width + x
            return lum(data[i]) - (lum(data[i - 1]) + lum(data[i + 1]) + lum(data[i - width]) + lum(data[i + width])) / 4
        }
        fun score(dx: Int, dy: Int, stride: Int): Double {
            var total = 0.0; var used = 0
            val marginX = maxOf(maxX + 2, 3); val marginY = maxOf(maxY + 2, 3)
            var y = marginY
            while (y < height - marginY) {
                val sy = y + dy
                var x = marginX
                while (x < width - marginX) {
                    val sx = x + dx
                    val a = feature(reference, x, y)
                    val b = feature(candidate, sx, sy)
                    // Robust truncated absolute error stops hot pixels and moving objects dominating registration.
                    total += minOf(kotlin.math.abs(a - b), 48)
                    used++; x += stride
                }
                y += stride
            }
            return if (used == 0) Double.MAX_VALUE else total / used
        }
        var bestX = 0; var bestY = 0; var best = Double.MAX_VALUE
        var dy = -maxY
        while (dy <= maxY) { var dx = -maxX
            while (dx <= maxX) { val v = score(dx, dy, 16); if (v < best) { best = v; bestX = dx; bestY = dy }; dx += 4 }
            dy += 4
        }
        val coarseX = bestX; val coarseY = bestY; best = Double.MAX_VALUE
        for (yy in (coarseY - 3).coerceAtLeast(-maxY)..(coarseY + 3).coerceAtMost(maxY)) {
            for (xx in (coarseX - 3).coerceAtLeast(-maxX)..(coarseX + 3).coerceAtMost(maxX)) {
                val v = score(xx, yy, 8); if (v < best) { best = v; bestX = xx; bestY = yy }
            }
        }
        return bestX to bestY
    }

    private fun supportsExact24Fps(): Boolean {
        return try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val wantedFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == wantedFacing } ?: return false
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty().any { it.lower == 24 && it.upper == 24 }
        } catch (_: Exception) { false }
    }

    private fun openCloneMaskEditor(files: List<File>) {
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bitmaps = files.mapNotNull { decodeOrientedFrame(it, opts) }
            if (bitmaps.size < 2) throw IllegalStateException("Need at least two clone poses")
            val editor = CloneMaskEditor(bitmaps)
            val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(4)) }
            root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
            val poses = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            bitmaps.indices.forEach { index -> poses.addView(textButton("Pose ${index + 1}", 13) { editor.selectPose(index) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) }) }
            root.addView(poses)
            val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val erase = CheckBox(this).apply { text = "Erase"; setTextColor(Color.WHITE) }
            val brushLabel = TextView(this).apply { text = "Brush"; setTextColor(Color.WHITE) }
            val brush = SeekBar(this).apply { max = 100; progress = 24; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { editor.brushSize = (progress + 8).toFloat() }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }) }
            erase.setOnCheckedChangeListener { _, checked -> editor.eraseMode = checked }
            tools.addView(erase); tools.addView(brushLabel); tools.addView(brush, LinearLayout.LayoutParams(0, dp(44), 1f)); root.addView(tools)
            val dialog = android.app.AlertDialog.Builder(this).setTitle("Clone · paint the poses to keep").setView(root)
                .setNegativeButton("Cancel") { _, _ -> bitmaps.forEach { it.recycle() }; files.forEach { it.delete() }; finishComputationalMode("Clone cancelled") }
                .setPositiveButton("Build composite", null).create()
            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    dialog.getButton(-1).isEnabled = false
                    dialog.getButton(-2).isEnabled = false
                    editor.isEnabled = false
                    Thread {
                        try {
                            val output = editor.buildComposite()
                            val saved = saveProcessedBitmap(output, "CLONE")
                            output.recycle(); bitmaps.forEach { if (!it.isRecycled) it.recycle() }; files.forEach { it.delete() }
                            mainHandler.post { dialog.dismiss(); updateGalleryButton(saved); finishComputationalMode("Clone composite saved") }
                        } catch (e: Exception) {
                            mainHandler.post { dialog.getButton(-1).isEnabled = true; dialog.getButton(-2).isEnabled = true; editor.isEnabled = true; Toast.makeText(this, "Clone processing failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
                        }
                    }.start()
                }
            }
            dialog.window?.setBackgroundDrawableResource(android.R.color.black)
            dialog.show()
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.96f).toInt(), (resources.displayMetrics.heightPixels * 0.82f).toInt())
            dialog.window?.setBackgroundDrawableResource(android.R.color.black)
            editor.invalidate()
        } catch (e: Exception) {
            files.forEach { it.delete() }; finishComputationalMode("Clone editor failed")
            Toast.makeText(this, "Could not open clone editor: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun finishComputationalMode(message: String) {
        computationalCaptureBusy = false
        if (::captureButton.isInitialized) captureButton.isEnabled = true
        modeLabel.text = message
    }

    private inner class CloneMaskEditor(private val frames: List<Bitmap>) : View(this@MainActivity) {
        private val masks = frames.map { Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888) }
        private val maskCanvases = masks.map { Canvas(it) }
        private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 72f }
        private val path = Path()
        private var currentPose = 1
        private var lastX = 0f; private var lastY = 0f
        var brushSize = 32f
        var eraseMode = false
        private var imageRect = RectF()
        init { setBackgroundColor(Color.BLACK); isFocusable = true }
        fun selectPose(index: Int) { currentPose = index.coerceIn(frames.indices); invalidate() }
        private fun updatePaint() { maskPaint.strokeWidth = brushSize * (frames[0].width / imageRect.width().coerceAtLeast(1f)); maskPaint.xfermode = if (eraseMode) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val base = frames[0]
            val scale = minOf(width.toFloat() / base.width, height.toFloat() / base.height)
            val dw = base.width * scale; val dh = base.height * scale
            imageRect = RectF((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f)
            canvas.drawBitmap(base, null, imageRect, null)
            if (currentPose > 0) {
                val layer = canvas.saveLayer(imageRect, null)
                canvas.drawBitmap(frames[currentPose], null, imageRect, null)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
                canvas.drawBitmap(masks[currentPose], null, imageRect, paint)
                paint.xfermode = null; canvas.restoreToCount(layer)
            }
            val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEEFFFFFF.toInt(); textSize = dp(14).toFloat(); setShadowLayer(4f, 1f, 1f, Color.BLACK) }
            canvas.drawText("Pose ${currentPose + 1}: paint regions to include", dp(10).toFloat(), dp(24).toFloat(), hint)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled || currentPose == 0 || imageRect.width() <= 0f) return true
            val bx = ((event.x - imageRect.left) / imageRect.width() * frames[0].width).coerceIn(0f, frames[0].width.toFloat())
            val by = ((event.y - imageRect.top) / imageRect.height() * frames[0].height).coerceIn(0f, frames[0].height.toFloat())
            updatePaint()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastX = bx; lastY = by; path.reset(); path.moveTo(bx, by); maskCanvases[currentPose].drawCircle(bx, by, maskPaint.strokeWidth / 2f, Paint(maskPaint).apply { style = Paint.Style.FILL }); invalidate(); return true }
                MotionEvent.ACTION_MOVE -> { path.lineTo(bx, by); maskCanvases[currentPose].drawPath(path, maskPaint); lastX = bx; lastY = by; invalidate(); return true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { path.reset(); return true }
            }
            return true
        }
        fun buildComposite(): Bitmap {
            val out = Bitmap.createBitmap(frames[0].width, frames[0].height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out); canvas.drawBitmap(frames[0], 0f, 0f, null)
            for (i in 1 until frames.size) {
                val layer = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
                val lc = Canvas(layer); lc.drawBitmap(frames[i], 0f, 0f, null)
                val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
                lc.drawBitmap(masks[i], 0f, 0f, maskPaint); maskPaint.xfermode = null
                canvas.drawBitmap(layer, 0f, 0f, null); layer.recycle()
            }
            return out
        }
    }

    private fun decodeOrientedFrame(file: File, options: BitmapFactory.Options): Bitmap? {
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val orientation = try { ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return decoded
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun saveProcessedBitmap(bitmap: Bitmap, mode: String): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VivoCam_${mode.replace(" ", "_")}_$stamp.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VivoCam")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create gallery entry")
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) throw IllegalStateException("JPEG encoding failed")
            } ?: throw IllegalStateException("Could not open output stream")
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
        return uri
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
                    modeLabel.text = if (currentMode == "CINEMA") "CINEMA REC · framing guides · tap to stop" else "RECORDING · tap shutter to stop"
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
            if (::adjustmentSeek.isInitialized && activeAdjustment == "Zoom") renderAdjustmentControl()
        } ?: run { zoom = value.coerceAtLeast(1f) }
    }
    private fun switchCamera() {
        if (isRecording) { Toast.makeText(this, "Stop recording before switching cameras", Toast.LENGTH_SHORT).show(); return }
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        manualExposureEnabled = false
        manualFocusEnabled = false
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
        manualFocusEnabled = false
        applyLiveCamera2Options()
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
        if (manualExposureEnabled) { Toast.makeText(this, "Flash is disabled while manual exposure is active", Toast.LENGTH_SHORT).show(); return }
        val available = camera?.cameraInfo?.hasFlashUnit() == true
        if (!available) { Toast.makeText(this, "Flash is unavailable on this camera", Toast.LENGTH_SHORT).show(); return }
        flashMode = when (flashMode) { ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO; ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON; else -> ImageCapture.FLASH_MODE_OFF }
        imageCapture?.flashMode = flashMode
        updateFlashUi()
    }
    private fun updateFlashUi() {
        val available = camera?.cameraInfo?.hasFlashUnit() == true
        flashButton.text = when { manualExposureEnabled -> "⚡ PRO"; !available -> "⚡ N/A"; flashMode == ImageCapture.FLASH_MODE_AUTO -> "⚡ AUTO"; flashMode == ImageCapture.FLASH_MODE_ON -> "⚡ ON"; else -> "⚡ OFF" }
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

    private fun applyManualExposure(builder: Preview.Builder) {
        if (!manualExposureEnabled) return
        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manualIso.coerceIn(manualIsoMin, manualIsoMax))
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, manualExposureTimeNs.coerceIn(manualExposureMinNs, manualExposureMaxNs))
    }

    private fun applyManualExposure(builder: ImageCapture.Builder) {
        if (!manualExposureEnabled) return
        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manualIso.coerceIn(manualIsoMin, manualIsoMax))
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, manualExposureTimeNs.coerceIn(manualExposureMinNs, manualExposureMaxNs))
    }

    private fun refreshManualCapabilities() {
        val active = camera ?: return
        try {
            val id = Camera2CameraInfo.from(active.cameraInfo).cameraId
            activeCameraId = id
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val chars = manager.getCameraCharacteristics(id)
            focusMaxDiopters = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            if (focusMaxDiopters <= 0f) manualFocusEnabled = false
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            manualSupported = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { range ->
                manualIsoMin = range.lower
                manualIsoMax = range.upper
            }
            chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { range ->
                manualExposureMinNs = range.lower
                manualExposureMaxNs = range.upper
            }
            manualIso = manualIso.coerceIn(manualIsoMin, manualIsoMax)
            manualExposureTimeNs = manualExposureTimeNs.coerceIn(manualExposureMinNs, manualExposureMaxNs)
            if (!manualSupported && manualExposureEnabled) manualExposureEnabled = false
        } catch (_: Exception) {
            manualSupported = false
            manualExposureEnabled = false
        }
    }

    private fun showProControls() {
        refreshManualCapabilities()
        if (!manualSupported) {
            android.app.AlertDialog.Builder(this).setTitle("Pro controls unavailable")
                .setMessage("The active camera does not report Android manual-sensor support. You can still use EV compensation, tap-to-focus and zoom.")
                .setPositiveButton("OK", null).show()
            return
        }
        val isoLabel = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER }
        val isoSeek = SeekBar(this).apply {
            max = (manualIsoMax - manualIsoMin).coerceAtLeast(1)
            progress = (manualIso - manualIsoMin).coerceIn(0, max)
        }
        fun updateIsoLabel(value: Int) { isoLabel.text = "ISO $value  ·  range $manualIsoMin–$manualIsoMax" }
        updateIsoLabel(manualIso)
        isoSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { updateIsoLabel(manualIsoMin + progress) }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        val exposureChoices = listOf(
            125_000L to "1/8000 s", 250_000L to "1/4000 s", 500_000L to "1/2000 s", 1_000_000L to "1/1000 s",
            2_000_000L to "1/500 s", 4_000_000L to "1/250 s", 8_000_000L to "1/125 s", 16_666_667L to "1/60 s",
            33_333_333L to "1/30 s", 66_666_667L to "1/15 s", 125_000_000L to "1/8 s", 250_000_000L to "1/4 s",
            500_000_000L to "1/2 s", 1_000_000_000L to "1 s", 2_000_000_000L to "2 s", 4_000_000_000L to "4 s",
            8_000_000_000L to "8 s", 15_000_000_000L to "15 s", 30_000_000_000L to "30 s"
        ).filter { it.first in manualExposureMinNs..manualExposureMaxNs }
        val usableChoices = if (exposureChoices.isEmpty()) listOf(manualExposureMinNs to formatShutter(manualExposureMinNs)) else exposureChoices
        val shutterLabel = TextView(this).apply { text = "Shutter speed"; setTextColor(Color.WHITE); textSize = 14f; setPadding(0, dp(10), 0, dp(4)) }
        val shutterAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, usableChoices.map { it.second }) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply { setTextColor(Color.WHITE) }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).apply { setTextColor(Color.WHITE); setBackgroundColor(0xFF202329.toInt()) }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val shutterSpinner = Spinner(this).apply {
            adapter = shutterAdapter
            val selected = usableChoices.indexOfFirst { it.first == manualExposureTimeNs }.let { if (it >= 0) it else usableChoices.indices.minByOrNull { index -> kotlin.math.abs(usableChoices[index].first - manualExposureTimeNs) } ?: 0 }
            setSelection(selected)
        }
        val enable = CheckBox(this).apply {
            text = "Enable manual exposure (disables auto exposure and flash)"
            setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(0xFFD8F36A.toInt())
            isChecked = manualExposureEnabled
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
            setBackgroundColor(0xFF202329.toInt())
            addView(enable)
            addView(isoLabel, LinearLayout.LayoutParams(-1, dp(32)))
            addView(isoSeek, LinearLayout.LayoutParams(-1, dp(44)))
            addView(shutterLabel, LinearLayout.LayoutParams(-1, dp(28)))
            addView(shutterSpinner, LinearLayout.LayoutParams(-1, dp(48)))
        }
        android.app.AlertDialog.Builder(this).setTitle("Pro · Manual exposure").setView(panel)
            .setNeutralButton("Auto", null)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        manualExposureEnabled = false
                        startCamera()
                        modeLabel.text = "PRO · Auto exposure"
                        updateFlashUi()
                        dialog.dismiss()
                    }
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        manualIso = manualIsoMin + isoSeek.progress
                        manualExposureTimeNs = usableChoices[shutterSpinner.selectedItemPosition.coerceIn(0, usableChoices.lastIndex)].first
                        manualExposureEnabled = enable.isChecked
                        if (manualExposureEnabled) flashMode = ImageCapture.FLASH_MODE_OFF
                        startCamera()
                        modeLabel.text = if (manualExposureEnabled) "PRO · ISO $manualIso · ${formatShutter(manualExposureTimeNs)}" else "PRO · Auto exposure"
                        updateFlashUi()
                        dialog.dismiss()
                    }
                }
            }.show()
    }

    private fun formatShutter(nanos: Long): String {
        if (nanos <= 0L) return "Auto"
        val seconds = nanos / 1_000_000_000.0
        return if (seconds >= 1.0) String.format(Locale.US, "%.1f", seconds).removeSuffix(".0") + " s"
        else "1/${(1.0 / seconds).toInt().coerceAtLeast(1)} s"
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
        val options = arrayOf("Pro controls · ISO / shutter", "Exposure compensation", "Photo timer", "Photo aspect ratio", "Video quality", "Composition grid", "Reset zoom", "Computational modes · status", "About camera support")
        android.app.AlertDialog.Builder(this).setTitle("VivoCam settings").setItems(options) { _, which ->
            when (which) {
                0 -> showProControls()
                1 -> showExposureCompensation()
                2 -> chooseTimer()
                3 -> chooseAspectRatio()
                4 -> chooseVideoQuality()
                5 -> { gridEnabled = !gridEnabled; gridOverlay.visibility = if (gridEnabled) View.VISIBLE else View.GONE; refreshControlChipLabels() }
                6 -> setZoom(1f)
                7 -> android.app.AlertDialog.Builder(this).setTitle("Computational modes").setMessage("ASTRO: 8-frame translation-aligned star stack with robust high-pass luminance registration. NIGHT: 6-frame noise-reduction average. LONG EXP: 8-frame maximum-intensity blend for moving lights. CLONE: 3-frame touch-mask editor with brush and eraser for selecting pose regions. CINEMA: 24 FPS target and Camera2 stabilization request when supported; framing bars remain guides. These are real multi-frame outputs but not substitutes for dedicated sensor long exposure, rotation-aware/affine astro alignment, or semantic automatic clone masks. 360° stitching, portrait depth blur, and RAW/DNG are not included yet.").setPositiveButton("OK", null).show()
                else -> android.app.AlertDialog.Builder(this).setTitle("Camera support").setMessage("VivoCam uses Android CameraX. Manual ISO/shutter is available only on cameras that expose manual-sensor capability. Zoom, flash, focus, exposure and video quality vary by lens and firmware.").setPositiveButton("OK", null).show()
            }
        }.setPositiveButton("Close", null).show()
    }

    private fun textButton(label: String, size: Int, action: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = size.toFloat(); gravity = Gravity.CENTER; setTextColor(Color.WHITE); isClickable = true; isFocusable = true
        background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(14).toFloat(); setColor(0x77202329); setStroke(dp(1), 0x44FFFFFF) }
        setPadding(dp(5), 0, dp(5), 0)
        setOnClickListener { action() }
    }
    private fun weightParams() = LinearLayout.LayoutParams(0, dp(42), 1f)
    private fun space(w: Int, h: Int) = View(this).apply { layoutParams = ViewGroup.LayoutParams(w, h) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class CinemaBars : View(this@MainActivity) {
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val barHeight = height * 0.105f
            canvas.drawRect(0f, 0f, width.toFloat(), barHeight, barPaint)
            canvas.drawRect(0f, height - barHeight, width.toFloat(), height.toFloat(), barPaint)
        }
    }

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
        try { cloneSegmenter.close() } catch (_: Exception) {}
        clearCloneSession()
        super.onDestroy()
    }

}
