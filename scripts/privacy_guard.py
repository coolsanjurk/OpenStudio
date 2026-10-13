#!/usr/bin/env python3
"""Fail-closed privacy guard for OpenStudio's current offline-only MVP.

This is a focused configuration check, not a full security audit. It intentionally
fails the CI job if network access, Android backup, or cleartext traffic is enabled.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
errors = []
checks = []

manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
try:
    root = ET.parse(manifest_path).getroot()
    app = root.find("application")
    permissions = {node.get(ANDROID + "name") for node in root.findall("uses-permission")}
    internet = "android.permission.INTERNET" in permissions
    checks.append(("Manifest has no INTERNET permission", not internet))
    if internet:
        errors.append("INTERNET permission is present; offline-only privacy guard rejects it.")
    if app is None:
        errors.append("AndroidManifest.xml has no <application> element.")
    else:
        backup = app.get(ANDROID + "allowBackup", "true").lower() == "true"
        checks.append(("Android backup disabled", not backup))
        if backup:
            errors.append("android:allowBackup must be explicitly false.")
        cleartext = app.get(ANDROID + "usesCleartextTraffic", "true").lower() == "true"
        checks.append(("Cleartext HTTP traffic disabled", not cleartext))
        if cleartext:
            errors.append("android:usesCleartextTraffic must be explicitly false.")
except (ET.ParseError, OSError) as exc:
    errors.append(f"Could not parse Android manifest: {exc}")

gradle_path = ROOT / "app/build.gradle.kts"
try:
    gradle = gradle_path.read_text(encoding="utf-8")
    # Block common networking/telemetry libraries from entering this offline MVP silently.
    forbidden = [
        r"\b(okhttp|retrofit|ktor-client|firebase-analytics|firebase-crashlytics|sentry-android|mixpanel)\b",
        r"com\.google\.android\.gms:play-services-analytics",
    ]
    matches = [pattern for pattern in forbidden if re.search(pattern, gradle, re.IGNORECASE)]
    checks.append(("No known network/analytics SDK dependencies", not matches))
    if matches:
        errors.append("Potential network/analytics dependency found in app/build.gradle.kts.")
except OSError as exc:
    errors.append(f"Could not read app Gradle file: {exc}")


cargo_path = ROOT / "rust/openstudio_native/Cargo.toml"
if cargo_path.exists():
    cargo = cargo_path.read_text(encoding="utf-8")
    forbidden_rust = [r"\\b(reqwest|ureq|hyper|isahc)\\b"]
    rust_matches = [pattern for pattern in forbidden_rust if re.search(pattern, cargo, re.IGNORECASE)]
    checks.append(("Rust crate has no known HTTP client dependency", not rust_matches))
    if rust_matches:
        errors.append("Potential HTTP client dependency found in Rust crate.")
else:
    errors.append("Rust native crate manifest is missing.")

for label, passed in checks:
    print(f"{'PASS' if passed else 'FAIL'}: {label}")
if errors:
    print("\nPRIVACY GUARD FAILED:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)
print(f"\nPrivacy guard passed ({len(checks)} checks). This is not proof that all possible leaks are absent.")
