package com.pyreview.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pyreview.app.data.review.Severity

private val Navy = Color(0xFF1E3A8A)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0A1F5C),
    secondary = Color(0xFF4A5779),
    secondaryContainer = Color(0xFFDCE4FF),
    onSecondaryContainer = Color(0xFF0A1F5C),
    background = Color(0xFFF7F8FB),
    onBackground = Color(0xFF181B22),
    surface = Color.White,
    onSurface = Color(0xFF181B22),
    surfaceVariant = Color(0xFFF0F2F7),
    onSurfaceVariant = Color(0xFF5B6273),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FB),
    surfaceContainer = Color(0xFFF0F2F7),
    surfaceContainerHigh = Color(0xFFE9ECF3),
    outline = Color(0xFFC9CEDA),
    outlineVariant = Color(0xFFE2E5EC),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF0A2472),
    primaryContainer = Color(0xFF26397A),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFBCC6E6),
    secondaryContainer = Color(0xFF26397A),
    onSecondaryContainer = Color(0xFFDCE4FF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E5EC),
    surface = Color(0xFF181A20),
    onSurface = Color(0xFFE3E5EC),
    surfaceVariant = Color(0xFF242833),
    onSurfaceVariant = Color(0xFFAEB4C2),
    surfaceContainerLowest = Color(0xFF0D0F13),
    surfaceContainerLow = Color(0xFF181A20),
    surfaceContainer = Color(0xFF1C1F26),
    surfaceContainerHigh = Color(0xFF262A33),
    outline = Color(0xFF4A5060),
    outlineVariant = Color(0xFF30343F),
    error = Color(0xFFF2B8B5),
)

@Immutable
data class StatusColors(
    val good: Color,
    val goodContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val error: Color,
    val errorContainer: Color,
) {
    fun accent(severity: Severity) = when (severity) {
        Severity.GOOD -> good
        Severity.WARNING -> warning
        Severity.ERROR -> error
    }

    fun container(severity: Severity) = when (severity) {
        Severity.GOOD -> goodContainer
        Severity.WARNING -> warningContainer
        Severity.ERROR -> errorContainer
    }
}

private val LightStatus = StatusColors(
    good = Color(0xFF1B7A3D), goodContainer = Color(0xFFE6F4EA),
    warning = Color(0xFFB45309), warningContainer = Color(0xFFFFF1DE),
    error = Color(0xFFC0262D), errorContainer = Color(0xFFFDE8E8),
)

private val DarkStatus = StatusColors(
    good = Color(0xFF7DD99A), goodContainer = Color(0xFF173423),
    warning = Color(0xFFF5B86A), warningContainer = Color(0xFF3B2A12),
    error = Color(0xFFFF9C9C), errorContainer = Color(0xFF431D1F),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

val CodeTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
}

@Composable
fun PyReviewTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
