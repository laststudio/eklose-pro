package com.shauiqiu.eklose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EkwingAnswerCacheRetryTest {
    private val key = "paper"
    private val good = EkwingAnswerSection("Section", "reading", "Text", emptyList())
    private val error = EkwingAnswerSection("Error", "error", "Failed", emptyList())

    private fun cached(status: EkwingAnswerParseStatus?, sections: List<EkwingAnswerSection>) =
        EkwingAnswerSourceState(
            sectionsByPaperKey = mapOf(key to sections),
            parseStateByPaperKey = status?.let { mapOf(key to EkwingAnswerParseState(it)) }.orEmpty(),
        )

    @Test
    fun failedCacheMustBeReparsed() {
        assertFalse(canReuseAnswerCache(cached(EkwingAnswerParseStatus.Failed, listOf(error)), key))
        assertFalse(canReuseAnswerCache(cached(EkwingAnswerParseStatus.Failed, listOf(good)), key))
    }

    @Test
    fun errorSectionsCannotBeMarkedReadyEvenWithStaleReadyState() {
        assertFalse(canReuseAnswerCache(cached(EkwingAnswerParseStatus.Ready, listOf(error)), key))
        assertFalse(canReuseAnswerCache(cached(null, listOf(good, error)), key))
    }

    @Test
    fun validCacheIsReusedAndMissingCacheIsNot() {
        assertTrue(canReuseAnswerCache(cached(EkwingAnswerParseStatus.Ready, listOf(good)), key))
        assertTrue(canReuseAnswerCache(cached(null, listOf(good)), key))
        assertFalse(canReuseAnswerCache(EkwingAnswerSourceState(), key))
        assertFalse(canReuseAnswerCache(cached(EkwingAnswerParseStatus.Ready, listOf(good)), "other"))
    }
}
