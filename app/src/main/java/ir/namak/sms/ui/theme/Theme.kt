package ir.namak.sms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

enum class AppThemeMode(val titleFa: String) {
    SYSTEM("خودکار (سیستم)"),
    LIGHT("روشن"),
    DARK("تیره")
}

enum class AppFontChoice(val titleFa: String, val family: FontFamily) {
    DEFAULT("پیش‌فرض سیستم (خوانا)", FontFamily.SansSerif),
    SERIF("کلاسیک / ادبی", FontFamily.Serif),
    MONOSPACE("تک‌عرض (کدها)", FontFamily.Monospace)
}

@Composable
fun NamakTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    seedColor: Color = Color(0xFF0D5C63),
    fontChoice: AppFontChoice = AppFontChoice.DEFAULT,
    textScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = seedColor.copy(alpha = 0.9f),
            onPrimary = Color.White,
            primaryContainer = seedColor.copy(alpha = 0.28f),
            onPrimaryContainer = Color(0xFFD5F3F6),
            secondary = Color(0xFFD99B26),
            background = Color(0xFF0B1418),
            surface = Color(0xFF121E24),
            surfaceVariant = Color(0xFF1B2B33),
            onSurface = Color(0xFFEDF4F5),
            onSurfaceVariant = Color(0xFFAAC0C4)
        )
    } else {
        lightColorScheme(
            primary = seedColor,
            onPrimary = Color.White,
            primaryContainer = seedColor.copy(alpha = 0.14f),
            onPrimaryContainer = seedColor,
            secondary = Color(0xFFC48617),
            background = Color(0xFFF2F6F7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE5EFF1),
            onSurface = Color(0xFF102024),
            onSurfaceVariant = Color(0xFF4A6268)
        )
    }

    val scaledTypography = Typography(
        titleLarge = TextStyle(
            fontFamily = fontChoice.family,
            fontWeight = FontWeight.Bold,
            fontSize = (20 * textScale).sp,
            lineHeight = (28 * textScale).sp
        ),
        titleMedium = TextStyle(
            fontFamily = fontChoice.family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (16 * textScale).sp,
            lineHeight = (24 * textScale).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = fontChoice.family,
            fontWeight = FontWeight.Normal,
            fontSize = (15 * textScale).sp,
            lineHeight = (23 * textScale).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = fontChoice.family,
            fontWeight = FontWeight.Normal,
            fontSize = (13.5 * textScale).sp,
            lineHeight = (20 * textScale).sp
        ),
        labelMedium = TextStyle(
            fontFamily = fontChoice.family,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * textScale).sp
        )
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            content = content
        )
    }
}
