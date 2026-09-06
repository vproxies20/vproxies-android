#!/usr/bin/env python3
"""Apply the VProxies Android overlay to a pinned sing-box checkout."""

from pathlib import Path
import shutil
import sys


EXPECTED_CORE = "56f91dfeabd6f4edbd437dfcc1e5b0ebc856b778"
EXPECTED_ANDROID = "af61098358a8141dea71f232b7eaebf4ccee8868"


def replace_once(path: Path, old: str, new: str) -> None:
    value = path.read_text(encoding="utf-8")
    if old not in value:
        raise RuntimeError(f"Expected text not found in {path}: {old!r}")
    path.write_text(value.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: prepare-upstream.py <sing-box-checkout>")
    root = Path(sys.argv[1]).resolve()
    client = root / "clients" / "android"
    overlay = Path(__file__).resolve().parents[1] / "overlay"
    if not (root / "go.mod").is_file() or not (client / "app").is_dir():
        raise SystemExit("The supplied path is not a recursive sing-box checkout")

    activity_src = overlay / "VProxiesActivity.kt"
    activity_dst = client / "app/src/main/java/io/nekohasekai/sfa/vproxies/VProxiesActivity.kt"
    activity_dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(activity_src, activity_dst)
    shutil.copy2(overlay / "VProxiesFrontend.kt", activity_dst.parent / "VProxiesFrontend.kt")
    shutil.copy2(overlay / "VProxiesSecureStore.kt", activity_dst.parent / "VProxiesSecureStore.kt")
    picker_src = overlay / "VProxiesAppPickerActivity.kt"
    picker_dst = client / "app/src/main/java/io/nekohasekai/sfa/vproxies/VProxiesAppPickerActivity.kt"
    shutil.copy2(picker_src, picker_dst)
    logo_src = overlay / "ic_vproxies_logo.xml"
    logo_dst = client / "app/src/main/res/drawable/ic_vproxies_logo.xml"
    shutil.copy2(logo_src, logo_dst)

    replace_once(
        client / "app/build.gradle.kts",
        'applicationId = "io.nekohasekai.sfa"',
        'applicationId = "app.vproxies.android"',
    )
    replace_once(
        client / "app/build.gradle.kts",
        "minSdk = 23",
        "minSdk = 24",
    )
    replace_once(
        client / "app/build.gradle.kts",
        "isUniversalApk = true",
        "isUniversalApk = false",
    )
    replace_once(
        client / "app/build.gradle.kts",
        'base.archivesName.set("SFA-${versionName}")',
        'base.archivesName.set("VProxies-${versionName}")',
    )
    replace_once(
        client / "app/src/main/AndroidManifest.xml",
        'android:name=".compose.MainActivity"',
        'android:name=".vproxies.VProxiesActivity"',
    )
    replace_once(
        client / "app/src/main/AndroidManifest.xml",
        "    </application>",
        '        <activity\n'
        '            android:name=".vproxies.VProxiesAppPickerActivity"\n'
        '            android:exported="false"\n'
        '            android:theme="@style/AppTheme" />\n\n'
        '        <activity\n'
        '            android:name=".compose.MainActivity"\n'
        '            android:exported="false"\n'
        '            android:theme="@style/AppTheme" />\n\n'
        "    </application>",
    )
    manifest = client / "app/src/main/AndroidManifest.xml"
    manifest.write_text(
        manifest.read_text(encoding="utf-8").replace(
            '@mipmap/ic_launcher',
            '@drawable/ic_vproxies_logo',
        ),
        encoding="utf-8",
    )
    replace_once(
        client / "app/src/main/AndroidManifest.xml",
        '<uses-permission android:name="android.permission.INTERNET" />',
        '<uses-permission android:name="android.permission.INTERNET" />\n'
        '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />',
    )

    for strings in (client / "app/src/main/res").glob("values*/strings.xml"):
        text = strings.read_text(encoding="utf-8")
        text = text.replace(
            '<string name="app_name" translatable="false">sing-box</string>',
            '<string name="app_name" translatable="false">VProxies</string>',
        )
        strings.write_text(text, encoding="utf-8")

    (client / "version.properties").write_text(
        "VERSION_CODE=9\nVERSION_NAME=0.4.2\nGO_VERSION=go1.26.7\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
