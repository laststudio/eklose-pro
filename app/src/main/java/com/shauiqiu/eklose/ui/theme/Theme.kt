package com.shauiqiu.eklose.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.shauiqiu.eklose.EkwingLoginStore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

enum class EkloseAccentColor(val argb: Int, val title: String) {
    SystemDefault(0, "系统默认"),
    Blue(0xFF3F6FE5.toInt(), "蓝色"),
    Green(0xFF4FAF50.toInt(), "绿色"),
    Purple(0xFF8559C7.toInt(), "紫色"),
    Orange(0xFFE8752A.toInt(), "橙色"),
    Red(0xFFD84A4A.toInt(), "红色");

    companion object {
        fun fromArgb(value: Int): EkloseAccentColor =
            entries.firstOrNull { it.argb == value } ?: SystemDefault
    }
}

enum class EkloseColorSpec(val value: String, val title: String) {
    Spec2021("Spec2021", "2021 色彩规范"),
    Spec2025("Spec2025", "2025 色彩规范");

    companion object {
        fun fromValue(value: String): EkloseColorSpec =
            entries.firstOrNull { it.value == value } ?: Spec2025
    }
}

enum class EklosePaletteStyle(val value: String, val title: String) {
    TonalSpot("TonalSpot", "柔和色板"),
    Neutral("Neutral", "中性色板"),
    Vibrant("Vibrant", "鲜艳色板"),
    Expressive("Expressive", "表现性色板"),
    Rainbow("Rainbow", "彩虹色板"),
    FruitSalad("FruitSalad", "果缤纷色板");

    companion object {
        fun fromValue(value: String): EklosePaletteStyle =
            entries.firstOrNull { it.value == value } ?: TonalSpot
    }
}

private val EklosePaletteStyle.supportsSpec2025: Boolean
    get() = this == EklosePaletteStyle.TonalSpot ||
        this == EklosePaletteStyle.Neutral ||
        this == EklosePaletteStyle.Vibrant ||
        this == EklosePaletteStyle.Expressive

internal fun effectiveColorSpec(
    requested: EkloseColorSpec,
    paletteStyle: EklosePaletteStyle,
): ThemeColorSpec = if (requested == EkloseColorSpec.Spec2025 && paletteStyle.supportsSpec2025) {
    ThemeColorSpec.Spec2025
} else {
    ThemeColorSpec.Spec2021
}

enum class EkloseColorMode(val storedValue: Int, val title: String) {
    System(0, "跟随系统"),
    Light(1, "浅色"),
    Dark(2, "深色"),
    Amoled(3, "深色 AMOLED"),
    MonetSystem(4, "跟随系统动态色"),
    MonetLight(5, "动态浅色"),
    MonetDark(6, "动态深色");

    fun isMonet(): Boolean = this == MonetSystem || this == MonetLight || this == MonetDark

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark, Amoled, MonetDark -> true
        MonetSystem -> systemDark
        MonetLight -> false
    }

    companion object {
        fun fromStoredValue(value: Int): EkloseColorMode =
            entries.firstOrNull { it.storedValue == value } ?: System
    }
}

val LocalEkloseBlurEnabled = staticCompositionLocalOf { true }
val LocalEkloseIsDarkTheme = staticCompositionLocalOf { false }
val LocalEklosePageBackground = staticCompositionLocalOf { Color(0xFFF7F7F7) }
val LocalEkloseCardSurface = staticCompositionLocalOf { Color.White }

@Composable
fun rememberEkloseBlurEnabled(): Boolean {
    val context = LocalContext.current.applicationContext
    var enabled by remember(context) { mutableStateOf(EkwingLoginStore.blurEnabled(context)) }
    DisposableEffect(context) {
        val stopObserving = EkwingLoginStore.observeBlurEnabled(context) { value -> enabled = value }
        onDispose { stopObserving() }
    }
    return enabled
}

@Composable
fun rememberEkloseAccentColor(): EkloseAccentColor {
    val context = LocalContext.current.applicationContext
    var accentColor by remember(context) {
        mutableStateOf(EkloseAccentColor.fromArgb(EkwingLoginStore.accentColor(context)))
    }
    DisposableEffect(context) {
        val stopObserving = EkwingLoginStore.observeAccentColor(context) { value ->
            accentColor = EkloseAccentColor.fromArgb(value)
        }
        onDispose { stopObserving() }
    }
    return accentColor
}

@Composable
fun rememberEkloseColorSpec(): EkloseColorSpec {
    val context = LocalContext.current.applicationContext
    var colorSpec by remember(context) {
        mutableStateOf(EkloseColorSpec.fromValue(EkwingLoginStore.colorSpec(context)))
    }
    DisposableEffect(context) {
        val stopObserving = EkwingLoginStore.observeColorSpec(context) { value ->
            colorSpec = EkloseColorSpec.fromValue(value)
        }
        onDispose { stopObserving() }
    }
    return colorSpec
}

@Composable
fun rememberEklosePaletteStyle(): EklosePaletteStyle {
    val context = LocalContext.current.applicationContext
    var paletteStyle by remember(context) {
        mutableStateOf(EklosePaletteStyle.fromValue(EkwingLoginStore.paletteStyle(context)))
    }
    DisposableEffect(context) {
        val stopObserving = EkwingLoginStore.observePaletteStyle(context) { value ->
            paletteStyle = EklosePaletteStyle.fromValue(value)
        }
        onDispose { stopObserving() }
    }
    return paletteStyle
}

/** Shared preference changes update every composed activity, not just the settings page. */
@Composable
fun rememberEkloseColorMode(): EkloseColorMode {
    val context = LocalContext.current.applicationContext
    var colorMode by remember(context) {
        mutableStateOf(EkloseColorMode.fromStoredValue(EkwingLoginStore.colorMode(context)))
    }
    DisposableEffect(context) {
        val stopObserving = EkwingLoginStore.observeColorMode(context) { value ->
            colorMode = EkloseColorMode.fromStoredValue(value)
        }
        onDispose { stopObserving() }
    }
    return colorMode
}

@SuppressLint("DiscouragedApi")
@Composable
fun EkloseTheme(
    colorMode: EkloseColorMode = rememberEkloseColorMode(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val paletteStyle = rememberEklosePaletteStyle()
    val colorSpec = rememberEkloseColorSpec()
    val accentColor = rememberEkloseAccentColor()
    val blurEnabled = rememberEkloseBlurEnabled()
    val darkTheme = colorMode.isDark(systemDarkTheme)
    val colors = if (darkTheme) {
        if (colorMode == EkloseColorMode.Amoled) {
            darkColorScheme(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainer = Color(0xFF101010),
                surfaceContainerHigh = Color(0xFF181818),
                surfaceContainerHighest = Color(0xFF202020),
            )
        } else {
            darkColorScheme()
        }
    } else {
        lightColorScheme()
    }
    val systemAccentColor = if (colorMode.isMonet()) {
        val resources = LocalResources.current
        val accentName = if (darkTheme) "system_accent1_200" else "system_accent1_600"
        val accentResId = resources.getIdentifier(accentName, "color", "android")
        accentResId.takeIf { it != 0 }?.let { Color(resources.getColor(it, context.theme)) }
    } else {
        null
    }
    val resolvedKeyColor = accentColor.argb.takeIf { it != 0 }?.let(::Color) ?: systemAccentColor
    val controllerPaletteStyle = runCatching {
        ThemePaletteStyle.valueOf(paletteStyle.value)
    }.getOrDefault(ThemePaletteStyle.TonalSpot)
    val controller = runCatching {
        val colorSchemeMode = when (colorMode) {
            EkloseColorMode.System -> ColorSchemeMode.System
            EkloseColorMode.Light -> ColorSchemeMode.Light
            EkloseColorMode.Dark, EkloseColorMode.Amoled -> ColorSchemeMode.Dark
            EkloseColorMode.MonetSystem -> ColorSchemeMode.MonetSystem
            EkloseColorMode.MonetLight -> ColorSchemeMode.MonetLight
            EkloseColorMode.MonetDark -> ColorSchemeMode.MonetDark
        }
        if (colorMode == EkloseColorMode.Amoled) {
            // Preserve Eklose's AMOLED surface override, while using KernelSU's generated
            // Miuix color pipeline for every other color mode.
            ThemeController(
                colorSchemeMode,
                lightColors = colors,
                darkColors = colors,
                keyColor = resolvedKeyColor,
                isDark = darkTheme,
                paletteStyle = controllerPaletteStyle,
                colorSpec = effectiveColorSpec(colorSpec, paletteStyle),
            )
        } else {
            // Match KernelSU: do not inject separate static light/dark palettes here.
            ThemeController(
                colorSchemeMode,
                keyColor = resolvedKeyColor,
                isDark = darkTheme,
                paletteStyle = controllerPaletteStyle,
                colorSpec = effectiveColorSpec(colorSpec, paletteStyle),
            )
        }
    }.getOrNull()
    val pageBackground = if (darkTheme) {
        if (colorMode == EkloseColorMode.Amoled) Color.Black else Color(0xFF101010)
    } else {
        Color(0xFFF7F7F7)
    }
    val cardSurface = if (darkTheme) Color(0xFF1C1C1E) else Color.White
    val themeContent: @Composable () -> Unit = {
        LaunchedEffect(darkTheme) {
            val window = (context as? Activity)?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        CompositionLocalProvider(
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            LocalEkloseBlurEnabled provides blurEnabled,
            LocalEkloseIsDarkTheme provides darkTheme,
            LocalEklosePageBackground provides pageBackground,
            LocalEkloseCardSurface provides cardSurface,
            content = content,
        )
    }

    if (controller == null) {
        MiuixTheme(colors = colors, content = themeContent)
    } else {
        MiuixTheme(controller = controller, content = themeContent)
    }
}
