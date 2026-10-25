package io.github.ricox.boostyvp.navigation

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Box
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.tvprovider.media.tv.TvContractCompat
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.domain.repository.*
import io.github.ricox.boostyvp.core.model.*
import io.github.ricox.boostyvp.core.ui.appearance.*
import io.github.ricox.boostyvp.core.ui.component.*
import io.github.ricox.boostyvp.core.ui.player.*
import io.github.ricox.boostyvp.core.ui.theme.BoostyVPTheme
import io.github.ricox.boostyvp.feature.home.*
import io.github.ricox.boostyvp.integration.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class CatalogIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val owner = MutableStateFlow<String?>("synthetic")
    private val account = object : CurrentAccount { override val key = owner; override suspend fun currentKey() = owner.value }
    private val posts = (0..5).map { index -> SavedPost("synthetic-$index", "synthetic-author", "Тестовое видео $index", "Тестовый автор", index.toLong(), null, true, "v$index", 60).toPost() }
    private val catalog = CatalogIndex()
    private val queue = PlaybackQueue()
    private var feedFails = false
    private val feeds = object : FeedRepository {
        override suspend fun feedPage(cursor: String?): Page<Post> { if (feedFails) throw java.io.IOException("synthetic offline"); return Page(posts, null, true) }
        override suspend fun subscriptions(limit: Int) = emptyList<Subscription>()
    }
    private val progress = object : PlaybackProgressRepository {
        override suspend fun save(progress: WatchProgress) {}
        override suspend fun resumePositionMs(postId: String, videoId: String) = 0L
        override suspend fun clear(postId: String, videoId: String) {}
        override fun continueWatching(limit: Int) = flowOf(emptyList<WatchProgress>())
        override fun progressByPost() = flowOf(emptyMap<String, Float>())
    }
    private val history = object : WatchHistoryRepository { override fun entries() = flowOf(emptyList<WatchProgress>()); override suspend fun removePost(postId: String) {} }
    private val library = object : PersonalLibraryRepository {
        override fun entries() = flowOf(PersonalLibrary())
        override suspend fun setLater(post: SavedPost, saved: Boolean) {}
        override suspend fun setFavoriteAuthor(blogUrl: String, title: String, favorite: Boolean) {}
        override suspend fun setWatched(postId: String, watched: Boolean) {}
        override suspend fun setFavoritePost(post: SavedPost, favorite: Boolean) {}
    }
    private val blogs = object : BlogRepository {
        override suspend fun blog(blogUrl: String): Blog = error("unused")
        override suspend fun post(blogUrl: String, postId: String): Post = error("unused")
        override suspend fun postsPage(blogUrl: String, cursor: String?) = Page(posts, null, true)
        override suspend fun recentPosts(blogUrl: String, limit: Int) = posts
    }
    private fun channels() = TvChannels(context, account, feeds, progress, history, library, catalog, blogs)
    @Before fun prepare() {
        check(context.packageName.endsWith(".debug")); device.wakeUp(); device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)
        clear(); catalog.activate("synthetic"); catalog.record("synthetic", posts); queue.activate("synthetic")
    }
    @After fun cleanup() {
        clear(); owner.value = null; runBlocking { channels().sync(false) }
        // Only disposable .debug channels created by this test, never the owner's release app.
        ownChannels().forEach { (id, _) -> context.contentResolver.delete(TvContractCompat.buildChannelUri(id), null, null) }
    }
    private fun clear() { listOf("player_preferences", "interface_preferences", "tv_channels").forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() } }

    @Test fun backupRoundTripPreservesLoginLibraryAndUnknownKeysAndRejectsBadTypesAtomically() {
        val prefs = context.getSharedPreferences("player_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString("private_sentinel", "must-stay").commit()
        PlayerPreferences(context).save(PlayerOptions(seekBackSeconds = 15, videoScale = VideoScale.Crop))
        CardSizePreferences(context).save(CardSize.Small)
        TvChannelPreferences(context).save(TvChannelOptions(count = 3))
        val backup = SettingsBackup(context); val text = backup.export()
        assertFalse(text.contains("private_sentinel")); assertFalse(text.contains("must-stay"))
        backup.reset(); assertEquals(PlayerOptions(), PlayerPreferences(context).load())
        backup.restore(text)
        assertEquals(15, PlayerPreferences(context).load().seekBackSeconds)
        assertEquals(VideoScale.Crop, PlayerPreferences(context).load().videoScale)
        assertEquals(CardSize.Small, CardSizePreferences(context).load()); assertEquals(3, TvChannelPreferences(context).load().count)
        assertEquals("must-stay", prefs.getString("private_sentinel", null))
        val before = backup.export()
        assertTrue(runCatching { backup.restore(text.replace("\"seek_back_seconds\":15", "\"seek_back_seconds\":\"bad\"")) }.isFailure)
        assertEquals(before, backup.export())
    }
    @Test fun channelsDefaultToNewAreIdempotentAndClearOnSignOut() = runBlocking {
        val sync = channels()
        assertTrue(sync.sync().startsWith("Обновлено каналов: 1"))
        val first = ownChannels(); assertEquals(1, first.size)
        assertTrue(first.single().second.contains("Новое"))
        val id = first.single().first
        assertEquals(6, programCount(id))
        assertTrue(sync.sync().startsWith("Обновлено каналов: 1")); assertEquals(first, ownChannels()); assertEquals(6, programCount(id))
        TvChannelPreferences(context).save(TvChannelOptions(count = 3))
        assertTrue(sync.sync().startsWith("Обновлено каналов: 3")); assertEquals(3, ownChannels().size)
        owner.value = null; sync.sync(false)
        assertEquals(0, programCount(id))
        // The default channel remains, so disabling/signing out does not destroy launcher approval.
        assertEquals(3, ownChannels().size)
    }
    @Test fun customChannelSourcesCountAndCardLimitsPersist() {
        val vm = IntegrationViewModel(account, catalog, queue, channels(), AppUpdates(context), blogs)
        compose.setContent { BoostyVPTheme { SettingsHubDialog(vm) {} } }
        activate("Каналы Android TV / Projectivy")
        compose.onNodeWithText("Число каналов: 1").assertIsDisplayed()
        activate("Число каналов: 1")
        activate("Канал 1: Новое")
        compose.onNodeWithText("Канал 1: Продолжить просмотр").assertIsDisplayed()
        assertEquals(2, TvChannelPreferences(context).load().count)
        assertEquals(TvFeedSource.Continue, TvChannelPreferences(context).load().sources.first())
    }
    @Test fun networkFailurePreservesOnlySameAccountAndSameSourceCards() = runBlocking {
        val sync = channels(); sync.sync()
        val id = ownChannels().single().first
        assertEquals(6, programCount(id))
        feedFails = true; sync.sync()
        assertEquals(6, programCount(id))
        owner.value = "different-account"; sync.sync()
        assertEquals(0, programCount(id))
        owner.value = "synthetic"; feedFails = false; sync.sync()
        assertEquals(6, programCount(id))
        TvChannelPreferences(context).save(TvChannelOptions().changeSource(0))
        sync.sync()
        assertEquals(0, programCount(id))
    }
    @Test fun searchFiltersLoadedMetadataAndQueueCanBeReorderedWithRemote() {
        queue.add(SavedPost.from(posts[0])); queue.add(SavedPost.from(posts[1]))
        var opened: String? = null
        compose.setContent { BoostyVPTheme {
            val items by queue.items.collectAsState()
            CompositionLocalProvider(LocalCatalogControls provides CatalogControls(posts, items, true, queue::add, queue::remove, queue::move, queue::clear)) {
                HomeLayout(HomeUiState.Content(emptyList()), { opened = it.id }, {}, {}, {})
            }
        } }
        activate("Поиск")
        compose.onNodeWithTag("catalog_query").performClick().performTextInput("видео 3")
        device.pressBack(); compose.waitForIdle()
        compose.onNodeWithText("Тестовое видео 3").assertExists()
        compose.onNodeWithText("Тестовое видео 2").assertDoesNotExist()
        compose.onNodeWithTag("sidebar_queue").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        device.pressDPadCenter(); compose.waitForIdle()
        compose.onNodeWithText("1. Тестовое видео 0").assertExists()
        compose.onAllNodesWithText("Ниже")[0].performSemanticsAction(SemanticsActions.RequestFocus)
        device.pressDPadCenter(); compose.waitForIdle()
        assertEquals("synthetic-1", queue.items.value.first().postId)
        activate("1. Тестовое видео 1"); assertEquals("synthetic-1", opened)
    }
    @Test fun contentLinksRoundTripAndRejectUnrelatedUrls() {
        val intent = ContentLinks.intent(context, "тестовый-автор", "id-with-space value")
        assertEquals(ContentTarget("тестовый-автор", "id-with-space value"), ContentLinks.read(intent.data))
        assertNull(ContentLinks.read(android.net.Uri.parse("https://example.org/post/a/b")))
        assertNull(ContentLinks.read(android.net.Uri.parse("boostyvp://post/a/b/c")))
    }
    @Test fun channelLinkWaitsForSignInHomeNavigationAndIsConsumedExactlyOnce() {
        var signedIn by mutableStateOf(false)
        var target by mutableStateOf<ContentTarget?>(ContentTarget("author", "post"))
        var handled = 0
        lateinit var nav: androidx.navigation.NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Routes.AUTH) {
                composable(Routes.AUTH) { Box(Modifier) }
                composable(Routes.HOME) { Box(Modifier) }
                playerDestination { _, _, _ -> Box(Modifier) }
            }
            HandleContentTarget(nav, signedIn, target) { handled++; target = null }
        }
        compose.runOnIdle { signedIn = true }
        compose.waitForIdle()
        assertEquals(0, handled)
        compose.runOnIdle { nav.navigate(Routes.HOME) { popUpTo(Routes.AUTH) { inclusive = true } } }
        compose.waitUntil(3_000) { handled == 1 }
        compose.runOnIdle { assertEquals(Routes.PLAYER, nav.currentDestination?.route) }
        compose.waitForIdle(); assertEquals(1, handled)
    }
    @Test fun pendingAuthorsAreNotReportedAsEmptyByTheUnwatchedFilter() {
        val blog = Blog("author", "author", "Автор", null, null, 0, 0, true, false, null)
        compose.setContent { BoostyVPTheme {
            HomeLayout(HomeUiState.Content(listOf(HomeRow(blog, emptyList(), loading = true)), refreshing = true), {}, {}, {}, {})
        } }
        activate("Только новые")
        compose.onNodeWithText("Загружается…").assertExists()
        compose.onNodeWithText("Непросмотренных публикаций пока нет").assertDoesNotExist()
    }
    private fun activate(text: String) {
        compose.onNodeWithText(text).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        device.pressDPadCenter(); compose.waitForIdle()
    }
    private fun ownChannels(): List<Pair<Long, String>> {
        val values = mutableListOf<Pair<Long, String>>()
        context.contentResolver.query(TvContractCompat.Channels.CONTENT_URI, arrayOf("_id", "display_name", "package_name", "internal_provider_id"), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(2) == context.packageName && c.getString(3)?.startsWith("boostyvp.slot.") == true) values += c.getLong(0) to c.getString(1)
        }; return values.sortedBy { it.first }
    }
    private fun programCount(id: Long) = context.contentResolver.query(TvContractCompat.buildPreviewProgramsUriForChannel(id), arrayOf("_id"), null, null, null)?.use { it.count } ?: 0
}
