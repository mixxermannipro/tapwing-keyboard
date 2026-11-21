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
    val isDark: Boolean,
    val appleLiquidGlass: Boolean,
    val boardBackground: Color,
    val boardGradientBottom: Color,
    val keySurface: Color,
    val keyPressed: Color,
    val keyText: Color,
    val keyHintText: Color,
    val keyBorder: Color,
    val keyGlassHighlight: Color,
    val modifierSurface: Color,
    val modifierText: Color,
    val spacebarSurface: Color,
    val actionSurface: Color,
    val actionText: Color,
    val stripChipSurface: Color,
    val stripChipText: Color,
    val cardSurface: Color,
    val cardAltSurface: Color
)

val LocalTapwingKeyboardColors = staticCompositionLocalOf {
    TapwingKeyboardColors(
        isDark = true,
        appleLiquidGlass = false,
        boardBackground = Color(0xFF1B1C1E),
        boardGradientBottom = Color(0xFF1B1C1E),
        keySurface = Color(0xFF303134),
        keyPressed = Color(0xFF45474D),
        keyText = Color(0xFFE8EAED),
        keyHintText = Color(0xFF9AA0A6),
        keyBorder = Color(0x14FFFFFF),
        keyGlassHighlight = Color(0x28FFFFFF),
        modifierSurface = Color(0xFF26282C),
        modifierText = Color(0xFFE8EAED),
        spacebarSurface = Color(0xFF303134),
        actionSurface = Color(0xFFA8C7FA),
        actionText = Color(0xFF062E6F),
        stripChipSurface = Color(0xFF2A2C30),
        stripChipText = Color(0xFFE8EAED),
        cardSurface = Color(0xFF242629),
        cardAltSurface = Color(0xFF2F3237)
    )
}

private val PixelSlateLightScheme = lightColorScheme(
    primary = Color(0xFF3B5998),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E3F8),
    onPrimaryContainer = Color(0xFF112244),
    secondary = Color(0xFF566074),
    secondaryContainer = Color(0xFFE3E8F2),
    background = Color(0xFFECEFF4),
    onBackground = Color(0xFF16181D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFE1E6EF),
    onSurfaceVariant = Color(0xFF555E6F)
)

private val PixelSlateDarkScheme = darkColorScheme(
    primary = Color(0xFFA9C0EE),
    onPrimary = Color(0xFF162747),
    primaryContainer = Color(0xFF38445C),
    onPrimaryContainer = Color(0xFFE5ECFA),
    secondary = Color(0xFFBEC6D8),
    secondaryContainer = Color(0xFF2B303C),
    background = Color(0xFF121316),
    onBackground = Color(0xFFF0F2F6),
    surface = Color(0xFF1C1E24),
    onSurface = Color(0xFFF0F2F6),
    surfaceVariant = Color(0xFF272B34),
    onSurfaceVariant = Color(0xFFA6AEBD)
)

private val AppleLightScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6EAFF),
    onPrimaryContainer = Color(0xFF002B5C),
    secondary = Color(0xFF34C759),
    secondaryContainer = Color(0xFFE5E5EA),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE9E9EF),
    onSurfaceVariant = Color(0xFF6C6C70)
)

private val AppleDarkScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1C3A5E),
    onPrimaryContainer = Color(0xFFD6EAFF),
    secondary = Color(0xFF30D158),
    secondaryContainer = Color(0xFF2C2C2E),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989F)
)

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
    themeMode: ThemeMode = ThemeMode.PIXEL_SLATE,
    darkModePreference: DarkModePreference = DarkModePreference.SYSTEM,
    appleModeEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (darkModePreference) {
        DarkModePreference.SYSTEM -> systemDark || themeMode == ThemeMode.AMOLED_DARK
        DarkModePreference.LIGHT -> false
        DarkModePreference.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme: ColorScheme = if (appleModeEnabled) {
        if (useDark) AppleDarkScheme else AppleLightScheme
    } else {
        when (themeMode) {
            ThemeMode.DYNAMIC -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    if (useDark) PixelSlateDarkScheme else PixelSlateLightScheme
                }
            }
            ThemeMode.PIXEL_SLATE -> if (useDark) PixelSlateDarkScheme else PixelSlateLightScheme
            ThemeMode.EXPRESSIVE_LAVENDER -> if (useDark) LavenderDarkScheme else LavenderLightScheme
            ThemeMode.PIXEL_BLUE -> if (useDark) PixelBlueDarkScheme else PixelBlueLightScheme
            ThemeMode.AMOLED_DARK -> AmoledDarkScheme
        }
    }

    val keyboardColors = buildKeyboardColors(themeMode, useDark, appleModeEnabled, colorScheme)

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
    appleModeEnabled: Boolean,
    scheme: ColorScheme
): TapwingKeyboardColors {
    if (appleModeEnabled) {
        return if (useDark) {
            TapwingKeyboardColors(
                isDark = true,
                appleLiquidGlass = true,
                boardBackground = Color(0xEB151822),
                boardGradientBottom = Color(0xF50B0D14),
                keySurface = Color(0xB8323644),
                keyPressed = Color(0xE04A5166),
                keyText = Color(0xFFF7F9FC),
                keyHintText = Color(0xFFAAB2C5),
                keyBorder = Color(0x28FFFFFF),
                keyGlassHighlight = Color(0x45FFFFFF),
                modifierSurface = Color(0x9E262B38),
                modifierText = Color(0xFFE7ECF8),
                spacebarSurface = Color(0xB8343948),
                actionSurface = Color(0xCC0A84FF),
                actionText = Color(0xFFFFFFFF),
                stripChipSurface = Color(0xA6292E3C),
                stripChipText = Color(0xFFF0F4FC),
                cardSurface = Color(0xD61F232E),
                cardAltSurface = Color(0xBA2A3040)
            )
        } else {
            TapwingKeyboardColors(
                isDark = false,
                appleLiquidGlass = true,
                boardBackground = Color(0xE8E5EAF3),
                boardGradientBottom = Color(0xF5D7DFEC),
                keySurface = Color(0xD9FFFFFF),
                keyPressed = Color(0xFFF2F6FC),
                keyText = Color(0xFF111318),
                keyHintText = Color(0xFF687082),
                keyBorder = Color(0x66FFFFFF),
                keyGlassHighlight = Color(0xD9FFFFFF),
                modifierSurface = Color(0xB8CDD6E6),
                modifierText = Color(0xFF1D2230),
                spacebarSurface = Color(0xDEFFFFFF),
                actionSurface = Color(0xE6007AFF),
                actionText = Color(0xFFFFFFFF),
                stripChipSurface = Color(0xC7F3F6FB),
                stripChipText = Color(0xFF151924),
                cardSurface = Color(0xE8FFFFFF),
                cardAltSurface = Color(0xCCDDE4F0)
            )
        }
    }

    return when {
        themeMode == ThemeMode.PIXEL_SLATE && useDark -> TapwingKeyboardColors(
            isDark = true,
            appleLiquidGlass = false,
            boardBackground = Color(0xFF1B1C1E),
            boardGradientBottom = Color(0xFF1B1C1E),
            keySurface = Color(0xFF303134),
            keyPressed = Color(0xFF45474D),
            keyText = Color(0xFFE8EAED),
            keyHintText = Color(0xFF9AA0A6),
            keyBorder = Color(0x10FFFFFF),
            keyGlassHighlight = Color(0x20FFFFFF),
            modifierSurface = Color(0xFF26282C),
            modifierText = Color(0xFFE8EAED),
            spacebarSurface = Color(0xFF303134),
            actionSurface = Color(0xFFA8C7FA),
            actionText = Color(0xFF062E6F),
            stripChipSurface = Color(0xFF2A2C30),
            stripChipText = Color(0xFFE8EAED),
            cardSurface = Color(0xFF242629),
            cardAltSurface = Color(0xFF2F3237)
        )
        themeMode == ThemeMode.PIXEL_SLATE -> TapwingKeyboardColors(
            isDark = false,
            appleLiquidGlass = false,
            boardBackground = Color(0xFFEEF0F4),
            boardGradientBottom = Color(0xFFEEF0F4),
            keySurface = Color(0xFFFFFFFF),
            keyPressed = Color(0xFFDCE1EA),
            keyText = Color(0xFF1F1F1F),
            keyHintText = Color(0xFF5F6368),
            keyBorder = Color(0x14000000),
            keyGlassHighlight = Color(0x55FFFFFF),
            modifierSurface = Color(0xFFD9E0EC),
            modifierText = Color(0xFF1F1F1F),
            spacebarSurface = Color(0xFFFFFFFF),
            actionSurface = Color(0xFFD3E3FD),
            actionText = Color(0xFF041E49),
            stripChipSurface = Color(0xFFE3E8F0),
            stripChipText = Color(0xFF1F1F1F),
            cardSurface = Color(0xFFFFFFFF),
            cardAltSurface = Color(0xFFE3E8F0)
        )
        themeMode == ThemeMode.AMOLED_DARK -> TapwingKeyboardColors(
            isDark = true,
            appleLiquidGlass = false,
            boardBackground = Color(0xFF000000),
            boardGradientBottom = Color(0xFF000000),
            keySurface = Color(0xFF18181D),
            keyPressed = Color(0xFF2D283A),
            keyText = Color(0xFFF3F3F7),
            keyHintText = Color(0xFF9E9CB0),
            keyBorder = Color(0x1FFFFFFF),
            keyGlassHighlight = Color(0x22FFFFFF),
            modifierSurface = Color(0xFF24222D),
            modifierText = Color(0xFFE7E0F4),
            spacebarSurface = Color(0xFF1C1C23),
            actionSurface = Color(0xFF6750A4),
            actionText = Color(0xFFFFFFFF),
            stripChipSurface = Color(0xFF211F29),
            stripChipText = Color(0xFFEDE7F8),
            cardSurface = Color(0xFF111116),
            cardAltSurface = Color(0xFF1B1923)
        )
        themeMode == ThemeMode.PIXEL_BLUE && useDark -> TapwingKeyboardColors(
            isDark = true,
            appleLiquidGlass = false,
            boardBackground = Color(0xFF11151C),
            boardGradientBottom = Color(0xFF11151C),
            keySurface = Color(0xFF232934),
            keyPressed = Color(0xFF334157),
            keyText = Color(0xFFF3F5F8),
            keyHintText = Color(0xFF9AA4B4),
            keyBorder = Color(0x1AFFFFFF),
            keyGlassHighlight = Color(0x22FFFFFF),
            modifierSurface = Color(0xFF1B222D),
            modifierText = Color(0xFFD6E2F5),
            spacebarSurface = Color(0xFF262D39),
            actionSurface = Color(0xFF8AB4F8),
            actionText = Color(0xFF062E6F),
            stripChipSurface = Color(0xFF1D2634),
            stripChipText = Color(0xFFE8F0FE),
            cardSurface = Color(0xFF171D26),
            cardAltSurface = Color(0xFF202A38)
        )
        themeMode == ThemeMode.PIXEL_BLUE -> TapwingKeyboardColors(
            isDark = false,
            appleLiquidGlass = false,
            boardBackground = Color(0xFFEDF2FA),
            boardGradientBottom = Color(0xFFEDF2FA),
            keySurface = Color(0xFFFFFFFF),
            keyPressed = Color(0xFFDDE7F9),
            keyText = Color(0xFF101828),
            keyHintText = Color(0xFF637082),
            keyBorder = Color(0x140F172A),
            keyGlassHighlight = Color(0x55FFFFFF),
            modifierSurface = Color(0xFFD8E4F7),
            modifierText = Color(0xFF172B4D),
            spacebarSurface = Color(0xFFFFFFFF),
            actionSurface = Color(0xFF1A73E8),
            actionText = Color(0xFFFFFFFF),
            stripChipSurface = Color(0xFFE1EBFC),
            stripChipText = Color(0xFF0F2C59),
            cardSurface = Color(0xFFFFFFFF),
            cardAltSurface = Color(0xFFE6EFFB)
        )
        themeMode == ThemeMode.EXPRESSIVE_LAVENDER && !useDark -> TapwingKeyboardColors(
            isDark = false,
            appleLiquidGlass = false,
            boardBackground = Color(0xFFF3EDF9),
            boardGradientBottom = Color(0xFFF3EDF9),
            keySurface = Color(0xFFFFFFFF),
            keyPressed = Color(0xFFE4DAF3),
            keyText = Color(0xFF1D1B20),
            keyHintText = Color(0xFF6B6575),
            keyBorder = Color(0x121D1B20),
            keyGlassHighlight = Color(0x66FFFFFF),
            modifierSurface = Color(0xFFE3DAF0),
            modifierText = Color(0xFF261C38),
            spacebarSurface = Color(0xFFFFFFFF),
            actionSurface = Color(0xFF5E41A8),
            actionText = Color(0xFFFFFFFF),
            stripChipSurface = Color(0xFFE8DFF6),
            stripChipText = Color(0xFF231937),
            cardSurface = Color(0xFFFFFFFF),
            cardAltSurface = Color(0xFFECE4F7)
        )
        themeMode == ThemeMode.EXPRESSIVE_LAVENDER && useDark -> TapwingKeyboardColors(
            isDark = true,
            appleLiquidGlass = false,
            boardBackground = Color(0xFF17141D),
            boardGradientBottom = Color(0xFF17141D),
            keySurface = Color(0xFF292433),
            keyPressed = Color(0xFF3E3550),
            keyText = Color(0xFFF2ECF8),
            keyHintText = Color(0xFFABA3B8),
            keyBorder = Color(0x18FFFFFF),
            keyGlassHighlight = Color(0x28FFFFFF),
            modifierSurface = Color(0xFF211C2A),
            modifierText = Color(0xFFE5DCF4),
            spacebarSurface = Color(0xFF2D2738),
            actionSurface = Color(0xFFD0BCFF),
            actionText = Color(0xFF381E72),
            stripChipSurface = Color(0xFF272133),
            stripChipText = Color(0xFFEDE5FA),
            cardSurface = Color(0xFF1F1B26),
            cardAltSurface = Color(0xFF2A2336)
        )
        else -> TapwingKeyboardColors(
            isDark = useDark,
            appleLiquidGlass = false,
            boardBackground = scheme.surfaceVariant,
            boardGradientBottom = scheme.surfaceVariant,
            keySurface = scheme.surface,
            keyPressed = scheme.primaryContainer,
            keyText = scheme.onSurface,
            keyHintText = scheme.onSurfaceVariant,
            keyBorder = Color(if (useDark) 0x18FFFFFF else 0x14000000),
            keyGlassHighlight = Color(0x28FFFFFF),
            modifierSurface = scheme.secondaryContainer,
            modifierText = scheme.onSecondaryContainer,
            spacebarSurface = scheme.surface,
            actionSurface = scheme.primary,
            actionText = scheme.onPrimary,
            stripChipSurface = scheme.secondaryContainer,
            stripChipText = scheme.onSecondaryContainer,
            cardSurface = scheme.surface,
            cardAltSurface = scheme.surfaceVariant
        )
    }
}
