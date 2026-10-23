package io.github.ricox.boostyvp.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ricox.boostyvp.core.data.repository.DefaultBlogRepository
import io.github.ricox.boostyvp.core.data.repository.DefaultFeedRepository
import io.github.ricox.boostyvp.core.data.repository.SharedPreferencesPlaybackProgressRepository
import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.domain.repository.FeedRepository
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindBlogRepository(impl: DefaultBlogRepository): BlogRepository

    @Binds
    abstract fun bindFeedRepository(impl: DefaultFeedRepository): FeedRepository

    @Binds
    abstract fun bindPlaybackProgress(impl: SharedPreferencesPlaybackProgressRepository): PlaybackProgressRepository
}
