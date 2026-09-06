# VProxies for Android

VProxies is an Android VPN client for proxy servers that the signed-in user is authorized to use.
It connects directly to the selected source proxy through Android `VpnService` and the sing-box
`libbox` runtime. Gateway servers are used only for catalog and health metadata.

## Current prototype

- VProxies account sign-in through `https://api.vproxies.app/api/v1/`.
- Loads assigned gateways and proxies.
- Shows country, city, status, latency and endpoint visibility.
- Supports HTTP, HTTPS, SOCKS4A and SOCKS5 when advertised by the selected proxy.
- Requests short-lived direct connection data from `/connections`.
- Creates an Android TUN with `VpnService`; Wintun is not included because it is Windows-only.
- Uses local DNS by default. DNS-through-proxy is intentionally off because it caused slow HTTPS/
  SOCKS5 startup and bootstrap failures in earlier Windows testing.
- Never saves the account password or source-proxy credentials.
- GitHub Actions builds a universal APK from pinned, reproducible upstream source.

## Build

Open **Actions → Build VProxies Android APK → Run workflow**. The result is uploaded as the
`VProxies-Android-APK` artifact.

The workflow pins sing-box commit `60b504a1c74a33fe24872c8144c8f0b7d3d61b2a`, applies the files in
`overlay/`, builds the official Android `libbox` AARs, then builds the rebranded APK.

## Licensing

This repository is a GPLv3-or-later derivative overlay for sing-box for Android. It must remain
source-available when distributing APKs. VProxies is independent and is not endorsed by SagerNet,
sing-box, WireGuard, or Wintun. See `THIRD-PARTY-NOTICES.md`.

Use only proxy servers that you own or are authorized to use.
