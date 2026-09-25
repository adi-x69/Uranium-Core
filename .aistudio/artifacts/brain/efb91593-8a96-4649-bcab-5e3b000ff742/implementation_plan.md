# Implementation Plan: Premium Access Control for Movie/Series Browsing

## Overview
Implement access restriction for the "Search Movies and Web Series" (Browse NetMirror) feature. When a user taps the search bar:
- If the user is flagged as premium (via Firebase Realtime Database or admin whitelist by email/UID), they enter the NetMirror browser screen immediately.
- If the user is not a premium user, an ultra-aesthetic, animated **"Uranium TV Premium"** dialog appears informing them that movie/series browsing is an exclusive perk, complete with cybernetic glowing borders, plasma radiation effects, and an aesthetic Close button.

---

## Architecture & Implementation Steps

### 1. Firebase Whitelist / Premium Status Verification
- In Firebase Realtime Database, premium status can be checked at:
  - `users/{uid}/isPremium == true` OR
  - `whitelist/{emailKey} == true` / `whitelist/{uid} == true`
  - In addition, automatically grant developer/admin access to your email (`adityamaurya2020june2009@gmail.com`) as a built-in safety whitelist so you never get locked out during development and testing.
- Listen or fetch the user's status in `RoomScreen` so verification is instantaneous with zero lag.

### 2. Aesthetic Animated Premium Subscription Dialog
- Create a futuristic, sci-fi dialog modal:
  - **Outer Frame:** Deep obsidian space-glass background (`#0A0D16`) with an animated rotating laser border (`Brush.sweepGradient`) and glowing neon crimson / electric gold plasma aura.
  - **Header & Visual Icon:** Pulsing nuclear reactor diamond / crown badge with glowing radioactive particles.
  - **Typography:** Sleek futuristic headline: *"REACTOR ACCESS RESTRICTED"* and *"Premium Clearance Required"*.
  - **Feature Showcase:** Clean glowing bullet points illustrating perks (e.g., *Unlimited High-Speed Movie & Web Series Streaming*, *Direct 4K/1080p Stream Capture*, *Ad-Shielded Engine*).
  - **Interactive Button:** An aesthetic, glowing neon crimson / gold button labeled **"ACKNOWLEDGE & CLOSE"** with haptic ripple and smooth dismissal animation.

### 3. Integration in `RoomScreen.kt`
- When the user taps `Search Movies and Web Series`:
  - If `isPremium == true`, execute `onNavigateToNetMirror()`.
  - If `isPremium == false`, show the animated `UraniumPremiumDialog`.

---

## Verification Plan
1. Test with non-whitelisted account: Tap the Search Movies bar and verify the aesthetic premium dialog opens with smooth animations.
2. Test with whitelisted/premium account: Verify instant navigation into the NetMirror browser.
3. Verify compilation with `compile_applet`.
