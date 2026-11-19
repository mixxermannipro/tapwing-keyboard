package com.tapwing.keyboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tapwing.keyboard.data.DarkModePreference
import com.tapwing.keyboard.data.ThemeMode

@Immutable
data class TapwingKeyboardColors(
    val boardBackground: Color,
    val keySurface: Color,
    val keyPressed: Color,
    val keyText: Color,
    val keyHintText: Color,
    val keyBorder: Color,
    val modifierSurface: Color,
    val modifierText: Color,
    val actionSurface: Color,
    val actionText: Color,
    val stripChipSurface: Color,
    val stripChipText: Color,
    val cardSurface: Color,
    val cardAltSurface: Color
)

val LocalTapwingKeyboardColors = staticCompositionLocalOf {
    TapwingKeyboardColors(
        boardBackground = Color(0xFFF3EDF9),
        keySurface = Color(0xFFFFFFFF),
        keyPressed = Color(0xFFE5DCF4),
        keyText = Color(0xFF1D1B20),
        keyHintText = Color(0xFF6B6575),
        keyBorder = Color(0x141D1B20),
        modifierSurface = Color(0xFFE4DBF1),
        modifierText = Color(0xFF282038),
        actionSurface = Color(0xFF6750A4),
        actionText = Color(0xFFFFFFFF),
        stripChipSurface = Color(0xFFE9E0F7),
        stripChipText = Color(0xFF231937),
        cardSurface = Color(0xFFFFFFFF),
        cardAltSurface = Color(0xFFECE4F6)
    )
}

private val LavenderLightScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFF5EFFB),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFEDE6F6),
    onSurfaceVariant = Color(0xFF49454F)
)

private val LavenderDarkScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF1D1B20),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF2B2633),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

private val PixelBlueLightScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF535F70),
    secondaryContainer = Color(0xFFE3EBF6),
    background = Color(0xFFF3F6FC),
    onBackground = Color(0xFF101828),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFE8EEF8),
    onSurfaceVariant = Color(0xFF444F5E)
)

private val PixelBlueDarkScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF002D6B),
    primaryContainer = Color(0xFF174EA6),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBDC7D8),
    secondaryContainer = Color(0xFF243042),
    background = Color(0xFF0F1217),
    onBackground = Color(0xFFF3F5F8),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFF3F5F8),
    surfaceVariant = Color(0xFF202834),
    onSurfaceVariant = Color(0xFFAAB3C0)
)

private val AmoledDarkScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF26125A),
    primaryContainer = Color(0xFF4A3485),
    onPrimaryContainer = Color(0xFFEADDFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF0D0D10),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF191920),
    onSurfaceVariant = Color(0xFFB4B4C2)
)

@Composable
fun TapwingTheme(
    themeMode: ThemeMode = ThemeMode.EXPRESSIVE_LAVENDER,
    darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (darkModePreference) {
        DarkModePreference.SYSTEM -> systemDark || themeMode == ThemeMode.AMOLED_DARK
        DarkModePreference.LIGHT -> false
        DarkModePreference.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme: ColorScheme = when (themeMode) {
        ThemeMode.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (useDark) LavenderDarkScheme else LavenderLightScheme
            }
        }
        ThemeMode.EXPRESSIVE_LAVENDER -> if (useDark) LavenderDarkScheme else LavenderLightScheme
        ThemeMode.PIXEL_BLUE -> if (useDark) PixelBlueDarkScheme else PixelBlueLightScheme
        ThemeMode.AMOLED_DARK -> AmoledDarkScheme
    }

    val keyboardColors = buildKeyboardColors(themeMode, useDark, colorScheme)

    CompositionLocalProvider(LocalTapwingKeyboardColors provides keyboardColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

private fun buildKeyboardColors(
    themeMode: ThemeMode,
    useDark: Boolean,
    scheme: ColorScheme
): TapwingKeyboardColors {
    return when {
        themeMode == ThemeMode.AMOLED_DARK -> TapwingKeyboardColors(
            boardBackground = Color(0xFF000000),
            keySurface = Color(0xFF18181D),
            keyPressed = Color(0xFF2D283A),
            keyText = Color(0xFFF3F3F7),
            keyHintText = Color(0xFF9E9CB0),
            keyBorder = Color(0x1FFFFFFF),
            modifierSurface = Color(0xFF24222D),
            modifierText = Color(0xFFE7E0F4),
            actionSurface = Color(0xFF6750A4),
            actionText = Color(0xFFFFFFFF),
            stripChipSurface = Color(0xFF211F29),
            stripChipText = Color(0xFFEDE7F8),
            cardSurface = Color(0xFF111116),
            cardAltSurface = Color(0xFF1B1923)
        )
        themeMode == ThemeMode.PIXEL_BLUE && useDark -> TapwingKeyboardColors(
            boardBackground = Color(0xFF11151C),
            keySurface = Color(0xFF232934),
            keyPressed = Color(0xFF334157),
            keyText = Color(0xFFF3F5F8),
            keyHintText = Color(0xFF9AA4B4),
            keyBorder = Color(0x1AFFFFFF),
            modifierSurface = Color(0xFF1B222D),
            modifierText = Color(0xFFD6E2F5),
            actionSurface = Color(0xFF8AB4F8),
            actionText = Color(0xFF062E6F),
            stripChipSurface = Color(0xFF1D2634),
            stripChipText = Color(0xFFE8F0FE),
            cardSurface = Color(0xFF171D26),
            cardAltSurface = Color(0xFF202A38)
        )
        themeMode == ThemeMode.PIXEL_BLUE -> TapwingKeyboardColors(
            boardBackground = Color(0xFFEDF2FA),
            keySurface = Color(0xFFFFFFFF),
            keyPressed = Color(0xFFDDE7F9),
            keyText = Color(0xFF101828),
            keyHintText = Color(0xFF637082),
            keyBorder = Color(0x140F172A),
            modifierSurface = Color(0xFFD8E4F7),
            modifierText = Color(0xFF172B4D),
            actionSurface = Color(0xFF1A73E8),
            actionText = Color(0xFFFFFFFF),
            stripChipSurface = Color(0xFFE1EBFC),
            stripChipText = Color(0xFF0F2C59),
            cardSurface = Color(0xFFFFFFFF),
            cardAltSurface = Color(0xFFE6EFFB)
        )
        themeMode == ThemeMode.EXPRESSIVE_LAVENDER && !useDark -> TapwingKeyboardColors(
            boardBackground = Color(0xFFF3EDF9),
            keySurface = Color(0xFFFFFFFF),
            keyPressed = Color(0xFFE4DAF3),
            keyText = Color(0xFF1D1B20),
            keyHintText = Color(0xFF6B6575),
            keyBorder = Color(0x121D1B20),
            modifierSurface = Color(0xFFE3DAF0),
            modifierText = Color(0xFF261C38),
            actionSurface = Color(0xFF5E41A8),
            actionText = Color(0xFFFFFFFF),
            stripChipSurface = Color(0xFFE8DFF6),
            stripChipText = Color(0xFF231937),
            cardSurface = Color(0xFFFFFFFF),
            cardAltSurface = Color(0xFFECE4F7)
        )
        themeMode == ThemeMode.EXPRESSIVE_LAVENDER && useDark -> TapwingKeyboardColors(
            boardBackground = Color(0xFF17141D),
            keySurface = Color(0xFF292433),
            keyPressed = Color(0xFF3E3550),
            keyText = Color(0xFFF2ECF8),
            keyHintText = Color(0xFFABA3B8),
            keyBorder = Color(0x18FFFFFF),
            modifierSurface = Color(0xFF211C2A),
            modifierText = Color(0xFFE5DCF4),
            actionSurface = Color(0xFFD0BCFF),
            actionText = Color(0xFF381E72),
            stripChipSurface = Color(0xFF272133),
            stripChipText = Color(0xFFEDE5FA),
            cardSurface = Color(0xFF1F1B26),
            cardAltSurface = Color(0xFF2A2336)
        )
        useDark -> TapwingKeyboardColors(
            boardBackground = scheme.surfaceVariant,
            keySurface = scheme.surface,
            keyPressed = scheme.primaryContainer,
            keyText = scheme.onSurface,
            keyHintText = scheme.onSurfaceVariant,
            keyBorder = Color(0x18FFFFFF),
            modifierSurface = scheme.secondaryContainer,
            modifierText = scheme.onSecondaryContainer,
            actionSurface = scheme.primary,
            actionText = scheme.onPrimary,
            stripChipSurface = scheme.secondaryContainer,
            stripChipText = scheme.onSecondaryContainer,
            cardSurface = scheme.surface,
            cardAltSurface = scheme.surfaceVariant
        )
        else -> TapwingKeyboardColors(
            boardBackground = scheme.surfaceVariant,
            keySurface = scheme.surface,
            keyPressed = scheme.primaryContainer,
            keyText = scheme.onSurface,
            keyHintText = scheme.onSurfaceVariant,
            keyBorder = Color(0x14000000),
            modifierSurface = scheme.secondaryContainer,
            modifierText = scheme.onSecondaryContainer,
            actionSurface = scheme.primary,
            actionText = scheme.onPrimary,
            stripChipSurface = scheme.secondaryContainer,
            stripChipText = scheme.onSecondaryContainer,
            cardSurface = scheme.surface,
            cardAltSurface = scheme.surfaceVariant
        )
    }
}
