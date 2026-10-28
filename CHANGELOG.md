# Changelog

## 0.7.0 — Cumulative automatic Clone mode

- Replaced timed multi-frame Clone capture with a cumulative shutter workflow.
- First capture establishes the base; every later capture adds a segmented person to the current composite.
- Added on-device ML Kit selfie segmentation, confidence-based soft mask compositing, and a translucent live-view alignment ghost.
- Added Undo, Reset, and Save composite controls so users can build the image over multiple poses.
- Added frame-size normalization and a bounded undo history to reduce memory pressure.
- Preserved Astro, Long Exposure, Night, and Cinema modes from 0.6.0.
- Limitations: no full optical-flow/background registration, no real-time background replacement, and subject segmentation quality depends on lighting and subject visibility.

# Changelog

## 0.2.0 — camera controls bundle

- Added flash Off/Auto/On cycling when supported.
- Added 3/5/10-second photo timer and countdown.
- Added 4:3 and 16:9 photo output selection.
- Added pinch zoom and zoom presets with device-range clamping.
- Added tap-to-focus/metering and center-focus shortcut.
- Added exposure compensation slider with reset.
- Added composition grid toggle.
- Added 720p/1080p/4K video quality selection with CameraX fallback.
- Added volume-key shutter support and latest-media shortcut.
- Added retry for transient Maven repository rate-limit/network failures in GitHub Actions.

Advanced computational modes remain marked as not yet implemented in the app.


## 0.3.0 — Pro controls and UI refinement

- Added Camera2 capability detection for manual sensor exposure on the active camera.
- Added Pro panel with ISO and shutter-time selection, bounded to reported sensor ranges.
- Added an Auto action to restore automatic exposure; manual exposure disables flash.
- Added clear messaging when the selected camera does not expose manual-sensor support.
- Refined common controls with rounded, outlined surfaces and improved Pro/status labels.
- Updated settings and support notes to distinguish implemented controls from computational-mode roadmap items.

Manual controls must be verified on the Vivo X60 Pro because available camera capabilities vary by lens and firmware. RAW/DNG and computational long-exposure processing are still not implemented.


## 0.4.0 — bottom control dock and live adjustments

- Removed the separate zoom and utility rows from the middle of the preview.
- Added one bottom dock with a horizontal control selector and a live slider for the selected adjustment.
- Zoom and EV now update while dragging; ISO and shutter sliders send live Camera2 request options when manual sensor support is available.
- Added manual focus-distance control where the active lens exposes focus distance, with tap-to-focus fallback.
- Added live white-balance cycling: Auto, Tungsten, Fluorescent, Daylight and Cloudy.
- Added quick dock controls for timer, photo ratio, flash, composition grid and video quality.
- Made Pro mode land on direct ISO controls instead of forcing the old dialog.

Hardware-dependent behavior still requires a device build and testing on the Vivo X60 Pro.


## 0.5.0 — computational capture bundle

- Added multi-frame ASTRO capture: 8-frame average stack with manual ISO/shutter presets when the active lens supports manual sensor control.
- Added NIGHT capture: 6-frame average composite intended to reduce random noise when the phone is held steady.
- Added LONG EXP capture: 8-frame maximum-intensity blend to accumulate moving bright lights and trails; uses short manual exposures when supported.
- Added CLONE capture: 3 timed frames with a pause between poses, composited with a bright-detail blend. A fixed phone position improves background consistency.
- Added CINEMA framing bars over the preview and clearer recording status while retaining selected CameraX video quality and microphone audio.
- Added orientation-aware frame decoding and gallery export for processed composites.
- Added a computational-mode status panel describing how each effect is produced and what is not implemented.

Known limitations: astro frames are not star-aligned; long-exposure output is a multi-frame blend, not one continuous sensor exposure; clone mode has no subject mask/editor; cinematic bars are preview guides and are not baked into the recorded video. 360° stitching, portrait depth blur, and RAW/DNG remain unimplemented.


## 0.6.0 — Astro registration, Clone mask editor, Cinema stabilization

- Astro stack now estimates per-frame translational star-field displacement using robust high-pass luminance matching, aligns frames to a reference, and averages only valid overlapping pixels. This is translation-only registration; it does not correct rotation, lens distortion, or star-field rotation over long sessions.
- Clone now opens an interactive mask editor after three pose captures. Select a pose, paint regions to include, adjust brush size, erase mask strokes, and build the composite. The editor uses reduced-resolution source frames to limit memory use.
- Cinema requests 24 FPS only when the active camera reports an exact 24/24 FPS range, and requests Camera2 electronic stabilization when supported, falling back to optical stabilization metadata when available. Otherwise it keeps the camera usable and reports that a fixed 24 FPS range is unavailable.
- Version bumped to 0.6.0 (versionCode 6).
