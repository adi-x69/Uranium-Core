# Direct Link & YouTube Watch Separation, Rotating Nuclear Buffering, and Player Optimization

A comprehensive architectural blueprint and implementation plan for completely decoupling the Direct Link video player from the YouTube watch experience in Uranium TV, adding custom rotating nuclear radiation buffering animations, enhancing gesture controls, and optimizing both streaming pipelines.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following choices have been confirmed based on your requirements and preferences:
> - **Complete Player Separation**: `WatchScreen.kt` will be strictly dedicated to ExoPlayer direct links (`.m3u8`, `.mp4`, `.mkv`, `.mpd`), completely removing embedded YouTube code, webviews, and title overlays at the top. `YouTubeWatchScreen.kt` will be strictly dedicated to YouTube with full web iframe quality selector menus, video title display, and synchronized playback.
> - **Rotating Nuclear Radiation Buffering**: A stylized rotating tri-foil nuclear radiation symbol animation in neon uranium green/cyan, displayed both inside the video viewport whenever buffering occurs and across the app whenever network latency blocks user taps.
> - **Precision Gesture Controls**: Added to both players:
>   1. Double-tap on left/right screen halves to seek -10s / +10s with visual neon wave ripple indicator.
>   2. Vertical swipe on the left side to adjust screen brightness.
>   3. Vertical swipe on the right side to adjust device media volume.
> - **Direct Link Header Simplification**: The top title bar in `WatchScreen.kt` will no longer display the video name, showing only room information, participants, and playback status controls.

---

## 1. Overview & Core Concept

### What It Does
Uranium TV provides shared synchronized watch parties for both online direct streams (HLS/m3u8/mp4) and YouTube videos. Previously, `WatchScreen.kt` contained mixed logic for both ExoPlayer and YouTubePlayerView, causing overhead, UI clutter, and duplicate controls. This update:
1. **Splits the Architecture Cleanly**: Routes YouTube links exclusively to `YouTubeWatchScreen.kt` and direct video links exclusively to `WatchScreen.kt`.
2. **Eliminates Unnecessary Title in Direct Watch**: Direct link streams often have obscure file names or no metadata; removing the top title header cleans up the viewport and avoids cluttered URLs or placeholder titles.
3. **Equips YouTube with Quality Control**: Features an accessible resolution/quality picker (Auto, 1080p, 720p, 480p, 360p, 240p) communicating with the YouTube iframe API.
4. **Delivers High-Tech Nuclear Buffering Indicator**: Replaces standard circular spinners with a radioactive hazard tri-foil indicator that rotates smoothly on a glowing canvas.
5. **Precision Touch Gestures**: Brings modern streaming app ergonomics (double-tap seek with ripples, swipe volume and brightness overlays) to both player experiences.

### Key Value
- **Zero Glitch Stream Decoupling**: Direct links no longer initialize unused YouTube webview listeners; YouTube no longer allocates unused ExoPlayer audio/codec sessions.
- **Immersion**: Clean, unobtrusive UI on direct links with zero text clutter.
- **Network Resilience**: Slow connections trigger immediate, themed nuclear buffering feedback rather than silent freeze states.

---

## 2. User Experience & Visual Design

### Key User Flows

```
┌────────────────────────────────────────────────────────────────────────┐
│                              ROOM SCREEN                               │
│                                                                        │
│   Input Stream URL:                                                    │
│   ├─ [ YouTube Link ]  ──►  Auto-detects YouTube  ──►  YouTubeWatchScreen  │
│   └─ [ Direct Video ]  ──►  Auto-detects Video    ──►  WatchScreen (Exo)  │
└────────────────────────────────────────────────────────────────────────┘
```

#### Flow A: Direct Video Link Watch Party (`WatchScreen.kt`)
1. User enters room with an m3u8 or MP4 direct link.
2. The player launches in immersive full-screen:
   - **Top Bar**: Minimalist — Back button, Room Code pill, Live Participant avatars, Chat trigger. **No video title text**.
   - **Gestures**:
     - Double-tap left half: Neon green "-10s" ripple wave.
     - Double-tap right half: Neon green "+10s" ripple wave.
     - Swipe left half vertically: Brightness slider overlay (0% to 100% with sun icon).
     - Swipe right half vertically: Volume slider overlay (0% to 100% with speaker icon).
   - **Buffering State**: The player darkens with a glowing, rotating tri-foil nuclear radiation icon in the center.

#### Flow B: YouTube Watch Party (`YouTubeWatchScreen.kt`)
1. User enters room with a YouTube URL or video ID.
2. The player launches with full YouTube synchronization:
   - **Top Bar**: Back button, **Video Title** (cleanly fetched from YouTube oEmbed/title data), Room Code pill, Quality Badge (e.g., "1080p", "720p", "Auto"), Settings gear.
   - **Quality Selector Dialog**: Tapping the quality badge opens a dialog allowing users to pick from Auto, 1080p, 720p, 480p, 360p, and 240p.
   - **Gestures**: Left/right swipe for brightness/volume, double-tap to seek 10s.
   - **Buffering State**: Rotating nuclear radiation icon with room synchronization status.

#### Flow C: App-Wide Slow Internet / Loading State
1. If any screen in Uranium TV is performing a network operation (joining room, verifying user, fetching NetMirror details) and user taps during network latency:
2. The app displays the `NuclearRadiationBufferingIndicator` centered in a semi-transparent HUD backdrop to provide immediate visual feedback.

### Visual Styling Tokens
- **Primary Radiation Glow**: `#00FF66` (Neon Uranium Green) and `#00E5FF` (Electric Cyan).
- **Hazard Core**: `#101820` (Dark Reactor Hull) with 3-blade tri-foil arcs separated by 60° gaps.
- **Rotation Dynamics**: Continuous smooth rotation (1400ms per full 360° turn via `rememberInfiniteTransition`).
- **Gesture HUDs**: Frosted glass rounded pill overlays (`Color(0xCC101820)`) with animated volume/brightness levels and haptic response.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Complete Removal of YouTube Code from `WatchScreen.kt`**
  - *Chosen Approach*: Strip out `YouTubePlayerView`, iframe JS injection, and YouTube player states from `WatchScreen.kt`. Keep `WatchScreen.kt` 100% dedicated to ExoPlayer.
  - *Why*: Eliminates memory leaks, removes conflicting lifecycle events, simplifies error handling, and prevents video player freezes when switching rooms.
- **Decision 2: Remove Video Title from Direct Watch Player Only**
  - *Chosen Approach*: Direct links have no top title text. YouTube player retains the title fetched via YouTube oEmbed/metadata.
  - *Why*: Direct links (m3u8, mp4) usually have ugly technical filenames (e.g. `stream_master_720.m3u8?token=xyz`) that look unpolished when displayed. YouTube videos have well-defined creative titles that users expect to see.
- **Decision 3: Standalone Reusable `NuclearRadiationBufferingIndicator` Component**
  - *Chosen Approach*: Build a standalone Jetpack Compose Canvas component in `FuturisticEffects.kt` that draws true geometric radiation blades (central hub, three 60-degree blade arcs, inner ring) and rotates smoothly.
  - *Why*: Lightweight, 60fps hardware-accelerated Canvas rendering with zero external bitmap dependencies; can be embedded at any size (16dp inside buttons to 72dp for full-screen loading).

---

## 4. Technical Architecture & Component Mapping

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MAIN NAVIGATION ROUTER                          │
│                           (MainActivity.kt)                            │
└───────────────┬────────────────────────────────────────┬───────────────┘
                │ isYouTube == false                     │ isYouTube == true
                ▼                                        ▼
┌─────────────────────────────────┐    ┌─────────────────────────────────┐
│          WatchScreen            │    │       YouTubeWatchScreen        │
│    (Dedicated ExoPlayer)        │    │    (Dedicated YouTube Player)   │
├─────────────────────────────────┤    ├─────────────────────────────────┤
│ • Pure ExoPlayer engine         │    │ • YouTubePlayerView engine      │
│ • No Video Title in Header      │    │ • Video Title prominently shown │
│ • Dual-tap 10s seek + ripples   │    │ • Dual-tap 10s seek + ripples   │
│ • Swipe volume & brightness     │    │ • Swipe volume & brightness     │
│ • Rotating Nuclear Buffering    │    │ • Rotating Nuclear Buffering    │
│ • Realtime Firebase Room Sync   │    │ • Resolution & Quality Menu     │
│ • Realtime Chat & Participants  │    │ • Realtime Firebase Room Sync   │
└─────────────────────────────────┘    └─────────────────────────────────┘
                │                                        │
                └───────────────────┬────────────────────┘
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   SHARED FUTURISTIC EFFECTS & HUD                      │
│                        (FuturisticEffects.kt)                          │
├────────────────────────────────────────────────────────────────────────┤
│ • NuclearRadiationBufferingIndicator (Canvas tri-foil 360° spinner)    │
│ • PlayerGestureOverlay (Double-tap ripple waves, Volume & Brightness)  │
│ • GlobalNetworkBufferingOverlay (Slow network loading blocker)         │
└────────────────────────────────────────────────────────────────────────┘
```

### Component & State Plan

1. **`FuturisticEffects.kt`**:
   - `NuclearRadiationBufferingIndicator(modifier, size, color, strokeWidth)`:
     - Uses `rememberInfiniteTransition` with continuous rotation `0f..360f`.
     - Draws outer circle or pulse, three 60° blade arcs at angles `0°, 120°, 240°`, inner circular gap, and central dot.
   - `PlayerGestureOverlay(onSeekRelative, onAdjustBrightness, onAdjustVolume, content)`:
     - Detects horizontal/vertical drags and double-tap events on left/right partitions.
     - Renders visual feedback (volume pill, brightness pill, "+10s" / "-10s" ripple flash).
   - `GlobalLoadingIndicator`:
     - Full-screen or box modal featuring the nuclear spinner and "CONNECTING TO REACTOR..." text.

2. **`WatchScreen.kt` (Direct Link Watch)**:
   - Remove `videoTitle` state and UI display from `TopBar`.
   - Remove all residual `YouTubePlayerView`, `isYouTubeMode`, and YouTube iframe script logic.
   - Attach `PlayerGestureOverlay` to `PlayerView`.
   - Replace any default progress spinners with `NuclearRadiationBufferingIndicator`.
   - Optimize ExoPlayer buffer parameters (`DefaultLoadControl` tuned for fast startup and low buffering on mobile data).

3. **`YouTubeWatchScreen.kt` (YouTube Watch)**:
   - Ensure clean `videoTitle` resolution from room snapshot and YouTube API.
   - Implement full quality selector menu (`Auto`, `1080p`, `720p`, `480p`, `360p`, `240p`) wired to YouTube iframe `setPlaybackQuality` / `setPlaybackQualityRange`.
   - Integrate `PlayerGestureOverlay` for double-tap seek and swipe gestures.
   - Replace standard spinners with `NuclearRadiationBufferingIndicator`.

4. **`RoomScreen.kt` & `MainActivity.kt`**:
   - Ensure seamless routing: URLs with YouTube IDs go to `YouTubeWatchScreen`, direct streams go to `WatchScreen`.

---

## 5. Next Feature Recommendations for Future Iterations

1. **Picture-in-Picture (PiP) Mode**: Enable Android native PiP so users can watch their room sync while chatting or multitasking.
2. **Audio-Only Mode**: Save 90% mobile bandwidth on direct links when listening to podcasts or concerts in rooms.
3. **Voice Chat in Rooms**: Low-latency WebRTC push-to-talk channel so friends can talk while watching together.
4. **Playback Speed Control (0.5x, 1x, 1.25x, 1.5x, 2x)**: Synced across the room for anime and lectures.
