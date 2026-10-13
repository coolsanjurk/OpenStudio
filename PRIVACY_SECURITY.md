# OpenStudio Privacy and Leak Checks

## Current MVP privacy defaults
- The Android manifest intentionally does not request `android.permission.INTERNET`.
- Android app backup is explicitly disabled and cleartext network traffic is disabled.
- The app uses Android's system photo picker and saves edited copies locally to `Pictures/OpenStudio`.
- No analytics, advertising, or crash-reporting SDK is declared in the app module.
- The Rust pixel-processing crate operates on in-memory pixels and does not access the network or files.

## Checks included
1. The in-app **Privacy / leak check** button checks the installed app's INTERNET permission, backup flag, and cleartext-traffic flag.
2. GitHub Actions runs `scripts/privacy_guard.py` before building. It checks the Android manifest and selected common networking/analytics dependencies, including the Rust crate's direct dependencies.
3. CI builds Rust native libraries for Android ARM64 and x86_64 and verifies they exist before APK packaging.

## Important limits
These are defense-in-depth configuration checks, not a guarantee that no private information can ever leak. They do not inspect every transitive dependency, all runtime network behavior, every exported component, logs, temporary files, or EXIF metadata. Any future cloud-AI feature must remain opt-in and obtain explicit consent before uploading a photo or metadata. The Rust integration is experimental and still needs native/Kotlin parity tests, performance benchmarks and physical-device testing.
