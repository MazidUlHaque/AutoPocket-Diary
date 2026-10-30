package com.mazidulhaque.autopocket.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mazidulhaque.autopocket.R

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App-specific colours, with separate light and dark values (chosen, not an automatic flip). */
data class AppColors(
    val ground: Color,
    val card: Color,
    val border: Color,
    val muted: Color,
    val moneyIn: Color,
    val moneyOut: Color,
    val ok: Color,
    val hero: Color,
    val heroTile: Color,
    val heroMuted: Color,
    val savings: Color,
    val warnBackground: Color,
    val warnBorder: Color,
    val warnText: Color,
    val dangerBackground: Color,
    val dangerText: Color,
    val topBar: Color,
    val inTile: Color,
    val outTile: Color,
)

private val LightColors = AppColors(
    ground = Color(0xFFF4F6F3), card = Color(0xFFFFFFFF), border = Color(0xFFE3E9E5), muted = Color(0xFF5B6B63),
    moneyIn = Color(0xFF1E7A4E), moneyOut = Color(0xFFB23A2A), ok = Color(0xFF1E7A4E),
    hero = Color(0xFF0F3D2E), heroTile = Color(0xFF164D3A), heroMuted = Color(0xFFB9D4C6), savings = Color(0xFFF3C987),
    warnBackground = Color(0xFFFFF6E8), warnBorder = Color(0xFFF1D9AE), warnText = Color(0xFF6A4410),
    dangerBackground = Color(0xFFFFEBEE), dangerText = Color(0xFFB71C1C), topBar = Color(0xFF0F3D2E),
    inTile = Color(0xFFF1F7F3), outTile = Color(0xFFFBF1EF),
)

private val DarkColors = AppColors(
    ground = Color(0xFF0E1412), card = Color(0xFF17201C), border = Color(0xFF26332D), muted = Color(0xFFA3B3AA),
    moneyIn = Color(0xFF81C995), moneyOut = Color(0xFFF2B8B5), ok = Color(0xFF81C995),
    hero = Color(0xFF123B2C), heroTile = Color(0xFF1B4B39), heroMuted = Color(0xFFB9D4C6), savings = Color(0xFFF3C987),
    warnBackground = Color(0xFF33260F), warnBorder = Color(0xFF5A4318), warnText = Color(0xFFFFD79A),
    dangerBackground = Color(0xFF3B1414), dangerText = Color(0xFFFFB4AB), topBar = Color(0xFF123B2C),
    inTile = Color(0xFF16291F), outTile = Color(0xFF2E1B19),
)

private val LocalAppColors = staticCompositionLocalOf { LightColors }

object AppTheme {
    val colors: AppColors @Composable get() = LocalAppColors.current
}

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Plus Jakarta Sans for English. */
private val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

/** Hind Siliguri for Bangla (it has Latin letters too, so mixed text looks even). */
private val Hind = FontFamily(
    Font(R.font.hind_siliguri_regular, FontWeight.Normal),
    Font(R.font.hind_siliguri_regular, FontWeight.Medium),
    Font(R.font.hind_siliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hind_siliguri_semibold, FontWeight.Bold),
    Font(R.font.hind_siliguri_semibold, FontWeight.ExtraBold),
)

private fun typography(family: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displaySmall = base.displaySmall.f().copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineMedium = base.headlineMedium.f().copy(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.4).sp),
        headlineSmall = base.headlineSmall.f().copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.f().copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.f().copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        titleSmall = base.titleSmall.f().copy(fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.f().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.f().copy(fontWeight = FontWeight.SemiBold),
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(),
        headlineLarge = base.headlineLarge.f(),
    )
}

@Composable
fun AutoPocketTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF81C995), onPrimary = Color(0xFF00390F),
            primaryContainer = Color(0xFF1B5E20), onPrimaryContainer = Color(0xFFC8E6C9),
            secondaryContainer = Color(0xFF1F3329), onSecondaryContainer = Color(0xFFD5E8DC),
            background = colors.ground, surface = colors.ground, surfaceVariant = colors.card,
            onSurfaceVariant = colors.muted, outline = colors.border, outlineVariant = colors.border,
            surfaceContainer = colors.card, surfaceContainerHigh = colors.card, surfaceContainerLow = colors.card,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0F5132), onPrimary = Color.White,
            primaryContainer = Color(0xFFC8E6C9), onPrimaryContainer = Color(0xFF0B3B25),
            secondaryContainer = Color(0xFFE3EFE7), onSecondaryContainer = Color(0xFF15201B),
            background = colors.ground, surface = colors.ground, surfaceVariant = colors.card,
            onSurface = Color(0xFF15201B), onBackground = Color(0xFF15201B),
            onSurfaceVariant = colors.muted, outline = Color(0xFFDDE4DF), outlineVariant = colors.border,
            surfaceContainer = colors.card, surfaceContainerHigh = colors.card, surfaceContainerLow = colors.card,
        )
    }
    val bangla = LocalConfiguration.current.locales[0].language == "bn"
    MaterialTheme(colorScheme = scheme, typography = typography(if (bangla) Hind else Jakarta)) {
        CompositionLocalProvider(LocalAppColors provides colors, content = content)
    }
}
