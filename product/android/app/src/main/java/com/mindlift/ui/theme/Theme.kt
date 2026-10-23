package com.mindlift.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Serious but calm palette: blue for action, green for progress, amber for recovery.
private val Blue = Color(0xFF315FE8)
private val Green = Color(0xFF197A5E)
private val Amber = Color(0xFFC78725)
private val Sand = Color(0xFFF7F8FC)
private val Ink = Color(0xFF172033)

private val Light = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Green,
    tertiary = Amber,
    background = Sand,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    outline = Color(0xFFD8E1EF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9EB6FF),
    onPrimary = Color(0xFF07153F),
    secondary = Color(0xFF77D9B3),
    tertiary = Color(0xFFE8C27A),
    background = Color(0xFF101828),
    surface = Color(0xFF182235),
    onBackground = Color(0xFFF2F4F7),
    onSurface = Color(0xFFF2F4F7),
)

private val MlTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun MindLiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = MlTypography,
        content = content,
    )
}
