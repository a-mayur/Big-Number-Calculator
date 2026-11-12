package info.mayuragarkar.calculator.ui

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A60), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF74F8E5), onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2), onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF456179), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCE5FF), onTertiaryContainer = Color(0xFF001E31),
    background = Color(0xFFFAFDFB), onBackground = Color(0xFF191C1B),
    surface = Color(0xFFFAFDFB), onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDAE5E1), onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C6),
    surfaceDim = Color(0xFFD9DBDA), surfaceBright = Color(0xFFFAFDFB),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F7F5),
    surfaceContainer = Color(0xFFEEF1EF), surfaceContainerHigh = Color(0xFFE9ECEA),
    surfaceContainerHighest = Color(0xFFE3E6E4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF53DBC9), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048), onPrimaryContainer = Color(0xFF74F8E5),
    secondary = Color(0xFFB1CCC6), onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47), onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFADCAE6), onTertiary = Color(0xFF153349),
    tertiaryContainer = Color(0xFF2D4960), onTertiaryContainer = Color(0xFFCCE5FF),
    background = Color(0xFF111413), onBackground = Color(0xFFE0E3E1),
    surface = Color(0xFF111413), onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899390), outlineVariant = Color(0xFF3F4947),
    surfaceDim = Color(0xFF111413), surfaceBright = Color(0xFF373A39),
    surfaceContainerLowest = Color(0xFF0B0F0E), surfaceContainerLow = Color(0xFF191C1B),
    surfaceContainer = Color(0xFF1D2020), surfaceContainerHigh = Color(0xFF272B2A),
    surfaceContainerHighest = Color(0xFF323534),
)

@Composable
fun BigNumberTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
