# OpenStudio Privacy and Leak Checks

## Current MVP privacy defaults
- The Android manifest intentionally does **not** request `android.permission.INTERNET`.
- Android app backup is explicitly disabled.
- Cleartext network traffic is explicitly disabled.
- The app uses Android's system photo picker and saves edited copies locally to `Pictures/OpenStudio`.
- No analytics, advertising, or crash-reporting SDK is currently declared in the app module.

## Checks included
1. The in-app **Privacy / leak check** button checks the installed app's INTERNET permission, backup flag, and cleartext-traffic flag. Any failed check is shown as a warning.
2. The GitHub Actions workflow runs `scripts/privacy_guard.py` before building. It fails the build if the manifest requests Internet access, backup is enabled, cleartext traffic is enabled, or a small set of common networking/analytics dependencies appears in the app Gradle file.

## Important limits
- These checks are defense-in-depth, **not a guarantee that no private information can ever leak**.
- A future cloud-AI feature must remain opt-in, explain exactly what will be uploaded, and obtain explicit consent before sending any photo or metadata. If cloud networking is introduced, this offline-only guard should be deliberately redesigned and reviewed rather than silently bypassed.
- Review transitive dependencies, exported components, logs, temporary files, EXIF metadata, and the final merged APK manifest before calling a release secure.
- The current workflow's guard is a configuration smoke check, not a complete static-analysis or penetration test.
- Do not upload sensitive photos to an untrusted build or test service. Test using synthetic/sample images first.
