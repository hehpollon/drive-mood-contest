package io.github.hehpollon.drivemood.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
internal data class MoodColors(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val text: Color,
    val secondaryText: Color,
    val rhythm: Color,
    val warm: Color,
    val primary: Color,
    val onPrimary: Color,
    val hero: Color,
    val navigation: Color,
    val selectedNavigation: Color,
    val onSelectedNavigation: Color,
    val calm: Color,
    val suggestion: Color,
    val outline: Color,
    val breathing: Color,
    val sky: Color,
    val distantHill: Color,
    val nearHill: Color,
    val meadow: Color,
    val sun: Color,
    val cloud: Color,
    val road: Color,
)

internal val DayMoodColors = MoodColors(
    background = Color(0xFFF6F3EB), surface = Color(0xFFFFFDF7), raised = Color(0xFFEDF1E9),
    text = Color(0xFF2E3F3C), secondaryText = Color(0xFF58665E),
    rhythm = Color(0xFF315F83), warm = Color(0xFF98502F),
    primary = Color(0xFF32645E), onPrimary = Color.White,
    hero = Color(0xFFE1EDF4), navigation = Color(0xFFEEF1E9),
    selectedNavigation = Color(0xFFD2E2D8), onSelectedNavigation = Color(0xFF294D47),
    calm = Color(0xFFEAF0E1), suggestion = Color(0xFFF6E5D9),
    outline = Color(0xFF7E8C75), breathing = Color(0xFFDCE7CE),
    sky = Color(0xFFE0EDF3), distantHill = Color(0xFFCADAC0),
    nearHill = Color(0xFF9CBAA9), meadow = Color(0xFF6C9586),
    sun = Color(0xFFEDB984), cloud = Color(0xFFF8FAF5), road = Color(0xFFFFF9E9),
)

internal val NightMoodColors = MoodColors(
    background = Color(0xFF25322D), surface = Color(0xFF2E3C35), raised = Color(0xFF3B4B42),
    text = Color(0xFFF0F0E3), secondaryText = Color(0xFFB7C8B6),
    rhythm = Color(0xFFA9CBE2), warm = Color(0xFFF1BC91),
    primary = Color(0xFFB6D8CA), onPrimary = Color(0xFF213B32),
    hero = Color(0xFF31433E), navigation = Color(0xFF29392F),
    selectedNavigation = Color(0xFF3A5B51), onSelectedNavigation = Color(0xFFF0F0E3),
    calm = Color(0xFF34483A), suggestion = Color(0xFF513C30),
    outline = Color(0xFF758B75), breathing = Color(0xFF3D5748),
    sky = Color(0xFF304641), distantHill = Color(0xFF496351),
    nearHill = Color(0xFF385C4C), meadow = Color(0xFF284C40),
    sun = Color(0xFFD6C69F), cloud = Color(0xFF70877D), road = Color(0xFF96B4A2),
)

internal val LocalMoodColors = staticCompositionLocalOf { DayMoodColors }

@Composable
internal fun DriveMoodTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) NightMoodColors else DayMoodColors
    val scheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalMoodColors provides colors) {
        MaterialTheme(colorScheme = scheme.copy(
            primary = colors.primary, onPrimary = colors.onPrimary,
            primaryContainer = colors.calm, onPrimaryContainer = colors.text,
            secondary = colors.rhythm, onSecondary = colors.surface,
            secondaryContainer = colors.hero, onSecondaryContainer = colors.text,
            tertiary = colors.warm, tertiaryContainer = colors.suggestion, onTertiaryContainer = colors.text,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.raised, onSurfaceVariant = colors.secondaryText,
            surfaceContainerHigh = colors.surface, surfaceTint = Color.Transparent,
            outline = colors.outline,
        ), content = content)
    }
}

internal val CanvasColor: Color @Composable get() = LocalMoodColors.current.background
internal val SurfaceColor: Color @Composable get() = LocalMoodColors.current.surface
internal val RaisedColor: Color @Composable get() = LocalMoodColors.current.raised
internal val TextColor: Color @Composable get() = LocalMoodColors.current.text
internal val SecondaryTextColor: Color @Composable get() = LocalMoodColors.current.secondaryText
internal val RhythmBlue: Color @Composable get() = LocalMoodColors.current.rhythm
internal val WarmOrange: Color @Composable get() = LocalMoodColors.current.warm
