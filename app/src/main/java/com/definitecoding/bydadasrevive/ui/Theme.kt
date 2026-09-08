package com.definitecoding.bydadasrevive.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ok = Color(0xFF3DDC97)
val Warn = Color(0xFFFFC857)
// Material's dark-theme error tone. The old #FF6B6B sat at about 4.3:1 on
// surfaceVariant, under the 4.5:1 floor at the sizes it is used.
val Bad = Color(0xFFFFB4AB)

private val ReviveColors = darkColorScheme(
    primary = Color(0xFF4CC2FF),
    onPrimary = Color(0xFF00121F),
    secondary = Color(0xFF8A93A6),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6E9EF),
    surface = Color(0xFF171B22),
    onSurface = Color(0xFFE6E9EF),
    surfaceVariant = Color(0xFF212733),
    onSurfaceVariant = Color(0xFFB6BECC),
    error = Bad,
)

/**
 * Material's defaults are sized for a phone held at 30cm. This is a dashboard read at
 * arm's length, so every role steps up: body copy to 18sp and the smallest text in the
 * app to 14sp, which is where Material's body starts.
 */
private val CarTypography = Typography().let { base ->
    base.copy(
        bodySmall = base.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        labelMedium = base.labelMedium.copy(fontSize = 14.sp),
        labelLarge = base.labelLarge.copy(fontSize = 16.sp),
        titleMedium = base.titleMedium.copy(fontSize = 20.sp),
        titleLarge = base.titleLarge.copy(fontSize = 24.sp),
        headlineSmall = base.headlineSmall.copy(fontSize = 28.sp),
    )
}

/** Automotive guidance puts the floor near 76dp; this is the compromise that fits. */
val TAP_TARGET_HEIGHT = 56.dp

/** Long lines are hard to track on a wide screen, so step copy is capped. */
val CONTENT_MAX_WIDTH = 900.dp

@Composable
fun ReviveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ReviveColors, typography = CarTypography, content = content)
}
