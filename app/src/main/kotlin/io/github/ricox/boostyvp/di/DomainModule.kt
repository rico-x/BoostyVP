package io.github.ricox.boostyvp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.domain.repository.FeedRepository
import io.github.ricox.boostyvp.core.domain.usecase.GetHomeFeed
import io.github.ricox.boostyvp.core.domain.repository.CatalogIndex
import io.github.ricox.boostyvp.core.domain.repository.PlaybackQueue
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides @Singleton fun provideCatalog() = CatalogIndex()
    @Provides @Singleton fun provideQueue() = PlaybackQueue()
    @Provides
    fun provideHomeFeed(feeds: FeedRepository, blogs: BlogRepository): GetHomeFeed = GetHomeFeed(feeds, blogs)
}
