package pe.pagobox.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandGreen = Color(0xFF0B8F5A)
val BrandGreenDark = Color(0xFF3FD18F)
val StatusOk = Color(0xFF1E8E3E)
val StatusWarn = Color(0xFFB26A00)
val StatusError = Color(0xFFC5221F)
val StatusNeutral = Color(0xFF6B7280)

private val Light = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFDE),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF3D5A80),
    background = Color(0xFFF7F9F8),
    surface = Color.White,
)

private val Dark = darkColorScheme(
    primary = BrandGreenDark,
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF005233),
    onPrimaryContainer = Color(0xFFCDEFDE),
    secondary = Color(0xFFA9C7F0),
)

@Composable
fun PagoboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
