package io.github.ricox.boostyvp.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.navigation.compose.NavHost
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.ui.component.PostCard
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real dialog windows and native PlayerView, with no account or network access. */
@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class PlayerReturnTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var list: LazyListState
    private lateinit var grid: LazyGridState
    private val rows = mutableMapOf<Int, LazyListState>()
    private val focus = mutableMapOf<String, FocusRequester>()
    private var creations = 0
    private var disposals = 0
    private lateinit var controller: NavHostController
    private var clickedId: String? = null

    @Test fun playerArgumentsAreDecodedExactlyOnce() {
        val blog = "author+name %2F"
        val post = "post/with+percent%25"
        var received: Pair<String, String>? = null
        compose.setContent {
            val nav = rememberNavController()
            controller = nav
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { Box(Modifier.fillMaxSize()) }
                playerDestination { actualBlog, actualPost -> received = actualBlog to actualPost }
            }
        }
        compose.runOnIdle { controller.navigate(Routes.player(blog, post)) }
        compose.runOnIdle { assertEquals(blog to post, received) }
    }

    @Test fun homeRetainsBothScrollAxesAndSelectedCard() {
        screen(isGrid = false)
        compose.onNodeWithTag("list").performScrollToIndex(6)
        compose.onNodeWithTag("row_6").performScrollToIndex(5)
        compose.runOnIdle { focus.getValue("6_5").requestFocus() }
        compose.onNodeWithTag("6_5").assertIsFocused()
        val before = compose.runOnIdle {
            listOf(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset,
                rows.getValue(6).firstVisibleItemIndex, rows.getValue(6).firstVisibleItemScrollOffset)
        }

        openAndReturn("6_5")

        compose.onNodeWithTag("6_5").assertIsFocused()
        compose.runOnIdle {
            assertEquals(before, listOf(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset,
                rows.getValue(6).firstVisibleItemIndex, rows.getValue(6).firstVisibleItemScrollOffset))
            assertEquals(1, creations)
            assertEquals(0, disposals)
        }
    }

    @Test fun authorRetainsGridScrollAndSelectedCard() {
        screen(isGrid = true)
        compose.onNodeWithTag("grid").performScrollToIndex(19)
        compose.runOnIdle { focus.getValue("19").requestFocus() }
        compose.onNodeWithTag("19").assertIsFocused()
        val before = compose.runOnIdle { grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset }

        openAndReturn("19")

        compose.onNodeWithTag("19").assertIsFocused()
        compose.runOnIdle {
            assertEquals(before, grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset)
            assertEquals(1, creations)
            assertEquals(0, disposals)
        }
    }

    private fun openAndReturn(tag: String) {
        // TV surfaces activate through the remote's center key.
        compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            assertEquals("Card click", tag, clickedId)
            assertEquals("Player navigation", Routes.PLAYER, controller.currentDestination?.route)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("native_player").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("native_player").assertExists()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("native_player").assertDoesNotExist()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun screen(isGrid: Boolean) {
        compose.setContent {
            BoostyVPTheme {
                val nav = rememberNavController()
                controller = nav
                NavHost(nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        DisposableEffect(Unit) {
                            creations++
                            onDispose { disposals++ }
                        }
                        val open: (String) -> Unit = {
                            clickedId = it
                            nav.navigate(Routes.player("test", it))
                        }
                        if (isGrid) Grid(open) else Rows(open)
                    }
                    playerDestination { _, _ ->
                        Box(Modifier.fillMaxSize().testTag("native_player")) {
                            AndroidView(
                                factory = { context ->
                                    PlayerView(context).apply { isFocusable = true; requestFocus() }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable private fun Rows(open: (String) -> Unit) {
        list = rememberLazyListState()
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("list")) {
            items(20, key = { it }) { row ->
                val state = rememberLazyListState()
                rows[row] = state
                LazyRow(
                    state = state,
                    contentPadding = PaddingValues(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.testTag("row_$row"),
                ) {
                    items(20, key = { it }) { column -> Card("${row}_$column", open) }
                }
            }
        }
    }

    @Composable private fun Grid(open: (String) -> Unit) {
        grid = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), state = grid,
            contentPadding = PaddingValues(32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize().testTag("grid"),
        ) { items(60, key = { it }) { Card(it.toString(), open) } }
    }

    @Composable private fun Card(id: String, open: (String) -> Unit) {
        val requester = remember { FocusRequester() }
        focus[id] = requester
        PostCard(
            post = Post(id, 0, "Test video $id", "test", "Test", 0, true,
                false, false, false, null, emptyList(), emptyList()),
            onClick = { open(id) },
            modifier = Modifier.testTag(id).focusRequester(requester),
        )
    }
}
