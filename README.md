# VProxies for Android

VProxies is an Android VPN client for proxy servers that the signed-in user is authorized to use.
It connects directly to the selected source proxy through Android `VpnService` and the sing-box
`libbox` runtime. Gateway servers are used only for catalog and health metadata.

## Current prototype

- VProxies account sign-in through `https://api.vproxies.app/api/v1/`.
- Loads assigned gateways and proxies.
- Shows country, city, status, latency and endpoint visibility.
- Lets the user choose HTTP, HTTPS, SOCKS4 or SOCKS5 when advertised by the selected proxy.
- Includes a separate manual-proxy connection form with optional credentials and HTTPS SNI.
- Supports full-system, web-rules-only and selected-app routing modes.
- Offers DNS-through-proxy (off by default), DNS hijacking and strict routing to prevent leaks.
- Requests short-lived direct connection data from `/connections`.
- Creates an Android TUN with `VpnService`; Wintun is not included because it is Windows-only.
- Uses local DNS by default. DNS-through-proxy is intentionally off because it caused slow HTTPS/
  SOCKS5 startup and bootstrap failures in earlier Windows testing.
- Never saves the account password or source-proxy credentials.
- Binds account/config API requests to the physical Wi-Fi/mobile network so an existing VPN
  tunnel cannot trap API DNS during reconnect.
- Uses the VProxies mark and cyan/violet branding from `vproxies.app`.
- Reports the real sing-box service state and startup errors in the VProxies screen.
- GitHub Actions builds separate ARM64, ARM32, x86_64 and x86 APKs from pinned upstream source.

## Build

Open **Actions → Build VProxies Android APK → Run workflow**. Download the clearly numbered artifact:

1. `1-VProxies-ARM64-Dien-thoai-hien-nay` contains `VProxies-ARM64.apk`.
2. `2-VProxies-ARM32-Dien-thoai-cu` contains `VProxies-ARM32.apk`.
3. The two `3-VProxies-x86...-Emulator` artifacts contain the x86 or x86_64 emulator APK.

The workflow pins the sing-box v1.13.20 commit `56f91dfeabd6f4edbd437dfcc1e5b0ebc856b778`, applies the files in
`overlay/`, builds the official Android `libbox` AARs, then builds the rebranded APK.

## Licensing

This repository is a GPLv3-or-later derivative overlay for sing-box for Android. It must remain
source-available when distributing APKs. VProxies is independent and is not endorsed by SagerNet,
sing-box, WireGuard, or Wintun. See `THIRD-PARTY-NOTICES.md`.

Use only proxy servers that you own or are authorized to use.
