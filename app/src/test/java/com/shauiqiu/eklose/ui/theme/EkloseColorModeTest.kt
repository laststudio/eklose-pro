package com.shauiqiu.eklose.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EkloseColorModeTest {
    @Test
    fun accentColorsRoundTripAndFallback() {
        EkloseAccentColor.entries.forEach { color ->
            assertEquals(color, EkloseAccentColor.fromArgb(color.argb))
        }
        assertEquals(EkloseAccentColor.SystemDefault, EkloseAccentColor.fromArgb(0x12345678))
    }

    @Test
    fun colorSpecFallsBackForPalettesWithoutSpec2025Support() {
        assertEquals(
            top.yukonga.miuix.kmp.theme.ThemeColorSpec.Spec2025,
            effectiveColorSpec(EkloseColorSpec.Spec2025, EklosePaletteStyle.TonalSpot),
        )
        assertEquals(
            top.yukonga.miuix.kmp.theme.ThemeColorSpec.Spec2021,
            effectiveColorSpec(EkloseColorSpec.Spec2025, EklosePaletteStyle.Rainbow),
        )
        assertEquals(
            top.yukonga.miuix.kmp.theme.ThemeColorSpec.Spec2021,
            effectiveColorSpec(EkloseColorSpec.Spec2021, EklosePaletteStyle.TonalSpot),
        )
    }

    @Test
    fun colorSpecValuesRoundTripAndFallback() {
        assertEquals(EkloseColorSpec.Spec2021, EkloseColorSpec.fromValue("Spec2021"))
        assertEquals(EkloseColorSpec.Spec2025, EkloseColorSpec.fromValue("Spec2025"))
        assertEquals(EkloseColorSpec.Spec2025, EkloseColorSpec.fromValue("unknown"))
    }

    @Test
    fun paletteStyleValuesRoundTripAndFallback() {
        EklosePaletteStyle.entries.forEach { style ->
            assertEquals(style, EklosePaletteStyle.fromValue(style.value))
        }
        assertEquals(EklosePaletteStyle.TonalSpot, EklosePaletteStyle.fromValue("unknown"))
    }

    @Test
    fun savedValuesRemainCompatible() {
        assertEquals(EkloseColorMode.System, EkloseColorMode.fromStoredValue(0))
        assertEquals(EkloseColorMode.Light, EkloseColorMode.fromStoredValue(1))
        assertEquals(EkloseColorMode.Dark, EkloseColorMode.fromStoredValue(2))
        assertEquals(EkloseColorMode.Amoled, EkloseColorMode.fromStoredValue(3))
        assertEquals(EkloseColorMode.MonetSystem, EkloseColorMode.fromStoredValue(4))
        assertEquals(EkloseColorMode.MonetLight, EkloseColorMode.fromStoredValue(5))
        assertEquals(EkloseColorMode.MonetDark, EkloseColorMode.fromStoredValue(6))
        EkloseColorMode.entries.forEach { mode ->
            assertEquals(mode, EkloseColorMode.fromStoredValue(mode.storedValue))
        }
    }

    @Test
    fun unknownValuesFallBackToSystem() {
        listOf(-1, 7, Int.MIN_VALUE, Int.MAX_VALUE).forEach { value ->
            assertEquals(EkloseColorMode.System, EkloseColorMode.fromStoredValue(value))
        }
    }

    @Test
    fun onlySystemModeFollowsSystemDarkSetting() {
        assertFalse(EkloseColorMode.System.isDark(false))
        assertTrue(EkloseColorMode.System.isDark(true))
        listOf(false, true).forEach { systemDark ->
            assertFalse(EkloseColorMode.Light.isDark(systemDark))
            assertTrue(EkloseColorMode.Dark.isDark(systemDark))
            assertTrue(EkloseColorMode.Amoled.isDark(systemDark))
            assertFalse(EkloseColorMode.MonetLight.isDark(systemDark))
            assertTrue(EkloseColorMode.MonetDark.isDark(systemDark))
        }
    }
}
