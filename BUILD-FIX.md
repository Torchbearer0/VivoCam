# Build notes for 0.7.1

This update fixes two Kotlin compilation errors in Clone-mode error handling by explicitly referencing `this@MainActivity` for Toast context. It retains the ML Kit selfie segmentation dependency (`com.google.mlkit:segmentation-selfie:16.0.0-beta6`) for on-device person masks in Clone mode. The first model use may require the ML Kit model to be available/downloaded. GitHub Actions should build the APK with the existing JDK 17 / Gradle 8.9 setup.

If dependency resolution fails transiently, rerun the workflow. If compilation fails, preserve the full Gradle error log before changing versions.
