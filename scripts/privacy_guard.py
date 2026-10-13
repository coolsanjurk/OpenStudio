#!/usr/bin/env python3
"""Fail-closed privacy guard for OpenStudio's offline-only Android MVP.

This is a focused static configuration check, not a complete security audit.
It rejects common Android network permissions, known networking/telemetry
dependencies, and direct network APIs in application source.
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
    forbidden_permissions = {
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.CHANGE_WIFI_STATE",
    }
    found_permissions = sorted(p for p in permissions if p in forbidden_permissions)
    checks.append(("Manifest excludes network-related permissions", not found_permissions))
    if found_permissions:
        errors.append("Offline-only build contains forbidden permissions: " + ", ".join(found_permissions))
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

# These are deliberately conservative: adding a network-capable API requires
# an explicit privacy review rather than silently entering the offline MVP.
dependency_path = ROOT / "app/build.gradle.kts"
try:
    gradle = dependency_path.read_text(encoding="utf-8")
    forbidden_dependencies = [
        r"\b(okhttp|retrofit|ktor-client|firebase-analytics|firebase-crashlytics|sentry-android|mixpanel)\b",
        r"com\.google\.android\.gms:play-services-analytics",
        r"com\.squareup\.okhttp3:",
        r"com\.squareup\.retrofit2:",
    ]
    matches = [pattern for pattern in forbidden_dependencies if re.search(pattern, gradle, re.IGNORECASE)]
    checks.append(("No known network/analytics SDK dependencies", not matches))
    if matches:
        errors.append("Potential network/analytics dependency found in app/build.gradle.kts.")
except OSError as exc:
    errors.append(f"Could not read app Gradle file: {exc}")

source_roots = [
    ROOT / "app/src/main/java",
    ROOT / "app/src/main/kotlin",
    ROOT / "rust/openstudio_native/src",
]
network_api_patterns = [
    r"\bjava\.net\.(?:URL|URI|Socket|ServerSocket|HttpURLConnection)\b",
    r"\b(?:okhttp3|retrofit2|io\.ktor\.client|org\.apache\.http)\b",
    r"\b(?:HttpURLConnection|HttpsURLConnection|Socket|ServerSocket)\s*\(",
    r"\b(?:FirebaseAnalytics|FirebaseCrashlytics|Sentry|Mixpanel)\b",
    r"\b(?:reqwest|ureq|hyper|isahc)::",
]
source_matches = []
for source_root in source_roots:
    if not source_root.exists():
        continue
    for path in source_root.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in {".kt", ".java", ".rs"}:
            continue
        try:
            content = path.read_text(encoding="utf-8")
        except OSError:
            errors.append(f"Could not read source file: {path.relative_to(ROOT)}")
            continue
        for pattern in network_api_patterns:
            if re.search(pattern, content):
                source_matches.append(f"{path.relative_to(ROOT)} matches {pattern}")
                break
checks.append(("No known direct networking/telemetry APIs in application source", not source_matches))
if source_matches:
    errors.append("Potential network/telemetry API found: " + "; ".join(source_matches))

cargo_path = ROOT / "rust/openstudio_native/Cargo.toml"
if cargo_path.exists():
    cargo = cargo_path.read_text(encoding="utf-8")
    forbidden_rust = [r"\b(reqwest|ureq|hyper|isahc)\b"]
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
