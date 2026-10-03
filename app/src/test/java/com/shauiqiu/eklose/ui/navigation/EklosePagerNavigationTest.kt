package com.shauiqiu.eklose.ui.navigation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EklosePagerNavigationTest {
    private class Harness(initialPage: Int = 0) {
        var current = initialPage
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val animations = mutableListOf<Pair<Int, CompletableDeferred<Unit>>>()
        val navigation = EklosePagerNavigation(
            currentPage = { current },
            pageCount = { 2 },
            animateToPage = { target ->
                val gate = CompletableDeferred<Unit>()
                animations += target to gate
                gate.await()
                current = target
            },
            scope = scope,
        )
    }

    @Test
    fun swipesSynchronizeWithoutStartingAnimation() {
        val h = Harness()
        try {
            h.current = 1
            h.navigation.syncFromPager()
            assertEquals(1, h.navigation.selectedPage)
            assertEquals(0, h.animations.size)
        } finally { h.scope.cancel() }
    }

    @Test
    fun intermediatePagerPositionDoesNotOverwriteNavigationTarget() = runBlocking {
        val h = Harness()
        try {
            h.navigation.navigateTo(1)
            h.navigation.syncFromPager()
            assertEquals(1, h.navigation.selectedPage)
            assertEquals(0, h.current)
            h.animations.single().second.complete(Unit)
            assertEquals(1, h.current)
            assertEquals(1, h.navigation.selectedPage)
        } finally { h.scope.cancel() }
    }

    @Test
    fun rapidTapBackCancelsOlderAnimation() = runBlocking {
        val h = Harness()
        try {
            h.navigation.navigateTo(1)
            h.navigation.navigateTo(0)
            assertEquals(listOf(1, 0), h.animations.map { it.first })
            h.animations[0].second.complete(Unit)
            assertEquals(0, h.navigation.selectedPage)
            h.animations[1].second.complete(Unit)
            assertEquals(0, h.current)
            assertEquals(0, h.navigation.selectedPage)
        } finally { h.scope.cancel() }
    }

    @Test
    fun repeatedTapDoesNotStartDuplicateAnimation() {
        val h = Harness()
        try {
            h.navigation.navigateTo(1)
            h.navigation.navigateTo(1)
            assertEquals(1, h.animations.size)
        } finally { h.scope.cancel() }
    }

    @Test
    fun restoredPageAndInvalidTarget() {
        val h = Harness(initialPage = 1)
        try {
            assertEquals(1, h.navigation.selectedPage)
            h.navigation.navigateTo(1)
            assertEquals(0, h.animations.size)
            assertThrows(IllegalArgumentException::class.java) { h.navigation.navigateTo(2) }
            assertThrows(IllegalArgumentException::class.java) { h.navigation.navigateTo(-1) }
        } finally { h.scope.cancel() }
    }
}
