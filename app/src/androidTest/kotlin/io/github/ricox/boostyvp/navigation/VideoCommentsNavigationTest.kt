package io.github.ricox.boostyvp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import io.github.ricox.boostyvp.core.model.*
import io.github.ricox.boostyvp.core.ui.component.*
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class VideoCommentsNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val post = Post("post/+%25", 0, "Video publication", "author+name %2F", "Author", 0,
        true, false, false, false, null, listOf(ContentBlock.Video("v", null, null, 10,
            null, null, null, null, null, emptyList(), true)), emptyList())

    @Test fun longOkMenuOpensCommentsWithoutPlaybackAndBackRestoresCard() {
        var played = false
        var received: Pair<String, String>? = null
        lateinit var nav: NavHostController
        compose.setContent { BoostyVPTheme {
            nav = rememberNavController()
            CompositionLocalProvider(LocalPostComments provides {
                nav.navigate(Routes.comments(it.blogUrl, it.id))
            }) {
                NavHost(nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        val focus = remember { FocusRequester() }
                        var actions by remember { mutableStateOf(false) }
                        PostCard(post, { played = true }, Modifier.focusRequester(focus).testTag("video_card"),
                            onLongClick = { actions = true })
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        if (actions) PostActionsDialog(post, { played = true }, { actions = false })
                    }
                    commentsDestination { blog, id ->
                        received = blog to id
                        Box(Modifier.fillMaxSize().testTag("video_comments_screen"))
                    }
                }
            }
        } }
        compose.onNodeWithTag("video_card").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithTag("post_action_comments").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("post_action_comments").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("video_comments_screen").assertExists()
        compose.runOnIdle { assertEquals(post.blogUrl to post.id, received); assertFalse(played) }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("video_card").assertIsFocused()
        compose.onNodeWithTag("post_actions").assertDoesNotExist()
    }
}
