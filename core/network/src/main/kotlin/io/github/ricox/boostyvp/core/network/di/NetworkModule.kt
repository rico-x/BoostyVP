package io.github.ricox.boostyvp.core.network.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ricox.boostyvp.core.domain.auth.SessionManager
import io.github.ricox.boostyvp.core.domain.auth.TokenRefresher
import io.github.ricox.boostyvp.core.domain.auth.PhoneLogin
import io.github.ricox.boostyvp.core.network.BoostyPhoneLogin
import io.github.ricox.boostyvp.core.network.BoostyApi
import io.github.ricox.boostyvp.core.network.BoostyTokenRefresher
import io.github.ricox.boostyvp.core.network.interceptor.AuthInterceptor
import io.github.ricox.boostyvp.core.network.interceptor.BoostyHeadersInterceptor
import io.github.ricox.boostyvp.core.network.interceptor.TokenAuthenticator
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BoostyOkHttp

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BoostyRefresh

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindsModule {
    @Binds
    @Singleton
    abstract fun bindTokenRefresher(impl: BoostyTokenRefresher): TokenRefresher

    @Binds
    @Singleton
    abstract fun bindPhoneLogin(impl: BoostyPhoneLogin): PhoneLogin
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Lenient on purpose. Every setting here exists to survive a change to an
     * undocumented API without failing the whole response:
     *  - ignoreUnknownKeys: Boosty adds fields constantly.
     *  - coerceInputValues: a null where a non-null primitive is declared
     *    becomes the default instead of throwing.
     *  - explicitNulls = false: keeps request bodies compact.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /**
     * Refresh uses a separate unauthenticated API, so this client can depend on
     * SessionManager without creating an injection cycle.
     */
    @Provides
    @Singleton
    @BoostyOkHttp
    fun provideOkHttpClient(
        sessionManager: SessionManager,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BoostyHeadersInterceptor())
        .addInterceptor(AuthInterceptor(sessionManager))
        .authenticator(TokenAuthenticator(sessionManager))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        @BoostyOkHttp client: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BoostyApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideBoostyApi(retrofit: Retrofit): BoostyApi = retrofit.create(BoostyApi::class.java)

    // AuthInterceptor and TokenAuthenticator block an OkHttp worker while
    // refreshing. A separate dispatcher lets the refresh run even when every
    // API worker is waiting for it. No auth interceptor belongs on this client.
    @Provides
    @Singleton
    @BoostyRefresh
    fun provideRefreshOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BoostyHeadersInterceptor())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @BoostyRefresh
    fun provideRefreshApi(@BoostyRefresh client: OkHttpClient, json: Json): BoostyApi = Retrofit.Builder()
        .baseUrl(BoostyApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BoostyApi::class.java)
}
