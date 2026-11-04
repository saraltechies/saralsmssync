package com.saraltechies.saralsmssync.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Saral Techies brand palette, taken from saraltechies.com.
private val BrandBlue = Color(0xFF0D6EFD)
private val BrandBlueDark = Color(0xFF0A58CA)
private val BrandNavy = Color(0xFF0D1B4A)
private val BrandInk = Color(0xFF1A1A2E)
private val BrandSlate = Color(0xFF64748B)
private val BrandMist = Color(0xFFF0F6FF)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = BrandNavy,
    secondary = BrandNavy,
    onSecondary = Color.White,
    background = Color(0xFFF8FAFF),
    onBackground = BrandInk,
    surface = Color.White,
    onSurface = BrandInk,
    surfaceVariant = BrandMist,
    onSurfaceVariant = BrandSlate,
    outline = Color(0xFFCBD5E1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EA8FE),
    onPrimary = BrandNavy,
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF93C5FD),
    onSecondary = BrandNavy,
    background = Color(0xFF0B1230),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F1838),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1A2550),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
)

@Composable
fun SaralTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
