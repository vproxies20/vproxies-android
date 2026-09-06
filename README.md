# VProxies for Android

VProxies is an Android VPN client for proxy servers that the signed-in user is authorized to use.
It connects directly to the selected source proxy through Android `VpnService` and the sing-box
`libbox` runtime. Gateway servers are used only for catalog and health metadata.

## Current prototype

- Native Jetpack Compose interface with VProxies Brand Kit colors and logo.
- Three focused tabs: Dashboard, Logs and Settings, with one state-aware power control.
- Live per-app upload/download rate and connection timer on the dashboard.
- VProxies account sign-in through `https://api.vproxies.app/api/v1/`.
- Loads assigned gateways and proxies.
- Shows country, city, status, latency and endpoint visibility.
- Lets the user choose HTTP, HTTPS, SOCKS4 or SOCKS5 when advertised by the selected proxy.
- Includes a separate manual-proxy connection form with optional credentials and HTTPS SNI.
- Supports full-system, web-rules-only and selected-app routing modes.
- Offers DNS-through-proxy (off by default), explicit TCP/UDP port 53 hijacking and strict routing.
- Preserves Android Private DNS by routing encrypted DNS-over-TLS (TCP/853) directly.
- Requests short-lived direct connection data from `/connections`.
- Creates an Android TUN with `VpnService`; Wintun is not included because it is Windows-only.
- Uses direct IP-literal DoH by default to avoid Android local-resolver loops. DNS-through-proxy is
  intentionally off because it caused slow HTTPS/SOCKS5 startup and bootstrap failures earlier.
- Never saves the account password or source-proxy credentials.
- Optionally remembers account and manual-proxy passwords using device-bound AES-GCM keys from
  Android Keystore. API-delivered source credentials remain ephemeral and are never remembered.
- Binds account/config API requests to the physical Wi-Fi/mobile network so an existing VPN
  tunnel cannot trap API DNS during reconnect.
- Uses the VProxies mark and cyan/violet branding from `vproxies.app`.
- Reports the real sing-box service state and startup errors in the VProxies screen.
- Runs an in-tunnel DNS/HTTPS check after startup instead of silently showing a connected state.
- Opens Android's native Always-on VPN settings, reports Always-on/Lockdown state and prevents an
  ambiguous in-app disconnect while Android owns the Always-on lifecycle.
- Checks GitHub Releases at startup and on demand, selects the correct device ABI, downloads over
  the physical network, verifies GitHub's SHA-256 asset digest and opens the Android installer.
- Disables and removes access to the inherited sing-box/SagerNet update screen, so update notices
  can only come from the VProxies GitHub repository.
- GitHub Actions builds separate ARM64, ARM32, x86_64 and x86 APKs from pinned upstream source.

## Build

Open **Actions → Build VProxies Android APK → Run workflow**. Download the clearly numbered artifact:

1. `1-VProxies-ARM64-Dien-thoai-hien-nay` contains `VProxies-ARM64.apk`.
2. `2-VProxies-ARM32-Dien-thoai-cu` contains `VProxies-ARM32.apk`.
3. The two `3-VProxies-x86...-Emulator` artifacts contain the x86 or x86_64 emulator APK.

The workflow pins the sing-box v1.13.20 commit `56f91dfeabd6f4edbd437dfcc1e5b0ebc856b778`, applies the files in
`overlay/`, builds the official Android `libbox` AARs, then builds the rebranded APK.

## Signed GitHub Releases

In-place Android updates require every published APK to use the same private release key. Configure
these GitHub Actions secrets before pushing a `v*` tag:

- `VPROXIES_KEYSTORE_BASE64`
- `VPROXIES_KEYSTORE_PASSWORD`
- `VPROXIES_KEY_ALIAS`
- `VPROXIES_KEY_PASSWORD`

The workflow refuses to publish a tagged release when any signing secret is missing. A successful
tag build publishes all four APKs to GitHub Releases; the in-app updater never uses expiring Actions
artifacts.

## Licensing

This repository is a GPLv3-or-later derivative overlay for sing-box for Android. It must remain
source-available when distributing APKs. VProxies is independent and is not endorsed by SagerNet,
sing-box, WireGuard, or Wintun. See `THIRD-PARTY-NOTICES.md`.

Use only proxy servers that you own or are authorized to use.
