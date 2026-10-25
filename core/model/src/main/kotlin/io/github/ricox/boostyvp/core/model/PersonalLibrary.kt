package io.github.ricox.boostyvp.core.model

/** Local card metadata only. Never contains access tokens, post bodies or media sources. */
data class SavedPost(
    val postId: String, val blogUrl: String, val title: String, val blogTitle: String,
    val publishedAt: Long, val previewUrl: String?, val hasVideo: Boolean,
    val videoId: String, val durationSeconds: Int,
) {
    fun toPost() = Post(postId, 0, title, blogUrl, blogTitle, publishedAt, true,
        false, false, false, null,
        if (hasVideo) listOf(ContentBlock.Video(videoId, null, null, durationSeconds,
            null, null, previewUrl, null, null, emptyList(), true))
        else previewUrl?.let { listOf(ContentBlock.Image("preview", it, null, null)) }.orEmpty(), emptyList())

    companion object {
        fun from(post: Post): SavedPost {
            val video = post.primaryVideo ?: post.videos.firstOrNull()
            return SavedPost(post.id, post.blogUrl, post.title, post.blogTitle, post.publishedAt,
                post.thumbnailUrl, post.hasVideo, video?.id.orEmpty(), video?.duration ?: 0)
        }
    }
}

data class FavoriteAuthor(val blogUrl: String, val title: String)

data class PersonalLibrary(
    val later: List<SavedPost> = emptyList(),
    val authors: List<FavoriteAuthor> = emptyList(),
    val watched: Set<String> = emptySet(),
    val favorites: List<SavedPost> = emptyList(),
)
