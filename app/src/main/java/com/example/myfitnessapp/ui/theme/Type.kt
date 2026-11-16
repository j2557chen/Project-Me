package com.example.myfitnessapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.myfitnessapp.R

// The app's font: Outfit (SIL Open Font License; the licence is in assets/licenses). Each weight,
// from light to bold, is its own file in res/font. (A single "variable" file holding every
// weight came out in its thinnest weight on Android, so the separate files are used instead.)
val AppFont = FontFamily(
    Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold)
)

// Every digit the same width ("tabular" figures), so a ticking timer doesn't shift sideways as
// its digits change, and weights and reps line up in their columns.
const val APP_FONT_FEATURES = "tnum"

private fun TextStyle.inAppFont() = copy(fontFamily = AppFont, fontFeatureSettings = APP_FONT_FEATURES)

// Material's standard text styles, all in the app's font. Text that doesn't pick a style takes
// bodyLarge, so this reaches every screen, button, menu and dialog.
private val material = Typography()
val Typography = Typography(
    displayLarge = material.displayLarge.inAppFont(),
    displayMedium = material.displayMedium.inAppFont(),
    displaySmall = material.displaySmall.inAppFont(),
    headlineLarge = material.headlineLarge.inAppFont(),
    headlineMedium = material.headlineMedium.inAppFont(),
    headlineSmall = material.headlineSmall.inAppFont(),
    titleLarge = material.titleLarge.inAppFont(),
    titleMedium = material.titleMedium.inAppFont(),
    titleSmall = material.titleSmall.inAppFont(),
    bodyLarge = material.bodyLarge.inAppFont(),
    bodyMedium = material.bodyMedium.inAppFont(),
    bodySmall = material.bodySmall.inAppFont(),
    labelLarge = material.labelLarge.inAppFont(),
    labelMedium = material.labelMedium.inAppFont(),
    labelSmall = material.labelSmall.inAppFont()
)
