# VivoCam launch crash fix

Fixed the startup crash reported by AndroidRuntime:
`IllegalStateException: The specified child already has a parent` at `MainActivity.buildUi`.

The camera overlay was added to the root FrameLayout once near its creation and then added a second time at the end of `buildUi()`. Android views cannot have two parents / be added twice. The second `root.addView(overlay)` has been removed.

## Apply
1. Replace the repository files with the contents of this archive (the project folder inside the ZIP is `vivocam`).
2. Commit and push to `main`.
3. Wait for the GitHub Actions build to pass and install the newly generated APK artifact. Uninstall the previous VivoCam build first if Android refuses to update it.

This fixes the exact crash in the supplied log. Camera device/runtime issues, if any, may need a separate log after this change.
