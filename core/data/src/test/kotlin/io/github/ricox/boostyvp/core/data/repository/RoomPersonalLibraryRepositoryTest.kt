package io.github.ricox.boostyvp.core.data.repository

import io.github.ricox.boostyvp.core.data.library.*
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.model.SavedPost
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RoomPersonalLibraryRepositoryTest {
    private class Dao : PersonalLibraryDao {
        val posts = MutableStateFlow<List<SavedPostEntity>>(emptyList())
        val people = MutableStateFlow<List<FavoriteAuthorEntity>>(emptyList())
        val marks = MutableStateFlow<List<WatchedPostEntity>>(emptyList())
        val favoritePosts = MutableStateFlow<List<SavedPostEntity>>(emptyList())
        override fun favorites(account: String) = favoritePosts.map { it.filter { post -> post.account == account } }
        override suspend fun saveFavorite(post: FavoritePostEntity) {
            favoritePosts.value = favoritePosts.value.filterNot { it.account == post.post.account && it.postId == post.post.postId } + post.post
        }
        override suspend fun removeFavorite(account: String, postId: String) {
            favoritePosts.value = favoritePosts.value.filterNot { it.account == account && it.postId == postId }
        }
        override fun later(account: String) = posts.map { rows -> rows.filter { it.account == account }.sortedByDescending { it.addedAt } }
        override suspend fun saveLater(post: SavedPostEntity) { posts.value = posts.value.filterNot { it.account == post.account && it.postId == post.postId } + post }
        override suspend fun removeLater(account: String, postId: String) { posts.value = posts.value.filterNot { it.account == account && it.postId == postId } }
        override fun authors(account: String) = people.map { rows -> rows.filter { it.account == account } }
        override suspend fun saveAuthor(author: FavoriteAuthorEntity) { people.value = people.value.filterNot { it.account == author.account && it.blogUrl == author.blogUrl } + author }
        override suspend fun removeAuthor(account: String, blogUrl: String) { people.value = people.value.filterNot { it.account == account && it.blogUrl == blogUrl } }
        override fun watched(account: String) = marks.map { rows -> rows.filter { it.account == account } }
        override suspend fun setWatched(post: WatchedPostEntity) { marks.value = marks.value.filterNot { it.account == post.account && it.postId == post.postId } + post }
    }
    private val account = object : CurrentAccount {
        override val key = MutableStateFlow<String?>("a")
        override suspend fun currentKey() = key.value
    }
    private val dao = Dao()
    private val repository = RoomPersonalLibraryRepository(dao, account)
    private val post = SavedPost("p", "author", "Title", "Author", 10, null, true, "v", 600)

    @Test fun `favorite videos are deduplicated persistent metadata and isolated from watch later`() = runTest {
        repeat(2) { repository.setFavoritePost(post, true) }
        repository.setLater(post, true)
        assertEquals(1, repository.entries().first().favorites.size)
        account.key.value = "b"
        assertTrue(repository.entries().first().favorites.isEmpty())
        repository.setFavoritePost(post.copy(title = "B"), true)
        repository.setFavoritePost(post, false)
        account.key.value = "a"
        assertEquals("Title", repository.entries().first().favorites.single().title)
        repository.setFavoritePost(post, false)
        assertTrue(repository.entries().first().favorites.isEmpty())
        assertEquals(1, repository.entries().first().later.size)
        assertTrue(runCatching { repository.setFavoritePost(post.copy(hasVideo = false), true) }.isFailure)
    }

    @Test fun `all lists are isolated between accounts and signed out is empty`() = runTest {
        repository.setLater(post, true); repository.setFavoriteAuthor("author", "Author", true); repository.setWatched("p", true)
        assertEquals(1, repository.entries().first().later.size)
        account.key.value = "b"
        assertTrue(repository.entries().first().later.isEmpty())
        assertTrue(repository.entries().first().authors.isEmpty())
        assertTrue(repository.entries().first().watched.isEmpty())
        repository.setLater(post.copy(title = "B"), true)
        account.key.value = "a"
        assertEquals("Title", repository.entries().first().later.single().title)
        account.key.value = null
        assertTrue(repository.entries().first().later.isEmpty())
        assertTrue(runCatching { repository.setWatched("p", true) }.isFailure)
    }
    @Test fun `repeated saves do not duplicate and removal does not affect other lists`() = runTest {
        repeat(2) { repository.setLater(post, true); repository.setFavoriteAuthor("author", "Author", true) }
        repository.setWatched("p", true)
        assertEquals(1, repository.entries().first().later.size)
        assertEquals(1, repository.entries().first().authors.size)
        repository.setLater(post, false)
        assertTrue(repository.entries().first().later.isEmpty())
        assertEquals(setOf("p"), repository.entries().first().watched)
        assertEquals(1, repository.entries().first().authors.size)
        repository.setWatched("p", false)
        assertTrue(repository.entries().first().watched.isEmpty())
        repository.setFavoriteAuthor("author", "Author", false)
        assertTrue(repository.entries().first().authors.isEmpty())
    }
    @Test fun `removing the same identifiers cannot remove another accounts library`() = runTest {
        for (key in listOf("a", "b")) {
            account.key.value = key
            repository.setLater(post, true); repository.setFavoriteAuthor("author", "Author", true); repository.setWatched("p", true)
        }
        repository.setLater(post, false); repository.setFavoriteAuthor("author", "Author", false); repository.setWatched("p", false)
        account.key.value = "a"
        val a = repository.entries().first()
        assertEquals(1, a.later.size); assertEquals(1, a.authors.size); assertEquals(setOf("p"), a.watched)
    }
    @Test fun `saved video and text cards never reconstruct playback sources`() {
        val video = post.toPost()
        assertTrue(video.hasVideo)
        assertFalse(video.isPlayable)
        assertNull(video.signedQuery)
        assertTrue(video.videos.single().sources.isEmpty())
        val text = post.copy(hasVideo = false, previewUrl = "https://example.org/cover.jpg").toPost()
        assertFalse(text.hasVideo)
        assertEquals("https://example.org/cover.jpg", text.thumbnailUrl)
    }
}
