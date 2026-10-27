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

## Included and functional

- CameraX live preview
- Standard photo capture saved to `Pictures/VivoCam`
- 1080p video recording saved to `Movies/VivoCam`
- Front/back camera switching
- Zoom buttons clamped to the active camera's reported zoom range
- Bottom mode strip and UI scaffold for Pro, Portrait, Night, Long Exposure, Clone, 360°, Cinematic and Astro modes

## Important limitations

Advanced mode buttons are UI/roadmap scaffolds, not implemented capture algorithms. They display a message instead of pretending to produce these effects.

- **Long exposure:** requires manual sensor exposure if supported by the selected camera, or frame accumulation/stacking. CameraX standard still capture alone does not guarantee multi-second shutter control.
- **Waterfall, fireworks, light painting/trails, traffic trails, star trails:** need per-mode exposure strategy, burst/frame accumulation, alignment and blending.
- **Milky Way / M31:** requires RAW/manual exposure where available, dark-frame handling and stacking. A phone lens/sensor has real limits for deep-sky detail.
- **Clone:** requires multiple timed captures, a stable camera, alignment, masks and compositing UI.
- **360°:** requires guided overlapping images plus a panorama stitcher; this is not a single camera API toggle.
- **Cinematic video:** current Video/Cinema mode records regular video. Depth segmentation, subject tracking and grading need a separate pipeline and may not be available in real time on this device.

## Open in Android Studio

1. Install current stable Android Studio and Android SDK Platform 35.
2. Open this folder as a project and let Gradle sync.
3. Connect the Vivo X60 Pro with USB debugging enabled and select it as the run target.
4. Run the `app` configuration and grant camera and microphone permissions.

The project targets Android 8.0+ and uses CameraX 1.4.1. Actual lens availability, zoom, resolution, stabilization and manual sensor support depend on the device firmware. Test on the phone; an emulator is not sufficient for camera feature validation.

## Suggested next implementation order

1. Read Camera2 characteristics for each physical/logical lens and expose only supported controls.
2. Add Camera2 interop for supported exposure time, ISO, focus and RAW capture; report unsupported settings clearly.
3. Implement burst/frame-stack capture for long exposure and astrophotography, with alignment and robust processing.
4. Add clone capture guide, mask editor and compositor.
5. Add guided panorama capture and stitching for 360° output.
6. Prototype cinematic video with supported stabilization, focus tracking and post-processing; verify performance on the X60 Pro.
