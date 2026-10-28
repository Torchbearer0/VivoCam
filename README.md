# VivoCam — Android camera starter

Native Kotlin + CameraX camera app starter with a dark, thumb-friendly UI inspired by the general layout principles of HyperOS Camera. It does not use Xiaomi's proprietary UI assets or image-processing stack.

## GitHub Actions build

This repository includes `.github/workflows/android-build.yml`. GitHub Actions builds a debug APK whenever you push to `main`/`master`, open or update a pull request targeting those branches, or manually run the workflow.

**Download the APK from GitHub:**

1. Push this project to a GitHub repository.
2. Open the repository's **Actions** tab.
3. Select **Android CI** and open the latest successful run.
4. Under **Artifacts**, download `vivocam-debug-apk` and extract `app-debug.apk`.
5. Transfer the APK to your Vivo X60 Pro and install it. Android may ask you to allow installation from the app used to open the APK.

The workflow uses JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3 and Android SDK Platform 35. No Gradle wrapper is required by this workflow because the Gradle setup action installs Gradle on the runner. For local Android Studio builds, install Android SDK Platform 35 and use a compatible Gradle 8.9 installation.

## Build locally

From the project root:

```bash
gradle --version
gradle assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Included and functional in v0.7.0

- CameraX live preview and standard still photo capture to `Pictures/VivoCam`
- Video recording with microphone audio (when permission is granted), saved to `Movies/VivoCam`
- Front/back camera switching
- Flash cycle: Off / Auto / On when supported by the active camera
- Photo timer: Off / 3 / 5 / 10 seconds, with visible countdown
- Photo aspect ratio: 4:3 and 16:9
- Pinch-to-zoom plus 0.6× / 1× / 2× / 5× presets, clamped to the active camera's reported zoom range
- Tap-to-focus / meter, plus a center-focus button
- Exposure compensation slider with reset, using the active camera's reported EV range
- Composition grid toggle
- Video quality selector for 720p / 1080p / 4K, with CameraX fallback when a quality is unsupported
- Pro controls for manual ISO and shutter time when the active Camera2 device reports manual-sensor capability
- Manual exposure controls show the reported ISO/shutter ranges; enabling them disables automatic exposure and flash
- Settings menu and open-latest-media shortcut
- Refined rounded control surfaces, clearer mode/status labels, and a consistent dark camera layout
- Replaced the old mid-viewfinder zoom/utility rows with a single bottom adjustment dock
- Added live zoom and EV sliders, plus continuous ISO and logarithmic shutter sliders on supported manual-sensor cameras
- Added live manual-focus distance where the lens reports a minimum focus distance; tap-to-focus remains the fallback
- Added live white-balance presets (Auto, Tungsten, Fluorescent, Daylight, Cloudy)
- Added quick bottom-dock toggles/cycles for timer, ratio, flash, grid and video quality; no modal is needed for common adjustments
- **ASTRO:** captures 8 frames and averages them to reduce random noise; uses manual ISO/shutter when available, but does not align stars between frames
- **NIGHT:** captures and averages 6 frames for noise reduction; best with a steady phone
- **LONG EXP:** captures 8 frames and maximum-intensity blends them to accumulate moving bright lights; this is not one continuous sensor exposure
- **CLONE:** MIUI-inspired cumulative workflow: first shutter sets the background, subsequent shutter presses run on-device ML Kit selfie/person segmentation and add the detected person to the composition; the previous composite appears as a translucent alignment ghost over the live preview. Includes Undo, Reset, and Save composite actions. Keep the phone fixed; segmentation is best for one clearly visible person in good light. The overlay is a ghosting guide, not a full real-time background replacement.
- **CINEMA:** adds cinematic framing bars to the live preview and records video at the selected CameraX quality; bars are framing guides and are not baked into the saved video

Clone uses the ML Kit selfie-segmentation model dependency and may need network access the first time the model is downloaded, depending on Google Play services/model availability. These controls are wired into CameraX rather than being decorative buttons. Actual flash, zoom, exposure range, focus behavior, and video resolution depend on the lens and device firmware.

## Important limitations

These computational modes are functional first implementations, not substitutes for dedicated computational photography pipelines:

- **Astro:** average stack without star alignment, dark-frame subtraction, or RAW processing. Tripod use is strongly recommended.
- **Long exposure:** multi-frame maximum-intensity blending can accumulate traffic/light trails, but is not a continuous long sensor exposure and can show artifacts if the camera moves.
- **Clone:** person segmentation is automatic and processed on-device. It can miss hair, motion-blurred limbs, people partly outside the frame, or subjects in low light. Small camera shifts are not fully registered automatically; use a tripod or steady surface. The live overlay is translucent guidance rather than a true segmented live composite.
- **Cinematic video:** preview-only letterbox guides; the recorded file is not cropped or color-graded and does not include depth-based subject separation.
- **360°:** guided overlapping capture and panorama stitching are not included yet.
- **Portrait:** depth-map blur is not included; capture falls back to a regular photo.
- **RAW/DNG:** not included.

## Open in Android Studio

1. Install current stable Android Studio and Android SDK Platform 35.
2. Open this folder as a project and let Gradle sync.
3. Connect the Vivo X60 Pro with USB debugging enabled and select it as the run target.
4. Run the `app` configuration and grant camera and microphone permissions.

The project targets Android 8.0+ and uses CameraX 1.4.1. Actual lens availability, zoom, resolution, stabilization and manual sensor support depend on the device firmware. Test on the phone; an emulator is not sufficient for camera feature validation.

## Next advanced feature stage

1. Add star alignment, dark-frame subtraction, and higher-quality astro processing.
2. Add a clone subject-mask editor so the user can select which pose regions are composited.
3. Add guided panorama capture and a stitching pipeline for true 360° output.
4. Add post-processed cinematic crop/color grading and verify whether hardware stabilization and focus tracking are exposed on the X60 Pro.
5. Investigate RAW/DNG output through a compatible Camera2 capture pipeline.

## Build reliability

The GitHub Actions workflow retries dependency resolution only when logs indicate a transient Maven/network problem (such as HTTP 429). Kotlin or Android compilation errors fail immediately, so a code issue does not trigger several unnecessary rebuilds inside one run.

## Remaining advanced work

Next stages could add star alignment and RAW/DNG support for astro, a clone mask editor, panorama stitching for 360°, and post-processed cinematic crop/color grading. These require additional pipelines beyond standard CameraX capture.


### 0.6.0 imaging improvements

Astro now performs translation-only registration before stacking: robust high-pass luminance matching estimates frame displacement, then aligned pixels are averaged to reduce random noise. This is not full rotation/affine star tracking, and tripod stability remains important. Clone mode now includes a brush/eraser mask editor to select regions from each of three captured poses. Cinema mode requests 24 FPS only when the active camera advertises a fixed 24 FPS range, and enables Camera2 video stabilization or optical stabilization when reported by the device. Actual recorded frame rate and stabilization depend on device/firmware support.
