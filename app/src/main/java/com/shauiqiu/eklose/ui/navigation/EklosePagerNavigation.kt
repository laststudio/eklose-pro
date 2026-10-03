package com.shauiqiu.eklose.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One navigation owner: swipe updates must not restart an in-flight navigation animation. */
internal class EklosePagerNavigation(
    private val currentPage: () -> Int,
    private val pageCount: () -> Int,
    private val animateToPage: suspend (Int) -> Unit,
    private val scope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(currentPage())
        private set
    private var navigationJob: Job? = null
    private var generation = 0L

    fun navigateTo(target: Int) {
        require(target in 0 until pageCount())
        if (selectedPage == target && navigationJob?.isActive == true) return
        if (currentPage() == target && navigationJob?.isActive != true) {
            selectedPage = target
            return
        }
        val request = ++generation
        navigationJob?.cancel()
        selectedPage = target
        // Assign the job before starting it, including with an immediate/unconfined dispatcher.
        navigationJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                animateToPage(target)
            } finally {
                if (request == generation) {
                    navigationJob = null
                    selectedPage = currentPage()
                }
            }
        }
        navigationJob?.start()
    }

    fun syncFromPager() {
        if (navigationJob?.isActive != true) selectedPage = currentPage()
    }
}
