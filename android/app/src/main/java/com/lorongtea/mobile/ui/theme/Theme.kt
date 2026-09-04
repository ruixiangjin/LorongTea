package com.lorongtea.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TeaColors = lightColorScheme(
    primary = Color(0xFF234838),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9DB),
    onPrimaryContainer = Color(0xFF16372A),
    secondary = Color(0xFF794927),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E5D5),
    onSecondaryContainer = Color(0xFF53331E),
    tertiary = Color(0xFF526539),
    background = Color(0xFFF7F4ED),
    onBackground = Color(0xFF202B23),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF202B23),
    surfaceVariant = Color(0xFFE9EDE3),
    onSurfaceVariant = Color(0xFF47554B),
    outline = Color(0xFF6E7D71),
    outlineVariant = Color(0xFFD8DBD0),
    surfaceContainer = Color(0xFFF1EEE6),
    surfaceContainerHigh = Color(0xFFEBE7DC),
    errorContainer = Color(0xFFFBE5DC),
    onErrorContainer = Color(0xFF742A20),
)

private val TeaTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 42.sp, lineHeight = 48.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 39.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun LorongTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TeaColors,
        typography = TeaTypography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
