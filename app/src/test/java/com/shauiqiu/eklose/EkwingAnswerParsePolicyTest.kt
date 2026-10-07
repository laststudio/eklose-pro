package com.shauiqiu.eklose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EkwingAnswerParsePolicyTest {
    private val key = "paper"
    private val paper = EkwingAnswerPaper(key, "考试", "说明")
    private val good = EkwingAnswerSection("答案", "reading", "原文", emptyList())
    private val error = EkwingAnswerSection("错误", "error", "失败", emptyList())

    private fun state(status: EkwingAnswerParseStatus, sections: List<EkwingAnswerSection> = emptyList()) =
        EkwingAnswerSourceState(
            papers = listOf(paper),
            parseStateByPaperKey = mapOf(key to EkwingAnswerParseState(status)),
            sectionsByPaperKey = if (sections.isEmpty()) emptyMap() else mapOf(key to sections),
        )

    @Test
    fun defaultModeLeavesEveryUnrequestedPaperNotStarted() {
        listOf("first", "second", "third").forEach { paperKey ->
            val result = paperParseStateAfterListRead(EkwingAnswerSourceState(), paperKey)
            assertEquals(EkwingAnswerParseStatus.NotStarted, result.status)
            assertFalse(canOpenPaper(result))
        }
    }

    @Test
    fun rereadingInManualModeDoesNotPretendOldQueuedWorkIsStillRunning() {
        listOf(EkwingAnswerParseStatus.Pending, EkwingAnswerParseStatus.Loading).forEach { status ->
            assertEquals(
                EkwingAnswerParseStatus.NotStarted,
                paperParseStateAfterListRead(state(status), key).status,
            )
        }
    }

    @Test
    fun activeManualRequestsKeepTheirQueueAndLoadingState() {
        listOf(EkwingAnswerParseStatus.Pending, EkwingAnswerParseStatus.Loading).forEach { status ->
            assertEquals(status, paperParseStateAfterListRead(state(status), key, isParsing = true).status)
        }
    }

    @Test
    fun successfulAnswersAreStillDirectlyViewable() {
        assertEquals(
            EkwingAnswerParseStatus.Ready,
            paperParseStateAfterListRead(state(EkwingAnswerParseStatus.Ready, listOf(good)), key).status,
        )
    }

    @Test
    fun failedAnswersRemainRetryableInsteadOfBeingMarkedReady() {
        assertEquals(
            EkwingAnswerParseStatus.Failed,
            paperParseStateAfterListRead(state(EkwingAnswerParseStatus.Failed, listOf(error)), key).status,
        )
    }

    @Test
    fun restoreResetsInterruptedWorkButPreservesLiveRequests() {
        val interrupted = state(EkwingAnswerParseStatus.Loading)
        assertEquals(EkwingAnswerParseStatus.NotStarted, restorePaperParseStates(interrupted).parseStateByPaperKey[key]?.status)
        assertEquals(EkwingAnswerParseStatus.Loading, restorePaperParseStates(interrupted) { true }.parseStateByPaperKey[key]?.status)
    }

    @Test
    fun unrequestedStateSurvivesCacheSerialization() {
        val restored = sourceStateFromJson(sourceStateToJson(state(EkwingAnswerParseStatus.NotStarted)))
        assertEquals(EkwingAnswerParseStatus.NotStarted, restored.parseStateByPaperKey[key]?.status)
    }

    @Test
    fun errorCacheDoesNotBecomeReadyOnRestore() {
        val restored = sourceStateFromJson(sourceStateToJson(state(EkwingAnswerParseStatus.Failed, listOf(error))))
        assertEquals(EkwingAnswerParseStatus.Failed, restored.parseStateByPaperKey[key]?.status)
        assertFalse(canReuseAnswerCache(restored, key))
    }
}
