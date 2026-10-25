package io.github.ricox.boostyvp.integration

import io.github.ricox.boostyvp.localization.tr
import io.github.ricox.boostyvp.localization.UiText

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.tvprovider.media.tv.*
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.domain.repository.*
import io.github.ricox.boostyvp.core.model.Post
import io.github.ricox.boostyvp.core.model.SavedPost
import io.github.ricox.boostyvp.core.model.WatchProgress
import io.github.ricox.boostyvp.core.ui.appearance.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton

/** Only owns records bearing this app's package AND our internal ID prefix. */
@Singleton
class TvChannels @Inject constructor(@ApplicationContext private val context: Context,
    private val account: CurrentAccount, private val feeds: FeedRepository,
    private val progress: PlaybackProgressRepository, private val history: WatchHistoryRepository,
    private val library: PersonalLibraryRepository, private val catalog: CatalogIndex,
    private val blogs: BlogRepository) {
    private val mutex = Mutex()
    private val resolver get() = context.contentResolver
    private val prefix = "boostyvp.slot."

    suspend fun sync(fetchFresh: Boolean = true): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < 26) return@withContext tr(UiText.tv_channels_001)
            try {
                val owner = account.currentKey()
                val options = TvChannelPreferences(context).load()
                val channels = ownChannels()
                val syncState = context.getSharedPreferences("tv_channel_sync", Context.MODE_PRIVATE)
                if (owner == null || !options.enabled) {
                    channels.values.forEach { clearPrograms(it) }
                    clearWatchNext()
                    syncState.edit().remove("owner").apply()
                    return@withContext if (owner == null) tr(UiText.tv_channels_002) else tr(UiText.tv_channels_003)
                }
                // Never retain another account's or another source's cards under a new channel label.
                if (syncState.getString("owner", null) != owner) {
                    channels.values.forEach { clearPrograms(it) }; clearWatchNext()
                    syncState.edit().putString("owner", owner).apply()
                }
                options.sources.take(options.count).forEachIndexed { slot, source ->
                    channels["$prefix$slot"]?.let { id ->
                        if (syncState.getString("source_$id", null) != source.id) {
                            clearPrograms(id); syncState.edit().putString("source_$id", source.id).apply()
                        }
                    }
                }
                val personal = library.entries().first()
                val continuing = progress.continueWatching(50).first()
                val recent = if (TvFeedSource.History in options.sources.take(options.count)) history.entries().first().take(50) else emptyList()
                val fresh = if (fetchFresh && TvFeedSource.New in options.sources.take(options.count)) {
                    try { freshVideos(options.cards) } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                } else null
                val favorites = if (fetchFresh && TvFeedSource.Favorites in options.sources.take(options.count)) coroutineScope {
                    val requests = Semaphore(4)
                    val results = personal.authors.take(20).map { author -> async {
                        requests.withPermit {
                            try { blogs.recentPosts(author.blogUrl).let { Result.success(it) } }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { Result.failure<List<Post>>(e) }
                        }
                    } }.awaitAll()
                    if (results.isNotEmpty() && results.all { it.isFailure }) null
                    else results.mapNotNull { it.getOrNull() }.flatten().distinctBy { it.id }.sortedByDescending { it.publishedAt }
                } else catalog.posts.value.filter { post -> personal.authors.any { it.blogUrl == post.blogUrl } }.sortedByDescending { it.publishedAt }
                if (account.currentKey() != owner) return@withContext tr(UiText.tv_channels_004)
                for ((slot, source) in options.sources.take(options.count).withIndex()) {
                    coroutineContext.ensureActive()
                    val key = "$prefix$slot"
                    val id = channels[key] ?: createChannel(slot, source.label).also { channels[key] = it }
                    syncState.edit().putString("source_$id", source.id).apply()
                    resolver.update(TvContractCompat.buildChannelUri(id), Channel.Builder()
                        .setDisplayName("BoostyVP · ${source.label}").setAppLinkIntentUri(android.net.Uri.parse(ContentLinks.intent(context).toUri(Intent.URI_INTENT_SCHEME))).build().toContentValues(), null, null)
                    val posts: List<Post>? = when (source) {
                        TvFeedSource.New -> fresh?.sortedByDescending { it.publishedAt }
                        TvFeedSource.Continue -> continuing.map { it.toCardPost() }
                        TvFeedSource.Later -> personal.later.map { it.toPost() }
                        TvFeedSource.History -> recent.map { it.toCardPost() }
                        TvFeedSource.Favorites -> favorites
                    }
                    // Preserve a previously successful channel on a transient network error.
                    if (posts != null) updatePrograms(id, posts.filter { it.hasAccess && it.hasVideo && !it.isWaitingVideo }.take(options.cards))
                }
                channels.filterKeys { it.removePrefix(prefix).toIntOrNull()?.let { n -> n >= options.count } == true }
                    .forEach { (_, id) -> clearPrograms(id); resolver.delete(TvContractCompat.buildChannelUri(id), null, null) }
                if (options.watchNext) updateWatchNext(continuing.take(options.cards)) else clearWatchNext()
                val stale = if (fetchFresh && TvFeedSource.New in options.sources.take(options.count) && fresh == null)
                    tr(UiText.tv_channels_005) else ""
                tr(UiText.tv_channels_006, options.count, stale)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { tr(UiText.tv_channels_007) }
        }
    }

    private suspend fun freshVideos(limit: Int): List<Post> {
        val posts = linkedMapOf<String, Post>()
        val visited = mutableSetOf<String>()
        var cursor: String? = null
        repeat(5) {
            val page = feeds.feedPage(cursor)
            page.items.filter { it.hasAccess && it.hasVideo && !it.isWaitingVideo }.forEach { posts[it.id] = it }
            if (posts.size >= limit || page.isLast || page.nextCursor == null) return posts.values.toList()
            cursor = page.nextCursor
            check(visited.add(requireNotNull(cursor))) { tr(UiText.tv_channels_008) }
        }
        return posts.values.toList()
    }

    private fun ownChannels(): MutableMap<String, Long> {
        val result = mutableMapOf<String, Long>()
        resolver.query(TvContractCompat.Channels.CONTENT_URI, arrayOf("_id", "internal_provider_id", "package_name"), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) if (cursor.getString(2) == context.packageName && cursor.getString(1)?.startsWith(prefix) == true)
                result[cursor.getString(1)] = cursor.getLong(0)
        }
        return result
    }

    private fun createChannel(slot: Int, label: String): Long {
        val uri = requireNotNull(resolver.insert(TvContractCompat.Channels.CONTENT_URI, Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW).setDisplayName("BoostyVP · $label")
            .setInternalProviderId("$prefix$slot").setAppLinkIntentUri(android.net.Uri.parse(ContentLinks.intent(context).toUri(Intent.URI_INTENT_SCHEME))).build().toContentValues()))
        val id = ContentUris.parseId(uri)
        val size = (80 * context.resources.displayMetrics.density).toInt().coerceAtLeast(80)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(android.graphics.Color.BLACK)
        context.getDrawable(io.github.ricox.boostyvp.R.drawable.ic_launcher_foreground)?.apply { setBounds(0, 0, size, size); draw(canvas) }
        ChannelLogoUtils.storeChannelLogo(context, id, bitmap); bitmap.recycle()
        if (slot == 0) TvContractCompat.requestChannelBrowsable(context, id)
        return id
    }

    private fun clearPrograms(id: Long) { resolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(id), null, null) }

    // tvprovider 1.1.0 publicly documents these Builder setters in PreviewProgram.java.
    // Lint propagates @RestrictTo from the hidden BasePreviewProgram/BaseProgram
    // superclass although the final public Builder exposes this inherited API.
    @SuppressLint("RestrictedApi")
    private fun updatePrograms(channel: Long, posts: List<Post>) {
        val existing = mutableMapOf<String, Long>()
        resolver.query(TvContractCompat.buildPreviewProgramsUriForChannel(channel), arrayOf("_id", "internal_provider_id"), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) existing[cursor.getString(1)] = cursor.getLong(0)
        }
        posts.forEachIndexed { index, post ->
            val values = PreviewProgram.Builder().setChannelId(channel).setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                .setTitle(post.title).setDescription(post.blogTitle).setAuthor(post.blogTitle)
                .setInternalProviderId(post.id).setContentId(post.id).setWeight(posts.size - index)
                .setDurationMillis(((post.primaryVideo ?: post.videos.firstOrNull())?.duration ?: 0).coerceAtMost(Int.MAX_VALUE / 1000) * 1000)
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .setPosterArtUri(post.thumbnailUrl?.let(android.net.Uri::parse))
                .setIntentUri(android.net.Uri.parse(ContentLinks.intent(context, post.blogUrl, post.id).toUri(Intent.URI_INTENT_SCHEME)))
                .build().toContentValues()
            val id = existing.remove(post.id)
            if (id == null) resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, values)
            else resolver.update(TvContractCompat.buildPreviewProgramUri(id), values, null, null)
        }
        existing.values.forEach { resolver.delete(TvContractCompat.buildPreviewProgramUri(it), null, null) }
    }

    private fun ownWatchNext(): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, arrayOf("_id", "internal_provider_id", "package_name"), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) if (cursor.getString(2) == context.packageName && cursor.getString(1)?.startsWith("boostyvp.continue.") == true)
                result[cursor.getString(1)] = cursor.getLong(0)
        }; return result
    }
    private fun clearWatchNext() { ownWatchNext().values.forEach { resolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null) } }
    // Same public inherited Builder API as PreviewProgram above (tvprovider 1.1.0).
    @SuppressLint("RestrictedApi")
    private fun updateWatchNext(posts: List<io.github.ricox.boostyvp.core.model.WatchProgress>) {
        val existing = ownWatchNext().toMutableMap()
        posts.forEach { post ->
            val key = "boostyvp.continue.${post.postId}"
            val values = WatchNextProgram.Builder().setInternalProviderId(key).setContentId(post.postId)
                .setType(TvContractCompat.WatchNextPrograms.TYPE_CLIP)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(post.updatedAt).setTitle(post.title).setDescription(post.blogTitle)
                .setDurationMillis(post.durationMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
                .setLastPlaybackPositionMillis(post.positionMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
                .setPosterArtAspectRatio(TvContractCompat.WatchNextPrograms.ASPECT_RATIO_16_9)
                .setPosterArtUri(post.previewUrl?.let(android.net.Uri::parse))
                .setIntentUri(android.net.Uri.parse(ContentLinks.intent(context, post.blogUrl, post.postId).toUri(Intent.URI_INTENT_SCHEME)))
                .build().toContentValues()
            val id = existing.remove(key)
            if (id == null) resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
            else resolver.update(TvContractCompat.buildWatchNextProgramUri(id), values, null, null)
        }
        existing.values.forEach { resolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null) }
    }
}

private fun WatchProgress.toCardPost(): Post = SavedPost(postId, blogUrl, title, blogTitle, 0,
    previewUrl, true, videoId, (durationMs / 1000).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()).toPost()
