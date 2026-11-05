package com.moto.files.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.moto.files.model.ThemeMode

/** One expressive spring reused by every transition in the app. */
val MotoSpring = SpringSpec(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color.White,
    background = Color(0xFFF8FAFF),
    surface = Color.White,
    surfaceVariant = Color(0xFFE1E7F5)
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    background = Color(0xFF121417),
    surface = Color(0xFF1B1D21),
    surfaceVariant = Color(0xFF2A2D33)
)
private val AmoledScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color.Black,
    background = Color.Black,
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF16181C)
)

private val ExpressiveType = Typography(
    displayLarge = Typography().displayLarge.copy(
        fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = (-1.5).sp),
    displayMedium = Typography().displayMedium.copy(
        fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-1).sp),
    headlineMedium = Typography().headlineMedium.copy(
        fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.5).sp),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    labelLarge = Typography().labelLarge.copy(
        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.4.sp)
)

@Composable
fun MotoFilesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val scheme = when {
        !dark -> LightScheme
        themeMode == ThemeMode.AMOLED -> AmoledScheme
        else -> DarkScheme
    }
    MaterialTheme(colorScheme = scheme, typography = ExpressiveType, content = content)
}
