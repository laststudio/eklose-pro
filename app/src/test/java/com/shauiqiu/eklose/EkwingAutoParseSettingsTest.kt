package com.shauiqiu.eklose

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EkwingAutoParseSettingsTest {
    @Test
    fun newAndExistingInstallationsDefaultToManualParsing() {
        val context = PreferenceContext()
        assertFalse(EkwingLoginStore.shouldAutoParseAllAnswers(context))
        EkwingLoginStore.setSaveLogin(context, true)
        assertFalse(EkwingLoginStore.shouldAutoParseAllAnswers(context))
    }

    @Test
    fun confirmedChoiceIsPersistedAndCanBeDisabled() {
        val context = PreferenceContext()
        EkwingLoginStore.setAutoParseAllAnswers(context, true)
        assertTrue(EkwingLoginStore.shouldAutoParseAllAnswers(context))
        EkwingLoginStore.setAutoParseAllAnswers(context, false)
        assertFalse(EkwingLoginStore.shouldAutoParseAllAnswers(context))
    }

    @Test
    fun changingParsingPreferenceDoesNotClearLoginPreference() {
        val context = PreferenceContext()
        EkwingLoginStore.setSaveLogin(context, true)
        EkwingLoginStore.setAutoParseAllAnswers(context, true)
        EkwingLoginStore.setAutoParseAllAnswers(context, false)
        assertTrue(EkwingLoginStore.shouldSaveLogin(context))
    }

    @Test
    fun disabledModeRejectsAutomaticQueueingForAllSources() {
        val context = PreferenceContext()
        val papers = listOf(EkwingAnswerPaper("first", "题目一", ""), EkwingAnswerPaper("second", "题目二", ""))
        try {
            EkwingAnswerSource.entries.forEach { source ->
                val initialState = EkwingAnswerSourceState(
                    papers = papers,
                    parseStateByPaperKey = papers.associate { it.key to EkwingAnswerParseState(EkwingAnswerParseStatus.NotStarted) },
                )
                EkwingAnswerState.setSourceState(source, initialState)
                EkwingAnswerPrefetchManager.enqueueAll(context, source, papers)
                assertEquals(initialState, EkwingAnswerState.sourceState(source))
                papers.forEach { assertFalse(EkwingAnswerPrefetchManager.isEnqueued(source, it.key)) }
            }
        } finally {
            EkwingAnswerState.clear()
        }
    }

    /** In-memory preference interfaces; no Android runtime or network is needed. */
    private class PreferenceContext : ContextWrapper(null) {
        private val values = mutableMapOf<String, Boolean>()
        private val pendingValues = mutableMapOf<String, Boolean>()
        private val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "putBoolean" -> {
                    pendingValues[args!![0] as String] = args[1] as Boolean
                    proxy
                }
                "apply", "commit" -> {
                    values.putAll(pendingValues)
                    pendingValues.clear()
                    if (method.name == "commit") true else null
                }
                else -> error("Unexpected preference editor method: ${method.name}")
            }
        } as SharedPreferences.Editor
        private val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getBoolean" -> values[args!![0] as String] ?: args[1] as Boolean
                "edit" -> editor
                else -> error("Unexpected preference method: ${method.name}")
            }
        } as SharedPreferences

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
        override fun getApplicationContext(): Context = this
    }
}
