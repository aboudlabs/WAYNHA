package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = WaynhaEmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = WaynhaTealSoft,
    onPrimaryContainer = WaynhaEmeraldDark,
    secondary = WaynhaTurquoiseAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = WaynhaEmeraldDark,
    tertiary = LostReportAmber,
    onTertiary = Color.White,
    tertiaryContainer = LostReportAmberContainer,
    onTertiaryContainer = Color(0xFF78350F),
    background = WaynhaWarmBackground,
    onBackground = WaynhaTextPrimaryLight,
    surface = WaynhaSurfaceLight,
    onSurface = WaynhaTextPrimaryLight,
    surfaceVariant = WaynhaSurfaceVariantLight,
    onSurfaceVariant = WaynhaTextSecondaryLight,
    outline = WaynhaOutlineLight,
    error = ErrorCrimson,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = WaynhaTurquoiseAccent,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = WaynhaEmeraldDark,
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = WaynhaDarkBackground,
    onBackground = WaynhaTextPrimaryDark,
    surface = WaynhaDarkSurface,
    onSurface = WaynhaTextPrimaryDark,
    surfaceVariant = WaynhaDarkSurfaceVariant,
    onSurfaceVariant = WaynhaTextSecondaryDark,
    outline = WaynhaOutlineDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

val WaynhaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WaynhaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // فرض اتجاه الواجهة العربية RTL بالكامل في جميع الشاشات
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = WaynhaShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    WaynhaTheme(darkTheme = darkTheme, content = content)
}
