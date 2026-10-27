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
