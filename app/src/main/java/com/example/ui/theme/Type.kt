package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- Futuristic/geometric type system ----
//
// NOTE ON FONTS: this ships using the platform sans-serif with wide tracking +
// heavy weights, which reads geometric/sci-fi without bundling anything and
// compiles correctly with zero setup. To swap in the real pairing this was
// designed for (Orbitron for display, Rajdhani for body - both free/open on
// Google Fonts), do this once in Android Studio:
//   1. Right-click res/ -> New -> Other -> "Google Fonts" font (or use the
//      Resource Manager's "+" -> Font button) and search "Orbitron", then
//      "Rajdhani". Android Studio downloads them and safely auto-generates
//      the res/values/font_certs.xml + res/font/*.xml it needs - this file
//      has a long certificate blob that must come from that generator, not
//      be hand-typed, so it's intentionally left out here.
//   2. Replace the two FontFamily vals below with
//      FontFamily(Font(R.font.orbitron)) / FontFamily(Font(R.font.rajdhani)).
// Everything else (Theme.kt, all screens) references DisplayFontFamily /
// BodyFontFamily, so that's the only edit needed.
val DisplayFontFamily = FontFamily.SansSerif
val BodyFontFamily = FontFamily.SansSerif

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.Black,
        fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = 1.5.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.Black,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = 1.5.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = 1.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.8.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.6.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.3.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
)
