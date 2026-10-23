package io.github.ricox.boostyvp.core.network

import io.github.ricox.boostyvp.core.network.dto.BlogDto
import io.github.ricox.boostyvp.core.network.dto.CommentDto
import io.github.ricox.boostyvp.core.network.dto.CurrentUserDto
import io.github.ricox.boostyvp.core.network.dto.FeedResponseDto
import io.github.ricox.boostyvp.core.network.dto.PagedResponse
import io.github.ricox.boostyvp.core.network.dto.PostDto
import io.github.ricox.boostyvp.core.network.dto.SubscriptionLevelDto
import io.github.ricox.boostyvp.core.network.dto.TokenResponseDto
import io.github.ricox.boostyvp.core.network.dto.PhoneCodeResponseDto
import io.github.ricox.boostyvp.core.network.dto.UserSubscriptionDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Boosty's private JSON API. Base URL: https://api.boosty.to/
 *
 * Endpoints marked VERIFIED were probed live on 2026-08-18. Endpoints marked
 * UNVERIFIED are inferred and must be confirmed in DevTools before use.
 */
interface BoostyApi {

    /** VERIFIED -- works without auth. */
    @GET("v1/blog/{blog}")
    suspend fun getBlog(@Path("blog") blog: String): BlogDto

    /** VERIFIED -- works without auth; returns richer data when authenticated. */
    @GET("v1/blog/{blog}/post/")
    suspend fun getBlogPosts(
        @Path("blog") blog: String,
        @Query("limit") limit: Int = DEFAULT_PAGE_SIZE,
        @Query("offset") offset: String? = null,
        @Query("comments_limit") commentsLimit: Int = 0,
        @Query("reply_limit") replyLimit: Int = 0,
    ): PagedResponse<PostDto>

    /** VERIFIED. */
    @GET("v1/blog/{blog}/post/{postId}")
    suspend fun getPost(
        @Path("blog") blog: String,
        @Path("postId") postId: String,
        @Query("comments_limit") commentsLimit: Int = 0,
        @Query("reply_limit") replyLimit: Int = 0,
    ): PostDto

    /** Read-only; verified against Boosty's web client and public API 2026-10-23.
     * `offset` is a comment intId, NOT a numeric item offset. With order=top,
     * continue before the oldest returned intId. Replies use the same endpoint.
     */
    @GET("v1/blog/{blog}/post/{postId}/comment/")
    suspend fun getComments(
        @Path("blog") blog: String,
        @Path("postId") postId: String,
        @Query("offset") offset: String? = null,
        @Query("parent_id") parentId: Long? = null,
        @Query("limit") limit: Int = 20,
        @Query("reply_limit") replyLimit: Int = 0,
        @Query("order") order: String = "top",
    ): PagedResponse<CommentDto>

    /** VERIFIED. */
    @GET("v1/blog/{blog}/subscription_level/")
    suspend fun getSubscriptionLevels(
        @Path("blog") blog: String,
        @Query("show_free_level") showFreeLevel: Boolean = true,
    ): PagedResponse<SubscriptionLevelDto>

    /** VERIFIED to exist -- returns 401 without a token. The "my channels" list. */
    @GET("v1/user/subscriptions")
    suspend fun getSubscriptions(
        @Query("limit") limit: Int = DEFAULT_PAGE_SIZE,
        @Query("offset") offset: String? = null,
        @Query("with_follow") withFollow: Boolean = true,
    ): PagedResponse<UserSubscriptionDto>

    /**
     * VERIFIED on a signed-in TV 2026-10-23 -- the subscriptions feed, newest first.
     * Unlike the per-blog list, `data` is an object: posts are in `data.posts`
     * and name their blog only through `user.blogUrl`.
     */
    @GET("v1/feed/post/")
    suspend fun getFeed(
        @Query("limit") limit: Int = DEFAULT_PAGE_SIZE,
        @Query("offset") offset: String? = null,
        @Query("only_allowed") onlyAllowed: Boolean = true,
    ): FeedResponseDto

    /**
     * VERIFIED on a signed-in TV 2026-10-23. The response also carries personal
     * fields (e-mail, settings); [CurrentUserDto] deliberately decodes only `id`.
     */
    @GET("v1/user/current")
    suspend fun getCurrentUser(): CurrentUserDto

    /** Verified against Boosty's own web client on 2026-10-19. */
    @FormUrlEncoded
    @POST("oauth/token/")
    suspend fun refreshToken(
        @Field("device_id") deviceId: String,
        @Field("device_os") deviceOs: String = "web",
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Header("X-From-Id") fromId: String = deviceId,
    ): TokenResponseDto

    @FormUrlEncoded
    @POST("auth/phone/verification_code/send")
    suspend fun sendPhoneCode(
        @Field("phone") phone: String,
        @Field("device_id") deviceId: String,
        @Field("device_os") deviceOs: String = "web",
        @Header("X-From-Id") fromId: String = deviceId,
    ): PhoneCodeResponseDto

    @FormUrlEncoded
    @PUT("auth/phone/verification_code/confirm")
    suspend fun confirmPhoneCode(
        @Field("phone") phone: String,
        @Field("code") code: String,
        @Field("sms_code") smsCode: String,
        @Field("device_id") deviceId: String,
        @Field("device_os") deviceOs: String = "web",
        @Header("X-From-Id") fromId: String = deviceId,
    ): TokenResponseDto

    companion object {
        const val BASE_URL = "https://api.boosty.to/"
        const val DEFAULT_PAGE_SIZE = 20
    }
}
