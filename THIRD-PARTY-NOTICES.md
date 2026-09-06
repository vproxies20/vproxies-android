# Third-party notices

## sing-box and sing-box for Android

- Core: https://github.com/SagerNet/sing-box
- Android client: https://github.com/SagerNet/sing-box-for-android
- Pinned core commit: `60b504a1c74a33fe24872c8144c8f0b7d3d61b2a`
- Pinned Android client commit: `af61098358a8141dea71f232b7eaebf4ccee8868`
- License: GNU General Public License version 3 or later, plus the upstream naming restriction.

VProxies uses a different name, package identifier and interface and does not imply association
with or endorsement by SagerNet. Corresponding upstream source and this complete modification
overlay are available from the links and files above.

## Wintun

Wintun is a Layer 3 TUN driver for Windows. It is used by the separate VProxies Windows build and
is deliberately not copied into the Android APK. Android provides its TUN file descriptor through
the platform `VpnService` API.
