package com.moto.files.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Explicit modes remain available even when system dynamic color is enabled. */
enum class ThemeMode { LIGHT, DARK, AMOLED, SYSTEM }

private val Light = lightColorScheme(primary = Color(0xFF3657AA), background = Color(0xFFFAF9FF), surface = Color(0xFFFAF9FF))
private val Dark = darkColorScheme(primary = Color(0xFFB4C5FF), background = Color(0xFF15161A), surface = Color(0xFF15161A))
private val Amoled = darkColorScheme(primary = Color(0xFFB4C5FF), background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF101014))

@Composable
fun MotoTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when (mode) {
        ThemeMode.AMOLED -> Amoled
        ThemeMode.LIGHT -> if (dynamicColor && Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context) else Light
        ThemeMode.DARK -> if (dynamicColor && Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else Dark
        ThemeMode.SYSTEM -> if (dark) {
            if (dynamicColor && Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else Dark
        } else {
            if (dynamicColor && Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context) else Light
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
