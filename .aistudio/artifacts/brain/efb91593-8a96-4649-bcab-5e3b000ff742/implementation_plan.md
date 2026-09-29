# Master Technical Prompt: UraniumTV Architecture & Feature Updates (Post-Sept 26, 2026)

This document contains the implementation plan and the comprehensive technical master prompt detailing all changes, architectural designs, algorithms, and data flows introduced into UraniumTV after September 26, 2026.

---

## Plan Overview

1. **Section 1: NetMirror Stream Keying, Deduplication & Quality Engine**
   - Clean regex detection (`VIDEO_URL_REGEX`) avoiding segment `.ts` matches.
   - Strict one-link-per-resolution deduplication (`1080p`, `720p`, `480p`, `360p`).
   - Domain scoring ranking `hakunayamata.com` / `hakunaymatata.com` over secondary proxies.
   - JS-to-Android `<video>` dimension bridge and auto-selection.

2. **Section 2: Minimalist Reactor Core Buffering Overlay**
   - Streamlining `PlayerBufferingOverlay` in `FuturisticEffects.kt`.
   - Removing status text labels to present a pure rotating nuclear radiation hazard symbol with glowing radial pulse.
   - Integration in `WatchScreen.kt` and `YouTubeWatchScreen.kt`.

3. **Section 3: Domain Rule Aliasing & Custom Headers**
   - Inclusion of `hakunayamata.com` alongside `hakunaymatata.com` in `AppConfig.REFERER_RULES`.
   - Automatic MIME type classification (`MimeTypes.APPLICATION_M3U8`) for HLS playback.

4. **Section 4: Firebase Realtime Database Whitelist & Subscription Lifecycle**
   - Dual-key lookup: Firebase Auth UID first, email with periods replaced by commas second.
   - Paths: `/whitelist/<key>` (boolean) and `/whitelistMeta/<key>` (`expiresAt`, `grantedAt`).
   - Strict rendering states: Hidden if never whitelisted; "Subscription: Lifetime access" if active without expiry; "Subscription active until [Date]" if future expiry; "Subscription expired on [Date]" if expired or revoked.
   - Date formatting: Readable string (e.g., "September 30, 2026").

5. **Section 5: Multi-Tiered Ad Blocker & Anti-Adblock Bypass Engine**
   - Anti-Adblock detection spoofing: Immutable getter for `window.llvpnLoaded` returning `true`.
   - Trap neutralization: `window.canRunAds = true`, `window.isAdBlockActive = false`, dummy `window.open` implementation.
   - Fake 200 OK stub responses for intercepted ad scripts to satisfy `<script>` `onload` and prevent `onerror` triggering.
   - 68-byte 1x1 transparent PNG stubs for tracking pixels.
   - Dynamic CSS injection and DOM pruning for full-screen clickjack overlays (`z-index: 2147483647`) and `.adblock-container` / `.adblock-card` shields.
   - Safe navigation allowlist permitting legitimate embed hosts (`watch21.shop`, `watch22.shop`, `watch-download.shop`, etc.) while rejecting ad redirects.

---

## Master Technical Prompt Specification

```markdown
You are building or maintaining UraniumTV (an advanced Android streaming and media playback app built with Kotlin, Jetpack Compose, ExoPlayer/Media3, and Firebase). Implement the following 5 core subsystems with exact accuracy, adhering to clean architecture, Material 3 aesthetics, and high performance:

### 1. NETMIRROR STREAM RESOLUTION KEYING & DEDUPLICATION ENGINE
In `NetMirrorScreen.kt`:
- **Stream Identification**: Use a strict regex `\.(m3u8|mp4|mkv|mpd)(\?|#|$)` (case-insensitive) that ignores transport segments (`.ts`, `.m4s`) and non-stream URLs (`watchbox.php`).
- **Keying & Normalization**: Compute unique stream keys using the base URL without query parameters or hash fragments (`url.substringBefore('#').substringBefore('?').lowercase()`).
- **Real Resolution Labeling**: Bridge video playback events from WebView to Kotlin (`onVideoPlayingFound(url, width, height)`). Derive standardized labels ("1080p", "720p", "480p", "360p", "240p", "144p") using the formula `if (width in 1 until height) width else maxOf(height, width * 9 / 16)` mapped to the closest standard resolution.
- **Strict Deduplication**: Aggregate captured links so that each distinct resolution label has strictly ONE link displayed.
- **Server Prioritization Scoring**: Rank links using a priority comparator:
  - Add 100,000 points if the URL domain contains `hakunayamata.com` or `hakunaymatata.com`.
  - Add 20,000 points if the URL domain contains `proxy22.shop` or `watch22.shop`.
  - Add 10,000 points for `.m3u8`, 5,000 for `.mp4`.
  - Add the integer resolution value.
- **Auto-Selection**: Automatically select the highest-scoring link upon link changes.

### 2. MINIMALIST HAZARD REACTOR BUFFERING OVERLAY
In `FuturisticEffects.kt`:
- Refactor `PlayerBufferingOverlay(isBuffering: Boolean, modifier: Modifier)`:
  - Display solely a centered, rotating nuclear radiation hazard icon with glowing neon Cyan/Green/Amber pulse effects.
  - Omit all status text ("BUFFERING REACTOR STREAM...", etc.) below the symbol for a clean, non-intrusive aesthetic.
  - Wire this overlay cleanly into both `WatchScreen.kt` and `YouTubeWatchScreen.kt`.

### 3. REFERER RULE ALIASING & MEDIA3 MIME RESOLUTION
In `AppConfig.kt` and `WatchScreen.kt`:
- Ensure both `hakunayamata.com` and `hakunaymatata.com` are mapped to `https://movieboxonline.net/` in `REFERER_RULES`.
- In `WatchScreen.kt`, explicitly check if the video URL contains `hakunayamata.com` or `hakunaymatata.com` (alongside `.m3u8` or `/hls`), and set `MediaItem.Builder().setMimeType(MimeTypes.APPLICATION_M3U8)` so ExoPlayer delegates to `HlsMediaSource`.

### 4. FIREBASE REALTIME DATABASE SUBSCRIPTION & WHITELIST TRACKER
In `ProfileScreen.kt`:
- **Data Model**: Track `/whitelist/<key>` (Boolean) and `/whitelistMeta/<key>` (`expiresAt: Long`, `grantedAt: Long`).
- **Key Resolution**: For the logged-in user, query the UID key first. If missing or false, query the sanitized email key (`user.email.replace('.', ',')`).
- **Lifecycle States**:
  1. *Never whitelisted*: If neither key exists in the database, do NOT show the subscription section at all.
  2. *Lifetime Access*: If whitelisted and `expiresAt == 0L` or missing, display: `"Subscription: Lifetime access"`.
  3. *Active Subscription*: If whitelisted and `expiresAt > System.currentTimeMillis()`, display: `"Subscription active until <Formatted Date>"` (formatted as readable date, e.g. "September 30, 2026").
  4. *Expired Subscription*: If previously whitelisted but current time > `expiresAt`, or if revoked, display: `"Subscription expired on <Formatted Date>"`.
- **Read-Only**: Only read from Firebase; do not push updates from the client app.
- **Styling**: Match existing UraniumTV theme typography (`DisplayFontFamily`, `BodyFontFamily`), dark elevation (`AbyssSurfaceElevated`), and neon accents (`CyanCore`).

### 5. MULTI-LAYERED AD BLOCKER & ANTI-ADBLOCK NEUTRALIZATION ENGINE
In `NetMirrorScreen.kt`:
- **Anti-AdBlock Defeater Script (Document Start)**:
  - Inject an inline script via `WebViewCompat.addDocumentStartJavaScript` and fallback `onPageStarted`.
  - Override `window.llvpnLoaded` with an immutable getter returning `true`:
    `Object.defineProperty(window, 'llvpnLoaded', { get: () => true, set: () => {}, configurable: true });`
  - Spoof anti-adblock traps: `window.canRunAds = true`, `window.isAdBlockActive = false`, `window.adblock = false`.
  - Override `window.open` to return a safe dummy window object to block rogue popups without breaking site scripts.
  - Inject global CSS setting `.adblock-container`, `.adblock-card`, and `div[style*="z-index: 2147483647"]` to `display: none !important;`.
- **Request Interception (`shouldInterceptRequest`)**:
  - Filter against a comprehensive streaming ad network blacklist (`llvpn.com`, `popads.net`, `popcash.net`, `propellerads.com`, `adsterra.com`, `clickadu.com`, `exoclick.com`, `juicyads.com`, `trafficjunky.com`, `doubleclick.net`, etc.).
  - When an ad script is intercepted (e.g. `tag.min.js`), return HTTP 200 OK `application/javascript` with dummy declarations (`window.llvpnLoaded = true; window.canRunAds = true;`). This triggers `<script onload>` and stops `<script onerror>`.
  - When an ad tracking image is intercepted, return a 68-byte 1x1 transparent PNG.
- **DOM & Overlay Pruner**:
  - Periodically and on `MutationObserver`, detect and remove transparent fixed overlay `div`s with `z-index > 10000` that intercept user taps.
  - Automatically remove any `.adblock-container` or `.adblock-card` elements.
- **Navigation Allowlist**:
  - Restrict navigation to valid domains (`netmirror`, Cloudflare/Turnstile verification, and player embed hosts `watch21.shop`, `watch22.shop`, `watch-download.shop`, `proxy22.shop`, `movieboxonline.net`, `mzfi.me`). Block arbitrary redirects.
```
