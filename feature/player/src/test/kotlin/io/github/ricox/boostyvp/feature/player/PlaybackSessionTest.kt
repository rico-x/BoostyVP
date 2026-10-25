package io.github.ricox.boostyvp.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import io.github.ricox.boostyvp.core.model.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionTest {
    private val dispatcher = StandardTestDispatcher()
    private val blogs = mockk<BlogRepository>()
    private val progress = mockk<PlaybackProgressRepository>(relaxed = true)
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { progress.recordsResumePositions() } returns true
        coEvery { blogs.post("blog", any()) } answers { post(secondArg()) }
    }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun post(id: String): Post {
        val video = ContentBlock.Video("video-$id", null, null, 60, null, null, null, null, null,
            listOf(VideoSource(VideoSource.Kind.Progressive, "https://example.invalid/$id.mp4", 1, "test")), true)
        return Post(id, 0, "Synthetic", "blog", "Creator", 1, true, false, false, false, null, listOf(video), emptyList())
    }
    private fun copyState(value: SavedStateHandle) = SavedStateHandle(value.keys().associateWith { value.get<Any?>(it) })

    @Test fun retainedEntryDoesNotReloadOrJumpBackToItsOriginalVideo() = runTest(dispatcher) {
        val model = PlayerViewModel(blogs, progress)
        model.open("blog", "first"); advanceUntilIdle()
        model.playNext(post("next")); advanceUntilIdle()
        val ready = model.state.value as PlayerUiState.Ready
        model.onProgress(ready, 25_000, 60_000, false)
        model.open("blog", "first"); advanceUntilIdle()
        assertEquals("next", (model.state.value as PlayerUiState.Ready).postId)
        assertEquals(25_000L to false, model.sessionStart(ready))
        coVerify(exactly = 1) { blogs.post("blog", "first") }
        coVerify(exactly = 1) { blogs.post("blog", "next") }
    }
    @Test fun recreatedEntryRestoresExactShortAndNearEndPositionsAndPauseWithoutRepeatingFromStart() = runTest(dispatcher) {
        for (position in listOf(0L, 4_000L, 59_000L)) {
            val handle = SavedStateHandle()
            val first = PlayerViewModel(blogs, progress, handle)
            first.open("blog", "first", fromStart = true); advanceUntilIdle()
            first.onProgress(first.state.value as PlayerUiState.Ready, position, 60_000, false)
            advanceUntilIdle()
            val recreated = PlayerViewModel(blogs, progress, copyState(handle))
            recreated.open("blog", "first", fromStart = true); advanceUntilIdle()
            val ready = recreated.state.value as PlayerUiState.Ready
            assertEquals(position, ready.startPositionMs)
            assertFalse(ready.startPlayWhenReady)
            assertEquals(position to false, recreated.sessionStart(ready))
        }
    }
    @Test fun processRestoreUsesTheCurrentAutoplayVideoNotTheOriginalNavigationArgument() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = PlayerViewModel(blogs, progress, handle)
        first.open("blog", "first"); advanceUntilIdle()
        first.playNext(post("next")); advanceUntilIdle()
        first.onProgress(first.state.value as PlayerUiState.Ready, 33_000, 60_000, true)
        val recreated = PlayerViewModel(blogs, progress, copyState(handle))
        recreated.open("blog", "first"); advanceUntilIdle()
        val ready = recreated.state.value as PlayerUiState.Ready
        assertEquals("next", ready.postId); assertEquals(33_000L, ready.startPositionMs)
        assertTrue(ready.startPlayWhenReady)
    }
    @Test fun aNewExternalRouteStillOpensItsRequestedVideoInTheRetainedEntry() = runTest(dispatcher) {
        val model = PlayerViewModel(blogs, progress)
        model.open("blog", "first"); advanceUntilIdle()
        model.onProgress(model.state.value as PlayerUiState.Ready, 25_000, 60_000, false)
        model.open("blog", "external"); advanceUntilIdle()
        assertEquals("external", (model.state.value as PlayerUiState.Ready).postId)
    }
    @Test fun aProcessRestartDuringANewLoadCannotRestoreThePreviousVideo() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val model = PlayerViewModel(blogs, progress, handle)
        model.open("blog", "first"); advanceUntilIdle()
        model.onProgress(model.state.value as PlayerUiState.Ready, 25_000, 60_000, false)
        model.open("blog", "external")
        // No network result yet; the old checkpoint must already be invalidated.
        val restarted = PlayerViewModel(blogs, progress, copyState(handle))
        restarted.open("blog", "external"); advanceUntilIdle()
        val ready = restarted.state.value as PlayerUiState.Ready
        assertEquals("external", ready.postId); assertEquals(0L, ready.startPositionMs)
    }
    @Test fun privacyOffKeepsOnlyLiveMemoryAndDropsRestoredPositions() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = PlayerViewModel(blogs, progress, handle)
        first.open("blog", "first"); advanceUntilIdle()
        first.onProgress(first.state.value as PlayerUiState.Ready, 33_000, 60_000, false)
        coEvery { progress.recordsResumePositions() } returns false
        val privateHandle = copyState(handle)
        val private = PlayerViewModel(blogs, progress, privateHandle)
        private.open("blog", "first"); advanceUntilIdle()
        val ready = private.state.value as PlayerUiState.Ready
        private.onProgress(ready, 4_000, 60_000, false)
        private.open("blog", "first")
        assertEquals(4_000L to false, private.sessionStart(ready))
        assertFalse(privateHandle.keys().any { it.startsWith("playback_session_") })
    }
    @Test fun aLateDisposedPlayerCannotOverwriteAnExplicitRestartOfTheSameVideo() = runTest(dispatcher) {
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "first"); advanceUntilIdle()
        val old = model.state.value as PlayerUiState.Ready
        model.load("blog", "first", 0); advanceUntilIdle()
        model.onProgress(old, 45_000, 60_000)
        coVerify(exactly = 0) { progress.save(match { it.positionMs == 45_000L }) }
        assertEquals(0L, model.sessionStart(model.state.value as PlayerUiState.Ready).first)
    }
    @Test fun finalSaveSurvivesNavigationCancellingTheViewModelScope() = runTest(dispatcher) {
        var completed = false
        coEvery { progress.save(any()) } coAnswers { delay(10); completed = true }
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "first"); advanceUntilIdle()
        model.onProgress(model.state.value as PlayerUiState.Ready, 25_000, 60_000, false)
        model.viewModelScope.cancel()
        advanceUntilIdle()
        assertTrue(completed)
    }
    @Test fun delayedWritesCannotReplaceTheFinalPositionWithAnOlderOne() = runTest(dispatcher) {
        val positions = mutableListOf<Long>()
        coEvery { progress.save(any()) } coAnswers {
            val position = firstArg<WatchProgress>().positionMs
            if (position == 15_000L) delay(100)
            positions += position
        }
        val model = PlayerViewModel(blogs, progress)
        model.load("blog", "first"); advanceUntilIdle()
        val ready = model.state.value as PlayerUiState.Ready
        model.onProgress(ready, 15_000, 60_000)
        model.onProgress(ready, 25_000, 60_000, false)
        model.viewModelScope.cancel(); advanceUntilIdle()
        assertEquals(listOf(15_000L, 25_000L), positions)
    }
    @Test fun repeatedBackgroundEventsDoNotReplaceThePrePausePlayIntent() {
        val state = PlaybackVisibility(true)
        state.leave(true); assertTrue(state.desired(false))
        state.leave(false); assertTrue(state.desired(false))
        assertTrue(state.enter())
        state.leave(false); assertFalse(state.enter())
    }
    @Test fun initiallyPausedAndInitiallyBackgroundSessionsNeverAutoplay() {
        val paused = PlaybackVisibility(false)
        paused.leave(false); assertFalse(paused.enter())
        val background = PlaybackVisibility(true, initiallyForeground = false)
        background.leave(false); assertTrue(background.desired(false)); assertTrue(background.enter())
    }
}
