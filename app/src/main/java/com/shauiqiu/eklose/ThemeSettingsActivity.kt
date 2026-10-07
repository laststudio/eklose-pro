package com.shauiqiu.eklose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shauiqiu.eklose.ui.theme.EkloseAccentColor
import com.shauiqiu.eklose.ui.theme.EkloseColorMode
import com.shauiqiu.eklose.ui.theme.EkloseColorSpec
import com.shauiqiu.eklose.ui.theme.EklosePaletteStyle
import com.shauiqiu.eklose.ui.theme.EkloseTheme
import com.shauiqiu.eklose.ui.theme.LocalEklosePageBackground
import com.shauiqiu.eklose.ui.theme.rememberEkloseAccentColor
import com.shauiqiu.eklose.ui.theme.rememberEkloseBlurEnabled
import com.shauiqiu.eklose.ui.theme.rememberEkloseColorMode
import com.shauiqiu.eklose.ui.theme.rememberEkloseColorSpec
import com.shauiqiu.eklose.ui.theme.rememberEklosePaletteStyle
import com.shauiqiu.eklose.ui.theme.rememberEkloseNavigationBadgeEnabled
import com.shauiqiu.eklose.ui.theme.rememberEklosePredictiveBackEnabled
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

class ThemeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode = rememberEkloseColorMode()
            EkloseTheme(colorMode = mode) {
                ThemeSettingsScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun ThemeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberEkloseBlurBackdrop()
    val modeValue = rememberEkloseColorMode()
    val accentValue = rememberEkloseAccentColor()
    val specValue = rememberEkloseColorSpec()
    val paletteValue = rememberEklosePaletteStyle()
    val blurValue = rememberEkloseBlurEnabled()
    val navigationBadgeEnabled = rememberEkloseNavigationBadgeEnabled()
    val predictiveBackEnabled = rememberEklosePredictiveBackEnabled()
    var colorMode by rememberSaveable { mutableStateOf(modeValue.storedValue) }
    var accentColor by rememberSaveable { mutableStateOf(accentValue.argb) }
    var colorSpec by rememberSaveable { mutableStateOf(specValue.value) }
    var paletteStyle by rememberSaveable { mutableStateOf(paletteValue.value) }
    var blurEnabled by rememberSaveable { mutableStateOf(blurValue) }
    var floating by rememberSaveable { mutableStateOf(EkwingLoginStore.useFloatingNavigationBar(context)) }
    var liquidGlass by rememberSaveable { mutableStateOf(EkwingLoginStore.useFloatingNavigationBarGlass(context)) }

    val mode = EkloseColorMode.fromStoredValue(colorMode)
    val selectedTab = when (mode) {
        EkloseColorMode.System, EkloseColorMode.MonetSystem -> 0
        EkloseColorMode.Light, EkloseColorMode.MonetLight -> 1
        else -> 2
    }
    val monet = mode.isMonet()
    val setMode: (Int) -> Unit = { tab ->
        val next = if (monet) {
            when (tab) {
                0 -> EkloseColorMode.MonetSystem
                1 -> EkloseColorMode.MonetLight
                else -> EkloseColorMode.MonetDark
            }
        } else {
            when (tab) {
                0 -> EkloseColorMode.System
                1 -> EkloseColorMode.Light
                else -> EkloseColorMode.Dark
            }
        }
        colorMode = next.storedValue
        EkwingLoginStore.setColorMode(context, next.storedValue)
    }

    Scaffold(
        containerColor = LocalEklosePageBackground.current,
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            EkloseBlurredBar(blurBackdrop) {
                TopAppBar(
                    title = "主题",
                    color = if (blurBackdrop != null) Color.Transparent else LocalEklosePageBackground.current,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Basic.ArrowRight,
                                modifier = Modifier.graphicsLayer(scaleX = -1f),
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .let { if (blurBackdrop != null) it.layerBackdrop(blurBackdrop) else it },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Spacer(Modifier.height(20.dp))
                    ThemePreviewCard(
                        keyColor = accentColor,
                        dark = mode.isDark(androidx.compose.foundation.isSystemInDarkTheme()),
                        monet = monet,
                        floating = floating,
                        floatingBlur = liquidGlass,
                        paletteStyle = runCatching { PaletteStyle.valueOf(paletteStyle) }.getOrDefault(PaletteStyle.TonalSpot),
                        colorSpec = runCatching { ColorSpec.SpecVersion.valueOf(colorSpec) }.getOrDefault(ColorSpec.SpecVersion.SPEC_2021),
                    )
                    Spacer(Modifier.height(36.dp))
                    TabRowWithContour(
                        tabs = listOf("跟随系统", "浅色", "深色"),
                        selectedTabIndex = selectedTab,
                        onTabSelected = setMode,
                    )
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            title = "动态色彩",
                            startAction = {
                                ThemePreferenceIcon(Icons.Rounded.Wallpaper, "动态色彩")
                            },
                            checked = monet,
                            onCheckedChange = {
                                val next = when {
                                    it && selectedTab == 0 -> EkloseColorMode.MonetSystem
                                    it && selectedTab == 1 -> EkloseColorMode.MonetLight
                                    it -> EkloseColorMode.MonetDark
                                    selectedTab == 0 -> EkloseColorMode.System
                                    selectedTab == 1 -> EkloseColorMode.Light
                                    else -> EkloseColorMode.Dark
                                }
                                colorMode = next.storedValue
                                EkwingLoginStore.setColorMode(context, next.storedValue)
                            },
                        )
                        AnimatedVisibility(visible = monet) {
                            Column {
                                OverlayDropdownPreference(
                                    title = "主题色",
                                    startAction = {
                                        ThemePreferenceIcon(Icons.Rounded.Colorize, "主题色")
                                    },
                                    items = EkloseAccentColor.entries.map { it.title },
                                    selectedIndex = EkloseAccentColor.entries.indexOfFirst { it.argb == accentColor }.coerceAtLeast(0),
                                    onSelectedIndexChange = {
                                        accentColor = EkloseAccentColor.entries[it].argb
                                        EkwingLoginStore.setAccentColor(context, accentColor)
                                    },
                                )
                                OverlayDropdownPreference(
                                    title = "动态色板",
                                    startAction = {
                                        ThemePreferenceIcon(Icons.Rounded.Style, "动态色板")
                                    },
                                    items = EklosePaletteStyle.entries.map { it.title },
                                    selectedIndex = EklosePaletteStyle.entries.indexOfFirst { it.value == paletteStyle }.coerceAtLeast(0),
                                    onSelectedIndexChange = {
                                        paletteStyle = EklosePaletteStyle.entries[it].value
                                        EkwingLoginStore.setPaletteStyle(context, paletteStyle)
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        OverlayDropdownPreference(
                            title = "颜色规范",
                            startAction = {
                                ThemePreferenceIcon(Icons.Rounded.DesignServices, "颜色规范")
                            },
                            items = EkloseColorSpec.entries.map { it.title },
                            selectedIndex = EkloseColorSpec.entries.indexOfFirst { it.value == colorSpec }.coerceAtLeast(0),
                            onSelectedIndexChange = {
                                colorSpec = EkloseColorSpec.entries[it].value
                                EkwingLoginStore.setColorSpec(context, colorSpec)
                            },
                        )
                        SwitchPreference(
                            title = "模糊",
                            startAction = {
                                ThemePreferenceIcon(Icons.Rounded.BlurOn, "模糊")
                            },
                            summary = "启用顶栏和底栏的模糊效果",
                            checked = blurEnabled,
                            onCheckedChange = {
                                blurEnabled = it
                                EkwingLoginStore.setBlurEnabled(context, it)
                            },
                        )
                        SwitchPreference(
                            title = "悬浮底部栏",
                            startAction = {
                                ThemePreferenceIcon(Icons.Rounded.CallToAction, "悬浮底部栏")
                            },
                            summary = "使用 Apple 风格的悬浮底部栏",
                            checked = floating,
                            onCheckedChange = {
                                floating = it
                                EkwingLoginStore.setUseFloatingNavigationBar(context, it)
                            },
                        )
                        AnimatedVisibility(visible = floating) {
                            SwitchPreference(
                                title = "液态玻璃",
                                startAction = {
                                    ThemePreferenceIcon(Icons.Rounded.WaterDrop, "液态玻璃")
                                },
                                summary = "启用悬浮底栏的液态玻璃效果",
                                checked = liquidGlass,
                                onCheckedChange = {
                                    liquidGlass = it
                                    EkwingLoginStore.setUseFloatingNavigationBarGlass(context, it)
                                },
                            )
                        }
                        SwitchPreference(
                            title = "导航栏角标",
                            startAction = {
                                ThemePreferenceIcon(Icons.Rounded.Pin, "导航栏角标")
                            },
                            summary = "在导航栏显示当前已读取到的作业数量",
                            checked = navigationBadgeEnabled,
                            onCheckedChange = { EkwingLoginStore.setNavigationBadgeEnabled(context, it) },
                        )
                        SwitchPreference(
                            title = "预见性返回手势",
                            startAction = {
                                ThemePreferenceIcon(Icons.AutoMirrored.Rounded.MenuOpen, "预见性返回手势")
                            },
                            summary = "启用对预见性返回手势的支持",
                            checked = predictiveBackEnabled,
                            onCheckedChange = { EkwingLoginStore.setPredictiveBackEnabled(context, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePreferenceIcon(icon: ImageVector, description: String) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        modifier = Modifier.padding(end = 6.dp).size(24.dp),
        tint = MiuixTheme.colorScheme.onBackground,
    )
}

@Suppress("ConfigurationScreenWidthHeight")
@Composable
private fun ThemePreviewCard(
    keyColor: Int,
    dark: Boolean,
    monet: Boolean,
    floating: Boolean,
    floatingBlur: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenRatio = screenWidth / screenHeight
    val seedColor = if (keyColor == 0) MiuixTheme.colorScheme.primary else Color(keyColor)
    val effectiveStyle = if (keyColor == 0) PaletteStyle.TonalSpot else paletteStyle
    val effectiveSpec = if (keyColor == 0) ColorSpec.SpecVersion.Default else colorSpec
    val dynamicCs = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = dark,
        style = effectiveStyle,
        specVersion = effectiveSpec,
    )
    val bgColor = if (monet) dynamicCs.background else MiuixTheme.colorScheme.surface
    val textColor = if (monet) dynamicCs.onSurface else MiuixTheme.colorScheme.onBackground
    val accentCardColor = when {
        monet -> dynamicCs.secondaryContainer
        dark -> Color(0xFF1A3825)
        else -> Color(0xFFDFFAE4)
    }
    val cardColor = if (monet) dynamicCs.surfaceContainerHighest else MiuixTheme.colorScheme.surfaceVariant
    val navBarColor = if (monet) dynamicCs.surfaceContainer else MiuixTheme.colorScheme.surface
    val iconColor = if (monet) dynamicCs.primary else MiuixTheme.colorScheme.primary
    val navSelectedColor = MiuixTheme.colorScheme.onSurface
    val navUnselectedColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio)
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
        ) {
            val content: @Composable () -> Unit = {
                Column {
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "翼课校长", fontSize = 12.sp, color = textColor)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(45.dp)
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentCardColor),
                    )
                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val smallCardCount = when {
                            maxHeight >= 96.dp -> 2
                            maxHeight >= 72.dp -> 1
                            else -> 0
                        }
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f)
                                    .clip(RoundedCornerShape(6.dp)).background(cardColor),
                            )
                            repeat(smallCardCount) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(12.dp)
                                        .clip(RoundedCornerShape(6.dp)).background(cardColor),
                                )
                            }
                        }
                    }
                }
            }
            content()
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (floatingBlur) navBarColor.copy(alpha = 0.5f) else navBarColor)
                        .border(0.5.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(2) {
                        Box(
                            modifier = Modifier.size(13.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (it == 0) iconColor else textColor),
                        )
                    }
                }
            }
        }
    }
}

